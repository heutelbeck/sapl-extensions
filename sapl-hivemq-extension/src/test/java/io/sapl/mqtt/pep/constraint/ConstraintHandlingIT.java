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

import static io.sapl.mqtt.pep.MqttTestUtil.*;
import static io.sapl.mqtt.pep.constraint.Constraints.ENVIRONMENT_CONSTRAINT_TYPE;
import static io.sapl.mqtt.pep.constraint.Constraints.ENVIRONMENT_ENABLED;
import static io.sapl.mqtt.pep.constraint.Constraints.ENVIRONMENT_LIMIT_MQTT_ACTION_DURATION;
import static io.sapl.mqtt.pep.constraint.Constraints.ENVIRONMENT_STATUS;
import static io.sapl.mqtt.pep.constraint.Constraints.ENVIRONMENT_TIME_LIMIT;
import static io.sapl.mqtt.pep.constraint.SubscriptionConstraints.ENVIRONMENT_RESUBSCRIBE_MQTT_SUBSCRIPTION;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.hivemq.embedded.EmbeddedHiveMQ;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.hivemq.client.mqtt.MqttGlobalPublishFilter;
import com.hivemq.client.mqtt.mqtt5.Mqtt5BlockingClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client;
import com.hivemq.client.mqtt.mqtt5.exceptions.Mqtt5ConnAckException;
import com.hivemq.client.mqtt.mqtt5.exceptions.Mqtt5SubAckException;
import com.hivemq.client.mqtt.mqtt5.message.connect.connack.Mqtt5ConnAckReasonCode;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import com.hivemq.client.mqtt.mqtt5.message.subscribe.Mqtt5Subscribe;
import com.hivemq.client.mqtt.mqtt5.message.subscribe.suback.Mqtt5SubAckReasonCode;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.IdentifiableAuthorizationDecision;
import io.sapl.api.pdp.MultiAuthorizationSubscription;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.interpreter.InitializationException;
import io.sapl.mqtt.pep.MqttPep;
import io.sapl.mqtt.pep.util.SaplSubscriptionUtility;
import org.junit.jupiter.api.io.TempDir;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

class ConstraintHandlingIT {

	@TempDir
	Path dataFolder;
	@TempDir
	Path configFolder;
	@TempDir
	Path extensionFolder;

	private final String subscriptionClientId = "subscriptionClient";
	private final String topic                = "testTopic";

