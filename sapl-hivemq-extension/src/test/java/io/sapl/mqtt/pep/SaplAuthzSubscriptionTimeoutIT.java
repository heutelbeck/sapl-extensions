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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
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
import org.junit.jupiter.api.io.TempDir;
import reactor.core.publisher.Flux;

class SaplAuthzSubscriptionTimeoutIT {

    @TempDir
    Path dataFolder;
    @TempDir
    Path configFolder;
    @TempDir
    Path extensionFolder;

    private static final String SUBSCRIPTION_CLIENT_ID = "subscriptionClient";
    private static final String PUBLISH_CLIENT_ID      = "publishClient";
    private static final String TOPIC                  = "testTopic";

    @Test
    void when_saplSubscriptionTimedOutBeforeFirstAuthzDecisionOnPublishEnforcement_then_unsubscribeSaplSubscription()
            throws InitializationException {
        // keep in mind that the asynchronous mqtt publish enforcement will time out
        // first
        // GIVEN
        String publishClientMqttConnectionSaplSubscriptionId = SaplSubscriptionUtility
                .buildSubscriptionId(PUBLISH_CLIENT_ID, MqttPep.CONNECT_AUTHZ_ACTION);
        String publishClientMqttPublishSaplSubscriptionId    = SaplSubscriptionUtility
                .buildSubscriptionId(PUBLISH_CLIENT_ID, MqttPep.PUBLISH_AUTHZ_ACTION, TOPIC);

        AtomicBoolean                           isCanceledPublishClientConnectionDecisionFlux  = new AtomicBoolean(
                false);
        Flux<IdentifiableAuthorizationDecision> publishClientConnectionDecisionFlux            = Flux
                .<IdentifiableAuthorizationDecision>never()
                .mergeWith(Flux.just(new IdentifiableAuthorizationDecision(
                        publishClientMqttConnectionSaplSubscriptionId, AuthorizationDecision.PERMIT)))
                .doOnCancel(() -> isCanceledPublishClientConnectionDecisionFlux.set(true));
        AtomicBoolean                           isCanceledPublishClientMqttPublishDecisionFlux = new AtomicBoolean(
                false);
        Flux<IdentifiableAuthorizationDecision> publishClientMqttPublishDecisionFlux           = Flux
                .<IdentifiableAuthorizationDecision>never()
                .mergeWith(Flux.just(new IdentifiableAuthorizationDecision(
                        publishClientMqttConnectionSaplSubscriptionId, AuthorizationDecision.PERMIT)))
                .doOnCancel(() -> isCanceledPublishClientMqttPublishDecisionFlux.set(true));
        PolicyDecisionPoint                     pdpMock                                        = mock(
                PolicyDecisionPoint.class);
        when(pdpMock.decide(any(MultiAuthorizationSubscription.class))).thenReturn(publishClientConnectionDecisionFlux)
                .thenReturn(publishClientMqttPublishDecisionFlux).thenReturn(publishClientConnectionDecisionFlux);

        ConcurrentHashMap<String, MqttClientState> mqttClientCache = new ConcurrentHashMap<>();

        EmbeddedHiveMQ      mqttBroker    = buildAndStartBroker(dataFolder, configFolder, extensionFolder, pdpMock,
                "src/test/resources/config/timeout/saplAuthzSubscription", mqttClientCache);
        Mqtt5BlockingClient publishClient = buildAndStartMqttClient(PUBLISH_CLIENT_ID);

        // WHEN
        Mqtt5Publish         publishMessage  = buildMqttPublishMessage(TOPIC, 1, false);
        Mqtt5PubAckException pubAckException = assertThrowsExactly(Mqtt5PubAckException.class,
                () -> publishClient.publish(publishMessage));
        assertEquals(Mqtt5PubAckReasonCode.NOT_AUTHORIZED, pubAckException.getMqttMessage().getReasonCode());

        // THEN
        await().atMost(3, TimeUnit.SECONDS)
                .untilAsserted(() -> verify(pdpMock, times(3)).decide(any(MultiAuthorizationSubscription.class)));
        assertTrue(isCanceledPublishClientMqttPublishDecisionFlux.get());
        assertTrue(isCanceledPublishClientConnectionDecisionFlux.get());
        assertMqttClientStateWasCleanedUp(mqttClientCache, PUBLISH_CLIENT_ID,
                publishClientMqttPublishSaplSubscriptionId);

        // FINALLY
        stopBroker(mqttBroker);
    }

