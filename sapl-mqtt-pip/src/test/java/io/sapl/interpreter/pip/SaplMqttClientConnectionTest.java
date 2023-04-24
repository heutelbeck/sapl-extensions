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

import static io.sapl.interpreter.pip.SaplMqttClient.ENVIRONMENT_BROKER_ADDRESS;
import static io.sapl.interpreter.pip.SaplMqttClient.ENVIRONMENT_BROKER_PORT;
import static io.sapl.interpreter.pip.SaplMqttClient.ENVIRONMENT_CLIENT_ID;
import static io.sapl.interpreter.pip.util.ConfigUtility.ENVIRONMENT_BROKER_CONFIG;
import static io.sapl.interpreter.pip.util.ConfigUtility.ENVIRONMENT_BROKER_CONFIG_NAME;
import static io.sapl.interpreter.pip.util.ConfigUtility.ENVIRONMENT_DEFAULT_BROKER_CONFIG_NAME;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.fasterxml.jackson.databind.JsonNode;
import com.hivemq.embedded.EmbeddedHiveMQ;

import io.sapl.api.interpreter.Val;
import io.sapl.interpreter.InitializationException;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;
import reactor.test.publisher.TestPublisher;

class SaplMqttClientConnectionTest extends SaplMqttClientTest {

    @AfterEach
    void afterEach() {
        embeddedHiveMqBroker.stop();
    }

    @Test
    @Timeout(30)
    void when_brokerConfigIsInvalid_then_returnValOfError() throws InitializationException {
        // GIVEN
        embeddedHiveMqBroker = startEmbeddedHiveMqBroker();
        var mqttPipConfigForUndefinedVal = JSON.objectNode()
                .put(ENVIRONMENT_DEFAULT_BROKER_CONFIG_NAME, "falseName")
                .set(ENVIRONMENT_BROKER_CONFIG, JSON.arrayNode().add(JSON.objectNode()
                        .put(ENVIRONMENT_BROKER_CONFIG_NAME, "production")
                        .put(ENVIRONMENT_BROKER_ADDRESS, "localhost")
                        .put(ENVIRONMENT_BROKER_PORT, 1883)
                        .put(ENVIRONMENT_CLIENT_ID, "mqttPipDefault")));
        var configForUndefinedVal = Map.of(
                "action", action,
                "environment", environment,
                "mqttPipConfig", mqttPipConfigForUndefinedVal,
                "resource", resource,
                "subject", subject);
        // WHEN
        var hiveMqClient = startMqttClient();
        Flux<Val> saplMqttMessageFlux = saplMqttClient.buildSaplMqttMessageFlux(topic, configForUndefinedVal);

        // THEN
        StepVerifier.create(saplMqttMessageFlux)
                .thenAwait(Duration.ofMillis(1000))
                .expectNextMatches(Val::isError)
                .thenCancel()
                .verify();

        hiveMqClient.disconnect();
        embeddedHiveMqBroker.stop().join();
    }

    @Test
    @Timeout(30)
    void when_noConfigIsSpecified_then_returnValOfError() {
        // GIVEN
        embeddedHiveMqBroker = startEmbeddedHiveMqBroker();
        Map<String, JsonNode> emptyPdpConfig = Map.of();
        // WHEN
        Flux<Val> saplMqttMessageFlux = saplMqttClient.buildSaplMqttMessageFlux(topic, emptyPdpConfig);

        // THEN
        StepVerifier.create(saplMqttMessageFlux)
                .thenAwait(Duration.ofMillis(1000))
                .expectNextMatches(Val::isError)
                .thenCancel()
                .verify();

        embeddedHiveMqBroker.stop().join();
    }