	@Test
	@Timeout(30)
	void when_mqttSubscriptionTimeoutIsSetAndNoSaplDecisionOccursWhileMqttSubscriptionExists_then_timeoutMqttSubscription()
			throws InitializationException, InterruptedException {
		// GIVEN
		String subscriptionClientMqttConnectionSaplSubscriptionId   = SaplSubscriptionUtility
				.buildSubscriptionId(subscriptionClientId, MqttPep.CONNECT_AUTHZ_ACTION);
		String subscriptionClientMqttSubscriptionSaplSubscriptionId = SaplSubscriptionUtility
				.buildSubscriptionId(subscriptionClientId, MqttPep.SUBSCRIBE_AUTHZ_ACTION, topic);
		String subscriptionClientMqttPublishSaplSubscriptionId      = SaplSubscriptionUtility
				.buildSubscriptionId(subscriptionClientId, MqttPep.PUBLISH_AUTHZ_ACTION, topic);

		ArrayNode mqttSubscriptionTimeLimitObligation = JsonNodeFactory.instance.arrayNode()
				.add(JsonNodeFactory.instance.objectNode()
						.put(ENVIRONMENT_CONSTRAINT_TYPE, ENVIRONMENT_LIMIT_MQTT_ACTION_DURATION)
						.put(ENVIRONMENT_TIME_LIMIT, 1));

		Flux<IdentifiableAuthorizationDecision> subscriptionClientConnectionDecisionFlux       = Flux
				.<IdentifiableAuthorizationDecision>never().mergeWith(
						Flux.just(new IdentifiableAuthorizationDecision(
								subscriptionClientMqttConnectionSaplSubscriptionId, AuthorizationDecision.PERMIT)));
		Flux<IdentifiableAuthorizationDecision> subscriptionClientMqttSubscriptionDecisionFlux = Flux.just(
				new IdentifiableAuthorizationDecision(
						subscriptionClientMqttSubscriptionSaplSubscriptionId, AuthorizationDecision.PERMIT
								.withObligations(mqttSubscriptionTimeLimitObligation)),
				new IdentifiableAuthorizationDecision(
						subscriptionClientMqttConnectionSaplSubscriptionId, AuthorizationDecision.PERMIT));
		Flux<IdentifiableAuthorizationDecision> subscriptionClientMqttPublishDecisionFlux      = Flux
				.<IdentifiableAuthorizationDecision>never().mergeWith(
						Flux.just(new IdentifiableAuthorizationDecision(
								subscriptionClientMqttConnectionSaplSubscriptionId, AuthorizationDecision.PERMIT),
								new IdentifiableAuthorizationDecision(
										subscriptionClientMqttPublishSaplSubscriptionId,
										AuthorizationDecision.PERMIT)));

		PolicyDecisionPoint pdpMock = mock(PolicyDecisionPoint.class);
		when(pdpMock.decide(any(MultiAuthorizationSubscription.class)))
				.thenReturn(subscriptionClientConnectionDecisionFlux)
				.thenReturn(subscriptionClientMqttSubscriptionDecisionFlux)
				.thenReturn(subscriptionClientMqttPublishDecisionFlux);

		Mqtt5Subscribe subscribeMessage = buildMqttSubscribeMessage(topic);
		Mqtt5Publish   publishMessage   = buildMqttPublishMessage(topic, false);

		// WHEN
		EmbeddedHiveMQ mqttBroker = buildAndStartBroker(dataFolder, configFolder, extensionFolder, pdpMock);

		Mqtt5BlockingClient subscribeClient = buildAndStartMqttClient(subscriptionClientId);
		subscribeClient.subscribe(subscribeMessage);
		subscribeClient.publish(publishMessage);
		assertTrue(subscribeClient
				.publishes(MqttGlobalPublishFilter.SUBSCRIBED)
				.receive(800, TimeUnit.MILLISECONDS)
				.isPresent());

		// THEN
		await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
			subscribeClient.publish(publishMessage);
			assertTrue(subscribeClient
					.publishes(MqttGlobalPublishFilter.SUBSCRIBED)
					.receive(1000, TimeUnit.MILLISECONDS)
					.isEmpty());
		});
		verify(pdpMock, times(3)).decide(any(MultiAuthorizationSubscription.class));

		// FINALLY
		stopBroker(mqttBroker);
	}

	@Test
	@Timeout(30)
	void when_constraintResubscribeMqttSubscriptionsIsSetAndSaplDecisionChangesToPermit_then_resubscribeClientToTopic()
			throws InitializationException {
		// GIVEN
		String subscriptionClientMqttConnectionSaplSubscriptionId   = SaplSubscriptionUtility
				.buildSubscriptionId(subscriptionClientId, MqttPep.CONNECT_AUTHZ_ACTION);
		String subscriptionClientMqttSubscriptionSaplSubscriptionId = SaplSubscriptionUtility
				.buildSubscriptionId(subscriptionClientId, MqttPep.SUBSCRIBE_AUTHZ_ACTION, topic);
		String publishClientId                                      = "publishClient";
		String publishClientMqttConnectionSaplSubscriptionId        = SaplSubscriptionUtility
				.buildSubscriptionId(publishClientId, MqttPep.CONNECT_AUTHZ_ACTION);
		String publishClientMqttPublishSaplSubscriptionId           = SaplSubscriptionUtility
				.buildSubscriptionId(publishClientId, MqttPep.PUBLISH_AUTHZ_ACTION, topic);

		ArrayNode resubscribeObligation = JsonNodeFactory.instance.arrayNode()
				.add(JsonNodeFactory.instance.objectNode()
						.put(ENVIRONMENT_CONSTRAINT_TYPE, ENVIRONMENT_RESUBSCRIBE_MQTT_SUBSCRIPTION)
						.put(ENVIRONMENT_STATUS, ENVIRONMENT_ENABLED));

		Flux<IdentifiableAuthorizationDecision> subscriptionClientConnectionDecisionFlux                 = Flux
				.just(new IdentifiableAuthorizationDecision(
						subscriptionClientMqttConnectionSaplSubscriptionId, AuthorizationDecision.PERMIT));
		AtomicBoolean                           isCompleteSubscriptionClientMqttSubscriptionDecisionFlux = new AtomicBoolean(
				false);
		Flux<IdentifiableAuthorizationDecision> subscriptionClientMqttSubscriptionDecisionFlux           = Flux
				.just(new IdentifiableAuthorizationDecision(
						subscriptionClientMqttSubscriptionSaplSubscriptionId, AuthorizationDecision.PERMIT),
						new IdentifiableAuthorizationDecision(
								subscriptionClientMqttSubscriptionSaplSubscriptionId, AuthorizationDecision.DENY),
						new IdentifiableAuthorizationDecision(
								subscriptionClientMqttSubscriptionSaplSubscriptionId, AuthorizationDecision.PERMIT
										.withObligations(resubscribeObligation)))
				.delayElements(Duration.ofSeconds(2))
				.startWith(new IdentifiableAuthorizationDecision(
						subscriptionClientMqttConnectionSaplSubscriptionId, AuthorizationDecision.PERMIT))
				.doOnComplete(() -> isCompleteSubscriptionClientMqttSubscriptionDecisionFlux.set(true));
		Flux<IdentifiableAuthorizationDecision> publishClientConnectionDecisionFlux                      = Flux
				.just(new IdentifiableAuthorizationDecision(
						publishClientMqttConnectionSaplSubscriptionId, AuthorizationDecision.PERMIT));
		Flux<IdentifiableAuthorizationDecision> publishClientMqttPublishDecisionFlux                     = Flux.just(
				new IdentifiableAuthorizationDecision(
						publishClientMqttConnectionSaplSubscriptionId, AuthorizationDecision.PERMIT),
				new IdentifiableAuthorizationDecision(
						publishClientMqttPublishSaplSubscriptionId, AuthorizationDecision.PERMIT));

		PolicyDecisionPoint pdpMock = mock(PolicyDecisionPoint.class);
		when(pdpMock.decide(any(MultiAuthorizationSubscription.class)))
				.thenReturn(subscriptionClientConnectionDecisionFlux)
				.thenReturn(publishClientConnectionDecisionFlux)
				.thenReturn(subscriptionClientMqttSubscriptionDecisionFlux)
				.thenReturn(publishClientMqttPublishDecisionFlux);

		Mqtt5Subscribe subscribeMessage = buildMqttSubscribeMessage(topic);
		Mqtt5Publish   publishMessage   = buildMqttPublishMessage(topic, false);

		EmbeddedHiveMQ mqttBroker = buildAndStartBroker(dataFolder, configFolder, extensionFolder, pdpMock);

		Mqtt5BlockingClient subscribeClient = buildAndStartMqttClient(subscriptionClientId);
		Mqtt5BlockingClient publishClient = buildAndStartMqttClient(publishClientId);

		// WHEN
		subscribeClient.subscribe(subscribeMessage);
		await().atMost(5, TimeUnit.SECONDS).untilTrue(isCompleteSubscriptionClientMqttSubscriptionDecisionFlux);

		// THEN
		await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
			publishClient.publish(publishMessage);
			Optional<Mqtt5Publish> receivedMessage = subscribeClient.publishes(MqttGlobalPublishFilter.ALL)
					.receive(3, TimeUnit.SECONDS);
			assertTrue(receivedMessage.isPresent());
			assertEquals(PUBLISH_MESSAGE_PAYLOAD, new String(receivedMessage.get().getPayloadAsBytes()));
		});

		verify(pdpMock, times(4)).decide(any(MultiAuthorizationSubscription.class));

		// FINALLY
		stopBroker(mqttBroker);
	}

	@Test
	@Timeout(30)
	void when_obligationForMqttSubscriptionFailed_then_denyMqttSubscription() throws InitializationException {
		// GIVEN
		String subscriptionClientMqttConnectionSaplSubscriptionId   = SaplSubscriptionUtility
				.buildSubscriptionId(subscriptionClientId, MqttPep.CONNECT_AUTHZ_ACTION);
		String subscriptionClientMqttSubscriptionSaplSubscriptionId = SaplSubscriptionUtility
				.buildSubscriptionId(subscriptionClientId, MqttPep.SUBSCRIBE_AUTHZ_ACTION, topic);

		ArrayNode illegalConstraint = JsonNodeFactory.instance.arrayNode()
				.add(JsonNodeFactory.instance.objectNode().put("illegalConstraint", 5));

		Flux<IdentifiableAuthorizationDecision> subscriptionClientConnectionDecisionFlux       = Flux
				.just(new IdentifiableAuthorizationDecision(
						subscriptionClientMqttConnectionSaplSubscriptionId, AuthorizationDecision.PERMIT));
		Flux<IdentifiableAuthorizationDecision> subscriptionClientMqttSubscriptionDecisionFlux = Flux.just(
				new IdentifiableAuthorizationDecision(
						subscriptionClientMqttConnectionSaplSubscriptionId, AuthorizationDecision.PERMIT),
				new IdentifiableAuthorizationDecision(
						subscriptionClientMqttSubscriptionSaplSubscriptionId, AuthorizationDecision.PERMIT
								.withObligations(illegalConstraint)));

		PolicyDecisionPoint pdpMock = mock(PolicyDecisionPoint.class);
		when(pdpMock.decide(any(MultiAuthorizationSubscription.class)))
				.thenReturn(subscriptionClientConnectionDecisionFlux)
				.thenReturn(subscriptionClientMqttSubscriptionDecisionFlux);

		Mqtt5Subscribe subscribeMessage = buildMqttSubscribeMessage(topic);

		// WHEN
		EmbeddedHiveMQ mqttBroker = buildAndStartBroker(dataFolder, configFolder, extensionFolder, pdpMock);
		Mqtt5BlockingClient subscribeClient = buildAndStartMqttClient(subscriptionClientId);

		// THEN
		Mqtt5SubAckException subAckException = assertThrowsExactly(Mqtt5SubAckException.class,
				() -> subscribeClient.subscribe(subscribeMessage));
		assertEquals(Mqtt5SubAckReasonCode.NOT_AUTHORIZED, subAckException.getMqttMessage().getReasonCodes().get(0));
		verify(pdpMock, times(2)).decide(any(MultiAuthorizationSubscription.class));

		// FINALLY
		stopBroker(mqttBroker);
	}

	@Test
	@Timeout(30)
	void when_obligationForMqttConnectionFailed_then_cancelMqttSubscription() {
		// GIVEN
		String    subscriptionClientMqttConnectionSaplSubscriptionId = SaplSubscriptionUtility
				.buildSubscriptionId(subscriptionClientId, MqttPep.CONNECT_AUTHZ_ACTION);
		ArrayNode illegalConstraint                                  = JsonNodeFactory.instance.arrayNode()
				.add(JsonNodeFactory.instance.objectNode().put("illegalConstraint", 5));

		Flux<IdentifiableAuthorizationDecision> subscriptionClientConnectionDecisionFlux = Flux
				.just(new IdentifiableAuthorizationDecision(
						subscriptionClientMqttConnectionSaplSubscriptionId, AuthorizationDecision.PERMIT
								.withObligations(illegalConstraint)));

		PolicyDecisionPoint pdpMock = mock(PolicyDecisionPoint.class);
		when(pdpMock.decide(any(MultiAuthorizationSubscription.class)))
				.thenReturn(subscriptionClientConnectionDecisionFlux);

		Mqtt5BlockingClient blockingMqttSubscriptionClient = Mqtt5Client.builder()
				.identifier(subscriptionClientId)
				.serverHost(BROKER_HOST)
				.serverPort(BROKER_PORT)
				.buildBlocking();

		// WHEN
		EmbeddedHiveMQ mqttBroker = buildAndStartBroker(dataFolder, configFolder, extensionFolder, pdpMock);

		// THEN
		Mqtt5ConnAckException connAckException = assertThrowsExactly(Mqtt5ConnAckException.class,
				blockingMqttSubscriptionClient::connect);
		assertEquals(Mqtt5ConnAckReasonCode.NOT_AUTHORIZED, connAckException.getMqttMessage().getReasonCode());
		verify(pdpMock, times(1)).decide(any(MultiAuthorizationSubscription.class));

		// FINALLY
		stopBroker(mqttBroker);
	}

	@Test
	@Timeout(30)
	void when_mqttSubscriptionTimeoutIsSetAndSaplDecisionOccursAfterAsyncProcessingTimeout_then_doNotBuildTimeout()
			throws InitializationException {
		// GIVEN
		String subscriptionClientMqttConnectionSaplSubscriptionId   = SaplSubscriptionUtility
				.buildSubscriptionId(subscriptionClientId, MqttPep.CONNECT_AUTHZ_ACTION);
		String subscriptionClientMqttSubscriptionSaplSubscriptionId = SaplSubscriptionUtility
				.buildSubscriptionId(subscriptionClientId, MqttPep.SUBSCRIBE_AUTHZ_ACTION, topic);

		ArrayNode mqttSubscriptionTimeLimitObligation = JsonNodeFactory.instance.arrayNode()
				.add(JsonNodeFactory.instance.objectNode()
						.put(ENVIRONMENT_CONSTRAINT_TYPE, ENVIRONMENT_LIMIT_MQTT_ACTION_DURATION)
						.put(ENVIRONMENT_TIME_LIMIT, 1));

		Flux<IdentifiableAuthorizationDecision>       subscriptionClientConnectionDecisionFlux = Flux
				.<IdentifiableAuthorizationDecision>never().mergeWith(
						Flux.just(new IdentifiableAuthorizationDecision(
								subscriptionClientMqttConnectionSaplSubscriptionId, AuthorizationDecision.PERMIT)));
		AtomicBoolean                                 wasCanceled                              = new AtomicBoolean(
				false);
		Sinks.Many<IdentifiableAuthorizationDecision> emitterUndefined                         = Sinks.many()
				.multicast().directAllOrNothing();

		PolicyDecisionPoint pdpMock = mock(PolicyDecisionPoint.class);
		when(pdpMock.decide(any(MultiAuthorizationSubscription.class)))
				.thenReturn(subscriptionClientConnectionDecisionFlux)
				.thenReturn(emitterUndefined.asFlux().doOnCancel(() -> wasCanceled.set(true)));

		Mqtt5Subscribe subscribeMessage = buildMqttSubscribeMessage(topic);

		// WHEN
		EmbeddedHiveMQ mqttBroker = buildAndStartBroker(dataFolder, configFolder, extensionFolder, pdpMock);
		Mqtt5BlockingClient subscribeClient = buildAndStartMqttClient(subscriptionClientId);
		Mqtt5SubAckException subAckException = assertThrowsExactly(Mqtt5SubAckException.class,
				() -> subscribeClient.subscribe(subscribeMessage));
		assertEquals(Mqtt5SubAckReasonCode.NOT_AUTHORIZED, subAckException.getMqttMessage().getReasonCodes().get(0));

		emitterUndefined.tryEmitNext(new IdentifiableAuthorizationDecision(
				subscriptionClientMqttSubscriptionSaplSubscriptionId,
				AuthorizationDecision.PERMIT.withObligations(mqttSubscriptionTimeLimitObligation)));

		// THEN
		verify(pdpMock, times(2)).decide(any(MultiAuthorizationSubscription.class));
		assertFalse(wasCanceled.get());

		// FINALLY
		stopBroker(mqttBroker);
	}
}