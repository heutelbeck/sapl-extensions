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
import java.util.concurrent.ExecutionException;

import com.hivemq.configuration.service.InternalConfigurations;
import com.hivemq.migration.meta.PersistenceType;
import lombok.SneakyThrows;
import org.junit.jupiter.api.io.TempDir;

import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt5.Mqtt5BlockingClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client;
import com.hivemq.client.mqtt.mqtt5.message.connect.connack.Mqtt5ConnAck;
import com.hivemq.client.mqtt.mqtt5.message.connect.connack.Mqtt5ConnAckReasonCode;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import com.hivemq.client.mqtt.mqtt5.message.subscribe.Mqtt5Subscribe;
import com.hivemq.embedded.EmbeddedExtension;
import com.hivemq.embedded.EmbeddedHiveMQ;

import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.interpreter.InitializationException;
import io.sapl.mqtt.pep.cache.MqttClientState;

public abstract class SaplMqttPepTest {
	protected static EmbeddedHiveMQ      MQTT_BROKER;
	protected static Mqtt5BlockingClient PUBLISH_CLIENT;
	protected static Mqtt5BlockingClient SUBSCRIBE_CLIENT;

	protected final static String POLICIES_PATH   = "src/test/resources/policies";
	protected final static String EXTENSIONS_PATH = "src/test/resources/config";
	protected final static String BROKER_HOST     = "localhost";
	protected final static int    BROKER_PORT     = 1883;

	@TempDir
	static Path DATA_FOLDER;
	@TempDir
	static Path CONFIG_FOLDER;
	@TempDir
	static Path EXTENSION_FOLDER;

	protected static final String PUBLISH_MESSAGE_PAYLOAD = "message";

	protected static EmbeddedHiveMQ startAndBuildBroker() {
		return startAndBuildBroker(EXTENSIONS_PATH);
	}

	protected static EmbeddedHiveMQ startAndBuildBroker(
			ConcurrentHashMap<String, MqttClientState> mqttClientCache) {
		return startAndBuildBroker(
				new HivemqPepExtensionMain(POLICIES_PATH, EXTENSIONS_PATH, mqttClientCache));
	}

	protected static EmbeddedHiveMQ startAndBuildBroker(PolicyDecisionPoint pdp) {
		return startAndBuildBroker(new HivemqPepExtensionMain(EXTENSIONS_PATH, pdp));
	}

	protected static EmbeddedHiveMQ startAndBuildBroker(PolicyDecisionPoint pdp, String saplExtensionConfigPath) {
		return startAndBuildBroker(new HivemqPepExtensionMain(saplExtensionConfigPath, pdp));
	}

	protected static EmbeddedHiveMQ startAndBuildBroker(PolicyDecisionPoint pdp,
														ConcurrentHashMap<String, MqttClientState> mqttClientCache) {
		return startAndBuildBroker(pdp, EXTENSIONS_PATH, mqttClientCache);
	}

	protected static EmbeddedHiveMQ startAndBuildBroker(PolicyDecisionPoint pdp, String saplExtensionConfigPath,
														ConcurrentHashMap<String, MqttClientState> mqttClientCache) {
		return startAndBuildBroker(new HivemqPepExtensionMain(saplExtensionConfigPath, pdp, mqttClientCache));
	}

	protected static EmbeddedHiveMQ startAndBuildBroker(String saplExtensionConfigPath) {
		return startAndBuildBroker(
				new HivemqPepExtensionMain(POLICIES_PATH, saplExtensionConfigPath));
	}

	protected static EmbeddedHiveMQ startAndBuildBroker(HivemqPepExtensionMain hiveMqPepExtensionMain) {
		buildBrokerWithExtension(hiveMqPepExtensionMain);
		startBroker();
		return MQTT_BROKER;
	}

	@SneakyThrows
	private static void startBroker() {
		MQTT_BROKER.start().get();
	}

	protected static void stopBroker() {
		try {
			MQTT_BROKER.stop().get();
			MQTT_BROKER.close();
		} catch (ExecutionException | IllegalStateException | InterruptedException e) {
			// NOP ignore if broker already closed
		}
	}

	private static void buildBrokerWithExtension(HivemqPepExtensionMain hiveMqPepExtensionMain) {
		buildBroker(buildExtension(hiveMqPepExtensionMain));
	}

	private static void buildBroker(EmbeddedExtension embeddedExtensionBuild) {
		MQTT_BROKER = EmbeddedHiveMQ.builder()
				.withConfigurationFolder(DATA_FOLDER)
				.withDataFolder(CONFIG_FOLDER)
				.withExtensionsFolder(EXTENSION_FOLDER)
				.withEmbeddedExtension(embeddedExtensionBuild).build();

		InternalConfigurations.PAYLOAD_PERSISTENCE_TYPE.set(PersistenceType.FILE);
		InternalConfigurations.RETAINED_MESSAGE_PERSISTENCE_TYPE.set(PersistenceType.FILE);
	}

	private static EmbeddedExtension buildExtension(HivemqPepExtensionMain hiveMqPepExtensionMain) {
		return EmbeddedExtension.builder()
				.withId("SAPL-MQTT-PEP")
				.withName("SAPL-MQTT-PEP")
				.withVersion("1.0.0")
				.withPriority(0)
				.withStartPriority(1000)
				.withAuthor("Nils Mahnken")
				.withExtensionMain(hiveMqPepExtensionMain)
				.build();
	}

	protected static Mqtt5BlockingClient startMqttClient(String mqttClientId) throws InitializationException {
		return startMqttClient(mqttClientId, BROKER_PORT);
	}

	protected static Mqtt5BlockingClient startMqttClient(String mqttClientId,
			int mqttServerPort) throws InitializationException {
		Mqtt5BlockingClient blockingMqttClient = Mqtt5Client.builder()
				.identifier(mqttClientId)
				.serverHost(BROKER_HOST)
				.serverPort(mqttServerPort)
				.buildBlocking();
		Mqtt5ConnAck        connAckMessage     = blockingMqttClient.connect();
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
				.payload(SaplMqttPepTest.PUBLISH_MESSAGE_PAYLOAD.getBytes(StandardCharsets.UTF_8))
				.build();
	}
}