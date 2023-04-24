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

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.LoggerFactory;

import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt5.Mqtt5BlockingClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client;
import com.hivemq.client.mqtt.mqtt5.message.connect.connack.Mqtt5ConnAck;
import com.hivemq.client.mqtt.mqtt5.message.connect.connack.Mqtt5ConnAckReasonCode;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import com.hivemq.client.mqtt.mqtt5.message.subscribe.Mqtt5Subscribe;
import com.hivemq.embedded.EmbeddedExtension;
import com.hivemq.embedded.EmbeddedHiveMQ;

import ch.qos.logback.classic.Logger;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.interpreter.InitializationException;
import io.sapl.mqtt.pep.cache.MqttClientState;

public abstract class SaplMqttPepTest {
    protected static final Logger rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    protected static EmbeddedHiveMQ embeddedHiveMq;
    protected static Mqtt5BlockingClient mqttClientPublish;
    protected static Mqtt5BlockingClient mqttClientSubscribe;

    protected static final String policiesPath = "src/test/resources/policies";
    protected static final String saplExtensionConfigPath = "src/test/resources/config";
    protected static final String mqttServerHost = "localhost";
    protected static final int mqttServerPort = 1883;

    protected static final String publishMessagePayload = "message";

    protected static EmbeddedHiveMQ startEmbeddedHiveMqBroker() {
        return startEmbeddedHiveMqBroker(saplExtensionConfigPath);
    }

    protected static EmbeddedHiveMQ startEmbeddedHiveMqBroker(ConcurrentHashMap<String, MqttClientState> mqttClientCache) {
        return startEmbeddedHiveMqBroker(
                new HivemqPepExtensionMain(policiesPath, saplExtensionConfigPath, mqttClientCache));
    }

    protected static EmbeddedHiveMQ startEmbeddedHiveMqBroker(PolicyDecisionPoint pdp) {
        return startEmbeddedHiveMqBroker(new HivemqPepExtensionMain(saplExtensionConfigPath, pdp));
    }

    protected static EmbeddedHiveMQ startEmbeddedHiveMqBroker(PolicyDecisionPoint pdp, String saplExtensionConfigPath) {
        return startEmbeddedHiveMqBroker(new HivemqPepExtensionMain(saplExtensionConfigPath, pdp));
    }

    protected static EmbeddedHiveMQ startEmbeddedHiveMqBroker(PolicyDecisionPoint pdp,
                                                              ConcurrentHashMap<String, MqttClientState> mqttClientCache) {
        return startEmbeddedHiveMqBroker(pdp, saplExtensionConfigPath, mqttClientCache);
    }

    protected static EmbeddedHiveMQ startEmbeddedHiveMqBroker(PolicyDecisionPoint pdp, String saplExtensionConfigPath,
                                                              ConcurrentHashMap<String, MqttClientState> mqttClientCache) {
        return startEmbeddedHiveMqBroker(new HivemqPepExtensionMain(saplExtensionConfigPath, pdp, mqttClientCache));
    }

    protected static EmbeddedHiveMQ startEmbeddedHiveMqBroker(String saplExtensionConfigPath) {
        return startEmbeddedHiveMqBroker(
                new HivemqPepExtensionMain(policiesPath, saplExtensionConfigPath));
    }

    protected static EmbeddedHiveMQ startEmbeddedHiveMqBroker(HivemqPepExtensionMain hiveMqPepExtensionMain) {
        // build extension
        final EmbeddedExtension embeddedExtensionBuild = EmbeddedExtension.builder()
                .withId("SAPL-MQTT-PEP")
                .withName("SAPL-MQTT-PEP")
                .withVersion("1.0.0")
                .withPriority(0)
                .withStartPriority(1000)
                .withAuthor("Nils Mahnken")
                .withExtensionMain(hiveMqPepExtensionMain)
                .build();

        // start hivemq broker with sapl extension
        embeddedHiveMq = EmbeddedHiveMQ.builder()
                .withConfigurationFolder(Path.of("src/test/resources/embedded-config-folder"))
                .withDataFolder(Path.of("src/test/resources/embedded-data-folder"))
                .withExtensionsFolder(Path.of("src/test/resources/embedded-extensions-folder"))
                .withEmbeddedExtension(embeddedExtensionBuild).build();
        embeddedHiveMq.start().join();
        return embeddedHiveMq;
    }

    protected static Mqtt5BlockingClient startMqttClient(String mqttClientId) throws InitializationException {
        return startMqttClient(mqttClientId, mqttServerPort);
    }

    protected static Mqtt5BlockingClient startMqttClient(String mqttClientId,
                                                         int mqttServerPort) throws InitializationException {
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

    protected static Mqtt5Subscribe buildMqttSubscribeMessage(String topic) {
        return buildMqttSubscribeMessage(topic, 0);
    }

    protected static Mqtt5Subscribe buildMqttSubscribeMessage(String topic, int qos) {
        return Mqtt5Subscribe.builder()
                .topicFilter(topic)
                .qos(Objects.requireNonNull(MqttQos.fromCode(qos)))
                .build();
    }

    protected static Mqtt5Publish buildMqttPublishMessage(String topic, boolean retain) {
        return buildMqttPublishMessage(topic, 0, retain);
    }

    protected static Mqtt5Publish buildMqttPublishMessage(String topic, int qos, boolean retain) {
        return buildMqttPublishMessage(topic, qos, retain, null);
    }

    protected static Mqtt5Publish buildMqttPublishMessage(String topic, int qos,
                                                          boolean retain, String contentType) {
        return Mqtt5Publish.builder()
                .topic(topic)
                .qos(Objects.requireNonNull(MqttQos.fromCode(qos)))
                .retain(retain)
                .contentType(contentType)
                .payload(SaplMqttPepTest.publishMessagePayload.getBytes(StandardCharsets.UTF_8))
                .build();
    }
}