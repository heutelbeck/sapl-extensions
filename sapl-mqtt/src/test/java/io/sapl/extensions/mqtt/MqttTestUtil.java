package io.sapl.extensions.mqtt;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt5.Mqtt5BlockingClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client;
import com.hivemq.client.mqtt.mqtt5.message.connect.connack.Mqtt5ConnAckReasonCode;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5PayloadFormatIndicator;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import com.hivemq.configuration.service.InternalConfigurations;
import com.hivemq.embedded.EmbeddedHiveMQ;
import com.hivemq.migration.meta.PersistenceType;

import io.sapl.api.interpreter.Val;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

@UtilityClass
class MqttTestUtil {
	public final static Mqtt5Publish MESSAGE = buildMqttPublishMessage("topic",
			"message", false);
	public final static Val          TOPIC   = Val.of("topic");

	final static String CLIENT_ID   = "SAPL_MQTT_CLIENT";
	final static String BROKER_HOST = "localhost";

	final static int          BROKER_PORT = 1883;
	final static ObjectMapper MAPPER      = new ObjectMapper();

	public static EmbeddedHiveMQ buildBroker(Path configDir, Path dataDir, Path extensionsDir) {
		var broker = EmbeddedHiveMQ.builder()
				.withConfigurationFolder(configDir)
				.withDataFolder(dataDir)
				.withExtensionsFolder(extensionsDir)
				.build();

		InternalConfigurations.PAYLOAD_PERSISTENCE_TYPE.set(PersistenceType.FILE);
		InternalConfigurations.RETAINED_MESSAGE_PERSISTENCE_TYPE.set(PersistenceType.FILE);

		return broker;
	}

	@SneakyThrows
	public static EmbeddedHiveMQ startBroker(EmbeddedHiveMQ broker) {
		broker.start().get();
		return broker;
	}

	public static EmbeddedHiveMQ buildAndStartBroker(Path configDir, Path dataDir, Path extensionsDir) {
		return startBroker(buildBroker(configDir, dataDir, extensionsDir));
	}

	public static void stopBroker(EmbeddedHiveMQ broker) {
		try {
			broker.stop().get();
			broker.close();
		} catch (ExecutionException | IllegalStateException | InterruptedException e) {
			// NOP ignore if broker already closed
		}
	}

	public static Mqtt5BlockingClient startClient() {
		var mqttClient     = Mqtt5Client.builder()
				.identifier(CLIENT_ID)
				.serverHost(BROKER_HOST)
				.serverPort(BROKER_PORT)
				.buildBlocking();
		var connAckMessage = mqttClient.connect();
		if (connAckMessage.getReasonCode() != Mqtt5ConnAckReasonCode.SUCCESS) {
			throw new IllegalStateException("Connection to the mqtt broker couldn't be established:" +
					connAckMessage.getReasonCode());
		}
		return mqttClient;
	}

	@SneakyThrows
	public static JsonNode defaultMqttPipConfig() {
		return MAPPER.readTree("""
				{
				  "defaultBrokerConfigName" : "production",
				  "emitAtRetry" : "false",
				  "brokerConfig" : [ {
				    "name" : "production",
				    "brokerAddress" : "localhost",
				    "brokerPort" : 1883,
				    "clientId" : "mqttPipDefault"
				  } ]
				}
				""");
	}

	public static Map<String, JsonNode> buildVariables(JsonNode pipConfig) {
		return Map.of(
				"action", MAPPER.nullNode(),
				"environment", MAPPER.nullNode(),
				"mqttPipConfig", defaultMqttPipConfig(),
				"resource", MAPPER.nullNode(),
				"subject", MAPPER.nullNode());
	}

	public static Map<String, JsonNode> buildVariables() {
		return buildVariables(defaultMqttPipConfig());
	}

	public static Mqtt5Publish buildMqttPublishMessage(String topic, String payload, boolean retain) {
		return Mqtt5Publish.builder()
				.topic(topic)
				.qos(MqttQos.AT_MOST_ONCE)
				.retain(retain)
				.payloadFormatIndicator(Mqtt5PayloadFormatIndicator.UTF_8)
				.payload(payload.getBytes(StandardCharsets.UTF_8))
				.build();
	}

}