    @Test
    @Timeout(30)
    void when_connectionIsShared_then_bothMessageFluxWorking() throws InitializationException {
        // GIVEN
        Flux<Val> saplMqttMessageFluxFirst = saplMqttClient.buildSaplMqttMessageFlux(Val.of("topic1"), config);
        Flux<Val> saplMqttMessageFluxSecond = saplMqttClient.buildSaplMqttMessageFlux(Val.of("topic2"), config);

        // WHEN
        Flux<Val> saplMqttMessageFluxMerge = Flux.merge(saplMqttMessageFluxFirst, saplMqttMessageFluxSecond);

        embeddedHiveMqBroker = startEmbeddedHiveMqBroker();
        var hiveMqClient = startMqttClient();

        // THEN
        StepVerifier.create(saplMqttMessageFluxMerge)
                .thenAwait(Duration.ofMillis(1000))
                .expectNext(Val.UNDEFINED, Val.UNDEFINED)
                .then(() -> hiveMqClient.publish(buildMqttPublishMessage("topic1",
                        "message1", false)))
                .expectNext(Val.of("message1"))
                .then(() -> hiveMqClient.publish(buildMqttPublishMessage("topic2",
                        "message2", false)))
                .expectNext(Val.of("message2"))
                .then(() -> hiveMqClient.publish(buildMqttPublishMessage("topic1",
                        "message1", false)))
                .expectNext(Val.of("message1"))
                .thenCancel()
                .verify();
        hiveMqClient.disconnect();
        embeddedHiveMqBroker.stop().join();
    }

    @Test
    @Timeout(30)
    void when_connectionIsNotSharedAnymore_then_singleFluxWorking() throws InitializationException {
        // GIVEN
        Flux<Val> saplMqttMessageFluxFirst = saplMqttClient.buildSaplMqttMessageFlux(Val.of("topic1"), config);
        Flux<Val> saplMqttMessageFluxSecond = saplMqttClient.buildSaplMqttMessageFlux(Val.of("topic2"), config)
                .takeUntil((message) -> message.getText().equals("lastMessage"));

        // WHEN
        Flux<Val> saplMqttMessageFluxMerge = Flux.merge(saplMqttMessageFluxFirst, saplMqttMessageFluxSecond);

        embeddedHiveMqBroker = startEmbeddedHiveMqBroker();
        var hiveMqClient = startMqttClient();

        // THEN
        StepVerifier.create(saplMqttMessageFluxMerge)
                .thenAwait(Duration.ofMillis(1000))
                .expectNext(Val.UNDEFINED, Val.UNDEFINED)
                .then(() -> hiveMqClient.publish(buildMqttPublishMessage("topic1",
                        "message1", false)))
                .expectNext(Val.of("message1"))
                .then(() -> hiveMqClient.publish(buildMqttPublishMessage("topic2",
                        "message2", false)))
                .expectNext(Val.of("message2"))
                .then(() -> hiveMqClient.publish(buildMqttPublishMessage("topic2",
                        "lastMessage", false)))
                .expectNext(Val.of("lastMessage"))
                .thenAwait(Duration.ofMillis(1000))
                .then(() -> hiveMqClient.publish(buildMqttPublishMessage("topic2",
                        "message2", false)))
                .expectNoEvent(Duration.ofMillis(1000))
                .then(() -> hiveMqClient.publish(buildMqttPublishMessage("topic1",
                        "message1", false)))
                .expectNext(Val.of("message1"))
                .thenCancel()
                .verify();
        hiveMqClient.disconnect();
        embeddedHiveMqBroker.stop().join();
    }

