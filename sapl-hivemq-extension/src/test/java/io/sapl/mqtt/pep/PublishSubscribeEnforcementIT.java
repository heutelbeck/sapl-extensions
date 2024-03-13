/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.mqtt.pep;

import static io.sapl.mqtt.pep.MqttTestUtil.*;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import com.hivemq.client.mqtt.mqtt5.Mqtt5BlockingClient;
import com.hivemq.embedded.EmbeddedHiveMQ;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.hivemq.client.mqtt.MqttGlobalPublishFilter;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt5.exceptions.Mqtt5PubAckException;
import com.hivemq.client.mqtt.mqtt5.exceptions.Mqtt5PubRecException;
import com.hivemq.client.mqtt.mqtt5.exceptions.Mqtt5SubAckException;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import com.hivemq.client.mqtt.mqtt5.message.publish.puback.Mqtt5PubAckReasonCode;
import com.hivemq.client.mqtt.mqtt5.message.publish.pubrec.Mqtt5PubRecReasonCode;
import com.hivemq.client.mqtt.mqtt5.message.subscribe.Mqtt5Subscribe;
import com.hivemq.client.mqtt.mqtt5.message.subscribe.Mqtt5Subscription;
import com.hivemq.client.mqtt.mqtt5.message.subscribe.suback.Mqtt5SubAckReasonCode;

import io.sapl.interpreter.InitializationException;
import org.junit.jupiter.api.io.TempDir;

class PublishSubscribeEnforcementIT {

    @TempDir
    Path dataFolder;
    @TempDir
    Path configFolder;
    @TempDir
    Path extensionFolder;

    private EmbeddedHiveMQ      mqttBroker;
    private Mqtt5BlockingClient publishClient;
    private Mqtt5BlockingClient subscribeClient;

    @BeforeEach
    void beforeEach() throws InitializationException {
        mqttBroker      = buildAndStartBroker(dataFolder, configFolder, extensionFolder);
        publishClient   = buildAndStartMqttClient("MQTT_CLIENT_PUBLISH");
        subscribeClient = buildAndStartMqttClient("MQTT_CLIENT_SUBSCRIBE");
    }

    @AfterEach
    void afterEach() {
        publishClient.disconnect();
        subscribeClient.disconnect();
        stopBroker(mqttBroker);
    }

    @Test
    @Timeout(10)
    void when_publishAndSubscribeForTopicPermitted_then_subscribeAndPublishTopic() throws InterruptedException {
        // GIVEN
        Mqtt5Subscribe subscribeMessage = buildMqttSubscribeMessage("topic");

        Mqtt5Publish publishMessage = buildMqttPublishMessage("topic", 0, false, "test_content");

        // WHEN
        subscribeClient.subscribe(subscribeMessage);
        publishClient.publish(publishMessage);

        // THEN
        Mqtt5Publish receivedMessage = subscribeClient.publishes(MqttGlobalPublishFilter.ALL).receive();

        assertEquals(PUBLISH_MESSAGE_PAYLOAD, new String(receivedMessage.getPayloadAsBytes()));
    }

    @Test
    @Timeout(10)
    void when_publishDenied_then_dropPublishMessage() throws InterruptedException {
        // GIVEN
        Mqtt5Subscribe subscribeMessage = buildMqttSubscribeMessage("denied_publish");

        Mqtt5Publish publishMessageQos0 = buildMqttPublishMessage("denied_publish", false);
        Mqtt5Publish publishMessageQos1 = buildMqttPublishMessage("denied_publish", 1, false);
        Mqtt5Publish publishMessageQos2 = buildMqttPublishMessage("denied_publish", 2, false);

        // WHEN
        subscribeClient.subscribe(subscribeMessage);
        publishClient.publish(publishMessageQos0);

        // THEN
        Optional<Mqtt5Publish> receivedMessage = subscribeClient.publishes(MqttGlobalPublishFilter.ALL).receive(1000,
                TimeUnit.MILLISECONDS);
        assertTrue(receivedMessage.isEmpty());

        Mqtt5PubAckException pubAckException = assertThrowsExactly(Mqtt5PubAckException.class,
                () -> publishClient.publish(publishMessageQos1));
        assertEquals(Mqtt5PubAckReasonCode.NOT_AUTHORIZED, pubAckException.getMqttMessage().getReasonCode());

        Mqtt5PubRecException pubRecException = assertThrowsExactly(Mqtt5PubRecException.class,
                () -> publishClient.publish(publishMessageQos2));
        assertEquals(Mqtt5PubRecReasonCode.NOT_AUTHORIZED, pubRecException.getMqttMessage().getReasonCode());
    }