    @Test
    void when_saplSubscriptionTimedOutAfterPermitDecisionOnPublishEnforcement_then_unsubscribeSaplSubscription()
            throws InitializationException {
        // GIVEN
        String publishClientMqttConnectionSaplSubscriptionId = SaplSubscriptionUtility
                .buildSubscriptionId(PUBLISH_CLIENT_ID, MqttPep.CONNECT_AUTHZ_ACTION);
        String publishClientMqttPublishSaplSubscriptionId    = SaplSubscriptionUtility
                .buildSubscriptionId(PUBLISH_CLIENT_ID, MqttPep.PUBLISH_AUTHZ_ACTION, TOPIC);

        AtomicBoolean                           isCanceledPublishClientConnectionDecisionFlux  = new AtomicBoolean(
                false);
        Flux<IdentifiableAuthorizationDecision> publishClientConnectionDecisionFlux            = Flux
                .<IdentifiableAuthorizationDecision>never()
                .mergeWith(Flux.just(new IdentifiableAuthorizationDecision(
                        publishClientMqttConnectionSaplSubscriptionId, AuthorizationDecision.PERMIT)))
                .doOnCancel(() -> isCanceledPublishClientConnectionDecisionFlux.set(true));
        AtomicBoolean                           isCanceledPublishClientMqttPublishDecisionFlux = new AtomicBoolean(
                false);
        Flux<IdentifiableAuthorizationDecision> publishClientMqttPublishDecisionFlux           = Flux
                .<IdentifiableAuthorizationDecision>never()
                .mergeWith(Flux.just(
                        new IdentifiableAuthorizationDecision(publishClientMqttConnectionSaplSubscriptionId,
                                AuthorizationDecision.PERMIT),
                        new IdentifiableAuthorizationDecision(publishClientMqttPublishSaplSubscriptionId,
                                AuthorizationDecision.PERMIT)))
                .doOnCancel(() -> isCanceledPublishClientMqttPublishDecisionFlux.set(true));
        PolicyDecisionPoint                     pdpMock                                        = mock(
                PolicyDecisionPoint.class);
        when(pdpMock.decide(any(MultiAuthorizationSubscription.class))).thenReturn(publishClientConnectionDecisionFlux)
                .thenReturn(publishClientMqttPublishDecisionFlux).thenReturn(publishClientConnectionDecisionFlux);

        ConcurrentHashMap<String, MqttClientState> mqttClientCache = new ConcurrentHashMap<>();

        EmbeddedHiveMQ      mqttBroker    = buildAndStartBroker(dataFolder, configFolder, extensionFolder, pdpMock,
                "src/test/resources/config/timeout/saplAuthzSubscription", mqttClientCache);
        Mqtt5BlockingClient publishClient = buildAndStartMqttClient(PUBLISH_CLIENT_ID);

        // WHEN
        Mqtt5Publish publishMessage = buildMqttPublishMessage(TOPIC, 1, false);
        publishClient.publish(publishMessage);

        // THEN
        await().atMost(3, TimeUnit.SECONDS)
                .untilAsserted(() -> verify(pdpMock, times(3)).decide(any(MultiAuthorizationSubscription.class)));
        assertTrue(isCanceledPublishClientConnectionDecisionFlux.get());
        assertTrue(isCanceledPublishClientMqttPublishDecisionFlux.get());
        assertMqttClientStateWasCleanedUp(mqttClientCache, PUBLISH_CLIENT_ID,
                publishClientMqttPublishSaplSubscriptionId);

        // FINALLY
        stopBroker(mqttBroker);
    }