    @Test
    @Timeout(30)
    void when_connectionIsNotSharedAnymoreAndThenSharedAgain_then_bothMessageFluxWorking()
            throws InitializationException {
        // GIVEN
        Flux<Val> saplMqttMessageFluxFirst = saplMqttClient.buildSaplMqttMessageFlux(Val.of("topic1"), config);
        Flux<Val> saplMqttMessageFluxSecond = saplMqttClient.buildSaplMqttMessageFlux(Val.of("topic2"), config)
                .takeUntil((message) -> message.getText().equals("lastMessage"));
        TestPublisher<String> testPublisher = TestPublisher.create();

        // WHEN
        Flux<Val> saplMqttMessageFluxMerge = Flux.merge(saplMqttMessageFluxFirst, saplMqttMessageFluxSecond,
                saplMqttMessageFluxSecond.delaySubscription(testPublisher.flux()));

        embeddedHiveMqBroker = startEmbeddedHiveMqBroker();
        var hiveMqClient = startMqttClient();

        // THEN
        StepVerifier.create(saplMqttMessageFluxMerge)
                .expectNext(Val.UNDEFINED, Val.UNDEFINED)
                .then(() -> hiveMqClient.publish(buildMqttPublishMessage("topic1",
                        "message1", false)))
                .expectNext(Val.of("message1"))
                .then(() -> hiveMqClient.publish(buildMqttPublishMessage("topic2",
                        "message2", false)))
                .expectNext(Val.of("message2"))
                .then(() -> hiveMqClient.publish(buildMqttPublishMessage("topic2",
                        "lastMessage", false)))
                .expectNext(Val.of("lastMessage"))
                .thenAwait(Duration.ofMillis(1000))
                .then(() -> hiveMqClient.publish(buildMqttPublishMessage("topic1",
                        "message1", false)))
                .expectNext(Val.of("message1"))
                .then(() -> testPublisher.emit("subscribe"))
                .thenAwait(Duration.ofMillis(1000))
                .then(() -> hiveMqClient.publish(buildMqttPublishMessage("topic2",
                        "message2", false)))
                .expectNext(Val.of("message2"))
                .then(() -> hiveMqClient.publish(buildMqttPublishMessage("topic1",
                        "message1", false)))
                .expectNext(Val.of("message1"))
                .thenCancel()
                .verify();
        hiveMqClient.disconnect();
        embeddedHiveMqBroker.stop().join();
    }

    @Test
    @Timeout(30)
    void when_brokerConnectionLost_then_reconnectToBroker() throws InitializationException {
        // WHEN
        embeddedHiveMqBroker = startEmbeddedHiveMqBroker();
        var embeddedHiveMqBrokerCopy = EmbeddedHiveMQ.builder()
                .withConfigurationFolder(Path.of("src/test/resources/embedded-config-folder"))
                .withDataFolder(Path.of("src/test/resources/embedded-data-folder"))
                .withExtensionsFolder(Path.of("src/test/resources/embedded-extensions-folder"))
                .build();
        var hiveMqClient = startMqttClient();
        Flux<Val> saplMqttMessageFlux = saplMqttClient.buildSaplMqttMessageFlux(topic, config);

        // THEN
        StepVerifier.create(saplMqttMessageFlux)
                .thenAwait(Duration.ofMillis(1000))
                .then(() -> hiveMqClient.publish(publishMessage))
                .expectNext(Val.of("message"))

                .then(hiveMqClient::disconnect)
                .thenAwait(Duration.ofMillis(1000))

                .then(() -> {
                    try {
                        embeddedHiveMqBroker.close();
                    } catch (ExecutionException | InterruptedException e) {
                        e.printStackTrace();
                    }
                })

                .thenAwait(Duration.ofMillis(5000))
                .then(() -> embeddedHiveMqBrokerCopy.start().join())
                .thenAwait(Duration.ofMillis(2000))

                .then(hiveMqClient::connect)
                .thenAwait(Duration.ofMillis(5000))

                .then(() -> hiveMqClient.publish(buildMqttPublishMessage("topic", "message", true)))
                .expectNext(Val.of("message"))
                .thenCancel()
                .verify();
        hiveMqClient.disconnect();
        embeddedHiveMqBrokerCopy.stop().join();
    }

