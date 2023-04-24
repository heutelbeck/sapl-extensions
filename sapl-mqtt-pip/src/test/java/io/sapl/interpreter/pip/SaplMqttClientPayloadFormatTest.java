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

import static io.sapl.interpreter.pip.util.PayloadFormatUtility.convertBytesToArrayNode;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.fasterxml.jackson.databind.JsonNode;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt5.Mqtt5BlockingClient;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5PayloadFormatIndicator;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import com.hivemq.embedded.EmbeddedHiveMQ;

import ch.qos.logback.classic.Level;
import io.sapl.api.interpreter.Val;
import io.sapl.interpreter.InitializationException;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

class SaplMqttClientPayloadFormatTest extends SaplMqttClientTest {

    private static EmbeddedHiveMQ embeddedHiveMqBroker;
    private static Mqtt5BlockingClient hiveMqClient;

    private static final String byteArrayTopic = "byteArrayTopic";
    private static final String jsonTopic = "jsonTopic";

    @BeforeAll
    static void setUp() {
        // set logging level
        logger.setLevel(Level.OFF);
    }

    @Test
    @Timeout(15)
    void when_mqttMessageContentTypeIsJson_then_getValOfJson() throws InitializationException {
        // GIVEN
        var topic = JSON.arrayNode().add(jsonTopic);
        var jsonMessage = JSON.arrayNode().add("message1").add(JSON.objectNode()
                .put("key", "value"));

        // WHEN
        embeddedHiveMqBroker = startEmbeddedHiveMqBroker();
        hiveMqClient = startMqttClient();
        Flux<Val> saplMqttMessageFlux = saplMqttClient.buildSaplMqttMessageFlux(Val.of(topic), config)
                .filter(val -> !val.isUndefined());

        // THEN
        StepVerifier.create(saplMqttMessageFlux)
                .thenAwait(Duration.ofMillis(2000))
                .then(() -> hiveMqClient.publish(buildMqttPublishJsonMessage(jsonMessage)))
                .expectNextMatches((value) -> value.get().equals(jsonMessage))
                .thenCancel()
                .verify();

        // FINALLY
        hiveMqClient.disconnect();
        embeddedHiveMqBroker.stop().join();
    }

    @Test
    @Timeout(10)
    void when_inconsistentMqttMessageIsPublished_then_getValOfError() throws InitializationException {
        // GIVEN
        var topic = JSON.arrayNode().add("topic");
        var jsonMessage = "{test}";
        var mqttMessage= Mqtt5Publish.builder()
                .topic("topic")
                .qos(MqttQos.AT_MOST_ONCE)
                .retain(true)
                .payloadFormatIndicator(Mqtt5PayloadFormatIndicator.UTF_8)
                .payload(jsonMessage.getBytes(StandardCharsets.UTF_8))
                .contentType("application/json")
                .build();

        // WHEN
        embeddedHiveMqBroker = startEmbeddedHiveMqBroker();
        hiveMqClient = startMqttClient();
        Flux<Val> saplMqttMessageFlux = saplMqttClient.buildSaplMqttMessageFlux(Val.of(topic), config)
                .filter(val -> !val.isUndefined());

        // THEN
        StepVerifier.create(saplMqttMessageFlux)
                .thenAwait(Duration.ofMillis(2000))
                .then(() -> hiveMqClient.publish(mqttMessage))
                .expectNextMatches((value) -> Objects.equals(value.getValType(), Val.error().getValType()))
                .thenCancel()
                .verify();

        // FINALLY
        hiveMqClient.disconnect();
        embeddedHiveMqBroker.stop().join();
    }

    @Test
    @Timeout(10)
    void when_mqttMessagePayloadIsFormatIsByteArray_then_getArrayOfBytesAsInts() throws InitializationException {
        // GIVEN
        var topic = JSON.arrayNode().add(byteArrayTopic);
        var message = "byteArray";

        // WHEN
        embeddedHiveMqBroker = startEmbeddedHiveMqBroker();
        hiveMqClient = startMqttClient();
        Flux<Val> saplMqttMessageFlux = saplMqttClient.buildSaplMqttMessageFlux(Val.of(topic), config)
                .filter(val -> !val.isUndefined());

        // THEN
        StepVerifier.create(saplMqttMessageFlux)
                .thenAwait(Duration.ofMillis(1500))
                .then(() -> hiveMqClient.publish(buildMqttPublishByteArrayMessageWithIndicator(message)))
                .expectNextMatches((valueArray) ->
                        valueArray.get().equals(convertBytesToArrayNode(message.getBytes(StandardCharsets.UTF_8))))
                .thenCancel()
                .verify();

        // FINALLY
        hiveMqClient.disconnect();
        embeddedHiveMqBroker.stop().join();
    }

