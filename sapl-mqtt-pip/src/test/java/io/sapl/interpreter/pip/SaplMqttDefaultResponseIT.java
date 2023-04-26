package io.sapl.interpreter.pip;

import static io.sapl.interpreter.pip.MqttTestUtil.buildAndStartBroker;
import static io.sapl.interpreter.pip.MqttTestUtil.buildVariables;
import static io.sapl.interpreter.pip.MqttTestUtil.startClient;
import static io.sapl.interpreter.pip.MqttTestUtil.stopBroker;
import static io.sapl.interpreter.pip.SaplMqttClient.ENVIRONMENT_BROKER_ADDRESS;
import static io.sapl.interpreter.pip.SaplMqttClient.ENVIRONMENT_BROKER_PORT;
import static io.sapl.interpreter.pip.SaplMqttClient.ENVIRONMENT_CLIENT_ID;
import static io.sapl.interpreter.pip.util.DefaultResponseUtility.ENVIRONMENT_DEFAULT_RESPONSE;
import static io.sapl.interpreter.pip.util.DefaultResponseUtility.ENVIRONMENT_DEFAULT_RESPONSE_TIMEOUT;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hivemq.client.mqtt.mqtt5.Mqtt5BlockingClient;
import com.hivemq.embedded.EmbeddedHiveMQ;

import io.sapl.api.interpreter.Val;
import io.sapl.interpreter.pip.util.DefaultResponseConfig;
import io.sapl.interpreter.pip.util.DefaultResponseUtility;
import lombok.SneakyThrows;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

class SaplMqttDefaultResponseIT {

	private final static long DELAY_MS = 1000L;

	private final static JsonNodeFactory JSON   = JsonNodeFactory.instance;
	private final static ObjectMapper    MAPPER = new ObjectMapper();

	@TempDir
	Path configDir;

	@TempDir
	Path dataDir;

	@TempDir
	Path extensionsDir;

	EmbeddedHiveMQ      mqttBroker;
	Mqtt5BlockingClient mqttClient;
	SaplMqttClient      saplMqttClient;

	@BeforeEach
	void beforeEach() {
		this.mqttBroker = buildAndStartBroker(configDir, dataDir, extensionsDir);
		this.mqttClient     = startClient();
		this.saplMqttClient = new SaplMqttClient();
	}

	@AfterEach
	void afterEach() {
		this.mqttClient.disconnect();
		stopBroker(this.mqttBroker);
	}

	@Test
	void when_subscribingWithDefaultConfigAndBrokerDoesNotSendMessage_then_getDefaultUndefined() {
		// GIVEN
		var topics = JSON.arrayNode().add("topic1").add("topic2");

		// WHEN
		Flux<Val> saplMqttMessageFlux = saplMqttClient.buildSaplMqttMessageFlux(Val.of(topics), buildVariables());

		// THEN
		StepVerifier.create(saplMqttMessageFlux)
				.thenAwait(Duration.ofMillis(DELAY_MS))
				.expectNext(Val.UNDEFINED)
				.thenCancel()
				.verify();
	}

	@Test
	void when_subscribingWithConfigDefaultResponseErrorAndBrokerDoesNotSendMessage_then_getDefaultError() {
		// GIVEN
		var topics = JSON.arrayNode().add("topic1").add("topic2");

		// WHEN
		Flux<Val> saplMqttMessageFlux = saplMqttClient.buildSaplMqttMessageFlux(Val.of(topics), buildCustomConfig());

		// THEN
		StepVerifier.create(saplMqttMessageFlux)
				.thenAwait(Duration.ofMillis(DELAY_MS))
				.expectNextMatches(Val::isError)
				.thenCancel()
				.verify();
	}

	@Test
	void when_subscribingWithDefaultResponseTypeSpecifiedInAttributeFinderParams_then_useThisDefaultResponseType() {
		// GIVEN
		var        topics       = JSON.arrayNode().add("topic1").add("topic2");
		ObjectNode configParams = JSON.objectNode();
		configParams.put(ENVIRONMENT_BROKER_ADDRESS, "localhost");
		configParams.put(ENVIRONMENT_BROKER_PORT, 1883);
		configParams.put(ENVIRONMENT_CLIENT_ID, "clientId");
		configParams.put(ENVIRONMENT_DEFAULT_RESPONSE, "error");

		// WHEN
		Flux<Val> saplMqttMessageFlux = saplMqttClient.buildSaplMqttMessageFlux(Val.of(topics), buildVariables(),
				Val.of(0), Val.of(configParams));

		// THEN
		StepVerifier.create(saplMqttMessageFlux)
				.thenAwait(Duration.ofMillis(DELAY_MS))
				.expectNextMatches(Val::isError)
				.thenCancel()
				.verify();
	}

	@Test
	void when_subscribingWithDefaultResponseTimeoutSpecifiedInAttributeFinderParams_then_useThisDefaultResponseTimeout() {
		// GIVEN
		var        topics       = JSON.arrayNode().add("topic1").add("topic2");
		ObjectNode configParams = JSON.objectNode();
		configParams.put(ENVIRONMENT_BROKER_ADDRESS, "localhost");
		configParams.put(ENVIRONMENT_BROKER_PORT, 1883);
		configParams.put(ENVIRONMENT_CLIENT_ID, "clientId");
		configParams.put(ENVIRONMENT_DEFAULT_RESPONSE, "error");
		configParams.put(ENVIRONMENT_DEFAULT_RESPONSE_TIMEOUT, 8 * DELAY_MS);

		// WHEN
		Flux<Val> saplMqttMessageFlux = saplMqttClient.buildSaplMqttMessageFlux(Val.of(topics), buildVariables(),
				Val.of(0), Val.of(configParams));

		// THEN
		StepVerifier.create(saplMqttMessageFlux)
				.thenAwait(Duration.ofMillis(10 * DELAY_MS))
				.expectNextMatches(Val::isError)
				.thenCancel()
				.verify();
	}

	@Test
	void when_specifyingIllegalDefaultResponseType_then_usingDefaultResponseType() {
		// GIVEN
		DefaultResponseConfig defaultResponseConfig = new DefaultResponseConfig(5000, "illegal");

		// WHEN
		Val defaultVal = DefaultResponseUtility.getDefaultVal(defaultResponseConfig);

		// THEN
		assertTrue(defaultVal.isUndefined());
	}

	@SneakyThrows
	private static JsonNode customPipConfig() {
		return MAPPER.readTree("""
				{
				  "defaultBrokerConfigName" : "production",
				  "emitAtRetry" : "false",
				  "defaultResponse" : "error",
				  "brokerConfig" : [ {
				    "name" : "production",
				    "brokerAddress" : "localhost",
				    "brokerPort" : 1883,
				    "clientId" : "mqttPipDefault"
				  } ]
				}
				""");
	}

	private static Map<String, JsonNode> buildCustomConfig() {
		return Map.of(
				"action", MAPPER.nullNode(),
				"environment", MAPPER.nullNode(),
				"mqttPipConfig", customPipConfig(),
				"resource", MAPPER.nullNode(),
				"subject", MAPPER.nullNode());
	}
}