    @Test
    @Timeout(30)
    void when_brokerConnectionLostWhileSharingConnection_then_reconnectToBroker() throws InitializationException {
        // GIVEN
        Flux<Val> saplMqttMessageFluxFirst = saplMqttClient.buildSaplMqttMessageFlux(Val.of("topic1"), config);
        Flux<Val> saplMqttMessageFluxSecond = saplMqttClient.buildSaplMqttMessageFlux(Val.of("topic2"), config);

        // WHEN
        Flux<Val> saplMqttMessageFluxMerge = Flux.merge(saplMqttMessageFluxFirst, saplMqttMessageFluxSecond);

        embeddedHiveMqBroker = startEmbeddedHiveMqBroker();
        var embeddedHiveMqBrokerCopy = EmbeddedHiveMQ.builder()
                .withConfigurationFolder(Path.of("src/test/resources/embedded-config-folder"))
                .withDataFolder(Path.of("src/test/resources/embedded-data-folder"))
                .withExtensionsFolder(Path.of("src/test/resources/embedded-extensions-folder"))
                .build();
        var hiveMqClient = startMqttClient();

        // THEN
        StepVerifier.create(saplMqttMessageFluxMerge)
                .thenAwait(Duration.ofMillis(1000))
                .then(() -> hiveMqClient.publish(buildMqttPublishMessage("topic1",
                        "message1", false)))
                .expectNext(Val.of("message1"))
                .then(() -> hiveMqClient.publish(buildMqttPublishMessage("topic2",
                        "message2", false)))
                .expectNext(Val.of("message2"))

                .then(hiveMqClient::disconnect)
                .thenAwait(Duration.ofMillis(1000))

                .then(() -> {
                    try {
                        embeddedHiveMqBroker.close();
                    } catch (ExecutionException | InterruptedException e) {
                        e.printStackTrace();
                    }
                })

                .thenAwait(Duration.ofMillis(5000))
                .then(() -> embeddedHiveMqBrokerCopy.start().join())
                .thenAwait(Duration.ofMillis(2000))

                .then(hiveMqClient::connect)
                .thenAwait(Duration.ofMillis(5000))

                .then(() -> hiveMqClient.publish(buildMqttPublishMessage("topic2",
                        "message2", true)))
                .then(() -> hiveMqClient.publish(buildMqttPublishMessage("topic1",
                        "message1", true)))
                .consumeNextWith(value -> {
                    assert value.getText().equals("message2") || value.getText().equals("message1");
                })
                .consumeNextWith(value -> {
                    assert value.getText().equals("message2") || value.getText().equals("message1");
                })
                .thenCancel()
                .verify();
        hiveMqClient.disconnect();
        embeddedHiveMqBrokerCopy.stop().join();
    }

    @Test
    @Timeout(30)
    void when_sharedReconnectToBroker_then_getMessagesOfMultipleTopics() throws InitializationException {
        // GIVEN
        var topicsFirstFlux = JSON.arrayNode().add("topic1").add("topic2");
        var topicsSecondFlux = JSON.arrayNode().add("topic2").add("topic3");

        Flux<Val> saplMqttMessageFluxFirst = saplMqttClient.buildSaplMqttMessageFlux(Val.of(topicsFirstFlux), config);
        Flux<Val> saplMqttMessageFluxSecond = saplMqttClient.buildSaplMqttMessageFlux(Val.of(topicsSecondFlux), config)
                .takeUntil(value->value.getText().equals("message2"));

        // WHEN
        Flux<Val> saplMqttMessageFluxMerge = Flux.merge(saplMqttMessageFluxFirst, saplMqttMessageFluxSecond);

        embeddedHiveMqBroker = startEmbeddedHiveMqBroker();
        var embeddedHiveMqBrokerCopy = EmbeddedHiveMQ.builder()
                .withConfigurationFolder(Path.of("src/test/resources/embedded-config-folder"))
                .withDataFolder(Path.of("src/test/resources/embedded-data-folder"))
                .withExtensionsFolder(Path.of("src/test/resources/embedded-extensions-folder"))
                .build();
        var hiveMqClient = startMqttClient();

        // THEN
        StepVerifier.create(saplMqttMessageFluxMerge)
                .thenAwait(Duration.ofMillis(1000))
                .then(() -> hiveMqClient.publish(buildMqttPublishMessage("topic1",
                        "message1", false)))
                .expectNext(Val.of("message1"))
                .then(() -> hiveMqClient.publish(buildMqttPublishMessage("topic3",
                        "message3", false)))
                .expectNext(Val.of("message3"))

                .then(hiveMqClient::disconnect)
                .thenAwait(Duration.ofMillis(1000))

                .then(() -> {
                    try {
                        embeddedHiveMqBroker.close();
                    } catch (ExecutionException | InterruptedException e) {
                        e.printStackTrace();
                    }
                })

                .thenAwait(Duration.ofMillis(5000))
                .then(() -> embeddedHiveMqBrokerCopy.start().join())
                .thenAwait(Duration.ofMillis(2000))

                .then(hiveMqClient::connect)
                .thenAwait(Duration.ofMillis(5000))

                .then(() -> hiveMqClient.publish(buildMqttPublishMessage("topic3",
                        "message3", true)))
                .expectNext(Val.of("message3"))
                .then(() -> hiveMqClient.publish(buildMqttPublishMessage("topic1",
                        "message1", true)))
                .expectNext(Val.of("message1"))
                .then(() -> hiveMqClient.publish(buildMqttPublishMessage("topic2",
                        "message2", true)))
                .expectNext(Val.of("message2"))
                .expectNext(Val.of("message2"))
                .thenAwait(Duration.ofMillis(1000))
                .then(() -> hiveMqClient.publish(buildMqttPublishMessage("topic1",
                        "message1", false)))
                .expectNext(Val.of("message1"))
                .then(() -> hiveMqClient.publish(buildMqttPublishMessage("topic2",
                        "message2", false)))
                .expectNext(Val.of("message2"))
                .expectNoEvent(Duration.ofMillis(2000))
                .then(() -> hiveMqClient.publish(buildMqttPublishMessage("topic3",
                        "message3", false)))
                .expectNoEvent(Duration.ofMillis(2000))
                .thenCancel()
                .verify();
        hiveMqClient.disconnect();
        embeddedHiveMqBrokerCopy.stop().join();
    }

