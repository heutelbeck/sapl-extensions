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
import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.NotNull;

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

@UtilityClass
public class MqttTestUtil {

	public static final int 	BROKER_PORT 			= 1883;
	public static final String 	BROKER_HOST 			= "localhost";
	public static final String 	EXTENSIONS_PATH 		= "src/test/resources/config";
	public static final String 	POLICIES_PATH 			= "src/test/resources/policies";
	public static final String 	PUBLISH_MESSAGE_PAYLOAD = "message";

	public static EmbeddedHiveMQ buildAndStartBroker(Path configDir, Path dataDir, Path extensionsDir) {
		return buildAndStartBroker(configDir, dataDir, extensionsDir, EXTENSIONS_PATH);
	}

	public static EmbeddedHiveMQ buildAndStartBroker(Path configDir, Path dataDir, Path extensionsDir,
			ConcurrentHashMap<String, MqttClientState> mqttClientCache) {
		return buildAndStartBroker(configDir, dataDir, extensionsDir,
				new HivemqPepExtensionMain(POLICIES_PATH, EXTENSIONS_PATH, mqttClientCache));
	}

	public static EmbeddedHiveMQ buildAndStartBroker(Path configDir, Path dataDir, Path extensionsDir,
													 PolicyDecisionPoint pdp) {
		return buildAndStartBroker(configDir, dataDir, extensionsDir, new HivemqPepExtensionMain(EXTENSIONS_PATH, pdp));
	}

	public static EmbeddedHiveMQ buildAndStartBroker(Path configDir, Path dataDir, Path extensionsDir,
													 PolicyDecisionPoint pdp, String saplExtensionConfigPath) {
		return buildAndStartBroker(configDir, dataDir, extensionsDir,
				new HivemqPepExtensionMain(saplExtensionConfigPath, pdp));
	}

	public static EmbeddedHiveMQ buildAndStartBroker(Path configDir, Path dataDir, Path extensionsDir,
													 PolicyDecisionPoint pdp, String saplExtensionConfigPath,
													 ConcurrentHashMap<String, MqttClientState> mqttClientCache) {
		return buildAndStartBroker(configDir, dataDir, extensionsDir,
				new HivemqPepExtensionMain(saplExtensionConfigPath, pdp, mqttClientCache));
	}

	public static EmbeddedHiveMQ buildAndStartBroker(Path configDir, Path dataDir, Path extensionsDir,
													 String saplExtensionConfigPath) {
		return buildAndStartBroker(configDir, dataDir, extensionsDir,
				new HivemqPepExtensionMain(POLICIES_PATH, saplExtensionConfigPath));
	}

	public static EmbeddedHiveMQ buildAndStartBroker(Path configDir, Path dataDir, Path extensionsDir,
													 HivemqPepExtensionMain hiveMqPepExtensionMain) {
		return startBroker(buildBrokerWithExtension(configDir, dataDir, extensionsDir, hiveMqPepExtensionMain));
	}

	@SneakyThrows
	private static EmbeddedHiveMQ startBroker(EmbeddedHiveMQ broker) {
		broker.start().get();
		return broker;
	}

	public static void stopBroker(EmbeddedHiveMQ broker) {
		if (broker != null) {
			try {
				broker.stop().get();
				broker.close();
			} catch (ExecutionException | IllegalStateException | InterruptedException e) {
				// NOP ignore if broker already closed
			}
		}
	}

	private static EmbeddedHiveMQ buildBrokerWithExtension(Path configDir, Path dataDir, Path extensionsDir,
														   HivemqPepExtensionMain hiveMqPepExtensionMain) {
		return buildBroker(configDir, dataDir, extensionsDir, buildExtension(hiveMqPepExtensionMain));
	}

	private static EmbeddedHiveMQ buildBroker(Path configDir, Path dataDir, Path extensionsDir,
											  EmbeddedExtension embeddedExtensionBuild) {
		EmbeddedHiveMQ broker = EmbeddedHiveMQ.builder()
				.withConfigurationFolder(configDir)
				.withDataFolder(dataDir)
				.withExtensionsFolder(extensionsDir)
				.withEmbeddedExtension(embeddedExtensionBuild)
				.build();

		InternalConfigurations.PAYLOAD_PERSISTENCE_TYPE.set(PersistenceType.FILE);
		InternalConfigurations.RETAINED_MESSAGE_PERSISTENCE_TYPE.set(PersistenceType.FILE);

		return broker;
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

	public static Mqtt5BlockingClient buildAndStartMqttClient(String mqttClientId) throws InitializationException {
		return buildAndStartMqttClient(mqttClientId, BROKER_PORT);
	}

	public static Mqtt5BlockingClient buildAndStartMqttClient(String mqttClientId, int mqttServerPort)
			throws InitializationException {
		Mqtt5BlockingClient blockingMqttClient = buildMqttClient(mqttClientId, mqttServerPort);
		startMqttClient(blockingMqttClient);
		return blockingMqttClient;
	}

	@NotNull
	private static Mqtt5BlockingClient buildMqttClient(String mqttClientId, int mqttServerPort) {
		return Mqtt5Client.builder()
				.identifier(mqttClientId)
				.serverHost(BROKER_HOST)
				.serverPort(mqttServerPort)
				.buildBlocking();
	}

	private static void startMqttClient(Mqtt5BlockingClient blockingMqttClient) throws InitializationException {
		Mqtt5ConnAck        connAckMessage     = blockingMqttClient.connect();
		if (connAckMessage.getReasonCode() != Mqtt5ConnAckReasonCode.SUCCESS) {
			throw new InitializationException("Connection to the mqtt broker couldn't be established" +
					"with reason code: " + connAckMessage.getReasonCode());
		}
	}

	public static Mqtt5Subscribe buildMqttSubscribeMessage(String topic) {
		return buildMqttSubscribeMessage(topic, 0);
	}

	public static Mqtt5Subscribe buildMqttSubscribeMessage(String topic, int qos) {
		return Mqtt5Subscribe.builder()
				.topicFilter(topic)
				.qos(Objects.requireNonNull(MqttQos.fromCode(qos)))
				.build();
	}

	public static Mqtt5Publish buildMqttPublishMessage(String topic, boolean retain) {
		return buildMqttPublishMessage(topic, 0, retain);
	}

	public static Mqtt5Publish buildMqttPublishMessage(String topic, int qos, boolean retain) {
		return buildMqttPublishMessage(topic, qos, retain, null);
	}

	public static Mqtt5Publish buildMqttPublishMessage(String topic, int qos,
			boolean retain, String contentType) {
		return Mqtt5Publish.builder()
				.topic(topic)
				.qos(Objects.requireNonNull(MqttQos.fromCode(qos)))
				.retain(retain)
				.contentType(contentType)
				.payload(PUBLISH_MESSAGE_PAYLOAD.getBytes(StandardCharsets.UTF_8))
				.build();
	}
}