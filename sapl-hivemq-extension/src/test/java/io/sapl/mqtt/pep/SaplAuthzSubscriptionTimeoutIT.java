package io.sapl.mqtt.pep;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.hivemq.client.mqtt.mqtt5.Mqtt5BlockingClient;
import com.hivemq.embedded.EmbeddedHiveMQ;
import org.junit.jupiter.api.Test;

import com.hivemq.client.mqtt.mqtt5.exceptions.Mqtt5PubAckException;
import com.hivemq.client.mqtt.mqtt5.exceptions.Mqtt5SubAckException;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import com.hivemq.client.mqtt.mqtt5.message.publish.puback.Mqtt5PubAckReasonCode;
import com.hivemq.client.mqtt.mqtt5.message.subscribe.Mqtt5Subscribe;
import com.hivemq.client.mqtt.mqtt5.message.subscribe.suback.Mqtt5SubAckReasonCode;
import com.hivemq.client.mqtt.mqtt5.message.unsubscribe.Mqtt5Unsubscribe;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.IdentifiableAuthorizationDecision;
import io.sapl.api.pdp.MultiAuthorizationSubscription;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.interpreter.InitializationException;
import io.sapl.mqtt.pep.cache.MqttClientState;
import io.sapl.mqtt.pep.util.SaplSubscriptionUtility;
import reactor.core.publisher.Flux;

class SaplAuthzSubscriptionTimeoutIT extends SaplMqttPepTestUtil {

	private final String subscriptionClientId = "subscriptionClient";
	private final String publishClientId      = "publishClient";
	private final String topic                = "testTopic";

	@Test
	void when_saplSubscriptionTimedOutBeforeFirstAuthzDecisionOnPublishEnforcement_then_unsubscribeSaplSubscription()
			throws InitializationException {
		// keep in mind that the asynchronous mqtt publish enforcement will time out
		// first
		// GIVEN
		String publishClientMqttConnectionSaplSubscriptionId = SaplSubscriptionUtility
				.buildSubscriptionId(publishClientId, MqttPep.CONNECT_AUTHZ_ACTION);
		String publishClientMqttPublishSaplSubscriptionId    = SaplSubscriptionUtility
				.buildSubscriptionId(publishClientId, MqttPep.PUBLISH_AUTHZ_ACTION, topic);

		AtomicBoolean                           isCanceledPublishClientConnectionDecisionFlux  = new AtomicBoolean(
				false);
		Flux<IdentifiableAuthorizationDecision> publishClientConnectionDecisionFlux            = Flux
				.<IdentifiableAuthorizationDecision>never().mergeWith(
						Flux.just(new IdentifiableAuthorizationDecision(
								publishClientMqttConnectionSaplSubscriptionId, AuthorizationDecision.PERMIT)))
				.doOnCancel(() -> isCanceledPublishClientConnectionDecisionFlux.set(true));
		AtomicBoolean                           isCanceledPublishClientMqttPublishDecisionFlux = new AtomicBoolean(
				false);
		Flux<IdentifiableAuthorizationDecision> publishClientMqttPublishDecisionFlux           = Flux
				.<IdentifiableAuthorizationDecision>never().mergeWith(
						Flux.just(new IdentifiableAuthorizationDecision(
								publishClientMqttConnectionSaplSubscriptionId, AuthorizationDecision.PERMIT)))
				.doOnCancel(() -> isCanceledPublishClientMqttPublishDecisionFlux.set(true));
		PolicyDecisionPoint                     pdpMock                                        = mock(
				PolicyDecisionPoint.class);
		when(pdpMock.decide(any(MultiAuthorizationSubscription.class)))
				.thenReturn(publishClientConnectionDecisionFlux)
				.thenReturn(publishClientMqttPublishDecisionFlux)
				.thenReturn(publishClientConnectionDecisionFlux);

		ConcurrentHashMap<String, MqttClientState> mqttClientCache = new ConcurrentHashMap<>();

		EmbeddedHiveMQ mqttBroker = buildAndStartBroker(pdpMock,
				"src/test/resources/config/timeout/saplAuthzSubscription", mqttClientCache);
		Mqtt5BlockingClient publishClient = buildAndStartMqttClient(publishClientId);

		// WHEN
		Mqtt5Publish         publishMessage  = buildMqttPublishMessage(topic, 1, false);
		Mqtt5PubAckException pubAckException = assertThrowsExactly(Mqtt5PubAckException.class,
				() -> publishClient.publish(publishMessage));
		assertEquals(Mqtt5PubAckReasonCode.NOT_AUTHORIZED, pubAckException.getMqttMessage().getReasonCode());

		// THEN
		await().atMost(3, TimeUnit.SECONDS)
				.untilAsserted(() -> verify(pdpMock, times(3)).decide(any(MultiAuthorizationSubscription.class)));
		assertTrue(isCanceledPublishClientMqttPublishDecisionFlux.get());
		assertTrue(isCanceledPublishClientConnectionDecisionFlux.get());
		assertMqttClientStateWasCleanedUp(mqttClientCache, publishClientId, publishClientMqttPublishSaplSubscriptionId);

		// FINALLY
		stopBroker(mqttBroker);
	}