    @Test
    @Timeout(10)
    void when_mqttMessagePayloadIsUtf8EncodedAndNoFormatIndicatorSet_then_getPayloadAsText()
            throws InitializationException {
        // GIVEN
        var topic = JSON.arrayNode().add(byteArrayTopic);
        var message = "byteArray";

        // WHEN
        embeddedHiveMqBroker = startEmbeddedHiveMqBroker();
        hiveMqClient = startMqttClient();
        Flux<Val> saplMqttMessageFlux = saplMqttClient.buildSaplMqttMessageFlux(Val.of(topic), config)
                .filter(val -> !val.isUndefined());

        // THEN
        StepVerifier.create(saplMqttMessageFlux)
                .thenAwait(Duration.ofMillis(1500))
                .then(() -> hiveMqClient.publish(buildMqttPublishByteArrayMessageWithoutIndicator(message)))
                .expectNextMatches((valueArray) ->
                        valueArray.get().textValue().equals(message))
                .thenCancel()
                .verify();

        // FINALLY
        hiveMqClient.disconnect();
        embeddedHiveMqBroker.stop().join();
    }

    @Test
    @Timeout(10)
    void when_mqttMessagePayloadIsNonValidUtf8EncodedAndNoFormatIndicatorSet_then_getPayloadAsBytes()
            throws InitializationException {
        // GIVEN
        var topic = JSON.arrayNode().add(byteArrayTopic);
        var message = "ßß";

        // WHEN
        embeddedHiveMqBroker = startEmbeddedHiveMqBroker();
        hiveMqClient = startMqttClient();
        Flux<Val> saplMqttMessageFlux = saplMqttClient.buildSaplMqttMessageFlux(Val.of(topic), config)
                .filter(val -> !val.isUndefined());

        // THEN
        StepVerifier.create(saplMqttMessageFlux)
                .thenAwait(Duration.ofMillis(1500))
                .then(() ->
                        hiveMqClient.publish(buildMqttPublishByteArrayMessageWithoutIndicatorAndNoValidEncoding(message)))
                .expectNextMatches((valueArray) ->
                        valueArray.get().equals(convertBytesToArrayNode(message.getBytes(StandardCharsets.UTF_16))))
                .thenCancel()
                .verify();

        // FINALLY
        hiveMqClient.disconnect();
        embeddedHiveMqBroker.stop().join();
    }

    private static Mqtt5Publish buildMqttPublishJsonMessage(JsonNode payload) {
        return Mqtt5Publish.builder()
                .topic(jsonTopic)
                .qos(MqttQos.AT_MOST_ONCE)
                .retain(true)
                .payload(payload.toString().getBytes(StandardCharsets.UTF_8))
                .payloadFormatIndicator(Mqtt5PayloadFormatIndicator.UTF_8)
                .contentType("application/json")
                .build();
    }

    private static Mqtt5Publish buildMqttPublishByteArrayMessageWithIndicator(String payload) {
        return Mqtt5Publish.builder()
                .topic(byteArrayTopic)
                .qos(MqttQos.AT_MOST_ONCE)
                .retain(true)
                .payloadFormatIndicator(Mqtt5PayloadFormatIndicator.UNSPECIFIED)
                .payload(payload.getBytes(StandardCharsets.UTF_8))
                .build();
    }

    private static Mqtt5Publish buildMqttPublishByteArrayMessageWithoutIndicator(String payload) {
        return Mqtt5Publish.builder()
                .topic(byteArrayTopic)
                .qos(MqttQos.AT_MOST_ONCE)
                .retain(true)
                .payload(payload.getBytes(StandardCharsets.UTF_8))
                .build();
    }

    private static Mqtt5Publish buildMqttPublishByteArrayMessageWithoutIndicatorAndNoValidEncoding(String payload) {
        return Mqtt5Publish.builder()
                .topic(byteArrayTopic)
                .qos(MqttQos.AT_MOST_ONCE)
                .retain(true)
                .payload(payload.getBytes(StandardCharsets.UTF_16))
                .build();
    }
}
