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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt5.Mqtt5BlockingClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client;
import com.hivemq.client.mqtt.mqtt5.message.connect.connack.Mqtt5ConnAck;
import com.hivemq.client.mqtt.mqtt5.message.connect.connack.Mqtt5ConnAckReasonCode;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5PayloadFormatIndicator;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import com.hivemq.embedded.EmbeddedHiveMQ;
import io.sapl.api.interpreter.Val;
import io.sapl.interpreter.InitializationException;
import org.junit.jupiter.api.BeforeAll;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;

import static io.sapl.interpreter.pip.SaplMqttClient.*;
import static io.sapl.interpreter.pip.util.ConfigUtility.*;
import static io.sapl.interpreter.pip.util.ErrorUtility.ENVIRONMENT_EMIT_AT_RETRY;

abstract class SaplMqttClientTest {

    protected static EmbeddedHiveMQ embeddedHiveMqBroker;

    protected static final String mqttClientId = "SAPL_MQTT_CLIENT";
    protected static final String mqttServerHost = "localhost";
    protected static final int mqttServerPort = 1883;

    protected static final String publishMessageTopic = "topic";
    protected static final String publishMessagePayload = "message";
    protected static final SaplMqttClient saplMqttClient = new SaplMqttClient();

    protected static final JsonNodeFactory JSON = JsonNodeFactory.instance;
    protected static final Logger logger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);

    protected static final Val topic = Val.of("topic");
    protected static final JsonNode action = JSON.nullNode();
    protected static final JsonNode environment = JSON.nullNode();
    protected static final JsonNode resource = JSON.nullNode();
    protected static final JsonNode subject = JSON.nullNode();
    protected static final JsonNode mqttPipConfig = buildDefaultMqttPipConfig();
    protected static final Map<String, JsonNode> config = buildConfig();

    protected static final Mqtt5Publish publishMessage = buildMqttPublishMessage(publishMessageTopic,
            publishMessagePayload, false);

    @BeforeAll
    static void beforeAll() {
        logger.setLevel(Level.DEBUG);
    }

    protected static EmbeddedHiveMQ startEmbeddedHiveMqBroker() {
        EmbeddedHiveMQ embeddedHiveMqBroker = EmbeddedHiveMQ.builder()
                .withConfigurationFolder(Path.of("src/test/resources/embedded-config-folder"))
                .withDataFolder(Path.of("src/test/resources/embedded-data-folder"))
                .withExtensionsFolder(Path.of("src/test/resources/embedded-extensions-folder"))
                .build();

        embeddedHiveMqBroker.start().join();
        return embeddedHiveMqBroker;
    }

    protected static Mqtt5BlockingClient startMqttClient() throws InitializationException {
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

    protected static Mqtt5Publish buildMqttPublishMessage(String topic, String payload, boolean retain) {
        return Mqtt5Publish.builder()
                .topic(topic)
                .qos(MqttQos.AT_MOST_ONCE)
                .retain(retain)
                .payloadFormatIndicator(Mqtt5PayloadFormatIndicator.UTF_8)
                .payload(payload.getBytes(StandardCharsets.UTF_8))
                .build();
    }

    protected static JsonNode buildDefaultMqttPipConfig() {
        return JSON.objectNode()
                .put(ENVIRONMENT_DEFAULT_BROKER_CONFIG_NAME, "production")
                .put(ENVIRONMENT_EMIT_AT_RETRY, "false")
                .set(ENVIRONMENT_BROKER_CONFIG, JSON.arrayNode().add(JSON.objectNode()
                        .put(ENVIRONMENT_BROKER_CONFIG_NAME, "production")
                        .put(ENVIRONMENT_BROKER_ADDRESS, "localhost")
                        .put(ENVIRONMENT_BROKER_PORT, 1883)
                        .put(ENVIRONMENT_CLIENT_ID, "mqttPipDefault")));
    }

    protected static Map<String, JsonNode> buildConfig() {
        return Map.of(
                "action", action,
                "environment", environment,
                "mqttPipConfig", mqttPipConfig,
                "resource", resource,
                "subject", subject);
    }
}