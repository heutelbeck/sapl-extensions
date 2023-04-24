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

import java.time.Duration;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.hivemq.client.mqtt.mqtt5.Mqtt5BlockingClient;
import com.hivemq.embedded.EmbeddedHiveMQ;

import ch.qos.logback.classic.Level;
import io.sapl.api.interpreter.Val;
import io.sapl.interpreter.InitializationException;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

class SaplMqttClientSubscriptionsTest extends SaplMqttClientTest {

    private static EmbeddedHiveMQ embeddedHiveMqBroker;
    private static Mqtt5BlockingClient hiveMqClient;

    @BeforeAll
    static void setUp() {
         // set logging level
        logger.setLevel(Level.DEBUG);
    }

    @Test
    @Timeout(10)
    void when_subscribeToMultipleTopicsOnSingleFlux_then_getMessagesOfMultipleTopics() throws InitializationException {
        // GIVEN
        var topics = JSON.arrayNode().add("topic1").add("topic2");

        // WHEN
        embeddedHiveMqBroker = startEmbeddedHiveMqBroker();
        hiveMqClient = startMqttClient();
        Flux<Val> saplMqttMessageFlux = saplMqttClient.buildSaplMqttMessageFlux(Val.of(topics), config)
                .filter(val -> !val.isUndefined());

        // THEN
        StepVerifier.create(saplMqttMessageFlux)
                .thenAwait(Duration.ofMillis(1500))
                .then(() -> hiveMqClient.publish(buildMqttPublishMessage("topic1",
                        "message1", false)))
                .expectNext(Val.of("message1"))
                .then(() -> hiveMqClient.publish(buildMqttPublishMessage("topic2",
                        "message2", false)))
                .expectNext(Val.of("message2"))
                .thenCancel()
                .verify();

        // FINALLY
        hiveMqClient.disconnect();
        embeddedHiveMqBroker.stop().join();
    }

    @Test
    @Timeout(10)
    void when_subscribeToMultipleTopicsOnDifferentFlux_then_getMessagesOfMultipleTopics() throws InitializationException {
        // GIVEN
        var topicsFirstFlux = JSON.arrayNode().add("topic1").add("topic2");
        var topicsSecondFlux = JSON.arrayNode().add("topic2").add("topic3");

        Flux<Val> saplMqttMessageFluxFirst = saplMqttClient.buildSaplMqttMessageFlux(Val.of(topicsFirstFlux), config);
        Flux<Val> saplMqttMessageFluxSecond = saplMqttClient.buildSaplMqttMessageFlux(Val.of(topicsSecondFlux), config);

        // WHEN
        embeddedHiveMqBroker = startEmbeddedHiveMqBroker();
        hiveMqClient = startMqttClient();
        Flux<Val> saplMqttMessageFluxMerge = Flux.merge(saplMqttMessageFluxFirst, saplMqttMessageFluxSecond)
                .filter(val -> !val.isUndefined());

        // THEN
        StepVerifier.create(saplMqttMessageFluxMerge)
                .thenAwait(Duration.ofMillis(1500))
                .then(() -> hiveMqClient.publish(buildMqttPublishMessage("topic1",
                        "message1", false)))
                .expectNext(Val.of("message1"))
                .then(() -> hiveMqClient.publish(buildMqttPublishMessage("topic2",
                        "message2", false)))
                .expectNext(Val.of("message2"))
                .expectNext(Val.of("message2"))
                .then(() -> hiveMqClient.publish(buildMqttPublishMessage("topic3",
                        "message3", false)))
                .expectNext(Val.of("message3"))
                .thenCancel()
                .verify();

        // FINALLY
        hiveMqClient.disconnect();
        embeddedHiveMqBroker.stop().join();
    }

    @Test
    @Timeout(20)
    void when_oneFluxIsCancelledWhileSubscribingToSingleTopics_then_getMessagesOfLeftTopics() throws InitializationException {
        // GIVEN
        Flux<Val> saplMqttMessageFluxFirst = saplMqttClient.buildSaplMqttMessageFlux(Val.of("topic"), config);
        Flux<Val> saplMqttMessageFluxSecond = saplMqttClient.buildSaplMqttMessageFlux(Val.of("topic"), config)
                .takeUntil(value->value.getText().equals("message"));

        // WHEN
        embeddedHiveMqBroker = startEmbeddedHiveMqBroker();
        hiveMqClient = startMqttClient();
        Flux<Val> saplMqttMessageFluxMerge = Flux.merge(saplMqttMessageFluxFirst, saplMqttMessageFluxSecond)
                .filter(val -> !val.isUndefined());

        // THEN
        StepVerifier.create(saplMqttMessageFluxMerge)
                .thenAwait(Duration.ofMillis(1500))
                .then(() -> hiveMqClient.publish(buildMqttPublishMessage("topic", "message", false)))
                .expectNext(Val.of("message"))
                .expectNext(Val.of("message"))
                .thenAwait(Duration.ofMillis(1000))
                .then(() -> hiveMqClient.publish(buildMqttPublishMessage("topic", "message", false)))
                .expectNext(Val.of("message"))
                .expectNoEvent(Duration.ofMillis(2000))
                .thenCancel()
                .verify();

        // FINALLY
        hiveMqClient.disconnect();
        embeddedHiveMqBroker.stop().join();
    }

