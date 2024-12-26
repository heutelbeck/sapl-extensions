/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * SPDX-License-Identifier: Apache-2.0
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
package io.sapl.mqtt.pep.constraint;

import static io.sapl.mqtt.pep.MqttTestUtil.PUBLISH_MESSAGE_PAYLOAD;
import static io.sapl.mqtt.pep.MqttTestUtil.buildAndStartBroker;
import static io.sapl.mqtt.pep.MqttTestUtil.buildAndStartMqttClient;
import static io.sapl.mqtt.pep.MqttTestUtil.buildMqttPublishMessage;
import static io.sapl.mqtt.pep.MqttTestUtil.buildMqttSubscribeMessage;
import static io.sapl.mqtt.pep.MqttTestUtil.stopBroker;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import com.hivemq.client.mqtt.MqttGlobalPublishFilter;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt5.Mqtt5BlockingClient;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import com.hivemq.client.mqtt.mqtt5.message.subscribe.Mqtt5Subscribe;
import com.hivemq.embedded.EmbeddedHiveMQ;
import com.nimbusds.jose.util.StandardCharset;

import io.sapl.interpreter.InitializationException;
import io.sapl.mqtt.pep.MqttTestUtil;

class ConstraintHandling2IT {

    @TempDir
    Path dataFolder;
    @TempDir
    Path configFolder;
    @TempDir
    Path extensionFolder;

    EmbeddedHiveMQ      mqttBroker;
    Mqtt5BlockingClient publishClient;
    Mqtt5BlockingClient subscribeClient;

    @BeforeEach
    void beforeEach() throws InitializationException {
        mqttBroker      = buildAndStartBroker(dataFolder, configFolder, extensionFolder);
        publishClient   = buildAndStartMqttClient("CONSTRAINT_MQTT_CLIENT_PUBLISH");
        subscribeClient = buildAndStartMqttClient("CONSTRAINT_MQTT_CLIENT_SUBSCRIBE");
    }

    @AfterEach
    void afterEach() {
        publishClient.disconnect();
        subscribeClient.disconnect();
        stopBroker(mqttBroker);
    }

    @Test
    @Timeout(10)
    void when_qosIsChangedViaObligation_then_useChangedQos() throws InterruptedException {
        // GIVEN
        Mqtt5Subscribe subscribeMessage = buildMqttSubscribeMessage("topic", 2);
        Mqtt5Publish   publishMessage   = buildMqttPublishMessage("topic", 0, false);

        // WHEN
        subscribeClient.subscribe(subscribeMessage);
        publishClient.publish(publishMessage);

        Mqtt5Publish receivedMessage = subscribeClient.publishes(MqttGlobalPublishFilter.ALL).receive();

        // THEN
        assertEquals(PUBLISH_MESSAGE_PAYLOAD, new String(receivedMessage.getPayloadAsBytes(), StandardCharset.UTF_8));
        assertEquals(2, receivedMessage.getQos().getCode());

        // FINALLY
        subscribeClient.unsubscribeWith().topicFilter("topic").send();
    }

    @Test
    @Timeout(10)
    void when_messageExpiryIntervalIsChangedViaObligation_then_useChangedMessageExpiryInterval()
            throws InterruptedException {
        // GIVEN
        Mqtt5Subscribe subscribeMessage = buildMqttSubscribeMessage("messageExpiry");
        Mqtt5Publish   publishMessage   = buildMqttPublishMessage("messageExpiry", true);

        // WHEN
        subscribeClient.subscribe(subscribeMessage);
        publishClient.publish(publishMessage);
        Mqtt5Publish receivedMessage = subscribeClient.publishes(MqttGlobalPublishFilter.ALL).receive();
        assertEquals(PUBLISH_MESSAGE_PAYLOAD, new String(receivedMessage.getPayloadAsBytes(), StandardCharset.UTF_8));

        // THEN
        await().atMost(2500, TimeUnit.MILLISECONDS).untilAsserted(() -> {
            Optional<Mqtt5Publish> receivedMessageAfterExpiry = subscribeClient.publishes(MqttGlobalPublishFilter.ALL)
                    .receive(1000, TimeUnit.MILLISECONDS);
            assertTrue(receivedMessageAfterExpiry.isEmpty());
        });

        // FINALLY
        subscribeClient.unsubscribeWith().topicFilter("messageExpiry").send();
    }

    @Test
    @Timeout(10)
    void when_contentTypeIsChangedViaObligation_then_useChangedContentType() throws InterruptedException {
        // GIVEN
        Mqtt5Subscribe subscribeMessage = buildMqttSubscribeMessage("contentTopic");
        Mqtt5Publish   publishMessage   = buildMqttPublishMessage("contentTopic", false);

        // WHEN
        subscribeClient.subscribe(subscribeMessage);
        publishClient.publish(publishMessage);

        Mqtt5Publish receivedMessage = subscribeClient.publishes(MqttGlobalPublishFilter.ALL).receive();

        // THEN
        assertEquals(PUBLISH_MESSAGE_PAYLOAD, new String(receivedMessage.getPayloadAsBytes(), StandardCharset.UTF_8));
        assertTrue(receivedMessage.getContentType().isPresent());
        assertEquals("content",
                StandardCharsets.UTF_8.decode(receivedMessage.getContentType().get().toByteBuffer()).toString());

        // FINALLY
        subscribeClient.unsubscribeWith().topicFilter("contentTopic").send();
    }