    @Test
    void when_saplSubscriptionTimedOutAfterDenyDecisionOnPublishEnforcement_then_unsubscribeSaplSubscription()
            throws InitializationException {
        // GIVEN
        String publishClientMqttConnectionSaplSubscriptionId = SaplSubscriptionUtility
                .buildSubscriptionId(PUBLISH_CLIENT_ID, MqttPep.CONNECT_AUTHZ_ACTION);
        String publishClientMqttPublishSaplSubscriptionId    = SaplSubscriptionUtility
                .buildSubscriptionId(PUBLISH_CLIENT_ID, MqttPep.PUBLISH_AUTHZ_ACTION, TOPIC);

        Flux<IdentifiableAuthorizationDecision> publishClientConnectionDecisionFlux            = Flux
                .just(new IdentifiableAuthorizationDecision(publishClientMqttConnectionSaplSubscriptionId,
                        AuthorizationDecision.PERMIT));
        AtomicBoolean                           isCanceledPublishClientMqttPublishDecisionFlux = new AtomicBoolean(
                false);
        Flux<IdentifiableAuthorizationDecision> publishClientMqttPublishDecisionFlux           = Flux
                .<IdentifiableAuthorizationDecision>never()
                .mergeWith(Flux.just(
                        new IdentifiableAuthorizationDecision(publishClientMqttConnectionSaplSubscriptionId,
                                AuthorizationDecision.PERMIT),
                        new IdentifiableAuthorizationDecision(publishClientMqttPublishSaplSubscriptionId,
                                AuthorizationDecision.DENY)))
                .doOnCancel(() -> isCanceledPublishClientMqttPublishDecisionFlux.set(true));
        PolicyDecisionPoint                     pdpMock                                        = mock(
                PolicyDecisionPoint.class);
        when(pdpMock.decide(any(MultiAuthorizationSubscription.class))).thenReturn(publishClientConnectionDecisionFlux)
                .thenReturn(publishClientMqttPublishDecisionFlux).thenReturn(publishClientConnectionDecisionFlux);

        ConcurrentHashMap<String, MqttClientState> mqttClientCache = new ConcurrentHashMap<>();

        EmbeddedHiveMQ      mqttBroker    = buildAndStartBroker(dataFolder, configFolder, extensionFolder, pdpMock,
                "src/test/resources/config/timeout/saplAuthzSubscription", mqttClientCache);
        Mqtt5BlockingClient publishClient = buildAndStartMqttClient(PUBLISH_CLIENT_ID);

        // WHEN
        Mqtt5Publish         publishMessage  = buildMqttPublishMessage(TOPIC, 1, false);
        Mqtt5PubAckException pubAckException = assertThrowsExactly(Mqtt5PubAckException.class,
                () -> publishClient.publish(publishMessage));
        assertEquals(Mqtt5PubAckReasonCode.NOT_AUTHORIZED, pubAckException.getMqttMessage().getReasonCode());

        // THEN
        await().atMost(3, TimeUnit.SECONDS)
                .untilAsserted(() -> verify(pdpMock, times(3)).decide(any(MultiAuthorizationSubscription.class)));
        assertTrue(isCanceledPublishClientMqttPublishDecisionFlux.get());
        assertMqttClientStateWasCleanedUp(mqttClientCache, PUBLISH_CLIENT_ID,
                publishClientMqttPublishSaplSubscriptionId);

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
                .buildSubscriptionId(SUBSCRIPTION_CLIENT_ID, MqttPep.CONNECT_AUTHZ_ACTION);
        String subscriptionClientMqttSubscriptionSaplSubscriptionId = SaplSubscriptionUtility
                .buildSubscriptionId(SUBSCRIPTION_CLIENT_ID, MqttPep.SUBSCRIBE_AUTHZ_ACTION, TOPIC);

        AtomicBoolean                           isCanceledSubscriptionClientConnectionDecisionFlux       = new AtomicBoolean(
                false);
        Flux<IdentifiableAuthorizationDecision> subscriptionClientConnectionDecisionFlux                 = Flux
                .<IdentifiableAuthorizationDecision>never()
                .mergeWith(Flux.just(new IdentifiableAuthorizationDecision(
                        subscriptionClientMqttConnectionSaplSubscriptionId, AuthorizationDecision.PERMIT)))
                .doOnCancel(() -> isCanceledSubscriptionClientConnectionDecisionFlux.set(true));
        AtomicBoolean                           isCanceledSubscriptionClientMqttSubscriptionDecisionFlux = new AtomicBoolean(
                false);
        Flux<IdentifiableAuthorizationDecision> subscriptionClientMqttSubscriptionDecisionFlux           = Flux
                .<IdentifiableAuthorizationDecision>never()
                .mergeWith(Flux.just(new IdentifiableAuthorizationDecision(
                        subscriptionClientMqttConnectionSaplSubscriptionId, AuthorizationDecision.PERMIT)))
                .doOnCancel(() -> isCanceledSubscriptionClientMqttSubscriptionDecisionFlux.set(true));
        PolicyDecisionPoint                     pdpMock                                                  = mock(
                PolicyDecisionPoint.class);
        when(pdpMock.decide(any(MultiAuthorizationSubscription.class)))
                .thenReturn(subscriptionClientConnectionDecisionFlux)
                .thenReturn(subscriptionClientMqttSubscriptionDecisionFlux)
                .thenReturn(subscriptionClientConnectionDecisionFlux);

        ConcurrentHashMap<String, MqttClientState> mqttClientCache = new ConcurrentHashMap<>();

        EmbeddedHiveMQ      mqttBroker      = buildAndStartBroker(dataFolder, configFolder, extensionFolder, pdpMock,
                "src/test/resources/config/timeout/saplAuthzSubscription", mqttClientCache);
        Mqtt5BlockingClient subscribeClient = buildAndStartMqttClient(SUBSCRIPTION_CLIENT_ID);

        // WHEN
        Mqtt5Subscribe       subscribeMessage = buildMqttSubscribeMessage(TOPIC);
        Mqtt5SubAckException subAckException  = assertThrowsExactly(Mqtt5SubAckException.class,
                () -> subscribeClient.subscribe(subscribeMessage));
        assertEquals(Mqtt5SubAckReasonCode.NOT_AUTHORIZED, subAckException.getMqttMessage().getReasonCodes().get(0));

        // THEN
        await().atMost(3, TimeUnit.SECONDS)
                .untilAsserted(() -> verify(pdpMock, times(3)).decide(any(MultiAuthorizationSubscription.class)));
        assertTrue(isCanceledSubscriptionClientConnectionDecisionFlux.get());
        assertTrue(isCanceledSubscriptionClientMqttSubscriptionDecisionFlux.get());
        assertMqttClientStateWasCleanedUp(mqttClientCache, SUBSCRIPTION_CLIENT_ID,
                subscriptionClientMqttSubscriptionSaplSubscriptionId);

        // FINALLY
        stopBroker(mqttBroker);
    }