    @Test
    @Timeout(10)
    void when_oneFluxIsCancelledWhileSubscribingToMultipleTopics_then_getMessagesOfLeftTopics() throws InitializationException {
        // GIVEN
        var topicsFirstFlux = JSON.arrayNode().add("topic1").add("topic2");
        var topicsSecondFlux = JSON.arrayNode().add("topic2").add("topic3");

        Flux<Val> saplMqttMessageFluxFirst = saplMqttClient.buildSaplMqttMessageFlux(Val.of(topicsFirstFlux), config);
        Flux<Val> saplMqttMessageFluxSecond = saplMqttClient.buildSaplMqttMessageFlux(Val.of(topicsSecondFlux), config)
                .takeUntil(value->value.getText().equals("message2"));

        // WHEN
        embeddedHiveMqBroker = startEmbeddedHiveMqBroker();
        hiveMqClient = startMqttClient();
        Flux<Val> saplMqttMessageFluxMerge = Flux.merge(saplMqttMessageFluxFirst, saplMqttMessageFluxSecond)
                .filter(val -> !val.isUndefined());

        // THEN
        StepVerifier.create(saplMqttMessageFluxMerge)
                .thenAwait(Duration.ofMillis(1500))
                .then(() -> hiveMqClient.publish(buildMqttPublishMessage("topic1",
                        "message1", false)))
                .expectNext(Val.of("message1"))
                .then(() -> hiveMqClient.publish(buildMqttPublishMessage("topic3",
                        "message3", false)))
                .expectNext(Val.of("message3"))
                .then(() -> hiveMqClient.publish(buildMqttPublishMessage("topic2",
                        "message2", false)))
                .expectNext(Val.of("message2"))
                .expectNext(Val.of("message2"))
                .then(() -> hiveMqClient.publish(buildMqttPublishMessage("topic3",
                        "message3", false)))
                .expectNoEvent(Duration.ofMillis(2000))
                .then(() -> hiveMqClient.publish(buildMqttPublishMessage("topic1",
                        "message1", false)))
                .expectNext(Val.of("message1"))
                .then(() -> hiveMqClient.publish(buildMqttPublishMessage("topic2",
                        "message2", false)))
                .expectNext(Val.of("message2"))
                .thenCancel()
                .verify();

        // FINALLY
        hiveMqClient.disconnect();
        embeddedHiveMqBroker.stop().join();
    }

    @Test
    @Timeout(10)
    void when_subscribingWithSingleLevelWildcard_then_getMessagesMatchingTopicsOfSingleLevelWildcard() throws InitializationException {
        // GIVEN

        // WHEN
        embeddedHiveMqBroker = startEmbeddedHiveMqBroker();
        hiveMqClient = startMqttClient();
        Flux<Val> saplMqttMessageFlux = saplMqttClient.buildSaplMqttMessageFlux(Val.of("level1/+/level3"), config)
                .filter(val -> !val.isUndefined());

        // THEN
        StepVerifier.create(saplMqttMessageFlux)
                .thenAwait(Duration.ofMillis(1500))
                .then(() -> hiveMqClient.publish(buildMqttPublishMessage("level1/singleLevelWildcard/level3",
                        "message1", false)))
                .expectNext(Val.of("message1"))
                .thenCancel()
                .verify();

        // FINALLY
        hiveMqClient.disconnect();
        embeddedHiveMqBroker.stop().join();
    }

    @Test
    @Timeout(10)
    void when_subscribingWithMultiLevelWildcard_then_getMessagesMatchingTopicsOfMultiLevelWildcard() throws InitializationException {
        // GIVEN

        // WHEN
        embeddedHiveMqBroker = startEmbeddedHiveMqBroker();
        hiveMqClient = startMqttClient();
        Flux<Val> saplMqttMessageFlux = saplMqttClient.buildSaplMqttMessageFlux(Val.of("level1/#"), config)
                .filter(val -> !val.isUndefined());

        // THEN
        StepVerifier.create(saplMqttMessageFlux)
                .thenAwait(Duration.ofMillis(1500))
                .then(() -> hiveMqClient.publish(buildMqttPublishMessage("level1/multiLevelWildcard",
                        "message1", false)))
                .expectNext(Val.of("message1"))
                .then(() -> hiveMqClient.publish(buildMqttPublishMessage("level1/multiLevelWildcard/level3",
                        "message2", false)))
                .expectNext(Val.of("message2"))
                .thenCancel()
                .verify();

        // FINALLY
        hiveMqClient.disconnect();
        embeddedHiveMqBroker.stop().join();
    }