	@Test
	void when_saplSubscriptionTimedOutAfterPermitDecisionOnPublishEnforcement_then_unsubscribeSaplSubscription()
			throws InitializationException {
		// GIVEN
		String publishClientMqttConnectionSaplSubscriptionId = SaplSubscriptionUtility
				.buildSubscriptionId(publishClientId, MqttPep.CONNECT_AUTHZ_ACTION);
		String publishClientMqttPublishSaplSubscriptionId    = SaplSubscriptionUtility
				.buildSubscriptionId(publishClientId, MqttPep.PUBLISH_AUTHZ_ACTION, topic);

		AtomicBoolean                           isCanceledPublishClientConnectionDecisionFlux  = new AtomicBoolean(
				false);
		Flux<IdentifiableAuthorizationDecision> publishClientConnectionDecisionFlux            = Flux
				.<IdentifiableAuthorizationDecision>never().mergeWith(Flux.just(new IdentifiableAuthorizationDecision(
						publishClientMqttConnectionSaplSubscriptionId, AuthorizationDecision.PERMIT)))
				.doOnCancel(() -> isCanceledPublishClientConnectionDecisionFlux.set(true));
		AtomicBoolean                           isCanceledPublishClientMqttPublishDecisionFlux = new AtomicBoolean(
				false);
		Flux<IdentifiableAuthorizationDecision> publishClientMqttPublishDecisionFlux           = Flux
				.<IdentifiableAuthorizationDecision>never().mergeWith(Flux.just(new IdentifiableAuthorizationDecision(
						publishClientMqttConnectionSaplSubscriptionId, AuthorizationDecision.PERMIT),
						new IdentifiableAuthorizationDecision(
								publishClientMqttPublishSaplSubscriptionId, AuthorizationDecision.PERMIT)))
				.doOnCancel(() -> isCanceledPublishClientMqttPublishDecisionFlux.set(true));
		PolicyDecisionPoint                     pdpMock                                        = mock(
				PolicyDecisionPoint.class);
		when(pdpMock.decide(any(MultiAuthorizationSubscription.class)))
				.thenReturn(publishClientConnectionDecisionFlux)
				.thenReturn(publishClientMqttPublishDecisionFlux)
				.thenReturn(publishClientConnectionDecisionFlux);

		ConcurrentHashMap<String, MqttClientState> mqttClientCache = new ConcurrentHashMap<>();

		EmbeddedHiveMQ mqttBroker = buildAndStartBroker(pdpMock,
				"src/test/resources/config/timeout/saplAuthzSubscription", mqttClientCache);
		Mqtt5BlockingClient publishClient = buildAndStartMqttClient(publishClientId);

		// WHEN
		Mqtt5Publish publishMessage = buildMqttPublishMessage(topic, 1, false);
		publishClient.publish(publishMessage);

		// THEN
		await().atMost(3, TimeUnit.SECONDS)
				.untilAsserted(() -> verify(pdpMock, times(3)).decide(any(MultiAuthorizationSubscription.class)));
		assertTrue(isCanceledPublishClientConnectionDecisionFlux.get());
		assertTrue(isCanceledPublishClientMqttPublishDecisionFlux.get());
		assertMqttClientStateWasCleanedUp(mqttClientCache, publishClientId, publishClientMqttPublishSaplSubscriptionId);

		// FINALLY
		stopBroker(mqttBroker);
	}

