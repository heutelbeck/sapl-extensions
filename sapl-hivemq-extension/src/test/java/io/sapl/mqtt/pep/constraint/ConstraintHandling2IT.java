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

package io.sapl.mqtt.pep.constraint;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.hivemq.client.mqtt.MqttGlobalPublishFilter;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt5.Mqtt5BlockingClient;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import com.hivemq.client.mqtt.mqtt5.message.subscribe.Mqtt5Subscribe;

import io.sapl.interpreter.InitializationException;
import io.sapl.mqtt.pep.SaplMqttPepTest;

class ConstraintHandling2IT extends SaplMqttPepTest {

	@BeforeAll
	public static void beforeAll() throws InitializationException {
		embeddedHiveMq      = startEmbeddedHiveMqBroker();
		mqttClientPublish   = startMqttClient("CONSTRAINT_MQTT_CLIENT_PUBLISH");
		mqttClientSubscribe = startMqttClient("CONSTRAINT_MQTT_CLIENT_SUBSCRIBE");
	}

	@AfterAll
	public static void afterAll() {
		mqttClientPublish.disconnect();
		mqttClientSubscribe.disconnect();
		embeddedHiveMq.stop().join();
	}

	@Test
	@Timeout(10)
	void when_qosIsChangedViaObligation_then_useChangedQos() throws InterruptedException {
		// GIVEN
		Mqtt5Subscribe subscribeMessage = buildMqttSubscribeMessage("topic", 2);
		Mqtt5Publish   publishMessage   = buildMqttPublishMessage("topic", 0, false);

		// WHEN
		mqttClientSubscribe.subscribe(subscribeMessage);
		mqttClientPublish.publish(publishMessage);

		Mqtt5Publish receivedMessage = mqttClientSubscribe.publishes(MqttGlobalPublishFilter.ALL).receive();

		// THEN
		assertEquals(publishMessagePayload, new String(receivedMessage.getPayloadAsBytes()));
		assertEquals(2, receivedMessage.getQos().getCode());

		// FINALLY
		mqttClientSubscribe.unsubscribeWith().topicFilter("topic").send();
	}

	@Test
	@Timeout(10)
	void when_messageExpiryIntervalIsChangedViaObligation_then_useChangedMessageExpiryInterval()
			throws InterruptedException {
		// GIVEN
		Mqtt5Subscribe subscribeMessage = buildMqttSubscribeMessage("messageExpiry");
		Mqtt5Publish   publishMessage   = buildMqttPublishMessage("messageExpiry", true);

		// WHEN
		mqttClientSubscribe.subscribe(subscribeMessage);
		mqttClientPublish.publish(publishMessage);
		Mqtt5Publish receivedMessage = mqttClientSubscribe.publishes(MqttGlobalPublishFilter.ALL).receive();
		assertEquals(publishMessagePayload, new String(receivedMessage.getPayloadAsBytes()));

		// THEN
		await().atMost(2500, TimeUnit.MILLISECONDS).untilAsserted(() -> {
			Optional<Mqtt5Publish> receivedMessageAfterExpiry = mqttClientSubscribe
					.publishes(MqttGlobalPublishFilter.ALL)
					.receive(1000, TimeUnit.MILLISECONDS);
			assertTrue(receivedMessageAfterExpiry.isEmpty());
		});

		// FINALLY
		mqttClientSubscribe.unsubscribeWith().topicFilter("messageExpiry").send();
	}

	@Test
	@Timeout(10)
	void when_contentTypeIsChangedViaObligation_then_useChangedContentType() throws InterruptedException {
		// GIVEN
		Mqtt5Subscribe subscribeMessage = buildMqttSubscribeMessage("contentTopic");
		Mqtt5Publish   publishMessage   = buildMqttPublishMessage("contentTopic", false);

		// WHEN
		mqttClientSubscribe.subscribe(subscribeMessage);
		mqttClientPublish.publish(publishMessage);

		Mqtt5Publish receivedMessage = mqttClientSubscribe.publishes(MqttGlobalPublishFilter.ALL).receive();

		// THEN
		assertEquals(publishMessagePayload, new String(receivedMessage.getPayloadAsBytes()));
		assertTrue(receivedMessage.getContentType().isPresent());
		assertEquals("content", StandardCharsets.UTF_8.decode(
				receivedMessage.getContentType().get().toByteBuffer()).toString());

		// FINALLY
		mqttClientSubscribe.unsubscribeWith().topicFilter("contentTopic").send();
	}