    @Test
    void when_saplSubscriptionTimedOutAfterInitialAuthzDecisionDenyOnSubscriptionEnforcement_then_unsubscribeSaplSubscription()
            throws InitializationException {
        // GIVEN
        String subscriptionClientMqttConnectionSaplSubscriptionId   = SaplSubscriptionUtility
                .buildSubscriptionId(SUBSCRIPTION_CLIENT_ID, MqttPep.CONNECT_AUTHZ_ACTION);
        String subscriptionClientMqttSubscriptionSaplSubscriptionId = SaplSubscriptionUtility
                .buildSubscriptionId(SUBSCRIPTION_CLIENT_ID, MqttPep.SUBSCRIBE_AUTHZ_ACTION, TOPIC);

        AtomicBoolean                           isCanceledSubscriptionClientConnectionDecisionFlux       = new AtomicBoolean(
                false);
        Flux<IdentifiableAuthorizationDecision> subscriptionClientConnectionDecisionFlux                 = Flux
                .<IdentifiableAuthorizationDecision>never()
                .mergeWith(Flux.just(new IdentifiableAuthorizationDecision(
                        subscriptionClientMqttConnectionSaplSubscriptionId, AuthorizationDecision.PERMIT)))
                .doOnCancel(() -> isCanceledSubscriptionClientConnectionDecisionFlux.set(true));
        AtomicBoolean                           isCanceledSubscriptionClientMqttSubscriptionDecisionFlux = new AtomicBoolean(
                false);
        Flux<IdentifiableAuthorizationDecision> subscriptionClientMqttSubscriptionDecisionFlux           = Flux
                .<IdentifiableAuthorizationDecision>never()
                .mergeWith(Flux
                        .just(new IdentifiableAuthorizationDecision(subscriptionClientMqttConnectionSaplSubscriptionId,
                                AuthorizationDecision.PERMIT))
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

        EmbeddedHiveMQ      mqttBroker      = buildAndStartBroker(dataFolder, configFolder, extensionFolder, pdpMock,
                "src/test/resources/config/timeout/saplAuthzSubscription", mqttClientCache);
        Mqtt5BlockingClient subscribeClient = buildAndStartMqttClient(SUBSCRIPTION_CLIENT_ID);

        // WHEN
        Mqtt5Subscribe       subscribeMessage = buildMqttSubscribeMessage(TOPIC);
        Mqtt5SubAckException subAckException  = assertThrowsExactly(Mqtt5SubAckException.class,
                () -> subscribeClient.subscribe(subscribeMessage));
        assertEquals(Mqtt5SubAckReasonCode.NOT_AUTHORIZED, subAckException.getMqttMessage().getReasonCodes().get(0));

        // THEN
        await().atMost(3, TimeUnit.SECONDS)
                .untilAsserted(() -> verify(pdpMock, times(3)).decide(any(MultiAuthorizationSubscription.class)));
        assertTrue(isCanceledSubscriptionClientConnectionDecisionFlux.get());
        assertTrue(isCanceledSubscriptionClientMqttSubscriptionDecisionFlux.get());
        assertMqttClientStateWasCleanedUp(mqttClientCache, SUBSCRIPTION_CLIENT_ID,
                subscriptionClientMqttSubscriptionSaplSubscriptionId);

        // FINALLY
        stopBroker(mqttBroker);
    }

    @Test
    void when_saplSubscriptionTimedOutAfterInitialPermitDecisionOnDenyOnSubscriptionEnforcement_then_unsubscribeSaplSubscription()
            throws InitializationException {
        // GIVEN
        String subscriptionClientMqttConnectionSaplSubscriptionId   = SaplSubscriptionUtility
                .buildSubscriptionId(SUBSCRIPTION_CLIENT_ID, MqttPep.CONNECT_AUTHZ_ACTION);
        String subscriptionClientMqttSubscriptionSaplSubscriptionId = SaplSubscriptionUtility
                .buildSubscriptionId(SUBSCRIPTION_CLIENT_ID, MqttPep.SUBSCRIBE_AUTHZ_ACTION, TOPIC);

        Flux<IdentifiableAuthorizationDecision> subscriptionClientConnectionDecisionFlux                 = Flux
                .just(new IdentifiableAuthorizationDecision(subscriptionClientMqttConnectionSaplSubscriptionId,
                        AuthorizationDecision.PERMIT));
        AtomicBoolean                           isCanceledSubscriptionClientMqttSubscriptionDecisionFlux = new AtomicBoolean(
                false);
        Flux<IdentifiableAuthorizationDecision> subscriptionClientMqttSubscriptionDecisionFlux           = Flux
                .<IdentifiableAuthorizationDecision>never()
                .mergeWith(Flux.just(
                        new IdentifiableAuthorizationDecision(subscriptionClientMqttConnectionSaplSubscriptionId,
                                AuthorizationDecision.PERMIT),
                        new IdentifiableAuthorizationDecision(subscriptionClientMqttSubscriptionSaplSubscriptionId,
                                AuthorizationDecision.DENY))
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

        EmbeddedHiveMQ      mqttBroker      = buildAndStartBroker(dataFolder, configFolder, extensionFolder, pdpMock,
                "src/test/resources/config/timeout/saplAuthzSubscription", mqttClientCache);
        Mqtt5BlockingClient subscribeClient = buildAndStartMqttClient(SUBSCRIPTION_CLIENT_ID);

        // WHEN
        Mqtt5Subscribe subscribeMessage = buildMqttSubscribeMessage(TOPIC);
        subscribeClient.subscribe(subscribeMessage);

        // THEN
        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> verify(pdpMock, times(3)).decide(any(MultiAuthorizationSubscription.class)));
        assertTrue(isCanceledSubscriptionClientMqttSubscriptionDecisionFlux.get());
        assertMqttClientStateWasCleanedUp(mqttClientCache, SUBSCRIPTION_CLIENT_ID,
                subscriptionClientMqttSubscriptionSaplSubscriptionId);

        // FINALLY
        stopBroker(mqttBroker);
    }

    @Test
    void when_saplSubscriptionTimedOutAfterUnsubscribeOnSubscriptionEnforcement_then_unsubscribeSaplSubscription()
            throws InitializationException {
        // GIVEN
        String subscriptionClientMqttConnectionSaplSubscriptionId   = SaplSubscriptionUtility
                .buildSubscriptionId(SUBSCRIPTION_CLIENT_ID, MqttPep.CONNECT_AUTHZ_ACTION);
        String subscriptionClientMqttSubscriptionSaplSubscriptionId = SaplSubscriptionUtility
                .buildSubscriptionId(SUBSCRIPTION_CLIENT_ID, MqttPep.SUBSCRIBE_AUTHZ_ACTION, TOPIC);

        Flux<IdentifiableAuthorizationDecision> subscriptionClientConnectionDecisionFlux                 = Flux
                .just(new IdentifiableAuthorizationDecision(subscriptionClientMqttConnectionSaplSubscriptionId,
                        AuthorizationDecision.PERMIT));
        AtomicBoolean                           isCanceledSubscriptionClientMqttSubscriptionDecisionFlux = new AtomicBoolean(
                false);
        Flux<IdentifiableAuthorizationDecision> subscriptionClientMqttSubscriptionDecisionFlux           = Flux
                .<IdentifiableAuthorizationDecision>never()
                .mergeWith(Flux
                        .just(new IdentifiableAuthorizationDecision(subscriptionClientMqttConnectionSaplSubscriptionId,
                                AuthorizationDecision.PERMIT))
                        .startWith(new IdentifiableAuthorizationDecision(
                                subscriptionClientMqttSubscriptionSaplSubscriptionId, AuthorizationDecision.PERMIT)))
                .doOnCancel(() -> isCanceledSubscriptionClientMqttSubscriptionDecisionFlux.set(true));

        PolicyDecisionPoint pdpMock = mock(PolicyDecisionPoint.class);
        when(pdpMock.decide(any(MultiAuthorizationSubscription.class)))
                .thenReturn(subscriptionClientConnectionDecisionFlux)
                .thenReturn(subscriptionClientMqttSubscriptionDecisionFlux)
                .thenReturn(subscriptionClientConnectionDecisionFlux);

        ConcurrentHashMap<String, MqttClientState> mqttClientCache = new ConcurrentHashMap<>();

        EmbeddedHiveMQ      mqttBroker      = buildAndStartBroker(dataFolder, configFolder, extensionFolder, pdpMock,
                "src/test/resources/config/timeout/saplAuthzSubscription", mqttClientCache);
        Mqtt5BlockingClient subscribeClient = buildAndStartMqttClient(SUBSCRIPTION_CLIENT_ID);

        // WHEN
        Mqtt5Subscribe   subscribeMessage   = buildMqttSubscribeMessage(TOPIC);
        Mqtt5Unsubscribe unsubscribeMessage = Mqtt5Unsubscribe.builder().topicFilter(TOPIC).build();
        subscribeClient.subscribe(subscribeMessage);

        subscribeClient.unsubscribe(unsubscribeMessage);

        // THEN
        await().atMost(3, TimeUnit.SECONDS)
                .untilAsserted(() -> verify(pdpMock, times(3)).decide(any(MultiAuthorizationSubscription.class)));
        assertTrue(isCanceledSubscriptionClientMqttSubscriptionDecisionFlux.get());
        assertMqttClientStateWasCleanedUp(mqttClientCache, SUBSCRIPTION_CLIENT_ID,
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