	@Test
	void when_saplSubscriptionTimedOutAfterDenyDecisionOnPublishEnforcement_then_unsubscribeSaplSubscription()
			throws InitializationException {
		// GIVEN
		String publishClientMqttConnectionSaplSubscriptionId = SaplSubscriptionUtility
				.buildSubscriptionId(publishClientId, MqttPep.CONNECT_AUTHZ_ACTION);
		String publishClientMqttPublishSaplSubscriptionId    = SaplSubscriptionUtility
				.buildSubscriptionId(publishClientId, MqttPep.PUBLISH_AUTHZ_ACTION, topic);

		Flux<IdentifiableAuthorizationDecision> publishClientConnectionDecisionFlux            = Flux
				.just(new IdentifiableAuthorizationDecision(
						publishClientMqttConnectionSaplSubscriptionId, AuthorizationDecision.PERMIT));
		AtomicBoolean                           isCanceledPublishClientMqttPublishDecisionFlux = new AtomicBoolean(
				false);
		Flux<IdentifiableAuthorizationDecision> publishClientMqttPublishDecisionFlux           = Flux
				.<IdentifiableAuthorizationDecision>never().mergeWith(Flux.just(new IdentifiableAuthorizationDecision(
						publishClientMqttConnectionSaplSubscriptionId, AuthorizationDecision.PERMIT),
						new IdentifiableAuthorizationDecision(
								publishClientMqttPublishSaplSubscriptionId, AuthorizationDecision.DENY)))
				.doOnCancel(() -> isCanceledPublishClientMqttPublishDecisionFlux.set(true));
		PolicyDecisionPoint                     pdpMock                                        = mock(
				PolicyDecisionPoint.class);
		when(pdpMock.decide(any(MultiAuthorizationSubscription.class)))
				.thenReturn(publishClientConnectionDecisionFlux)
				.thenReturn(publishClientMqttPublishDecisionFlux)
				.thenReturn(publishClientConnectionDecisionFlux);

		ConcurrentHashMap<String, MqttClientState> mqttClientCache = new ConcurrentHashMap<>();

		EmbeddedHiveMQ mqttBroker = buildAndStartBroker(pdpMock,
				"src/test/resources/config/timeout/saplAuthzSubscription", mqttClientCache);
		Mqtt5BlockingClient publishClient = buildAndStartMqttClient(publishClientId);

		// WHEN
		Mqtt5Publish         publishMessage  = buildMqttPublishMessage(topic, 1, false);
		Mqtt5PubAckException pubAckException = assertThrowsExactly(Mqtt5PubAckException.class,
				() -> publishClient.publish(publishMessage));
		assertEquals(Mqtt5PubAckReasonCode.NOT_AUTHORIZED, pubAckException.getMqttMessage().getReasonCode());

		// THEN
		await().atMost(3, TimeUnit.SECONDS)
				.untilAsserted(() -> verify(pdpMock, times(3)).decide(any(MultiAuthorizationSubscription.class)));
		assertTrue(isCanceledPublishClientMqttPublishDecisionFlux.get());
		assertMqttClientStateWasCleanedUp(mqttClientCache, publishClientId, publishClientMqttPublishSaplSubscriptionId);

		// FINALLY
		stopBroker(mqttBroker);
	}