	@Test
	@Timeout(10)
	void when_payloadIsChangedViaObligation_then_useChangedPayload() throws InterruptedException {
		// GIVEN
		Mqtt5Subscribe subscribeMessage = buildMqttSubscribeMessage("payloadTopic");
		Mqtt5Publish   publishMessage   = buildMqttPublishMessage("payloadTopic", false);

		// WHEN
		mqttClientSubscribe.subscribe(subscribeMessage);
		mqttClientPublish.publish(publishMessage);

		Mqtt5Publish receivedMessage = mqttClientSubscribe.publishes(MqttGlobalPublishFilter.ALL).receive();

		// THEN
		assertTrue(receivedMessage.getPayload().isPresent());
		assertEquals("changedPayload", StandardCharsets.UTF_8.decode(
				receivedMessage.getPayload().get()).toString());

		// FINALLY
		mqttClientSubscribe.unsubscribeWith().topicFilter("payloadTopic").send();
	}

	@Test
	@Timeout(10)
	void when_payloadIsBlackenedViaObligation_then_useBlackenedPayload() throws InterruptedException {
		// GIVEN
		Mqtt5Subscribe subscribeMessage = buildMqttSubscribeMessage("blackenTopic");
		Mqtt5Publish   publishMessage   = Mqtt5Publish.builder()
				.topic("blackenTopic")
				.qos(Objects.requireNonNull(MqttQos.fromCode(0)))
				.retain(false)
				.contentType("text/plain")
				.payload(SaplMqttPepTest.publishMessagePayload.getBytes(StandardCharsets.UTF_8))
				.build();

		// WHEN
		mqttClientSubscribe.subscribe(subscribeMessage);
		mqttClientPublish.publish(publishMessage);

		Mqtt5Publish receivedMessage = mqttClientSubscribe.publishes(MqttGlobalPublishFilter.ALL).receive();

		// THEN
		assertTrue(receivedMessage.getPayload().isPresent());
		assertEquals("*****ge", StandardCharsets.UTF_8.decode(
				receivedMessage.getPayload().get()).toString());

		// FINALLY
		mqttClientSubscribe.unsubscribeWith().topicFilter("blackenTopic").send();
	}

	@Test
	@Timeout(10)
	void when_timeLimitForSubscriptionIsSet_then_limitSubscriptionTime() {
		// GIVEN
		Mqtt5Subscribe subscribeMessage = buildMqttSubscribeMessage("time_limit");
		Mqtt5Publish   publishMessage   = buildMqttPublishMessage("time_limit", false);

		// WHEN
		mqttClientSubscribe.subscribe(subscribeMessage);

		// THEN
		await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
			mqttClientPublish.publish(publishMessage);
			Optional<Mqtt5Publish> receivedMessage = mqttClientSubscribe.publishes(MqttGlobalPublishFilter.ALL)
					.receive(500, TimeUnit.MILLISECONDS);
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
		mqttClientSubscribe.subscribe(subscribeMessage);
		mqttClientPublish.publish(publishMessage);

		// THEN
		Optional<Mqtt5Publish> receivedMessageAfterExpiry = mqttClientSubscribe
				.publishes(MqttGlobalPublishFilter.ALL)
				.receive(1000, TimeUnit.MILLISECONDS);
		assertTrue(receivedMessageAfterExpiry.isEmpty());

		// FINALLY
		mqttClientSubscribe.unsubscribeWith().topicFilter("illegalObligation").send();
	}

	// tests for illegal connections and subscriptions constraints

	@Test
	@Timeout(10)
	void when_timeLimitForConnectionIsSet_then_limitConnectionTime() throws InitializationException {
		// GIVEN
		Mqtt5BlockingClient mqttClientConnection = startMqttClient("CONSTRAINT_MQTT_CLIENT_CONNECT");

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
		mqttClientSubscribe.subscribe(subscribeMessage);
		mqttClientPublish.publish(publishMessage);

		// THEN
		Mqtt5Publish receivedMessage = mqttClientSubscribe.publishes(MqttGlobalPublishFilter.ALL).receive();
		assertEquals(publishMessagePayload, new String(receivedMessage.getPayloadAsBytes()));

		// FINALLY
		mqttClientSubscribe.unsubscribeWith().topicFilter("illegalAdvice").send();
	}

	@Test
	@Timeout(10)
	void when_authorizationDecisionContainsResource_then_denyAccess() throws InterruptedException {
		// GIVEN
		Mqtt5Subscribe subscribeMessage = buildMqttSubscribeMessage("resourceTransformation");
		Mqtt5Publish   publishMessage   = buildMqttPublishMessage("resourceTransformation", false);

		// WHEN
		mqttClientSubscribe.subscribe(subscribeMessage);
		mqttClientPublish.publish(publishMessage);

		// THEN
		Optional<Mqtt5Publish> receivedMessageAfterExpiry = mqttClientSubscribe
				.publishes(MqttGlobalPublishFilter.ALL)
				.receive(1000, TimeUnit.MILLISECONDS);
		assertTrue(receivedMessageAfterExpiry.isEmpty());

		// FINALLY
		mqttClientSubscribe.unsubscribeWith().topicFilter("resourceTransformation").send();
	}
}