    @Test
    @Timeout(30)
    void when_emitValUndefinedActivatedAndBrokerConnectionLost_then_reconnectToBrokerAndEmitValUndefined()
            throws InitializationException {
        // GIVEN
        embeddedHiveMqBroker = startEmbeddedHiveMqBroker();
        var embeddedHiveMqBrokerSecond = EmbeddedHiveMQ.builder()
                .withConfigurationFolder(Path.of("src/test/resources/embedded-config-folder"))
                .withDataFolder(Path.of("src/test/resources/embedded-data-folder"))
                .withExtensionsFolder(Path.of("src/test/resources/embedded-extensions-folder"))
                .build();
        var mqttPipConfigForUndefinedVal = JSON.objectNode()
                .put(ENVIRONMENT_DEFAULT_BROKER_CONFIG_NAME, "production")
                .set(ENVIRONMENT_BROKER_CONFIG, JSON.arrayNode().add(JSON.objectNode()
                        .put(ENVIRONMENT_BROKER_CONFIG_NAME, "production")
                        .put(ENVIRONMENT_BROKER_ADDRESS, "localhost")
                        .put(ENVIRONMENT_BROKER_PORT, 1883)
                        .put(ENVIRONMENT_CLIENT_ID, "mqttPipDefault")));
        var configForUndefinedVal = Map.of(
                "action", action,
                "environment", environment,
                "mqttPipConfig", mqttPipConfigForUndefinedVal,
                "resource", resource,
                "subject", subject);

        // WHEN
        var hiveMqClient = startMqttClient();
        Flux<Val> saplMqttMessageFlux = saplMqttClient.buildSaplMqttMessageFlux(topic, configForUndefinedVal);

        // THEN
        StepVerifier.create(saplMqttMessageFlux)
                .thenAwait(Duration.ofMillis(1000))
                .then(() -> hiveMqClient.publish(publishMessage))
                .expectNext(Val.of("message"))

                .then(hiveMqClient::disconnect)
                .thenAwait(Duration.ofMillis(1000))

                .then(() -> {
                    try {
                        embeddedHiveMqBroker.close();
                    } catch (ExecutionException | InterruptedException e) {
                        e.printStackTrace();
                    }
                })

                .thenAwait(Duration.ofMillis(5000))
                .then(() -> embeddedHiveMqBrokerSecond.start().join())
                .thenAwait(Duration.ofMillis(2000))

                .then(hiveMqClient::connect)
                .thenAwait(Duration.ofMillis(5000))

                .expectNext(Val.UNDEFINED)
                .then(() -> hiveMqClient.publish(buildMqttPublishMessage("topic", "message", true)))
                .expectNext(Val.of("message"))

                .thenCancel()
                .verify();

        hiveMqClient.disconnect();
        embeddedHiveMqBrokerSecond.stop().join();
    }
}