	@Test
	void when_saplSubscriptionTimedOutBeforeFirstAuthzDecisionOnSubscriptionEnforcement_then_unsubscribeSaplSubscription()
			throws InitializationException {
		// keep in mind that the asynchronous mqtt subscription enforcement will time
		// out first
		// GIVEN
		String subscriptionClientMqttConnectionSaplSubscriptionId   = SaplSubscriptionUtility
				.buildSubscriptionId(subscriptionClientId, MqttPep.CONNECT_AUTHZ_ACTION);
		String subscriptionClientMqttSubscriptionSaplSubscriptionId = SaplSubscriptionUtility
				.buildSubscriptionId(subscriptionClientId, MqttPep.SUBSCRIBE_AUTHZ_ACTION, topic);

		AtomicBoolean                           isCanceledSubscriptionClientConnectionDecisionFlux       = new AtomicBoolean(
				false);
		Flux<IdentifiableAuthorizationDecision> subscriptionClientConnectionDecisionFlux                 = Flux
				.<IdentifiableAuthorizationDecision>never().mergeWith(Flux.just(new IdentifiableAuthorizationDecision(
						subscriptionClientMqttConnectionSaplSubscriptionId, AuthorizationDecision.PERMIT)))
				.doOnCancel(() -> isCanceledSubscriptionClientConnectionDecisionFlux.set(true));
		AtomicBoolean                           isCanceledSubscriptionClientMqttSubscriptionDecisionFlux = new AtomicBoolean(
				false);
		Flux<IdentifiableAuthorizationDecision> subscriptionClientMqttSubscriptionDecisionFlux           = Flux
				.<IdentifiableAuthorizationDecision>never().mergeWith(Flux.just(new IdentifiableAuthorizationDecision(
						subscriptionClientMqttConnectionSaplSubscriptionId, AuthorizationDecision.PERMIT)))
				.doOnCancel(() -> isCanceledSubscriptionClientMqttSubscriptionDecisionFlux.set(true));
		PolicyDecisionPoint                     pdpMock                                                  = mock(
				PolicyDecisionPoint.class);
		when(pdpMock.decide(any(MultiAuthorizationSubscription.class)))
				.thenReturn(subscriptionClientConnectionDecisionFlux)
				.thenReturn(subscriptionClientMqttSubscriptionDecisionFlux)
				.thenReturn(subscriptionClientConnectionDecisionFlux);

		ConcurrentHashMap<String, MqttClientState> mqttClientCache = new ConcurrentHashMap<>();

		EmbeddedHiveMQ mqttBroker = buildAndStartBroker(pdpMock,
				"src/test/resources/config/timeout/saplAuthzSubscription", mqttClientCache);
		Mqtt5BlockingClient subscribeClient = buildAndStartMqttClient(subscriptionClientId);

		// WHEN
		Mqtt5Subscribe       subscribeMessage = buildMqttSubscribeMessage(topic);
		Mqtt5SubAckException subAckException  = assertThrowsExactly(Mqtt5SubAckException.class,
				() -> subscribeClient.subscribe(subscribeMessage));
		assertEquals(Mqtt5SubAckReasonCode.NOT_AUTHORIZED, subAckException.getMqttMessage().getReasonCodes().get(0));

		// THEN
		await().atMost(3, TimeUnit.SECONDS)
				.untilAsserted(() -> verify(pdpMock, times(3)).decide(any(MultiAuthorizationSubscription.class)));
		assertTrue(isCanceledSubscriptionClientConnectionDecisionFlux.get());
		assertTrue(isCanceledSubscriptionClientMqttSubscriptionDecisionFlux.get());
		assertMqttClientStateWasCleanedUp(mqttClientCache, subscriptionClientId,
				subscriptionClientMqttSubscriptionSaplSubscriptionId);

		// FINALLY
		stopBroker(mqttBroker);
	}

