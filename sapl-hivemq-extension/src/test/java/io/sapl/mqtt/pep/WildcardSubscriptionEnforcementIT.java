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

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.hivemq.client.mqtt.MqttGlobalPublishFilter;
import com.hivemq.client.mqtt.mqtt5.exceptions.Mqtt5SubAckException;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import com.hivemq.client.mqtt.mqtt5.message.subscribe.Mqtt5Subscribe;
import com.hivemq.client.mqtt.mqtt5.message.subscribe.Mqtt5Subscription;
import com.hivemq.client.mqtt.mqtt5.message.subscribe.suback.Mqtt5SubAckReasonCode;

import ch.qos.logback.classic.Level;
import io.sapl.interpreter.InitializationException;

class WildcardSubscriptionEnforcementIT extends SaplMqttPepTest {

	@BeforeAll
	public static void beforeAll() throws InitializationException {
		// set logging level
		rootLogger.setLevel(Level.OFF);

		embeddedHiveMq      = startEmbeddedHiveMqBroker();
		mqttClientPublish   = startMqttClient("WILDCARD_MQTT_CLIENT_PUBLISH");
		mqttClientSubscribe = startMqttClient("WILDCARD_MQTT_CLIENT_SUBSCRIBE");
	}

	@AfterAll
	public static void afterAll() {
		mqttClientPublish.disconnect();
		mqttClientSubscribe.disconnect();
		embeddedHiveMq.stop().join();
	}

	@Test
	@Timeout(10)
	void when_subscribedWithMultilevelWildcard_then_getMessageOfPermittedTopic() {
		// GIVEN
		Mqtt5Subscribe subscribeMessage = buildMqttSubscribeMessage("first/#");

		Mqtt5Publish publishMessage = buildMqttPublishMessage("first/second/third",
				false);

		// WHEN
		mqttClientSubscribe.subscribe(subscribeMessage);

		// THEN
		await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
			mqttClientPublish.publish(publishMessage);
			Optional<Mqtt5Publish> receivedMessage = mqttClientSubscribe.publishes(MqttGlobalPublishFilter.ALL)
					.receive(2, TimeUnit.SECONDS);
			assertTrue(receivedMessage.isPresent());
			assertEquals(publishMessagePayload, new String(receivedMessage.get().getPayloadAsBytes()));
		});

		// FINALLY
		mqttClientSubscribe.unsubscribeWith().topicFilter("first/#").send();
	}

	@Test
	@Timeout(10)
	void when_subscribedWithSingleLevelWildcard_then_getMessageOfPermittedTopic() {
		// GIVEN
		Mqtt5Subscribe subscribeMessage = buildMqttSubscribeMessage("first/+/third");

		Mqtt5Publish publishMessage = buildMqttPublishMessage("first/second/third",
				false);

		// WHEN
		mqttClientSubscribe.subscribe(subscribeMessage);

		// THEN
		await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
			mqttClientPublish.publish(publishMessage);
			Optional<Mqtt5Publish> receivedMessage = mqttClientSubscribe.publishes(MqttGlobalPublishFilter.ALL)
					.receive(2, TimeUnit.SECONDS);
			assertTrue(receivedMessage.isPresent());
			assertEquals(publishMessagePayload, new String(receivedMessage.get().getPayloadAsBytes()));
		});

		// FINALLY
		mqttClientSubscribe.unsubscribeWith().topicFilter("first/+/third").send();
	}

	@Test
	@Timeout(10)
	void when_subscribedWithMultiLevelWildcardAndAnotherTopicSubscriptionDenied_then_getMessagesOverWildcard() {
		// GIVEN
		Mqtt5Subscription firstSubscription              = Mqtt5Subscription.builder().topicFilter("first/#").build();
		Mqtt5Subscription secondSubscription             = Mqtt5Subscription.builder().topicFilter("first/second")
				.build();
		Mqtt5Subscribe    subscribeMessageMultipleTopics = Mqtt5Subscribe.builder()
				.addSubscriptions(firstSubscription, secondSubscription)
				.build();

		Mqtt5Publish publishMessage = buildMqttPublishMessage("first/second",
				false);

		// WHEN
		Mqtt5SubAckException subAckException = assertThrowsExactly(Mqtt5SubAckException.class,
				() -> mqttClientSubscribe.subscribe(subscribeMessageMultipleTopics));
		assertEquals(Mqtt5SubAckReasonCode.GRANTED_QOS_2, subAckException.getMqttMessage().getReasonCodes().get(0));
		assertEquals(Mqtt5SubAckReasonCode.NOT_AUTHORIZED, subAckException.getMqttMessage().getReasonCodes().get(1));

		// THEN
		await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
			mqttClientPublish.publish(publishMessage);
			Optional<Mqtt5Publish> receivedMessage = mqttClientSubscribe.publishes(MqttGlobalPublishFilter.ALL)
					.receive(2, TimeUnit.SECONDS);
			assertTrue(receivedMessage.isPresent());
			assertEquals(publishMessagePayload, new String(receivedMessage.get().getPayloadAsBytes()));
		});

		// FINALLY
		mqttClientSubscribe.unsubscribeWith().topicFilter("first/#").send();
	}

	@Test
	@Timeout(10)
	void when_subscribedWithSingleLevelWildcardAndAnotherTopicSubscriptionDenied_then_getMessagesOverWildcard() {
		// GIVEN
		Mqtt5Subscription firstSubscription              = Mqtt5Subscription.builder().topicFilter("first/+/third")
				.build();
		Mqtt5Subscription secondSubscription             = Mqtt5Subscription.builder().topicFilter("first/second/third")
				.build();
		Mqtt5Subscribe    subscribeMessageMultipleTopics = Mqtt5Subscribe.builder()
				.addSubscriptions(firstSubscription, secondSubscription)
				.build();

		Mqtt5Publish publishMessage = buildMqttPublishMessage("first/second/third",
				false);

		// WHEN
		Mqtt5SubAckException subAckException = assertThrowsExactly(Mqtt5SubAckException.class,
				() -> mqttClientSubscribe.subscribe(subscribeMessageMultipleTopics));
		assertEquals(Mqtt5SubAckReasonCode.GRANTED_QOS_2, subAckException.getMqttMessage().getReasonCodes().get(0));
		assertEquals(Mqtt5SubAckReasonCode.NOT_AUTHORIZED, subAckException.getMqttMessage().getReasonCodes().get(1));

		// THEN
		await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
			mqttClientPublish.publish(publishMessage);
			Optional<Mqtt5Publish> receivedMessage = mqttClientSubscribe.publishes(MqttGlobalPublishFilter.ALL)
					.receive(2, TimeUnit.SECONDS);
			assertTrue(receivedMessage.isPresent());
			assertEquals(publishMessagePayload, new String(receivedMessage.get().getPayloadAsBytes()));
		});

		// FINALLY
		mqttClientSubscribe.unsubscribeWith().topicFilter("first/+/third").send();
	}
}