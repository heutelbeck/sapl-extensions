/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * SPDX-License-Identifier: Apache-2.0
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

import static io.sapl.mqtt.pep.MqttTestUtil.*;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import com.hivemq.client.mqtt.mqtt5.Mqtt5BlockingClient;
import lombok.SneakyThrows;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
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

import io.sapl.interpreter.InitializationException;
import io.sapl.mqtt.pep.config.SaplMqttExtensionConfig;

@Testcontainers
class RemotePdpUsageIT {

    @TempDir
    Path dataFolder;
    @TempDir
    Path configFolder;
    @TempDir
    Path extensionFolder;
    @TempDir
    Path extensionConfigDir;

    private EmbeddedHiveMQ      mqttBroker;
    private Mqtt5BlockingClient publishClient;
    private Mqtt5BlockingClient subscribeClient;

    private final String extensionConfigFileName = "sapl-extension-config.xml";

    @Container
    static final GenericContainer<?> SAPL_SERVER_LT = new GenericContainer<>(
            DockerImageName.parse("ghcr.io/heutelbeck/sapl-server-lt:3.0.0-snapshot"))
            .withCopyFileToContainer(MountableFile.forHostPath("src/test/resources/policies"), "/pdp/data")
            .withExposedPorts(8080);

    @BeforeEach
    void beforeEach() throws InitializationException, ParserConfigurationException, TransformerException {
        if (!SAPL_SERVER_LT.isRunning()) {
            SAPL_SERVER_LT.start();
        }
        createExtensionConfigFile(SAPL_SERVER_LT.getFirstMappedPort());

        mqttBroker      = startAndBuildBrokerWithRemotePdp();
        subscribeClient = buildAndStartMqttClient("MQTT_CLIENT_SUBSCRIBE");
        publishClient   = buildAndStartMqttClient("MQTT_CLIENT_PUBLISH");
    }

    @AfterEach
    void afterEach() {
        if (publishClient.getState().isConnected()) {
            publishClient.disconnect();
        }
        if (subscribeClient.getState().isConnected()) {
            subscribeClient.disconnect();
        }
        if (SAPL_SERVER_LT.isRunning()) {
            SAPL_SERVER_LT.stop();
        }
        stopBroker(mqttBroker);
    }

    @Test
    @Timeout(10)
    void when_publishAndSubscribeForTopicPermitted_then_subscribeAndPublishTopic() throws InterruptedException {
        // GIVEN
        Mqtt5Subscribe subscribeMessage = buildMqttSubscribeMessage("topic");

        Mqtt5Publish publishMessage = buildMqttPublishMessage("topic", false);

        // WHEN
        subscribeClient.subscribe(subscribeMessage);
        publishClient.publish(publishMessage);

        // THEN
        Mqtt5Publish receivedMessage = subscribeClient.publishes(MqttGlobalPublishFilter.ALL).receive();

        assertEquals(PUBLISH_MESSAGE_PAYLOAD, new String(receivedMessage.getPayloadAsBytes()));

        // FINALLY
        subscribeClient.unsubscribeWith().topicFilter("topic").send();
    }

    @Test
    @Timeout(10)
    void when_losingConnectionToPdpServer_then_DenyOnIndeterminate() {
        // GIVEN
        Mqtt5Subscribe subscribeMessage = buildMqttSubscribeMessage("topic");

        Mqtt5Publish publishMessage = buildMqttPublishMessage("topic", 1, true);

        // WHEN
        subscribeClient.subscribe(subscribeMessage);
        publishClient.publish(publishMessage);

        SAPL_SERVER_LT.stop();

        // THEN
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            assertFalse(SAPL_SERVER_LT.isRunning());
            assertFalse(subscribeClient.getState().isConnected());
            assertFalse(publishClient.getState().isConnected());
        });
    }

    @SneakyThrows
    private EmbeddedHiveMQ startAndBuildBrokerWithRemotePdp() {
        EmbeddedHiveMQ embeddedHiveMq = buildBrokerWithExtension();
        startBrokerWithRemotePdp(embeddedHiveMq);
        return embeddedHiveMq;
    }

    private void startBrokerWithRemotePdp(EmbeddedHiveMQ embeddedHiveMq)
            throws InterruptedException, ExecutionException {
        embeddedHiveMq.start().get();
    }

    private EmbeddedHiveMQ buildBrokerWithExtension() {
        final EmbeddedExtension embeddedExtensionBuild = buildBrokerExtension();
        return buildBroker(embeddedExtensionBuild);
    }

    private EmbeddedHiveMQ buildBroker(EmbeddedExtension embeddedExtensionBuild) {
        return EmbeddedHiveMQ.builder().withConfigurationFolder(configFolder).withDataFolder(dataFolder)
                .withExtensionsFolder(extensionFolder).withEmbeddedExtension(embeddedExtensionBuild).build();
    }

    private EmbeddedExtension buildBrokerExtension() {
        return EmbeddedExtension.builder().withId("SAPL-MQTT-PEP").withName("SAPL-MQTT-PEP").withVersion("1.0.0")
                .withPriority(0).withStartPriority(1000).withAuthor("Nils Mahnken")
                .withExtensionMain(new HivemqPepExtensionMain(extensionConfigDir.toString())).build();
    }

    private void createExtensionConfigFile(Integer port) throws ParserConfigurationException, TransformerException {
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
        clientSecret
                .appendChild(document.createTextNode("$2a$10$Ph9bF71xYb0MK8KubWLB7e0Dpl2AfMiEUi9jrKTbFR4kkWABRb9yO"));
        root.appendChild(clientSecret);

        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer        transformer        = transformerFactory.newTransformer();
        DOMSource          domSource          = new DOMSource(document);
        StreamResult       streamResult       = new StreamResult(
                new File(Path.of(extensionConfigDir.toString(), extensionConfigFileName).toString()));

        transformer.transform(domSource, streamResult);
    }
}