	@Test
	void when_saplSubscriptionTimedOutAfterInitialAuthzDecisionDenyOnSubscriptionEnforcement_then_unsubscribeSaplSubscription()
			throws InitializationException {
		// GIVEN
		String subscriptionClientMqttConnectionSaplSubscriptionId   = SaplSubscriptionUtility
				.buildSubscriptionId(subscriptionClientId, MqttPep.CONNECT_AUTHZ_ACTION);
		String subscriptionClientMqttSubscriptionSaplSubscriptionId = SaplSubscriptionUtility
				.buildSubscriptionId(subscriptionClientId, MqttPep.SUBSCRIBE_AUTHZ_ACTION, topic);

		AtomicBoolean                           isCanceledSubscriptionClientConnectionDecisionFlux       = new AtomicBoolean(
				false);
		Flux<IdentifiableAuthorizationDecision> subscriptionClientConnectionDecisionFlux                 = Flux
				.<IdentifiableAuthorizationDecision>never().mergeWith(Flux.just(new IdentifiableAuthorizationDecision(
						subscriptionClientMqttConnectionSaplSubscriptionId, AuthorizationDecision.PERMIT)))
				.doOnCancel(() -> isCanceledSubscriptionClientConnectionDecisionFlux.set(true));
		AtomicBoolean                           isCanceledSubscriptionClientMqttSubscriptionDecisionFlux = new AtomicBoolean(
				false);
		Flux<IdentifiableAuthorizationDecision> subscriptionClientMqttSubscriptionDecisionFlux           = Flux
				.<IdentifiableAuthorizationDecision>never().mergeWith(Flux.just(new IdentifiableAuthorizationDecision(
						subscriptionClientMqttConnectionSaplSubscriptionId, AuthorizationDecision.PERMIT))
						.startWith(new IdentifiableAuthorizationDecision(
								subscriptionClientMqttSubscriptionSaplSubscriptionId, AuthorizationDecision.DENY))
						.delayElements(Duration.ofMillis(1000)))
				.doOnCancel(() -> isCanceledSubscriptionClientMqttSubscriptionDecisionFlux.set(true));

		PolicyDecisionPoint pdpMock = mock(PolicyDecisionPoint.class);
		when(pdpMock.decide(any(MultiAuthorizationSubscription.class)))
				.thenReturn(subscriptionClientConnectionDecisionFlux)
				.thenReturn(subscriptionClientMqttSubscriptionDecisionFlux)
				.thenReturn(subscriptionClientConnectionDecisionFlux);

		ConcurrentHashMap<String, MqttClientState> mqttClientCache = new ConcurrentHashMap<>();

		EmbeddedHiveMQ mqttBroker = buildAndStartBroker(pdpMock,
				"src/test/resources/config/timeout/saplAuthzSubscription", mqttClientCache);
		Mqtt5BlockingClient subscribeClient = buildAndStartMqttClient(subscriptionClientId);

		// WHEN
		Mqtt5Subscribe       subscribeMessage = buildMqttSubscribeMessage(topic);
		Mqtt5SubAckException subAckException  = assertThrowsExactly(Mqtt5SubAckException.class,
				() -> subscribeClient.subscribe(subscribeMessage));
		assertEquals(Mqtt5SubAckReasonCode.NOT_AUTHORIZED, subAckException.getMqttMessage().getReasonCodes().get(0));

		// THEN
		await().atMost(3, TimeUnit.SECONDS)
				.untilAsserted(() -> verify(pdpMock, times(3)).decide(any(MultiAuthorizationSubscription.class)));
		assertTrue(isCanceledSubscriptionClientConnectionDecisionFlux.get());
		assertTrue(isCanceledSubscriptionClientMqttSubscriptionDecisionFlux.get());
		assertMqttClientStateWasCleanedUp(mqttClientCache, subscriptionClientId,
				subscriptionClientMqttSubscriptionSaplSubscriptionId);

		// FINALLY
		stopBroker(mqttBroker);
	}

