/*
 * Copyright © 2019-2022 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.sapl.mqtt.pep;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.hivemq.client.mqtt.MqttGlobalPublishFilter;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import com.hivemq.client.mqtt.mqtt5.message.subscribe.Mqtt5Subscribe;
import com.hivemq.embedded.EmbeddedExtension;
import com.hivemq.embedded.EmbeddedHiveMQ;

import ch.qos.logback.classic.Level;
import io.sapl.interpreter.InitializationException;
import io.sapl.mqtt.pep.config.SaplMqttExtensionConfig;

@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RemotePdpUsageTest extends SaplMqttPepTest {

    private static final String xmlFilePath = "src/test/resources/config/remote/sapl-extension-config.xml";

    @Container
    static final GenericContainer<?> saplServerLt =
            new GenericContainer<>(DockerImageName.parse("ghcr.io/heutelbeck/sapl-server-lt:2.1.0-snapshot"))
                    .withCopyFileToContainer(MountableFile.forHostPath("src/test/resources/policies"),
                            "/pdp/data")
                    .withExposedPorts(8080);


    @BeforeAll
    public static void beforeAll() throws InitializationException, ParserConfigurationException, TransformerException {
        // set logging level
        rootLogger.setLevel(Level.INFO);

        createExtensionConfigFile(saplServerLt.getFirstMappedPort());

        embeddedHiveMq = startEmbeddedBrokerWithRemotePdp();
        mqttClientSubscribe = startMqttClient("MQTT_CLIENT_SUBSCRIBE");
        mqttClientPublish = startMqttClient("MQTT_CLIENT_PUBLISH");
    }

    @AfterAll
    public static void afterAll() {
        if (mqttClientPublish.getState().isConnected()) {
            mqttClientPublish.disconnect();
        }
        if (mqttClientSubscribe.getState().isConnected()) {
            mqttClientSubscribe.disconnect();
        }
        embeddedHiveMq.stop().join();
    }

    @Test
    @Order(1)
    @Timeout(10)
    void when_publishAndSubscribeForTopicPermitted_then_subscribeAndPublishTopic() throws InterruptedException {
        // GIVEN
        Mqtt5Subscribe subscribeMessage = buildMqttSubscribeMessage("topic");

        Mqtt5Publish publishMessage = buildMqttPublishMessage("topic",
                false);

        // WHEN
        mqttClientSubscribe.subscribe(subscribeMessage);
        mqttClientPublish.publish(publishMessage);

        // THEN
        Mqtt5Publish receivedMessage = mqttClientSubscribe.publishes(MqttGlobalPublishFilter.ALL).receive();

        assertEquals(publishMessagePayload, new String(receivedMessage.getPayloadAsBytes()));

        // FINALLY
        mqttClientSubscribe.unsubscribeWith().topicFilter("topic").send();
    }

    @Test
    @Order(2)
    @Timeout(10)
    void when_losingConnectionToPdpServer_then_DenyOnIndeterminate() {
        // GIVEN
        Mqtt5Subscribe subscribeMessage = buildMqttSubscribeMessage("topic");

        Mqtt5Publish publishMessage = buildMqttPublishMessage("topic", 1, true);

        // WHEN
        mqttClientSubscribe.subscribe(subscribeMessage);
        mqttClientPublish.publish(publishMessage);

        saplServerLt.stop();

        // THEN
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            assertFalse(saplServerLt.isRunning());
            assertFalse(mqttClientSubscribe.getState().isConnected());
            assertFalse(mqttClientPublish.getState().isConnected());
        });
    }

    private static EmbeddedHiveMQ startEmbeddedBrokerWithRemotePdp() {
        // build extension
        final EmbeddedExtension embeddedExtensionBuild = EmbeddedExtension.builder()
                .withId("SAPL-MQTT-PEP")
                .withName("SAPL-MQTT-PEP")
                .withVersion("1.0.0")
                .withPriority(0)
                .withStartPriority(1000)
                .withAuthor("Nils Mahnken")
                .withExtensionMain(new HivemqPepExtensionMain("src/test/resources/config/remote"))
                .build();

        // start hivemq broker with sapl extension
        EmbeddedHiveMQ embeddedHiveMq = EmbeddedHiveMQ.builder()
                .withConfigurationFolder(Path.of("src/test/resources/embedded-config-folder"))
                .withDataFolder(Path.of("src/test/resources/embedded-data-folder"))
                .withExtensionsFolder(Path.of("src/test/resources/embedded-extensions-folder"))
                .withEmbeddedExtension(embeddedExtensionBuild).build();
        embeddedHiveMq.start().join();
        return embeddedHiveMq;
    }

    private static void createExtensionConfigFile(Integer port) throws ParserConfigurationException, TransformerException {
        DocumentBuilderFactory documentFactory = DocumentBuilderFactory.newInstance();

        DocumentBuilder documentBuilder = documentFactory.newDocumentBuilder();

        Document document = documentBuilder.newDocument();

        // root element
        Element root = document.createElement(SaplMqttExtensionConfig.ENVIRONMENT_ROOT_ELEMENT);
        document.appendChild(root);

        // sapl extension config elements
        Element pdpImplementation = document.createElement(SaplMqttExtensionConfig.ENVIRONMENT_PDP_IMPLEMENTATION);
        pdpImplementation.appendChild(document.createTextNode("remote"));
        root.appendChild(pdpImplementation);

        Element baseUrl = document.createElement(SaplMqttExtensionConfig.ENVIRONMENT_REMOTE_PDP_BASE_URL);
        baseUrl.appendChild(document.createTextNode("http://localhost:" + port));
        root.appendChild(baseUrl);

        Element clientKey = document.createElement(SaplMqttExtensionConfig.ENVIRONMENT_REMOTE_PDP_CLIENT_KEY);
        clientKey.appendChild(document.createTextNode("YJi7gyT5mfdKbmL"));
        root.appendChild(clientKey);

        Element clientSecret = document.createElement(SaplMqttExtensionConfig.ENVIRONMENT_REMOTE_PDP_CLIENT_SECRET);
        clientSecret.appendChild(
                document.createTextNode("$2a$10$Ph9bF71xYb0MK8KubWLB7e0Dpl2AfMiEUi9jrKTbFR4kkWABRb9yO"));
        root.appendChild(clientSecret);

        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        DOMSource domSource = new DOMSource(document);
        StreamResult streamResult = new StreamResult(new File(xmlFilePath));

        transformer.transform(domSource, streamResult);
    }
}