    @Test
    @Timeout(10)
    void when_payloadIsChangedViaObligation_then_useChangedPayload() throws InterruptedException {
        // GIVEN
        Mqtt5Subscribe subscribeMessage = buildMqttSubscribeMessage("payloadTopic");
        Mqtt5Publish   publishMessage   = buildMqttPublishMessage("payloadTopic", false);

        // WHEN
        subscribeClient.subscribe(subscribeMessage);
        publishClient.publish(publishMessage);

        Mqtt5Publish receivedMessage = subscribeClient.publishes(MqttGlobalPublishFilter.ALL).receive();

        // THEN
        assertTrue(receivedMessage.getPayload().isPresent());
        assertEquals("changedPayload", StandardCharsets.UTF_8.decode(receivedMessage.getPayload().get()).toString());

        // FINALLY
        subscribeClient.unsubscribeWith().topicFilter("payloadTopic").send();
    }

    @Test
    @Timeout(10)
    void when_payloadIsBlackenedViaObligation_then_useBlackenedPayload() throws InterruptedException {
        // GIVEN
        Mqtt5Subscribe subscribeMessage = buildMqttSubscribeMessage("blackenTopic");
        Mqtt5Publish   publishMessage   = Mqtt5Publish.builder().topic("blackenTopic")
                .qos(Objects.requireNonNull(MqttQos.fromCode(0))).retain(false).contentType("text/plain")
                .payload(MqttTestUtil.PUBLISH_MESSAGE_PAYLOAD.getBytes(StandardCharsets.UTF_8)).build();

        // WHEN
        subscribeClient.subscribe(subscribeMessage);
        publishClient.publish(publishMessage);

        Mqtt5Publish receivedMessage = subscribeClient.publishes(MqttGlobalPublishFilter.ALL).receive();

        // THEN
        assertTrue(receivedMessage.getPayload().isPresent());
        assertEquals("*****ge", StandardCharsets.UTF_8.decode(receivedMessage.getPayload().get()).toString());

        // FINALLY
        subscribeClient.unsubscribeWith().topicFilter("blackenTopic").send();
    }

    @Test
    @Timeout(10)
    void when_timeLimitForSubscriptionIsSet_then_limitSubscriptionTime() {
        // GIVEN
        Mqtt5Subscribe subscribeMessage = buildMqttSubscribeMessage("time_limit");
        Mqtt5Publish   publishMessage   = buildMqttPublishMessage("time_limit", false);

        // WHEN
        subscribeClient.subscribe(subscribeMessage);

        // THEN
        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            publishClient.publish(publishMessage);
            Optional<Mqtt5Publish> receivedMessage = subscribeClient.publishes(MqttGlobalPublishFilter.ALL).receive(500,
                    TimeUnit.MILLISECONDS);
            assertTrue(receivedMessage.isEmpty());
        });
    }

    @Test
    @Timeout(10)
    void when_specifiedIllegalObligationInPolicy_then_denyAccess() throws InterruptedException {
        // GIVEN
        Mqtt5Subscribe subscribeMessage = buildMqttSubscribeMessage("illegalObligation");
        Mqtt5Publish   publishMessage   = buildMqttPublishMessage("illegalObligation", false);

        // WHEN
        subscribeClient.subscribe(subscribeMessage);
        publishClient.publish(publishMessage);

        // THEN
        Optional<Mqtt5Publish> receivedMessageAfterExpiry = subscribeClient.publishes(MqttGlobalPublishFilter.ALL)
                .receive(1000, TimeUnit.MILLISECONDS);
        assertTrue(receivedMessageAfterExpiry.isEmpty());

        // FINALLY
        subscribeClient.unsubscribeWith().topicFilter("illegalObligation").send();
    }

    // tests for illegal connections and subscriptions constraints

    @Test
    @Timeout(10)
    void when_timeLimitForConnectionIsSet_then_limitConnectionTime() throws InitializationException {
        // GIVEN
        Mqtt5BlockingClient mqttClientConnection = buildAndStartMqttClient("CONSTRAINT_MQTT_CLIENT_CONNECT");

        // THEN
        await().atMost(2, TimeUnit.SECONDS)
                .untilAsserted(() -> assertFalse(mqttClientConnection.getState().isConnected()));
    }

    @Test
    @Timeout(10)
    void when_specifiedIllegalAdviceInPolicy_then_doNotAlterAccess() throws InterruptedException {
        // GIVEN
        Mqtt5Subscribe subscribeMessage = buildMqttSubscribeMessage("illegalAdvice");
        Mqtt5Publish   publishMessage   = buildMqttPublishMessage("illegalAdvice", false);

        // WHEN
        subscribeClient.subscribe(subscribeMessage);
        publishClient.publish(publishMessage);

        // THEN
        Mqtt5Publish receivedMessage = subscribeClient.publishes(MqttGlobalPublishFilter.ALL).receive();
        assertEquals(PUBLISH_MESSAGE_PAYLOAD, new String(receivedMessage.getPayloadAsBytes(), StandardCharset.UTF_8));

        // FINALLY
        subscribeClient.unsubscribeWith().topicFilter("illegalAdvice").send();
    }

    @Test
    @Timeout(10)
    void when_authorizationDecisionContainsResource_then_denyAccess() throws InterruptedException {
        // GIVEN
        Mqtt5Subscribe subscribeMessage = buildMqttSubscribeMessage("resourceTransformation");
        Mqtt5Publish   publishMessage   = buildMqttPublishMessage("resourceTransformation", false);

        // WHEN
        subscribeClient.subscribe(subscribeMessage);
        publishClient.publish(publishMessage);

        // THEN
        Optional<Mqtt5Publish> receivedMessageAfterExpiry = subscribeClient.publishes(MqttGlobalPublishFilter.ALL)
                .receive(1000, TimeUnit.MILLISECONDS);
        assertTrue(receivedMessageAfterExpiry.isEmpty());

        // FINALLY
        subscribeClient.unsubscribeWith().topicFilter("resourceTransformation").send();
    }
}