	@Test
	void when_saplSubscriptionTimedOutAfterInitialPermitDecisionOnDenyOnSubscriptionEnforcement_then_unsubscribeSaplSubscription()
			throws InitializationException {
		// GIVEN
		String subscriptionClientMqttConnectionSaplSubscriptionId   = SaplSubscriptionUtility
				.buildSubscriptionId(subscriptionClientId, MqttPep.CONNECT_AUTHZ_ACTION);
		String subscriptionClientMqttSubscriptionSaplSubscriptionId = SaplSubscriptionUtility
				.buildSubscriptionId(subscriptionClientId, MqttPep.SUBSCRIBE_AUTHZ_ACTION, topic);

		Flux<IdentifiableAuthorizationDecision> subscriptionClientConnectionDecisionFlux                 = Flux
				.just(new IdentifiableAuthorizationDecision(
						subscriptionClientMqttConnectionSaplSubscriptionId, AuthorizationDecision.PERMIT));
		AtomicBoolean                           isCanceledSubscriptionClientMqttSubscriptionDecisionFlux = new AtomicBoolean(
				false);
		Flux<IdentifiableAuthorizationDecision> subscriptionClientMqttSubscriptionDecisionFlux           = Flux
				.<IdentifiableAuthorizationDecision>never().mergeWith(Flux.just(new IdentifiableAuthorizationDecision(
						subscriptionClientMqttConnectionSaplSubscriptionId, AuthorizationDecision.PERMIT),
						new IdentifiableAuthorizationDecision(
								subscriptionClientMqttSubscriptionSaplSubscriptionId, AuthorizationDecision.DENY))
						.startWith(new IdentifiableAuthorizationDecision(
								subscriptionClientMqttSubscriptionSaplSubscriptionId, AuthorizationDecision.PERMIT))
						.delayElements(Duration.ofMillis(1000)))
				.doOnCancel(() -> isCanceledSubscriptionClientMqttSubscriptionDecisionFlux.set(true));

		PolicyDecisionPoint pdpMock = mock(PolicyDecisionPoint.class);
		when(pdpMock.decide(any(MultiAuthorizationSubscription.class)))
				.thenReturn(subscriptionClientConnectionDecisionFlux)
				.thenReturn(subscriptionClientMqttSubscriptionDecisionFlux)
				.thenReturn(subscriptionClientConnectionDecisionFlux);

		ConcurrentHashMap<String, MqttClientState> mqttClientCache = new ConcurrentHashMap<>();

		EmbeddedHiveMQ mqttBroker = buildAndStartBroker(pdpMock,
				"src/test/resources/config/timeout/saplAuthzSubscription", mqttClientCache);
		Mqtt5BlockingClient subscribeClient = buildAndStartMqttClient(subscriptionClientId);

		// WHEN
		Mqtt5Subscribe subscribeMessage = buildMqttSubscribeMessage(topic);
		subscribeClient.subscribe(subscribeMessage);

		// THEN
		await().atMost(5, TimeUnit.SECONDS)
				.untilAsserted(() -> verify(pdpMock, times(3)).decide(any(MultiAuthorizationSubscription.class)));
		assertTrue(isCanceledSubscriptionClientMqttSubscriptionDecisionFlux.get());
		assertMqttClientStateWasCleanedUp(mqttClientCache, subscriptionClientId,
				subscriptionClientMqttSubscriptionSaplSubscriptionId);

		// FINALLY
		stopBroker(mqttBroker);
	}

