package io.sapl.interpreter.pip;

import static io.sapl.interpreter.pip.SaplMqttClient.ENVIRONMENT_BROKER_ADDRESS;
import static io.sapl.interpreter.pip.SaplMqttClient.ENVIRONMENT_BROKER_PORT;
import static io.sapl.interpreter.pip.SaplMqttClient.ENVIRONMENT_CLIENT_ID;
import static io.sapl.interpreter.pip.util.ConfigUtility.ENVIRONMENT_BROKER_CONFIG;
import static io.sapl.interpreter.pip.util.ConfigUtility.ENVIRONMENT_BROKER_CONFIG_NAME;
import static io.sapl.interpreter.pip.util.ConfigUtility.ENVIRONMENT_DEFAULT_BROKER_CONFIG_NAME;
import static io.sapl.interpreter.pip.util.DefaultResponseUtility.ENVIRONMENT_DEFAULT_RESPONSE;
import static io.sapl.interpreter.pip.util.DefaultResponseUtility.ENVIRONMENT_DEFAULT_RESPONSE_TIMEOUT;
import static io.sapl.interpreter.pip.util.ErrorUtility.ENVIRONMENT_EMIT_AT_RETRY;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hivemq.client.mqtt.mqtt5.Mqtt5BlockingClient;
import com.hivemq.embedded.EmbeddedHiveMQ;

import ch.qos.logback.classic.Level;
import io.sapl.api.interpreter.Val;
import io.sapl.interpreter.InitializationException;
import io.sapl.interpreter.pip.util.DefaultResponseConfig;
import io.sapl.interpreter.pip.util.DefaultResponseUtility;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

class SaplMqttDefaultResponseTest  extends SaplMqttClientTest {
    private static EmbeddedHiveMQ embeddedHiveMqBroker;
    private static Mqtt5BlockingClient hiveMqClient;

    @BeforeAll
    static void setUp() throws InitializationException {
        // set logging level
        logger.setLevel(Level.OFF);

        embeddedHiveMqBroker = startEmbeddedHiveMqBroker();
        hiveMqClient = startMqttClient();
    }

    @AfterAll
    static void tearDown() {
        hiveMqClient.disconnect();
        embeddedHiveMqBroker.stop().join();
    }

    @Test
    void when_subscribingWithDefaultConfigAndBrokerDoesNotSendMessage_then_getDefaultUndefined() {
        // GIVEN
        var topics = JSON.arrayNode().add("topic1").add("topic2");

        // WHEN
        Flux<Val> saplMqttMessageFlux = saplMqttClient.buildSaplMqttMessageFlux(Val.of(topics), config);

        // THEN
        StepVerifier.create(saplMqttMessageFlux)
                .thenAwait(Duration.ofMillis(1000))
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
                .thenAwait(Duration.ofMillis(1000))
                .expectNextMatches(Val::isError)
                .thenCancel()
                .verify();
    }

    @Test
    void when_subscribingWithDefaultResponseTypeSpecifiedInAttributeFinderParams_then_useThisDefaultResponseType() {
        // GIVEN
        var topics = JSON.arrayNode().add("topic1").add("topic2");
        ObjectNode configParams = JSON.objectNode();
        configParams.put(ENVIRONMENT_BROKER_ADDRESS, "localhost");
        configParams.put(ENVIRONMENT_BROKER_PORT, 1883);
        configParams.put(ENVIRONMENT_CLIENT_ID, "clientId");
        configParams.put(ENVIRONMENT_DEFAULT_RESPONSE, "error");

        // WHEN
        Flux<Val> saplMqttMessageFlux = saplMqttClient.buildSaplMqttMessageFlux(Val.of(topics), config,
                Val.of(0), Val.of(configParams));

        // THEN
        StepVerifier.create(saplMqttMessageFlux)
                .thenAwait(Duration.ofMillis(1000))
                .expectNextMatches(Val::isError)
                .thenCancel()
                .verify();
    }

    @Test
    void when_subscribingWithDefaultResponseTimeoutSpecifiedInAttributeFinderParams_then_useThisDefaultResponseTimeout() {
        // GIVEN
        var topics = JSON.arrayNode().add("topic1").add("topic2");
        ObjectNode configParams = JSON.objectNode();
        configParams.put(ENVIRONMENT_BROKER_ADDRESS, "localhost");
        configParams.put(ENVIRONMENT_BROKER_PORT, 1883);
        configParams.put(ENVIRONMENT_CLIENT_ID, "clientId");
        configParams.put(ENVIRONMENT_DEFAULT_RESPONSE, "error");
        configParams.put(ENVIRONMENT_DEFAULT_RESPONSE_TIMEOUT, 8000);

        // WHEN
        Flux<Val> saplMqttMessageFlux = saplMqttClient.buildSaplMqttMessageFlux(Val.of(topics), config,
                Val.of(0), Val.of(configParams));

        // THEN
        StepVerifier.create(saplMqttMessageFlux)
                .thenAwait(Duration.ofMillis(10000))
                .expectNextMatches(Val::isError)
                .thenCancel()
                .verify();
    }

    @Test
    void when_specifyingIllegalDefaultResponseType_then_usingDefaultResponseType() {
        // GIVEN
        DefaultResponseConfig defaultResponseConfig =
                new DefaultResponseConfig(5000, "illegal");

        // WHEN
        Val defaultVal = DefaultResponseUtility.getDefaultVal(defaultResponseConfig);

        // THEN
        assertTrue(defaultVal.isUndefined());
    }

    protected JsonNode buildCustomMqttPipConfig() {
        return JSON.objectNode()
                .put(ENVIRONMENT_DEFAULT_BROKER_CONFIG_NAME, "production")
                .put(ENVIRONMENT_EMIT_AT_RETRY, "false")
                .put(ENVIRONMENT_DEFAULT_RESPONSE, "error")
                .set(ENVIRONMENT_BROKER_CONFIG, JSON.arrayNode().add(JSON.objectNode()
                        .put(ENVIRONMENT_BROKER_CONFIG_NAME, "production")
                        .put(ENVIRONMENT_BROKER_ADDRESS, "localhost")
                        .put(ENVIRONMENT_BROKER_PORT, 1883)
                        .put(ENVIRONMENT_CLIENT_ID, "mqttPipDefault")));
    }

    protected Map<String, JsonNode> buildCustomConfig() {
        return Map.of(
                "action", action,
                "environment", environment,
                "mqttPipConfig", buildCustomMqttPipConfig(),
                "resource", resource,
                "subject", subject);
    }
}