    @Test
    @Timeout(10)
    void when_unsubscribingTopicOnSharedConnectionWithMultiLevelWildcard_then_getMessagesMatchingTopicsOfMultiLevelWildcard() throws InitializationException {
        // GIVEN
        Flux<Val> saplMqttMessageFluxFirst = saplMqttClient.buildSaplMqttMessageFlux(Val.of("level1/#"), config);
        Flux<Val> saplMqttMessageFluxSecond = saplMqttClient.buildSaplMqttMessageFlux(Val.of("level1/level2"), config)
                .takeUntil(value -> value.getText().equals("message1"));

        // WHEN
        embeddedHiveMqBroker = startEmbeddedHiveMqBroker();
        hiveMqClient = startMqttClient();
        Flux<Val> saplMqttMessageFluxMerge = Flux.merge(saplMqttMessageFluxFirst, saplMqttMessageFluxSecond)
                .filter(val -> !val.isUndefined());

        // THEN
        StepVerifier.create(saplMqttMessageFluxMerge)
                .thenAwait(Duration.ofMillis(1500))
                .then(() -> hiveMqClient.publish(buildMqttPublishMessage("level1/level2",
                        "message1", false)))
                .expectNext(Val.of("message1"))
                .expectNext(Val.of("message1"))
                .thenAwait(Duration.ofMillis(1000))
                .then(() -> hiveMqClient.publish(buildMqttPublishMessage("level1/level2",
                        "message1", false)))
                .expectNext(Val.of("message1"))
                .expectNoEvent(Duration.ofMillis(2000))
                .thenCancel()
                .verify();

        // FINALLY
        hiveMqClient.disconnect();
        embeddedHiveMqBroker.stop().join();
    }

    @Test
    @Timeout(10)
    void when_unsubscribingMultiLevelWildcardTopicOnSharedConnectionWithSimpleTopic_then_getMessagesMatchingSimpleTopic() throws InitializationException {
        // GIVEN
        Flux<Val> saplMqttMessageFluxFirst = saplMqttClient.buildSaplMqttMessageFlux(Val.of("level1/level2"), config);
        Flux<Val> saplMqttMessageFluxSecond = saplMqttClient.buildSaplMqttMessageFlux(Val.of("level1/#"), config)
                .takeUntil(value -> value.getText().equals("message1"));

        // WHEN
        embeddedHiveMqBroker = startEmbeddedHiveMqBroker();
        hiveMqClient = startMqttClient();
        Flux<Val> saplMqttMessageFluxMerge = Flux.merge(saplMqttMessageFluxFirst, saplMqttMessageFluxSecond)
                .filter(val -> !val.isUndefined());

        // THEN
        StepVerifier.create(saplMqttMessageFluxMerge)
                .thenAwait(Duration.ofMillis(1500))
                .then(() -> hiveMqClient.publish(buildMqttPublishMessage("level1/level2",
                        "message1", false)))
                .expectNext(Val.of("message1"))
                .expectNext(Val.of("message1"))
                .thenAwait(Duration.ofMillis(1000))
                .then(() -> hiveMqClient.publish(buildMqttPublishMessage("level1/level2",
                        "message1", false)))
                .expectNext(Val.of("message1"))
                .then(() -> hiveMqClient.publish(buildMqttPublishMessage("level1/xxx",
                        "message1", false)))
                .expectNoEvent(Duration.ofMillis(2000))
                .thenCancel()
                .verify();

        // FINALLY
        hiveMqClient.disconnect();
        embeddedHiveMqBroker.stop().join();
    }

    @Test
    @Timeout(10)
    void when_unsubscribingSingleLevelWildcardTopicOnSharedConnectionWithSimpleTopic_then_getMessagesMatchingSimpleTopic() throws InitializationException {
        // GIVEN
        Flux<Val> saplMqttMessageFluxFirst = saplMqttClient.buildSaplMqttMessageFlux(
                Val.of("level1/level2/level3"), config);
        Flux<Val> saplMqttMessageFluxSecond = saplMqttClient.buildSaplMqttMessageFlux(
                Val.of("level1/+/level3"), config)
                .takeUntil(value -> value.getText().equals("message1"));

        // WHEN
        embeddedHiveMqBroker = startEmbeddedHiveMqBroker();
        hiveMqClient = startMqttClient();
        Flux<Val> saplMqttMessageFluxMerge = Flux.merge(saplMqttMessageFluxFirst, saplMqttMessageFluxSecond)
                .filter(val -> !val.isUndefined());

        // THEN
        StepVerifier.create(saplMqttMessageFluxMerge)
                .thenAwait(Duration.ofMillis(1500))
                .then(() -> hiveMqClient.publish(buildMqttPublishMessage("level1/level2/level3",
                        "message1", false)))
                .expectNext(Val.of("message1"))
                .expectNext(Val.of("message1"))
                .thenAwait(Duration.ofMillis(1000))
                .then(() -> hiveMqClient.publish(buildMqttPublishMessage("level1/level2/level3",
                        "message1", false)))
                .expectNext(Val.of("message1"))
                .then(() -> hiveMqClient.publish(buildMqttPublishMessage("level1/xxx/level3",
                        "message1", false)))
                .expectNoEvent(Duration.ofMillis(2000))
                .thenCancel()
                .verify();

        // FINALLY
        hiveMqClient.disconnect();
        embeddedHiveMqBroker.stop().join();
    }
}