	@Test
	void when_saplSubscriptionTimedOutAfterUnsubscribeOnSubscriptionEnforcement_then_unsubscribeSaplSubscription()
			throws InitializationException {
		// GIVEN
		String subscriptionClientMqttConnectionSaplSubscriptionId   = SaplSubscriptionUtility
				.buildSubscriptionId(subscriptionClientId, MqttPep.CONNECT_AUTHZ_ACTION);
		String subscriptionClientMqttSubscriptionSaplSubscriptionId = SaplSubscriptionUtility
				.buildSubscriptionId(subscriptionClientId, MqttPep.SUBSCRIBE_AUTHZ_ACTION, topic);

		Flux<IdentifiableAuthorizationDecision> subscriptionClientConnectionDecisionFlux                 = Flux
				.just(new IdentifiableAuthorizationDecision(
						subscriptionClientMqttConnectionSaplSubscriptionId, AuthorizationDecision.PERMIT));
		AtomicBoolean                           isCanceledSubscriptionClientMqttSubscriptionDecisionFlux = new AtomicBoolean(
				false);
		Flux<IdentifiableAuthorizationDecision> subscriptionClientMqttSubscriptionDecisionFlux           = Flux
				.<IdentifiableAuthorizationDecision>never().mergeWith(Flux.just(new IdentifiableAuthorizationDecision(
						subscriptionClientMqttConnectionSaplSubscriptionId, AuthorizationDecision.PERMIT))
						.startWith(new IdentifiableAuthorizationDecision(
								subscriptionClientMqttSubscriptionSaplSubscriptionId, AuthorizationDecision.PERMIT)))
				.doOnCancel(() -> isCanceledSubscriptionClientMqttSubscriptionDecisionFlux.set(true));

		PolicyDecisionPoint pdpMock = mock(PolicyDecisionPoint.class);
		when(pdpMock.decide(any(MultiAuthorizationSubscription.class)))
				.thenReturn(subscriptionClientConnectionDecisionFlux)
				.thenReturn(subscriptionClientMqttSubscriptionDecisionFlux)
				.thenReturn(subscriptionClientConnectionDecisionFlux);

		ConcurrentHashMap<String, MqttClientState> mqttClientCache = new ConcurrentHashMap<>();

		EmbeddedHiveMQ mqttBroker = buildAndStartBroker(pdpMock,
				"src/test/resources/config/timeout/saplAuthzSubscription", mqttClientCache);
		Mqtt5BlockingClient subscribeClient = buildAndStartMqttClient(subscriptionClientId);

		// WHEN
		Mqtt5Subscribe   subscribeMessage   = buildMqttSubscribeMessage(topic);
		Mqtt5Unsubscribe unsubscribeMessage = Mqtt5Unsubscribe.builder().topicFilter(topic).build();
		subscribeClient.subscribe(subscribeMessage);

		subscribeClient.unsubscribe(unsubscribeMessage);

		// THEN
		await().atMost(3, TimeUnit.SECONDS)
				.untilAsserted(() -> verify(pdpMock, times(3)).decide(any(MultiAuthorizationSubscription.class)));
		assertTrue(isCanceledSubscriptionClientMqttSubscriptionDecisionFlux.get());
		assertMqttClientStateWasCleanedUp(mqttClientCache, subscriptionClientId,
				subscriptionClientMqttSubscriptionSaplSubscriptionId);

		// FINALLY
		stopBroker(mqttBroker);
	}

	private void assertMqttClientStateWasCleanedUp(ConcurrentHashMap<String, MqttClientState> mqttClientCache,
			String clientId, String saplSubscriptionId) {
		MqttClientState mqttClientState = mqttClientCache.get(clientId);
		assertNull(mqttClientState.getSaplAuthzSubscriptionFromMultiSubscription(saplSubscriptionId));
		assertFalse(mqttClientState.getIdentAuthzDecisionMap().containsKey(saplSubscriptionId));
		assertNull(mqttClientState.getConstraintDetailsFromMap(saplSubscriptionId));
		assertNull(mqttClientState.getMqttActionDecisionFluxFromMap(saplSubscriptionId));
		assertNull(mqttClientState.getMqttActionDecisionFluxDisposableFromMap(saplSubscriptionId));
		assertNull(mqttClientState.getMqttActionStartTimeFromMap(saplSubscriptionId));
		assertNull(mqttClientState.getMqttActionStartTimeFromMap(saplSubscriptionId));
		assertNull(mqttClientState.getLastSignalTimeFromMap(saplSubscriptionId));
		assertNull(mqttClientState.getTopicSubscriptionFromMap(saplSubscriptionId));
		assertTrue(mqttClientState.isUnsubscribeMessageTopicsMapEmpty());
	}
}