    @Test
    @Timeout(10)
    void when_subscribeDenied_then_DontStartSubscription() {
        // GIVEN
        Mqtt5Subscribe subscribeMessageQos0 = buildMqttSubscribeMessage("denied_subscription");
        Mqtt5Subscribe subscribeMessageQos1 = buildMqttSubscribeMessage("denied_subscription", 1);
        Mqtt5Subscribe subscribeMessageQos2 = buildMqttSubscribeMessage("denied_subscription", 2);

        // THEN
        Mqtt5SubAckException subAckException = assertThrowsExactly(Mqtt5SubAckException.class,
                () -> subscribeClient.subscribe(subscribeMessageQos0));
        assertEquals(Mqtt5SubAckReasonCode.NOT_AUTHORIZED, subAckException.getMqttMessage().getReasonCodes().get(0));

        subAckException = assertThrowsExactly(Mqtt5SubAckException.class,
                () -> subscribeClient.subscribe(subscribeMessageQos1));
        assertEquals(Mqtt5SubAckReasonCode.NOT_AUTHORIZED, subAckException.getMqttMessage().getReasonCodes().get(0));

        subAckException = assertThrowsExactly(Mqtt5SubAckException.class,
                () -> subscribeClient.subscribe(subscribeMessageQos2));
        assertEquals(Mqtt5SubAckReasonCode.NOT_AUTHORIZED, subAckException.getMqttMessage().getReasonCodes().get(0));
    }

    @Test
    @Timeout(20)
    void when_subscribeWithMultipleTopicsPermitted_then_subscribeWithMultipleTopics() {
        // GIVEN
        Mqtt5Subscription firstSubscription              = Mqtt5Subscription.builder().topicFilter("topic")
                .qos(MqttQos.AT_LEAST_ONCE).build();
        Mqtt5Subscription secondSubscription             = Mqtt5Subscription.builder().topicFilter("secondTopic")
                .qos(MqttQos.AT_LEAST_ONCE).build();
        Mqtt5Subscribe    subscribeMessageMultipleTopics = Mqtt5Subscribe.builder()
                .addSubscriptions(firstSubscription, secondSubscription).build();

        Mqtt5Publish firstPublishMessage  = buildMqttPublishMessage("topic", true);
        Mqtt5Publish secondPublishMessage = buildMqttPublishMessage("secondTopic", true);

        // WHEN
        subscribeClient.subscribe(subscribeMessageMultipleTopics);

        // THEN
        await().atMost(6, TimeUnit.SECONDS).untilAsserted(() -> {
            publishClient.publish(firstPublishMessage);
            Optional<Mqtt5Publish> receivedMessage = subscribeClient.publishes(MqttGlobalPublishFilter.ALL).receive(2,
                    TimeUnit.SECONDS);
            assertTrue(receivedMessage.isPresent());
            assertEquals(PUBLISH_MESSAGE_PAYLOAD, new String(receivedMessage.get().getPayloadAsBytes()));
        });

        await().atMost(6, TimeUnit.SECONDS).untilAsserted(() -> {
            publishClient.publish(secondPublishMessage);
            Optional<Mqtt5Publish> receivedMessage = subscribeClient.publishes(MqttGlobalPublishFilter.ALL).receive(2,
                    TimeUnit.SECONDS);
            assertTrue(receivedMessage.isPresent());
            assertEquals(PUBLISH_MESSAGE_PAYLOAD, new String(receivedMessage.get().getPayloadAsBytes()));
        });
    }

    @Test
    @Timeout(20)
    void when_subscribeWithMultipleTopicsOnlyOneTopicAllowed_then_subscribeToRemainingTopic()
            throws InterruptedException {
        // GIVEN
        Mqtt5Subscription firstSubscription              = Mqtt5Subscription.builder().topicFilter("topic").build();
        Mqtt5Subscription secondSubscription             = Mqtt5Subscription.builder()
                .topicFilter("denied_subscription").build();
        Mqtt5Subscribe    subscribeMessageMultipleTopics = Mqtt5Subscribe.builder()
                .addSubscriptions(firstSubscription, secondSubscription).build();

        Mqtt5Publish firstPublishMessage  = buildMqttPublishMessage("topic", false);
        Mqtt5Publish secondPublishMessage = buildMqttPublishMessage("denied_subscription", false);

        // WHEN
        Mqtt5SubAckException subAckException = assertThrowsExactly(Mqtt5SubAckException.class,
                () -> subscribeClient.subscribe(subscribeMessageMultipleTopics));
        assertEquals(Mqtt5SubAckReasonCode.GRANTED_QOS_2, subAckException.getMqttMessage().getReasonCodes().get(0));
        assertEquals(Mqtt5SubAckReasonCode.NOT_AUTHORIZED, subAckException.getMqttMessage().getReasonCodes().get(1));

        // THEN
        publishClient.publish(firstPublishMessage);
        Mqtt5Publish receivedMessage = subscribeClient.publishes(MqttGlobalPublishFilter.ALL).receive();
        assertEquals(PUBLISH_MESSAGE_PAYLOAD, new String(receivedMessage.getPayloadAsBytes()));

        publishClient.publish(secondPublishMessage);
        Optional<Mqtt5Publish> receivedMessageSecond = subscribeClient.publishes(MqttGlobalPublishFilter.ALL)
                .receive(1000, TimeUnit.MILLISECONDS);
        assertTrue(receivedMessageSecond.isEmpty());
    }
}
