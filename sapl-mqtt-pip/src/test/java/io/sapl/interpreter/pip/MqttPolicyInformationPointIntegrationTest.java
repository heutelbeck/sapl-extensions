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

package io.sapl.interpreter.pip;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt5.Mqtt5BlockingClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client;
import com.hivemq.client.mqtt.mqtt5.message.connect.connack.Mqtt5ConnAck;
import com.hivemq.client.mqtt.mqtt5.message.connect.connack.Mqtt5ConnAckReasonCode;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5PayloadFormatIndicator;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import com.hivemq.embedded.EmbeddedHiveMQ;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.interpreter.InitializationException;
import io.sapl.pdp.EmbeddedPolicyDecisionPoint;
import io.sapl.pdp.PolicyDecisionPointFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

class MqttPolicyInformationPointIntegrationTest {
    private static final String mqttClientId = "SAPL_MQTT_CLIENT";
    private static final String mqttServerHost = "localhost";
    private static final int mqttServerPort = 1883;
    private static final String publishMessageTopic = "single_topic";
    private static final String publishMessagePayload = "message";

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;
    private static final String SUBJECT = "subjectName";
    private static final String TOPIC = "single_topic";
    private static final ObjectNode RESOURCE = buildJsonResource();
    private static final Mqtt5Publish publishMessage = buildMqttPublishMessage();

    private static EmbeddedHiveMQ embeddedHiveMqBroker;
    private static Mqtt5BlockingClient hiveMqClient;
    private static EmbeddedPolicyDecisionPoint pdp;

    protected static final Logger logger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);

    @BeforeAll
    static void setUp() throws InitializationException {
        logger.setLevel(Level.DEBUG);

        embeddedHiveMqBroker = startEmbeddedHiveMqBroker();
        hiveMqClient = startMqttClient();
        pdp = buildPdp();
    }

    @AfterAll
    static void tearDown() {
        hiveMqClient.disconnect();
        pdp.dispose();
        embeddedHiveMqBroker.stop().join();
    }

    @ParameterizedTest
    @ValueSource(strings = {"actionWithoutParams", "actionWithQos", "actionNameWithQosAndConfig"})
    void when_messagesIsCalled_then_getPublishedMessages(String action) {
        // GIVEN
        AuthorizationSubscription authzSubscription = AuthorizationSubscription.of(SUBJECT,
                action, RESOURCE);

        // WHEN
        var pdpDecisionFlux = pdp.decide(authzSubscription);

        // THEN
        StepVerifier.create(pdpDecisionFlux)
                .thenAwait(Duration.ofMillis(1000))
                .then(()->hiveMqClient.publish(publishMessage))
                .expectNextMatches(authzDecision -> authzDecision.getDecision() == Decision.PERMIT)
                .thenCancel()
                .verify();
    }

    private static EmbeddedHiveMQ startEmbeddedHiveMqBroker() {
        EmbeddedHiveMQ embeddedHiveMqBroker = EmbeddedHiveMQ.builder()
                .withConfigurationFolder(Path.of("src/test/resources/embedded-config-folder"))
                .withDataFolder(Path.of("src/test/resources/embedded-data-folder"))
                .withExtensionsFolder(Path.of("src/test/resources/embedded-extensions-folder"))
                .build();

        embeddedHiveMqBroker.start().join();
        return embeddedHiveMqBroker;
    }

    private static Mqtt5BlockingClient startMqttClient() throws InitializationException {
        Mqtt5BlockingClient blockingMqttClient = Mqtt5Client.builder()
                .identifier(mqttClientId)
                .serverHost(mqttServerHost)
                .serverPort(mqttServerPort)
                .buildBlocking();
        Mqtt5ConnAck connAckMessage = blockingMqttClient.connect();
        if (connAckMessage.getReasonCode() != Mqtt5ConnAckReasonCode.SUCCESS) {
            throw new InitializationException("Connection to the mqtt broker couldn't be established" +
                    "with reason code: " + connAckMessage.getReasonCode());
        }
        return blockingMqttClient;
    }

    private static EmbeddedPolicyDecisionPoint buildPdp() throws InitializationException {
        return PolicyDecisionPointFactory
                .filesystemPolicyDecisionPoint("src/test/resources/policies",
                        List.of(new MqttPolicyInformationPoint()), List.of());
    }

    private static Mqtt5Publish buildMqttPublishMessage() {
        return Mqtt5Publish.builder()
                .topic(publishMessageTopic)
                .qos(MqttQos.AT_MOST_ONCE)
                .payloadFormatIndicator(Mqtt5PayloadFormatIndicator.UTF_8)
                .payload(publishMessagePayload.getBytes(StandardCharsets.UTF_8))
                .build();
    }

    private static ObjectNode buildJsonResource() {
        ObjectNode resource = JSON.objectNode();
        resource.put("topic", TOPIC);
        return resource;
    }
}