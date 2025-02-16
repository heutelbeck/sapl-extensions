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
package io.sapl.mqtt.pep;

import static io.sapl.mqtt.pep.MqttTestUtil.BROKER_HOST;
import static io.sapl.mqtt.pep.MqttTestUtil.BROKER_PORT;
import static io.sapl.mqtt.pep.MqttTestUtil.PUBLISH_MESSAGE_PAYLOAD;
import static io.sapl.mqtt.pep.MqttTestUtil.buildAndStartBroker;
import static io.sapl.mqtt.pep.MqttTestUtil.buildAndStartMqttClient;
import static io.sapl.mqtt.pep.MqttTestUtil.buildMqttPublishMessage;
import static io.sapl.mqtt.pep.MqttTestUtil.buildMqttSubscribeMessage;
import static io.sapl.mqtt.pep.MqttTestUtil.stopBroker;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import com.hivemq.client.mqtt.MqttGlobalPublishFilter;
import com.hivemq.client.mqtt.mqtt5.Mqtt5BlockingClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client;
import com.hivemq.client.mqtt.mqtt5.exceptions.Mqtt5ConnAckException;
import com.hivemq.client.mqtt.mqtt5.exceptions.Mqtt5PubAckException;
import com.hivemq.client.mqtt.mqtt5.exceptions.Mqtt5SubAckException;
import com.hivemq.client.mqtt.mqtt5.message.connect.connack.Mqtt5ConnAckReasonCode;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import com.hivemq.client.mqtt.mqtt5.message.publish.puback.Mqtt5PubAckReasonCode;
import com.hivemq.client.mqtt.mqtt5.message.subscribe.Mqtt5Subscribe;
import com.hivemq.client.mqtt.mqtt5.message.subscribe.suback.Mqtt5SubAckReasonCode;
import com.hivemq.embedded.EmbeddedHiveMQ;
import com.hivemq.extension.sdk.api.client.ClientContext;
import com.hivemq.extension.sdk.api.client.parameter.ClientInformation;
import com.hivemq.extension.sdk.api.client.parameter.InitializerInput;
import com.hivemq.extension.sdk.api.events.EventRegistry;
import com.hivemq.extension.sdk.api.interceptor.unsuback.UnsubackOutboundInterceptor;
import com.hivemq.extension.sdk.api.interceptor.unsuback.parameter.UnsubackOutboundInput;
import com.hivemq.extension.sdk.api.interceptor.unsuback.parameter.UnsubackOutboundOutput;
import com.hivemq.extension.sdk.api.packets.unsuback.UnsubackPacket;
import com.hivemq.extension.sdk.api.packets.unsuback.UnsubackReasonCode;
import com.hivemq.extension.sdk.api.services.Services;
import com.hivemq.extension.sdk.api.services.auth.SecurityRegistry;
import com.hivemq.extension.sdk.api.services.intializer.ClientInitializer;
import com.hivemq.extension.sdk.api.services.intializer.InitializerRegistry;
import com.hivemq.extension.sdk.api.services.session.ClientService;
import com.hivemq.extension.sdk.api.services.subscription.SubscriptionStore;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.IdentifiableAuthorizationDecision;
import io.sapl.api.pdp.MultiAuthorizationSubscription;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.interpreter.InitializationException;
import io.sapl.mqtt.pep.cache.MqttClientState;
import io.sapl.mqtt.pep.config.SaplMqttExtensionConfig;
import io.sapl.mqtt.pep.util.SaplSubscriptionUtility;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

class MqttActionUnitIT {

    @TempDir
    Path dataFolder;
    @TempDir
    Path configFolder;
    @TempDir
    Path extensionFolder;

    private static final String EXTENSIONS_CONFIG_PATH_SHORT_TIMEOUT = "src/test/resources/config/timeout/saplAuthzSubscription";
    private static final String SUBSCRIPTION_CLIENT_ID               = "subscriptionClient";
    private static final String PUBLISH_CLIENT_ID                    = "publishClient";
    private static final String TOPIC                                = "testTopic";

    @Test
    void when_connectionPermitted_then_establishConnection() throws InitializationException {
        // GIVEN
        String subscriptionClientMqttConnectionSaplSubscriptionId = SaplSubscriptionUtility
                .buildSubscriptionId(SUBSCRIPTION_CLIENT_ID, MqttPep.CONNECT_AUTHZ_ACTION);

        AtomicBoolean                           isCanceled                               = new AtomicBoolean(false);
        Flux<IdentifiableAuthorizationDecision> subscriptionClientConnectionDecisionFlux = Flux
                .<IdentifiableAuthorizationDecision>never()
                .mergeWith(Flux.just(new IdentifiableAuthorizationDecision(
                        subscriptionClientMqttConnectionSaplSubscriptionId, AuthorizationDecision.PERMIT)))
                .doOnCancel(() -> isCanceled.set(true));

        PolicyDecisionPoint pdpMock = mock(PolicyDecisionPoint.class);
        when(pdpMock.decide(any(MultiAuthorizationSubscription.class)))
                .thenReturn(subscriptionClientConnectionDecisionFlux);

        // WHEN
        EmbeddedHiveMQ      mqttBroker      = buildAndStartBroker(dataFolder, configFolder, extensionFolder, pdpMock);
        Mqtt5BlockingClient subscribeClient = buildAndStartMqttClient(SUBSCRIPTION_CLIENT_ID);

        // THEN
        assertTrue(subscribeClient.getState().isConnected());
        verify(pdpMock, times(1)).decide(any(MultiAuthorizationSubscription.class));
        assertFalse(isCanceled.get());

        // FINALLY
        stopBroker(mqttBroker);
    }

    @Test
    void when_connectionDenied_then_prohibitConnection() {
        // GIVEN
        String subscriptionClientMqttConnectionSaplSubscriptionId = SaplSubscriptionUtility
                .buildSubscriptionId(SUBSCRIPTION_CLIENT_ID, MqttPep.CONNECT_AUTHZ_ACTION);

        AtomicBoolean                           isCanceled                               = new AtomicBoolean(false);
        Flux<IdentifiableAuthorizationDecision> subscriptionClientConnectionDecisionFlux = Flux
                .<IdentifiableAuthorizationDecision>never()
                .mergeWith(Flux.just(new IdentifiableAuthorizationDecision(
                        subscriptionClientMqttConnectionSaplSubscriptionId, AuthorizationDecision.DENY)))
                .doOnCancel(() -> isCanceled.set(true));

        PolicyDecisionPoint pdpMock = mock(PolicyDecisionPoint.class);
        when(pdpMock.decide(any(MultiAuthorizationSubscription.class)))
                .thenReturn(subscriptionClientConnectionDecisionFlux);

        // WHEN
        EmbeddedHiveMQ      mqttBroker         = buildAndStartBroker(dataFolder, configFolder, extensionFolder,
                pdpMock);
        Mqtt5BlockingClient blockingMqttClient = Mqtt5Client.builder().identifier(SUBSCRIPTION_CLIENT_ID)
                .serverHost(BROKER_HOST).serverPort(BROKER_PORT).buildBlocking();

        // THEN
        Mqtt5ConnAckException connAckException = assertThrowsExactly(Mqtt5ConnAckException.class,
                blockingMqttClient::connect);
        assertEquals(Mqtt5ConnAckReasonCode.NOT_AUTHORIZED, connAckException.getMqttMessage().getReasonCode());
        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> assertTrue(isCanceled.get()));

        verify(pdpMock, times(1)).decide(any(MultiAuthorizationSubscription.class));

        // FINALLY
        stopBroker(mqttBroker);
    }

    @Test
    void when_publishAndSubscribeOnTwoClientsForTopicPermitted_then_shareDecisionFluxPerClient()
            throws InitializationException, InterruptedException {
        // GIVEN
        String subscriptionClientMqttConnectionSaplSubscriptionId   = SaplSubscriptionUtility
                .buildSubscriptionId(SUBSCRIPTION_CLIENT_ID, MqttPep.CONNECT_AUTHZ_ACTION);
        String publishClientId                                      = "publishClient";
        String publishClientMqttConnectionSaplSubscriptionId        = SaplSubscriptionUtility
                .buildSubscriptionId(publishClientId, MqttPep.CONNECT_AUTHZ_ACTION);
        String subscriptionClientMqttSubscriptionSaplSubscriptionId = SaplSubscriptionUtility
                .buildSubscriptionId(SUBSCRIPTION_CLIENT_ID, MqttPep.SUBSCRIBE_AUTHZ_ACTION, TOPIC);
        String publishClientMqttPublishSaplSubscriptionId           = SaplSubscriptionUtility
                .buildSubscriptionId(publishClientId, MqttPep.PUBLISH_AUTHZ_ACTION, TOPIC);

        AtomicBoolean                           isCanceledSubscriptionClientMqttConnectionFlux = new AtomicBoolean(
                false);
        Flux<IdentifiableAuthorizationDecision> subscriptionClientMqttConnectionDecisionFlux   = Flux
                .<IdentifiableAuthorizationDecision>never()
                .mergeWith(Flux.just(new IdentifiableAuthorizationDecision(
                        subscriptionClientMqttConnectionSaplSubscriptionId, AuthorizationDecision.PERMIT)))
                .doOnCancel(() -> isCanceledSubscriptionClientMqttConnectionFlux.set(true));
        AtomicBoolean                           isCanceledPublishClientMqttConnectionFlux      = new AtomicBoolean(
                false);
        Flux<IdentifiableAuthorizationDecision> publishClientMqttConnectionDecisionFlux        = Flux
                .<IdentifiableAuthorizationDecision>never()
                .mergeWith(Flux.just(new IdentifiableAuthorizationDecision(
                        publishClientMqttConnectionSaplSubscriptionId, AuthorizationDecision.PERMIT)))
                .doOnCancel(() -> isCanceledPublishClientMqttConnectionFlux.set(true));

        AtomicBoolean                           isCanceledSubscriptionClientMqttSubscription   = new AtomicBoolean(
                false);
        Flux<IdentifiableAuthorizationDecision> subscriptionClientMqttSubscriptionDecisionFlux = Flux
                .<IdentifiableAuthorizationDecision>never()
                .mergeWith(Flux.just(
                        new IdentifiableAuthorizationDecision(subscriptionClientMqttConnectionSaplSubscriptionId,
                                AuthorizationDecision.PERMIT),
                        new IdentifiableAuthorizationDecision(subscriptionClientMqttSubscriptionSaplSubscriptionId,
                                AuthorizationDecision.PERMIT)))
                .doOnCancel(() -> isCanceledSubscriptionClientMqttSubscription.set(true));
        AtomicBoolean                           isCanceledPublishClientMqttPublish             = new AtomicBoolean(
                false);
        Flux<IdentifiableAuthorizationDecision> publishClientMqttPublishDecisionFlux           = Flux
                .<IdentifiableAuthorizationDecision>never()
                .mergeWith(Flux.just(
                        new IdentifiableAuthorizationDecision(publishClientMqttConnectionSaplSubscriptionId,
                                AuthorizationDecision.PERMIT),
                        new IdentifiableAuthorizationDecision(publishClientMqttPublishSaplSubscriptionId,
                                AuthorizationDecision.PERMIT)))
                .doOnCancel(() -> isCanceledPublishClientMqttPublish.set(true));

        PolicyDecisionPoint pdpMock = mock(PolicyDecisionPoint.class);
        when(pdpMock.decide(any(MultiAuthorizationSubscription.class)))
                .thenReturn(subscriptionClientMqttConnectionDecisionFlux)
                .thenReturn(publishClientMqttConnectionDecisionFlux)
                .thenReturn(subscriptionClientMqttSubscriptionDecisionFlux)
                .thenReturn(publishClientMqttPublishDecisionFlux);

        Mqtt5Subscribe subscribeMessage = buildMqttSubscribeMessage(TOPIC);
        Mqtt5Publish   publishMessage   = buildMqttPublishMessage(TOPIC, false);

        // WHEN
        EmbeddedHiveMQ      mqttBroker      = buildAndStartBroker(dataFolder, configFolder, extensionFolder, pdpMock);
        Mqtt5BlockingClient subscribeClient = buildAndStartMqttClient(SUBSCRIPTION_CLIENT_ID);
        Mqtt5BlockingClient publishClient   = buildAndStartMqttClient(publishClientId);
        subscribeClient.subscribe(subscribeMessage);
        publishClient.publish(publishMessage);

        // THEN
        assertTrue(subscribeClient.publishes(MqttGlobalPublishFilter.SUBSCRIBED).receive(2000, TimeUnit.MILLISECONDS)
                .isPresent());

        verify(pdpMock, times(4)).decide(any(MultiAuthorizationSubscription.class));

        assertTrue(isCanceledSubscriptionClientMqttConnectionFlux.get());
        assertTrue(isCanceledPublishClientMqttConnectionFlux.get());
        assertFalse(isCanceledSubscriptionClientMqttSubscription.get());
        assertFalse(isCanceledPublishClientMqttPublish.get());

        // FINALLY
        stopBroker(mqttBroker);
    }

    @Test
    void when_decisionIsIndeterminateOnConnectionStart_then_prohibitConnection() {
        // GIVEN
        AtomicBoolean                           isCanceled                               = new AtomicBoolean(false);
        Flux<IdentifiableAuthorizationDecision> subscriptionClientConnectionDecisionFlux = Flux
                .<IdentifiableAuthorizationDecision>never()
                .mergeWith(Flux.just(IdentifiableAuthorizationDecision.INDETERMINATE))
                .doOnCancel(() -> isCanceled.set(true));

        PolicyDecisionPoint pdpMock = mock(PolicyDecisionPoint.class);
        when(pdpMock.decide(any(MultiAuthorizationSubscription.class)))
                .thenReturn(subscriptionClientConnectionDecisionFlux);

        // WHEN
        EmbeddedHiveMQ      mqttBroker         = buildAndStartBroker(dataFolder, configFolder, extensionFolder,
                pdpMock);
        Mqtt5BlockingClient blockingMqttClient = Mqtt5Client.builder().identifier(SUBSCRIPTION_CLIENT_ID)
                .serverHost(BROKER_HOST).serverPort(BROKER_PORT).buildBlocking();

        // THEN
        Mqtt5ConnAckException connAckException = assertThrowsExactly(Mqtt5ConnAckException.class,
                blockingMqttClient::connect);
        assertEquals(Mqtt5ConnAckReasonCode.NOT_AUTHORIZED, connAckException.getMqttMessage().getReasonCode());

        verify(pdpMock, times(1)).decide(any(MultiAuthorizationSubscription.class));
        await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> assertTrue(isCanceled.get()));

        // FINALLY
        stopBroker(mqttBroker);
    }

    @Test
    void when_decisionIsIndeterminateLaterOn_then_cancelConnection() throws InitializationException {
        // GIVEN
        String subscriptionClientMqttConnectionSaplSubscriptionId = SaplSubscriptionUtility
                .buildSubscriptionId(SUBSCRIPTION_CLIENT_ID, MqttPep.CONNECT_AUTHZ_ACTION);

        AtomicBoolean                                 isCanceled                               = new AtomicBoolean(
                false);
        Sinks.Many<IdentifiableAuthorizationDecision> emitterIdentAuthzDecision                = Sinks.many()
                .multicast().directAllOrNothing();
        Flux<IdentifiableAuthorizationDecision>       subscriptionClientConnectionDecisionFlux = Flux
                .<IdentifiableAuthorizationDecision>never()
                .mergeWith(Flux.just(new IdentifiableAuthorizationDecision(
                        subscriptionClientMqttConnectionSaplSubscriptionId, AuthorizationDecision.PERMIT)))
                .mergeWith(emitterIdentAuthzDecision.asFlux()).doOnCancel(() -> isCanceled.set(true));

        PolicyDecisionPoint pdpMock = mock(PolicyDecisionPoint.class);
        when(pdpMock.decide(any(MultiAuthorizationSubscription.class)))
                .thenReturn(subscriptionClientConnectionDecisionFlux);

        // WHEN
        EmbeddedHiveMQ      mqttBroker      = buildAndStartBroker(dataFolder, configFolder, extensionFolder, pdpMock);
        Mqtt5BlockingClient subscribeClient = buildAndStartMqttClient(SUBSCRIPTION_CLIENT_ID);
        assertTrue(subscribeClient.getState().isConnected());
        emitterIdentAuthzDecision.tryEmitNext(IdentifiableAuthorizationDecision.INDETERMINATE);

        // THEN
        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> assertFalse(subscribeClient.getState().isConnected()));
        verify(pdpMock, times(1)).decide(any(MultiAuthorizationSubscription.class));
        assertTrue(isCanceled.get());

        // FINALLY
        stopBroker(mqttBroker);
    }

    @Test
    void when_subscriptionToTopicGetsDeniedLaterOn_then_cancelSubscription() throws InitializationException {
        // GIVEN
        String subscriptionClientConnectionSaplSubscriptionId       = SaplSubscriptionUtility
                .buildSubscriptionId(SUBSCRIPTION_CLIENT_ID, MqttPep.CONNECT_AUTHZ_ACTION);
        String publishClientConnectionSaplSubscriptionId            = SaplSubscriptionUtility
                .buildSubscriptionId(PUBLISH_CLIENT_ID, MqttPep.CONNECT_AUTHZ_ACTION);
        String subscriptionClientMqttSubscriptionSaplSubscriptionId = SaplSubscriptionUtility
                .buildSubscriptionId(SUBSCRIPTION_CLIENT_ID, MqttPep.SUBSCRIBE_AUTHZ_ACTION, TOPIC);
        String publishClientMqttPublishSaplSubscriptionId           = SaplSubscriptionUtility
                .buildSubscriptionId(PUBLISH_CLIENT_ID, MqttPep.PUBLISH_AUTHZ_ACTION, TOPIC);

        Flux<IdentifiableAuthorizationDecision>       subscriptionClientConnectionDecisionFlux       = Flux
                .<IdentifiableAuthorizationDecision>never().mergeWith(Flux.just(new IdentifiableAuthorizationDecision(
                        subscriptionClientConnectionSaplSubscriptionId, AuthorizationDecision.PERMIT)));
        Flux<IdentifiableAuthorizationDecision>       publishClientConnectionDecisionFlux            = Flux
                .<IdentifiableAuthorizationDecision>never()
                .mergeWith(Flux.just(new IdentifiableAuthorizationDecision(publishClientConnectionSaplSubscriptionId,
                        AuthorizationDecision.PERMIT)));
        Sinks.Many<IdentifiableAuthorizationDecision> emitterIdentAuthzDecision                      = Sinks.many()
                .multicast().directAllOrNothing();
        Flux<IdentifiableAuthorizationDecision>       subscriptionClientMqttSubscriptionDecisionFlux = Flux
                .<IdentifiableAuthorizationDecision>never()
                .mergeWith(Flux.just(new IdentifiableAuthorizationDecision(
                        subscriptionClientMqttSubscriptionSaplSubscriptionId, AuthorizationDecision.PERMIT)))
                .mergeWith(emitterIdentAuthzDecision.asFlux());
        Flux<IdentifiableAuthorizationDecision>       publishClientMqttPublishDecisionFlux           = Flux
                .<IdentifiableAuthorizationDecision>never()
                .mergeWith(Flux.just(new IdentifiableAuthorizationDecision(publishClientMqttPublishSaplSubscriptionId,
                        AuthorizationDecision.PERMIT)));

        PolicyDecisionPoint pdpMock = mock(PolicyDecisionPoint.class);
        when(pdpMock.decide(any(MultiAuthorizationSubscription.class)))
                .thenReturn(subscriptionClientConnectionDecisionFlux).thenReturn(publishClientConnectionDecisionFlux)
                .thenReturn(subscriptionClientMqttSubscriptionDecisionFlux)
                .thenReturn(publishClientMqttPublishDecisionFlux);

        Mqtt5Subscribe subscribeMessage = buildMqttSubscribeMessage(TOPIC);
        Mqtt5Publish   publishMessage   = buildMqttPublishMessage(TOPIC, 1, false);

        // WHEN
        EmbeddedHiveMQ      mqttBroker      = buildAndStartBroker(dataFolder, configFolder, extensionFolder, pdpMock);
        Mqtt5BlockingClient subscribeClient = buildAndStartMqttClient(SUBSCRIPTION_CLIENT_ID);
        Mqtt5BlockingClient publishClient   = buildAndStartMqttClient(PUBLISH_CLIENT_ID);
        assertTrue(subscribeClient.getState().isConnected());
        assertTrue(publishClient.getState().isConnected());

        subscribeClient.subscribe(subscribeMessage);
        emitterIdentAuthzDecision.tryEmitNext(new IdentifiableAuthorizationDecision(
                subscriptionClientMqttSubscriptionSaplSubscriptionId, AuthorizationDecision.DENY));
        // THEN
        await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> {
            publishClient.publish(publishMessage);
            assertFalse(subscribeClient.publishes(MqttGlobalPublishFilter.ALL).receiveNow().isPresent());
        });
        verify(pdpMock, times(4)).decide(any(MultiAuthorizationSubscription.class));

        // FINALLY
        stopBroker(mqttBroker);
    }

    @Test
    void when_multipleClientsSubscribingSameTopicAndOneSubscriptionGetsDeniedLaterOn_then_doNotCancelOtherSubscription()
            throws InitializationException, InterruptedException {
        // GIVEN
        String secondMqttClientId = "SECOND_MQTT_CLIENT_SUBSCRIBE";

        String secondSubscriptionClientConnectionSaplSubscriptionId       = SaplSubscriptionUtility
                .buildSubscriptionId(secondMqttClientId, MqttPep.CONNECT_AUTHZ_ACTION);
        String subscriptionClientConnectionSaplSubscriptionId             = SaplSubscriptionUtility
                .buildSubscriptionId(SUBSCRIPTION_CLIENT_ID, MqttPep.CONNECT_AUTHZ_ACTION);
        String publishClientConnectionSaplSubscriptionId                  = SaplSubscriptionUtility
                .buildSubscriptionId(PUBLISH_CLIENT_ID, MqttPep.CONNECT_AUTHZ_ACTION);
        String secondSubscriptionClientMqttSubscriptionSaplSubscriptionId = SaplSubscriptionUtility
                .buildSubscriptionId(secondMqttClientId, MqttPep.SUBSCRIBE_AUTHZ_ACTION, TOPIC);
        String subscriptionClientMqttSubscriptionSaplSubscriptionId       = SaplSubscriptionUtility
                .buildSubscriptionId(SUBSCRIPTION_CLIENT_ID, MqttPep.SUBSCRIBE_AUTHZ_ACTION, TOPIC);
        String publishClientMqttPublishSaplSubscriptionId                 = SaplSubscriptionUtility
                .buildSubscriptionId(PUBLISH_CLIENT_ID, MqttPep.PUBLISH_AUTHZ_ACTION, TOPIC);

        Flux<IdentifiableAuthorizationDecision>       secondSubscriptionClientConnectionDecisionFlux       = Flux
                .<IdentifiableAuthorizationDecision>never().mergeWith(Flux.just(new IdentifiableAuthorizationDecision(
                        secondSubscriptionClientConnectionSaplSubscriptionId, AuthorizationDecision.PERMIT)));
        Flux<IdentifiableAuthorizationDecision>       subscriptionClientConnectionDecisionFlux             = Flux
                .<IdentifiableAuthorizationDecision>never().mergeWith(Flux.just(new IdentifiableAuthorizationDecision(
                        subscriptionClientConnectionSaplSubscriptionId, AuthorizationDecision.PERMIT)));
        Flux<IdentifiableAuthorizationDecision>       publishClientConnectionDecisionFlux                  = Flux
                .<IdentifiableAuthorizationDecision>never()
                .mergeWith(Flux.just(new IdentifiableAuthorizationDecision(publishClientConnectionSaplSubscriptionId,
                        AuthorizationDecision.PERMIT)));
        Flux<IdentifiableAuthorizationDecision>       secondSubscriptionClientMqttSubscriptionDecisionFlux = Flux
                .<IdentifiableAuthorizationDecision>never().mergeWith(Flux.just(new IdentifiableAuthorizationDecision(
                        secondSubscriptionClientMqttSubscriptionSaplSubscriptionId, AuthorizationDecision.PERMIT)));
        Sinks.Many<IdentifiableAuthorizationDecision> emitterIdentAuthzDecision                            = Sinks
                .many().multicast().directAllOrNothing();
        Flux<IdentifiableAuthorizationDecision>       subscriptionClientMqttSubscriptionDecisionFlux       = Flux
                .<IdentifiableAuthorizationDecision>never()
                .mergeWith(Flux.just(new IdentifiableAuthorizationDecision(
                        subscriptionClientMqttSubscriptionSaplSubscriptionId, AuthorizationDecision.PERMIT)))
                .mergeWith(emitterIdentAuthzDecision.asFlux());
        Flux<IdentifiableAuthorizationDecision>       publishClientMqttPublishDecisionFlux                 = Flux
                .<IdentifiableAuthorizationDecision>never()
                .mergeWith(Flux.just(new IdentifiableAuthorizationDecision(publishClientMqttPublishSaplSubscriptionId,
                        AuthorizationDecision.PERMIT)));

        PolicyDecisionPoint pdpMock = mock(PolicyDecisionPoint.class);
        when(pdpMock.decide(any(MultiAuthorizationSubscription.class)))
                .thenReturn(secondSubscriptionClientConnectionDecisionFlux)
                .thenReturn(subscriptionClientConnectionDecisionFlux).thenReturn(publishClientConnectionDecisionFlux)
                .thenReturn(secondSubscriptionClientMqttSubscriptionDecisionFlux)
                .thenReturn(subscriptionClientMqttSubscriptionDecisionFlux)
                .thenReturn(publishClientMqttPublishDecisionFlux);

        Mqtt5Subscribe subscribeMessage = buildMqttSubscribeMessage(TOPIC);
        Mqtt5Publish   publishMessage   = buildMqttPublishMessage(TOPIC, 0, false);

        // WHEN
        EmbeddedHiveMQ      mqttBroker                = buildAndStartBroker(dataFolder, configFolder, extensionFolder,
                pdpMock);
        Mqtt5BlockingClient secondMqttClientSubscribe = buildAndStartMqttClient(secondMqttClientId);
        Mqtt5BlockingClient subscribeClient           = buildAndStartMqttClient(SUBSCRIPTION_CLIENT_ID);
        Mqtt5BlockingClient publishClient             = buildAndStartMqttClient(PUBLISH_CLIENT_ID);

        secondMqttClientSubscribe.subscribe(subscribeMessage);
        subscribeClient.subscribe(subscribeMessage);
        emitterIdentAuthzDecision.tryEmitNext(new IdentifiableAuthorizationDecision(
                subscriptionClientMqttSubscriptionSaplSubscriptionId, AuthorizationDecision.DENY));

        // THEN
        await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> {
            publishClient.publish(publishMessage);
            Optional<Mqtt5Publish> receivedMessageSecond = subscribeClient.publishes(MqttGlobalPublishFilter.SUBSCRIBED)
                    .receive(1000, TimeUnit.MILLISECONDS);
            assertTrue(receivedMessageSecond.isEmpty());
        });

        publishClient.publish(publishMessage);
        var receivedMessage = secondMqttClientSubscribe.publishes(MqttGlobalPublishFilter.SUBSCRIBED).receive();
        assertEquals(PUBLISH_MESSAGE_PAYLOAD, new String(receivedMessage.getPayloadAsBytes(), StandardCharsets.UTF_8));

        verify(pdpMock, times(6)).decide(any(MultiAuthorizationSubscription.class));

        // FINALLY
        stopBroker(mqttBroker);
    }

    @Test
    void when_decisionIsNotApplicableOnConnectionStart_then_prohibitConnection() {
        // GIVEN
        String subscriptionClientMqttConnectionSaplSubscriptionId = SaplSubscriptionUtility
                .buildSubscriptionId(SUBSCRIPTION_CLIENT_ID, MqttPep.CONNECT_AUTHZ_ACTION);

        AtomicBoolean                           isCanceled                               = new AtomicBoolean(false);
        Flux<IdentifiableAuthorizationDecision> subscriptionClientConnectionDecisionFlux = Flux
                .<IdentifiableAuthorizationDecision>never()
                .mergeWith(Flux.just(new IdentifiableAuthorizationDecision(
                        subscriptionClientMqttConnectionSaplSubscriptionId, AuthorizationDecision.NOT_APPLICABLE)))
                .doOnCancel(() -> isCanceled.set(true));

        PolicyDecisionPoint pdpMock = mock(PolicyDecisionPoint.class);
        when(pdpMock.decide(any(MultiAuthorizationSubscription.class)))
                .thenReturn(subscriptionClientConnectionDecisionFlux);

        // WHEN
        EmbeddedHiveMQ      mqttBroker         = buildAndStartBroker(dataFolder, configFolder, extensionFolder,
                pdpMock);
        Mqtt5BlockingClient blockingMqttClient = Mqtt5Client.builder().identifier(SUBSCRIPTION_CLIENT_ID)
                .serverHost(BROKER_HOST).serverPort(BROKER_PORT).buildBlocking();

        // THEN
        Mqtt5ConnAckException connAckException = assertThrowsExactly(Mqtt5ConnAckException.class,
                blockingMqttClient::connect);
        assertEquals(Mqtt5ConnAckReasonCode.NOT_AUTHORIZED, connAckException.getMqttMessage().getReasonCode());

        verify(pdpMock, times(1)).decide(any(MultiAuthorizationSubscription.class));
        await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> assertTrue(isCanceled.get()));

        // FINALLY
        stopBroker(mqttBroker);
    }

    @Test
    void when_decisionIsNotApplicableOnConnectionStartAndNoIdentAuthzDecisionId_then_prohibitConnection() {
        // GIVEN
        AtomicBoolean                           isCanceled                               = new AtomicBoolean(false);
        Flux<IdentifiableAuthorizationDecision> subscriptionClientConnectionDecisionFlux = Flux
                .<IdentifiableAuthorizationDecision>never()
                .mergeWith(Flux.just(new IdentifiableAuthorizationDecision(null, AuthorizationDecision.NOT_APPLICABLE)))
                .doOnCancel(() -> isCanceled.set(true));

        PolicyDecisionPoint pdpMock = mock(PolicyDecisionPoint.class);
        when(pdpMock.decide(any(MultiAuthorizationSubscription.class)))
                .thenReturn(subscriptionClientConnectionDecisionFlux);

        // WHEN
        EmbeddedHiveMQ      mqttBroker         = buildAndStartBroker(dataFolder, configFolder, extensionFolder,
                pdpMock);
        Mqtt5BlockingClient blockingMqttClient = Mqtt5Client.builder().identifier(SUBSCRIPTION_CLIENT_ID)
                .serverHost(BROKER_HOST).serverPort(BROKER_PORT).buildBlocking();

        // THEN
        Mqtt5ConnAckException connAckException = assertThrowsExactly(Mqtt5ConnAckException.class,
                blockingMqttClient::connect);
        assertEquals(Mqtt5ConnAckReasonCode.NOT_AUTHORIZED, connAckException.getMqttMessage().getReasonCode());

        verify(pdpMock, times(1)).decide(any(MultiAuthorizationSubscription.class));
        await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> assertTrue(isCanceled.get()));

        // FINALLY
        stopBroker(mqttBroker);
    }

    @Test
    void when_decisionIsNotApplicableLaterOn_then_cancelConnection() throws InitializationException {
        // GIVEN
        String subscriptionClientMqttConnectionSaplSubscriptionId = SaplSubscriptionUtility
                .buildSubscriptionId(SUBSCRIPTION_CLIENT_ID, MqttPep.CONNECT_AUTHZ_ACTION);

        AtomicBoolean                                 isCanceled                               = new AtomicBoolean(
                false);
        Sinks.Many<IdentifiableAuthorizationDecision> emitterIdentAuthzDecision                = Sinks.many()
                .multicast().directAllOrNothing();
        Flux<IdentifiableAuthorizationDecision>       subscriptionClientConnectionDecisionFlux = Flux
                .<IdentifiableAuthorizationDecision>never()
                .mergeWith(Flux.just(new IdentifiableAuthorizationDecision(
                        subscriptionClientMqttConnectionSaplSubscriptionId, AuthorizationDecision.PERMIT)))
                .mergeWith(emitterIdentAuthzDecision.asFlux()).doOnCancel(() -> isCanceled.set(true));

        PolicyDecisionPoint pdpMock = mock(PolicyDecisionPoint.class);
        when(pdpMock.decide(any(MultiAuthorizationSubscription.class)))
                .thenReturn(subscriptionClientConnectionDecisionFlux);

        // WHEN
        EmbeddedHiveMQ      mqttBroker      = buildAndStartBroker(dataFolder, configFolder, extensionFolder, pdpMock);
        Mqtt5BlockingClient subscribeClient = buildAndStartMqttClient(SUBSCRIPTION_CLIENT_ID);
        assertTrue(subscribeClient.getState().isConnected());
        emitterIdentAuthzDecision.tryEmitNext(new IdentifiableAuthorizationDecision(
                subscriptionClientMqttConnectionSaplSubscriptionId, AuthorizationDecision.NOT_APPLICABLE));

        // THEN
        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> assertFalse(subscribeClient.getState().isConnected()));
        verify(pdpMock, times(1)).decide(any(MultiAuthorizationSubscription.class));
        assertTrue(isCanceled.get());

        // FINALLY
        stopBroker(mqttBroker);
    }

    @Test
    void when_clientSendUnsubscribeMessageButUnsubscribeFailed_then_doNotCancelSubscriptionEnforcement() {
        // GIVEN
        int                                        packetId          = 251;
        String                                     clientId          = "mockedClientId";
        ConcurrentHashMap<String, MqttClientState> mqttClientCache   = new ConcurrentHashMap<>();
        MqttClientState                            mqttClientState   = new MqttClientState(clientId);
        List<String>                               unsubscribeTopics = List.of(TOPIC);
        mqttClientState.addUnsubscribeMessageTopicsToMap(packetId, unsubscribeTopics);
        mqttClientCache.put(clientId, mqttClientState);
        SaplMqttExtensionConfig saplMqttExtensionConfigMock = mock(SaplMqttExtensionConfig.class);
        EventRegistry           eventRegistryMock           = mock(EventRegistry.class);
        SecurityRegistry        securityRegistryMock        = mock(SecurityRegistry.class);
        SubscriptionStore       subscriptionStoreMock       = mock(SubscriptionStore.class);
        ClientService           clientServiceMock           = mock(ClientService.class);

        // mock pdp
        PolicyDecisionPoint pdpMock = mock(PolicyDecisionPoint.class);
        when(pdpMock.decide(any(MultiAuthorizationSubscription.class))).thenReturn(Flux.never());

        // mock unSubAck interceptor
        InitializerRegistry    initializerRegistryMock    = mock(InitializerRegistry.class);
        InitializerInput       initializerInputMock       = mock(InitializerInput.class);
        ClientContext          clientContextMock          = mock(ClientContext.class);
        UnsubackOutboundInput  unsubackOutboundInputMock  = mock(UnsubackOutboundInput.class);
        UnsubackOutboundOutput unsubackOutboundOutputMock = mock(UnsubackOutboundOutput.class);
        ClientInformation      clientInformationMock      = mock(ClientInformation.class);
        when(unsubackOutboundInputMock.getClientInformation()).thenReturn(clientInformationMock);
        when(clientInformationMock.getClientId()).thenReturn(clientId);
        UnsubackPacket unsubackPacketMock = mock(UnsubackPacket.class);
        when(unsubackPacketMock.getPacketIdentifier()).thenReturn(packetId);
        when(unsubackOutboundInputMock.getUnsubackPacket()).thenReturn(unsubackPacketMock);
        List<UnsubackReasonCode> unsubackReasonCodeList = List.of(UnsubackReasonCode.UNSPECIFIED_ERROR);
        when(unsubackPacketMock.getReasonCodes()).thenReturn(unsubackReasonCodeList);
        doAnswer(answer -> {
            UnsubackOutboundInterceptor unsubackOutboundInterceptor = answer.getArgument(0);
            unsubackOutboundInterceptor.onOutboundUnsuback(unsubackOutboundInputMock, unsubackOutboundOutputMock);
            return null;
        }).when(clientContextMock).addUnsubackOutboundInterceptor(any(UnsubackOutboundInterceptor.class));
        doAnswer(answer -> {
            ClientInitializer clientInitializer = answer.getArgument(0);
            clientInitializer.initialize(initializerInputMock, clientContextMock);
            return null;
        }).when(initializerRegistryMock).setClientInitializer(any(ClientInitializer.class));

        // mock pep initialisation
        try (MockedStatic<Services> servicesMockedStatic = mockStatic(Services.class);
                MockedStatic<SaplSubscriptionUtility> saplSubscriptionUtilityMockedStatic = mockStatic(
                        SaplSubscriptionUtility.class)) {
            servicesMockedStatic.when(Services::eventRegistry).thenReturn(eventRegistryMock);
            servicesMockedStatic.when(Services::securityRegistry).thenReturn(securityRegistryMock);
            servicesMockedStatic.when(Services::subscriptionStore).thenReturn(subscriptionStoreMock);
            servicesMockedStatic.when(Services::initializerRegistry).thenReturn(initializerRegistryMock);
            servicesMockedStatic.when(Services::clientService).thenReturn(clientServiceMock);

            // WHEN
            new MqttPep(pdpMock, saplMqttExtensionConfigMock, mqttClientCache).startEnforcement();

            // THEN
            saplSubscriptionUtilityMockedStatic.verifyNoInteractions();
        }
    }

    @ParameterizedTest
    @ValueSource(ints = { 1, 2 })
    void when_clientWasDisconnectedWhileStartingMqttSubscriptionEnforcementBeforeEstablishedDecisionFlux_then_stopEnforcement(
            int mqttClientCacheCallThreshold) throws InitializationException {
        // GIVEN
        String subscriptionClientMqttConnectionSaplSubscriptionId   = SaplSubscriptionUtility
                .buildSubscriptionId(SUBSCRIPTION_CLIENT_ID, MqttPep.CONNECT_AUTHZ_ACTION);
        String subscriptionClientMqttSubscriptionSaplSubscriptionId = SaplSubscriptionUtility
                .buildSubscriptionId(SUBSCRIPTION_CLIENT_ID, MqttPep.SUBSCRIBE_AUTHZ_ACTION, TOPIC);

        Flux<IdentifiableAuthorizationDecision> subscriptionClientMqttConnectionDecisionFlux   = Flux
                .<IdentifiableAuthorizationDecision>never().mergeWith(Flux.just(new IdentifiableAuthorizationDecision(
                        subscriptionClientMqttConnectionSaplSubscriptionId, AuthorizationDecision.PERMIT)));
        AtomicBoolean                           isSubscribed                                   = new AtomicBoolean(
                false);
        Flux<IdentifiableAuthorizationDecision> subscriptionClientMqttSubscriptionDecisionFlux = Flux
                .<IdentifiableAuthorizationDecision>never()
                .mergeWith(Flux.just(new IdentifiableAuthorizationDecision(
                        subscriptionClientMqttSubscriptionSaplSubscriptionId, AuthorizationDecision.PERMIT)))
                .doOnSubscribe(sub -> isSubscribed.set(true));

        PolicyDecisionPoint pdpMock = mock(PolicyDecisionPoint.class);
        when(pdpMock.decide(any(MultiAuthorizationSubscription.class)))
                .thenReturn(subscriptionClientMqttConnectionDecisionFlux)
                .thenReturn(subscriptionClientMqttSubscriptionDecisionFlux);

        // prepare simulation of client disconnect on start of next mqtt action
        // enforcement
        ConcurrentHashMap<String, MqttClientState> mqttClientCacheSpy            = spy(new ConcurrentHashMap<>());
        AtomicBoolean                              isStopCallRealMqttClientCache = new AtomicBoolean(false);
        AtomicInteger                              counterMqttClientCacheCalled  = new AtomicInteger(0);
        when(mqttClientCacheSpy.get(SUBSCRIPTION_CLIENT_ID)).thenAnswer(answer -> {
            if (!isStopCallRealMqttClientCache.get()) {
                return answer.callRealMethod();
            }
            counterMqttClientCacheCalled.getAndAdd(1);
            if (counterMqttClientCacheCalled.get() >= mqttClientCacheCallThreshold) {
                return null;
            }
            return answer.callRealMethod();
        });

        Mqtt5Subscribe mqttSubscribeMessage = buildMqttSubscribeMessage(TOPIC);

        // WHEN
        EmbeddedHiveMQ      mqttBroker      = buildAndStartBroker(dataFolder, configFolder, extensionFolder, pdpMock,
                EXTENSIONS_CONFIG_PATH_SHORT_TIMEOUT, mqttClientCacheSpy);
        Mqtt5BlockingClient subscribeClient = buildAndStartMqttClient(SUBSCRIPTION_CLIENT_ID);

        isStopCallRealMqttClientCache.set(true); // simulate client disconnect
        Mqtt5SubAckException subAckException = assertThrowsExactly(Mqtt5SubAckException.class,
                () -> subscribeClient.subscribe(mqttSubscribeMessage));

        // THEN
        assertEquals(Mqtt5SubAckReasonCode.NOT_AUTHORIZED, subAckException.getMqttMessage().getReasonCodes().get(0));
        assertFalse(isSubscribed.get());
        verify(pdpMock, times(1)).decide(any(MultiAuthorizationSubscription.class));

        // FINALLY
        stopBroker(mqttBroker);
    }

    @ParameterizedTest
    @ValueSource(ints = { 1, 2 })
    void when_clientWasDisconnectedWhileStartingMqttPublishEnforcementBeforeEstablishedDecisionFlux_then_stopEnforcement(
            int mqttClientCacheCallThreshold) throws InitializationException {
        // GIVEN
        String publishClientMqttConnectionSaplSubscriptionId   = SaplSubscriptionUtility
                .buildSubscriptionId(PUBLISH_CLIENT_ID, MqttPep.CONNECT_AUTHZ_ACTION);
        String publishClientMqttSubscriptionSaplSubscriptionId = SaplSubscriptionUtility
                .buildSubscriptionId(PUBLISH_CLIENT_ID, MqttPep.PUBLISH_AUTHZ_ACTION, TOPIC);

        Flux<IdentifiableAuthorizationDecision> publishClientMqttConnectionDecisionFlux   = Flux
                .<IdentifiableAuthorizationDecision>never().mergeWith(Flux.just(new IdentifiableAuthorizationDecision(
                        publishClientMqttConnectionSaplSubscriptionId, AuthorizationDecision.PERMIT)));
        AtomicBoolean                           isSubscribed                              = new AtomicBoolean(false);
        Flux<IdentifiableAuthorizationDecision> publishClientMqttSubscriptionDecisionFlux = Flux
                .<IdentifiableAuthorizationDecision>never()
                .mergeWith(
                        Flux.just(new IdentifiableAuthorizationDecision(publishClientMqttSubscriptionSaplSubscriptionId,
                                AuthorizationDecision.PERMIT)))
                .doOnSubscribe(sub -> isSubscribed.set(true));

        PolicyDecisionPoint pdpMock = mock(PolicyDecisionPoint.class);
        when(pdpMock.decide(any(MultiAuthorizationSubscription.class)))
                .thenReturn(publishClientMqttConnectionDecisionFlux)
                .thenReturn(publishClientMqttSubscriptionDecisionFlux);

        // prepare simulation of client disconnect on start of next mqtt action
        // enforcement
        ConcurrentHashMap<String, MqttClientState> mqttClientCacheSpy            = spy(new ConcurrentHashMap<>());
        AtomicBoolean                              isStopCallRealMqttClientCache = new AtomicBoolean(false);
        AtomicInteger                              counterMqttClientCacheCalled  = new AtomicInteger(0);
        when(mqttClientCacheSpy.get(PUBLISH_CLIENT_ID)).thenAnswer(answer -> {
            if (!isStopCallRealMqttClientCache.get()) {
                return answer.callRealMethod();
            }
            counterMqttClientCacheCalled.getAndAdd(1);
            if (counterMqttClientCacheCalled.get() >= mqttClientCacheCallThreshold) {
                return null;
            }
            return answer.callRealMethod();
        });

        Mqtt5Publish publishMessageQos1 = buildMqttPublishMessage(TOPIC, 1, false);

        // WHEN
        EmbeddedHiveMQ      mqttBroker    = buildAndStartBroker(dataFolder, configFolder, extensionFolder, pdpMock,
                EXTENSIONS_CONFIG_PATH_SHORT_TIMEOUT, mqttClientCacheSpy);
        Mqtt5BlockingClient publishClient = buildAndStartMqttClient(PUBLISH_CLIENT_ID);

        isStopCallRealMqttClientCache.set(true); // simulate client disconnect
        Mqtt5PubAckException pubAckException = assertThrowsExactly(Mqtt5PubAckException.class,
                () -> publishClient.publish(publishMessageQos1));

        // THEN
        assertEquals(Mqtt5PubAckReasonCode.NOT_AUTHORIZED, pubAckException.getMqttMessage().getReasonCode());
        assertFalse(isSubscribed.get());
        verify(pdpMock, times(1)).decide(any(MultiAuthorizationSubscription.class));

        // FINALLY
        stopBroker(mqttBroker);
    }

    @ParameterizedTest
    @ValueSource(ints = { 1, 2 })
    void when_clientDisconnectsWhileEnforcingTheInitialConnectionAttemptBeforeEstablishedDecisionFlux_then_stopEnforcement(
            int mqttClientCacheCallThreshold) {
        // GIVEN
        String publishClientMqttConnectionSaplSubscriptionId = SaplSubscriptionUtility
                .buildSubscriptionId(PUBLISH_CLIENT_ID, MqttPep.CONNECT_AUTHZ_ACTION);

        Flux<IdentifiableAuthorizationDecision> publishClientMqttConnectionDecisionFlux = Flux
                .<IdentifiableAuthorizationDecision>never().mergeWith(Flux.just(new IdentifiableAuthorizationDecision(
                        publishClientMqttConnectionSaplSubscriptionId, AuthorizationDecision.PERMIT)));

        PolicyDecisionPoint pdpMock = mock(PolicyDecisionPoint.class);
        when(pdpMock.decide(any(MultiAuthorizationSubscription.class)))
                .thenReturn(publishClientMqttConnectionDecisionFlux);

        // prepare simulation of client disconnect on start of next mqtt action
        // enforcement
        ConcurrentHashMap<String, MqttClientState> mqttClientCacheSpy           = spy(new ConcurrentHashMap<>());
        AtomicInteger                              counterMqttClientCacheCalled = new AtomicInteger(0);
        when(mqttClientCacheSpy.get(PUBLISH_CLIENT_ID)).thenAnswer(answer -> {
            counterMqttClientCacheCalled.getAndAdd(1);
            if (counterMqttClientCacheCalled.get() >= mqttClientCacheCallThreshold) {
                return null;
            }
            return answer.callRealMethod();
        });

        Mqtt5BlockingClient blockingMqttClient = Mqtt5Client.builder().identifier(PUBLISH_CLIENT_ID)
                .serverHost(BROKER_HOST).serverPort(BROKER_PORT).buildBlocking();

        // WHEN
        EmbeddedHiveMQ        mqttBroker       = buildAndStartBroker(dataFolder, configFolder, extensionFolder, pdpMock,
                EXTENSIONS_CONFIG_PATH_SHORT_TIMEOUT, mqttClientCacheSpy);
        Mqtt5ConnAckException connAckException = assertThrowsExactly(Mqtt5ConnAckException.class,
                blockingMqttClient::connect);

        // THEN
        assertEquals(Mqtt5ConnAckReasonCode.NOT_AUTHORIZED, connAckException.getMqttMessage().getReasonCode());
        verify(pdpMock, never()).decide(any(MultiAuthorizationSubscription.class));

        // FINALLY
        stopBroker(mqttBroker);
    }

    @Test
    void when_clientDisconnectsWhileEnforcingTheInitialConnectionAttemptWithEstablishedDecisionFlux_then_stopEnforcement() {
        // GIVEN
        String publishClientMqttConnectionSaplSubscriptionId = SaplSubscriptionUtility
                .buildSubscriptionId(PUBLISH_CLIENT_ID, MqttPep.CONNECT_AUTHZ_ACTION);

        AtomicBoolean                           isAuthzDecisionEmitted                  = new AtomicBoolean(false);
        Flux<IdentifiableAuthorizationDecision> publishClientMqttConnectionDecisionFlux = Flux
                .<IdentifiableAuthorizationDecision>never()
                .mergeWith(Flux.just(new IdentifiableAuthorizationDecision(
                        publishClientMqttConnectionSaplSubscriptionId, AuthorizationDecision.PERMIT)))
                .doOnNext(authzDecision -> isAuthzDecisionEmitted.set(true));

        PolicyDecisionPoint pdpMock = mock(PolicyDecisionPoint.class);
        when(pdpMock.decide(any(MultiAuthorizationSubscription.class)))
                .thenReturn(publishClientMqttConnectionDecisionFlux);

        // prepare simulation of client disconnect
        ConcurrentHashMap<String, MqttClientState> mqttClientCacheSpy = spy(new ConcurrentHashMap<>());
        when(mqttClientCacheSpy.get(PUBLISH_CLIENT_ID)).thenAnswer(answer -> {
            if (!isAuthzDecisionEmitted.get()) {
                return answer.callRealMethod();
            }
            return null;
        });

        Mqtt5BlockingClient blockingMqttClient = Mqtt5Client.builder().identifier(PUBLISH_CLIENT_ID)
                .serverHost(BROKER_HOST).serverPort(BROKER_PORT).buildBlocking();

        // WHEN
        EmbeddedHiveMQ        mqttBroker       = buildAndStartBroker(dataFolder, configFolder, extensionFolder, pdpMock,
                EXTENSIONS_CONFIG_PATH_SHORT_TIMEOUT, mqttClientCacheSpy);
        Mqtt5ConnAckException connAckException = assertThrowsExactly(Mqtt5ConnAckException.class,
                blockingMqttClient::connect);

        // THEN
        assertEquals(Mqtt5ConnAckReasonCode.NOT_AUTHORIZED, connAckException.getMqttMessage().getReasonCode());
        verify(pdpMock, times(1)).decide(any(MultiAuthorizationSubscription.class));

        // FINALLY
        stopBroker(mqttBroker);
    }

    @Test
    void when_clientWasDisconnectedWhileStartingMqttPublishEnforcementWithEstablishedDecisionFlux_then_stopEnforcement()
            throws InitializationException {
        // GIVEN
        String publishClientMqttConnectionSaplSubscriptionId   = SaplSubscriptionUtility
                .buildSubscriptionId(PUBLISH_CLIENT_ID, MqttPep.CONNECT_AUTHZ_ACTION);
        String publishClientMqttSubscriptionSaplSubscriptionId = SaplSubscriptionUtility
                .buildSubscriptionId(PUBLISH_CLIENT_ID, MqttPep.PUBLISH_AUTHZ_ACTION, TOPIC);

        Flux<IdentifiableAuthorizationDecision> publishClientMqttConnectionDecisionFlux   = Flux
                .<IdentifiableAuthorizationDecision>never().mergeWith(Flux.just(new IdentifiableAuthorizationDecision(
                        publishClientMqttConnectionSaplSubscriptionId, AuthorizationDecision.PERMIT)));
        AtomicBoolean                           isAuthzDecisionEmitted                    = new AtomicBoolean(false);
        Flux<IdentifiableAuthorizationDecision> publishClientMqttSubscriptionDecisionFlux = Flux
                .<IdentifiableAuthorizationDecision>never()
                .mergeWith(Flux.just(new IdentifiableAuthorizationDecision(
                        publishClientMqttSubscriptionSaplSubscriptionId, AuthorizationDecision.PERMIT)))
                .doOnNext(authzDecision -> isAuthzDecisionEmitted.set(true));

        PolicyDecisionPoint pdpMock = mock(PolicyDecisionPoint.class);
        when(pdpMock.decide(any(MultiAuthorizationSubscription.class)))
                .thenReturn(publishClientMqttConnectionDecisionFlux)
                .thenReturn(publishClientMqttSubscriptionDecisionFlux);

        // prepare simulation of client disconnect
        ConcurrentHashMap<String, MqttClientState> mqttClientCacheSpy = spy(new ConcurrentHashMap<>());
        when(mqttClientCacheSpy.get(PUBLISH_CLIENT_ID)).thenAnswer(answer -> {
            if (!isAuthzDecisionEmitted.get()) {
                return answer.callRealMethod();
            }
            return null;
        });

        Mqtt5Publish publishMessageQos1 = buildMqttPublishMessage(TOPIC, 1, false);

        // WHEN
        EmbeddedHiveMQ      mqttBroker    = buildAndStartBroker(dataFolder, configFolder, extensionFolder, pdpMock,
                EXTENSIONS_CONFIG_PATH_SHORT_TIMEOUT, mqttClientCacheSpy);
        Mqtt5BlockingClient publishClient = buildAndStartMqttClient(PUBLISH_CLIENT_ID);

        Mqtt5PubAckException pubAckException = assertThrowsExactly(Mqtt5PubAckException.class,
                () -> publishClient.publish(publishMessageQos1));

        // THEN
        assertEquals(Mqtt5PubAckReasonCode.NOT_AUTHORIZED, pubAckException.getMqttMessage().getReasonCode());
        verify(pdpMock, times(2)).decide(any(MultiAuthorizationSubscription.class));

        // FINALLY
        stopBroker(mqttBroker);
    }

    @ParameterizedTest
    @ValueSource(ints = { 0, 4 })
    void when_clientWasDisconnectedWhileStartingMqttSubscriptionEnforcementWithEstablishedDecisionFlux_then_stopEnforcement(
            int mqttClientCacheCallThreshold) throws InitializationException {
        // GIVEN
        String subscriptionClientMqttConnectionSaplSubscriptionId   = SaplSubscriptionUtility
                .buildSubscriptionId(SUBSCRIPTION_CLIENT_ID, MqttPep.CONNECT_AUTHZ_ACTION);
        String subscriptionClientMqttSubscriptionSaplSubscriptionId = SaplSubscriptionUtility
                .buildSubscriptionId(SUBSCRIPTION_CLIENT_ID, MqttPep.SUBSCRIBE_AUTHZ_ACTION, TOPIC);

        Flux<IdentifiableAuthorizationDecision> subscriptionClientMqttConnectionDecisionFlux   = Flux
                .<IdentifiableAuthorizationDecision>never().mergeWith(Flux.just(new IdentifiableAuthorizationDecision(
                        subscriptionClientMqttConnectionSaplSubscriptionId, AuthorizationDecision.PERMIT)));
        AtomicBoolean                           isAuthzDecisionEmitted                         = new AtomicBoolean(
                false);
        Flux<IdentifiableAuthorizationDecision> subscriptionClientMqttSubscriptionDecisionFlux = Flux
                .<IdentifiableAuthorizationDecision>never()
                .mergeWith(Flux.just(new IdentifiableAuthorizationDecision(
                        subscriptionClientMqttSubscriptionSaplSubscriptionId, AuthorizationDecision.PERMIT)))
                .doOnNext(authzDecision -> isAuthzDecisionEmitted.set(true));

        PolicyDecisionPoint pdpMock = mock(PolicyDecisionPoint.class);
        when(pdpMock.decide(any(MultiAuthorizationSubscription.class)))
                .thenReturn(subscriptionClientMqttConnectionDecisionFlux)
                .thenReturn(subscriptionClientMqttSubscriptionDecisionFlux);

        // prepare simulation of client disconnect
        ConcurrentHashMap<String, MqttClientState> mqttClientCacheSpy           = spy(new ConcurrentHashMap<>());
        AtomicInteger                              counterMqttClientCacheCalled = new AtomicInteger(0);
        when(mqttClientCacheSpy.get(SUBSCRIPTION_CLIENT_ID)).thenAnswer(answer -> {
            if (!isAuthzDecisionEmitted.get()) {
                return answer.callRealMethod();
            }

            counterMqttClientCacheCalled.getAndAdd(1);
            if (counterMqttClientCacheCalled.get() <= mqttClientCacheCallThreshold) {
                return answer.callRealMethod();
            }
            return null;
        });

        Mqtt5Subscribe mqttSubscribeMessage = buildMqttSubscribeMessage(TOPIC);

        // WHEN
        EmbeddedHiveMQ      mqttBroker      = buildAndStartBroker(dataFolder, configFolder, extensionFolder, pdpMock,
                EXTENSIONS_CONFIG_PATH_SHORT_TIMEOUT, mqttClientCacheSpy);
        Mqtt5BlockingClient subscribeClient = buildAndStartMqttClient(SUBSCRIPTION_CLIENT_ID);

        Mqtt5SubAckException subAckException = assertThrowsExactly(Mqtt5SubAckException.class,
                () -> subscribeClient.subscribe(mqttSubscribeMessage));

        // THEN
        assertEquals(Mqtt5SubAckReasonCode.NOT_AUTHORIZED, subAckException.getMqttMessage().getReasonCodes().get(0));
        verify(pdpMock, times(2)).decide(any(MultiAuthorizationSubscription.class));

        // FINALLY
        stopBroker(mqttBroker);
    }

    @Test
    void when_clientDisconnectsWhileEnforcingIndeterminateDecisionWhenSubscriptionExists_then_stopEnforcement()
            throws InitializationException {
        // GIVEN
        String publishClientMqttConnectionSaplSubscriptionId   = SaplSubscriptionUtility
                .buildSubscriptionId(PUBLISH_CLIENT_ID, MqttPep.CONNECT_AUTHZ_ACTION);
        String publishClientMqttSubscriptionSaplSubscriptionId = SaplSubscriptionUtility
                .buildSubscriptionId(PUBLISH_CLIENT_ID, MqttPep.SUBSCRIBE_AUTHZ_ACTION, TOPIC);

        Flux<IdentifiableAuthorizationDecision> publishClientMqttConnectionDecisionFlux   = Flux
                .<IdentifiableAuthorizationDecision>never().mergeWith(Flux.just(new IdentifiableAuthorizationDecision(
                        publishClientMqttConnectionSaplSubscriptionId, AuthorizationDecision.PERMIT)));
        Flux<IdentifiableAuthorizationDecision> publishClientMqttSubscriptionDecisionFlux = Flux
                .<IdentifiableAuthorizationDecision>never()
                .mergeWith(Flux.just(
                        new IdentifiableAuthorizationDecision(publishClientMqttConnectionSaplSubscriptionId,
                                AuthorizationDecision.PERMIT),
                        new IdentifiableAuthorizationDecision(publishClientMqttSubscriptionSaplSubscriptionId,
                                AuthorizationDecision.PERMIT)));
        AtomicBoolean                           isAuthzDecisionEmitted                    = new AtomicBoolean(false);
        Flux<IdentifiableAuthorizationDecision> publishClientMqttPublishDecisionFlux      = Flux
                .<IdentifiableAuthorizationDecision>never()
                .mergeWith(Flux.just(IdentifiableAuthorizationDecision.INDETERMINATE))
                .doOnNext(authzDecision -> isAuthzDecisionEmitted.set(true));

        PolicyDecisionPoint pdpMock = mock(PolicyDecisionPoint.class);
        when(pdpMock.decide(any(MultiAuthorizationSubscription.class)))
                .thenReturn(publishClientMqttConnectionDecisionFlux)
                .thenReturn(publishClientMqttSubscriptionDecisionFlux).thenReturn(publishClientMqttPublishDecisionFlux);

        // prepare simulation of client disconnect
        ConcurrentHashMap<String, MqttClientState> mqttClientCacheSpy = spy(new ConcurrentHashMap<>());
        when(mqttClientCacheSpy.get(PUBLISH_CLIENT_ID)).thenAnswer(answer -> {
            if (!isAuthzDecisionEmitted.get()) {
                return answer.callRealMethod();
            }
            return null;
        });

        Mqtt5Subscribe mqttSubscribeMessage = buildMqttSubscribeMessage(TOPIC);
        Mqtt5Publish   mqttPublishMessage   = buildMqttPublishMessage(TOPIC, false);

        // WHEN
        EmbeddedHiveMQ      mqttBroker    = buildAndStartBroker(dataFolder, configFolder, extensionFolder, pdpMock,
                EXTENSIONS_CONFIG_PATH_SHORT_TIMEOUT, mqttClientCacheSpy);
        Mqtt5BlockingClient publishClient = buildAndStartMqttClient(PUBLISH_CLIENT_ID);
        publishClient.subscribe(mqttSubscribeMessage);
        publishClient.publish(mqttPublishMessage);

        // THEN
        await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> assertFalse(publishClient.getState().isConnected()));
        verify(pdpMock, times(3)).decide(any(MultiAuthorizationSubscription.class));

        // FINALLY
        stopBroker(mqttBroker);
    }

    @Test
    void when_CancellationExceptionOccursOnSharedDecisionFlux_then_stopEnforcement() {
        // GIVEN
        String publishClientMqttConnectionSaplSubscriptionId = SaplSubscriptionUtility
                .buildSubscriptionId(PUBLISH_CLIENT_ID, MqttPep.CONNECT_AUTHZ_ACTION);

        AtomicBoolean                           isAuthzDecisionEmitted                  = new AtomicBoolean(false);
        Flux<IdentifiableAuthorizationDecision> publishClientMqttConnectionDecisionFlux = Flux
                .<IdentifiableAuthorizationDecision>never()
                .mergeWith(
                        Flux.just(new IdentifiableAuthorizationDecision(publishClientMqttConnectionSaplSubscriptionId,
                                AuthorizationDecision.PERMIT)))
                .map(identAuthzDecision -> {
                                                                                                    throw new CancellationException();
                                                                                                });

        PolicyDecisionPoint pdpMock = mock(PolicyDecisionPoint.class);
        when(pdpMock.decide(any(MultiAuthorizationSubscription.class)))
                .thenReturn(publishClientMqttConnectionDecisionFlux);

        // prepare simulation of client disconnect
        ConcurrentHashMap<String, MqttClientState> mqttClientCacheSpy = spy(new ConcurrentHashMap<>());
        when(mqttClientCacheSpy.get(PUBLISH_CLIENT_ID)).thenAnswer(answer -> {
            if (!isAuthzDecisionEmitted.get()) {
                return answer.callRealMethod();
            }
            return null;
        });

        Mqtt5BlockingClient blockingMqttClient = Mqtt5Client.builder().identifier(PUBLISH_CLIENT_ID)
                .serverHost(BROKER_HOST).serverPort(BROKER_PORT).buildBlocking();

        // WHEN
        EmbeddedHiveMQ        mqttBroker       = buildAndStartBroker(dataFolder, configFolder, extensionFolder, pdpMock,
                EXTENSIONS_CONFIG_PATH_SHORT_TIMEOUT, mqttClientCacheSpy);
        Mqtt5ConnAckException connAckException = assertThrowsExactly(Mqtt5ConnAckException.class,
                blockingMqttClient::connect);

        // THEN
        assertEquals(Mqtt5ConnAckReasonCode.NOT_AUTHORIZED, connAckException.getMqttMessage().getReasonCode());
        verify(pdpMock, times(1)).decide(any(MultiAuthorizationSubscription.class));

        // FINALLY
        stopBroker(mqttBroker);
    }

    @Test
    void when_authzSubscriptionNotInMultiSubscriptionBecauseOfDisconnectWhileEnforcing_then_stopEnforcement() {
        // GIVEN
        String publishClientMqttConnectionSaplSubscriptionId = SaplSubscriptionUtility
                .buildSubscriptionId(PUBLISH_CLIENT_ID, MqttPep.CONNECT_AUTHZ_ACTION);

        Flux<IdentifiableAuthorizationDecision> publishClientMqttConnectionDecisionFlux = Flux
                .<IdentifiableAuthorizationDecision>never().mergeWith(Flux.just(new IdentifiableAuthorizationDecision(
                        publishClientMqttConnectionSaplSubscriptionId, AuthorizationDecision.PERMIT)));

        PolicyDecisionPoint pdpMock = mock(PolicyDecisionPoint.class);
        when(pdpMock.decide(any(MultiAuthorizationSubscription.class)))
                .thenReturn(publishClientMqttConnectionDecisionFlux);

        // prepare simulation of client disconnect
        ConcurrentHashMap<String, MqttClientState> mqttClientCacheSpy = spy(new ConcurrentHashMap<>());
        when(mqttClientCacheSpy.get(PUBLISH_CLIENT_ID)).thenAnswer(answer -> {
            MqttClientState mqttClientState = (MqttClientState) answer.callRealMethod();
            mqttClientState
                    .removeSaplAuthzSubscriptionFromMultiSubscription(publishClientMqttConnectionSaplSubscriptionId);
            return mqttClientState;
        });

        Mqtt5BlockingClient blockingMqttClient = Mqtt5Client.builder().identifier(PUBLISH_CLIENT_ID)
                .serverHost(BROKER_HOST).serverPort(BROKER_PORT).buildBlocking();

        // WHEN
        EmbeddedHiveMQ        mqttBroker       = buildAndStartBroker(dataFolder, configFolder, extensionFolder, pdpMock,
                EXTENSIONS_CONFIG_PATH_SHORT_TIMEOUT, mqttClientCacheSpy);
        Mqtt5ConnAckException connAckException = assertThrowsExactly(Mqtt5ConnAckException.class,
                blockingMqttClient::connect);

        // THEN
        assertEquals(Mqtt5ConnAckReasonCode.NOT_AUTHORIZED, connAckException.getMqttMessage().getReasonCode());
        verify(pdpMock, times(1)).decide(any(MultiAuthorizationSubscription.class));

        // FINALLY
        stopBroker(mqttBroker);
    }

    @Test
    void when_permitDecisionFollowingPermitDecisionOnResubscribedFluxForMqttSubscriptionEnforcement_then_staySubscribed()
            throws InitializationException {
        // GIVEN
        String secondTopic                                          = "secondTopic";
        String subscriptionClientMqttConnectionSaplSubscriptionId   = SaplSubscriptionUtility
                .buildSubscriptionId(SUBSCRIPTION_CLIENT_ID, MqttPep.CONNECT_AUTHZ_ACTION);
        String subscriptionClientMqttSubscriptionSaplSubscriptionId = SaplSubscriptionUtility
                .buildSubscriptionId(SUBSCRIPTION_CLIENT_ID, MqttPep.SUBSCRIBE_AUTHZ_ACTION, TOPIC);
        String subscriptionClientMqttSecondSaplSubscriptionId       = SaplSubscriptionUtility
                .buildSubscriptionId(SUBSCRIPTION_CLIENT_ID, MqttPep.SUBSCRIBE_AUTHZ_ACTION, secondTopic);

        Flux<IdentifiableAuthorizationDecision> subscriptionClientMqttConnectionDecisionFlux      = Flux
                .<IdentifiableAuthorizationDecision>never().mergeWith(Flux.just(new IdentifiableAuthorizationDecision(
                        subscriptionClientMqttConnectionSaplSubscriptionId, AuthorizationDecision.PERMIT)));
        Flux<IdentifiableAuthorizationDecision> subscriptionClientMqttSubscriptionDecisionFlux    = Flux
                .<IdentifiableAuthorizationDecision>never()
                .mergeWith(Flux.just(
                        new IdentifiableAuthorizationDecision(subscriptionClientMqttConnectionSaplSubscriptionId,
                                AuthorizationDecision.PERMIT),
                        new IdentifiableAuthorizationDecision(subscriptionClientMqttSubscriptionSaplSubscriptionId,
                                AuthorizationDecision.PERMIT)));
        Flux<IdentifiableAuthorizationDecision> subscriptionClientSecondMqttSubscribeDecisionFlux = Flux
                .<IdentifiableAuthorizationDecision>never()
                .mergeWith(Flux.just(
                        new IdentifiableAuthorizationDecision(subscriptionClientMqttConnectionSaplSubscriptionId,
                                AuthorizationDecision.PERMIT),
                        new IdentifiableAuthorizationDecision(subscriptionClientMqttSubscriptionSaplSubscriptionId,
                                AuthorizationDecision.PERMIT),
                        new IdentifiableAuthorizationDecision(subscriptionClientMqttSecondSaplSubscriptionId,
                                AuthorizationDecision.PERMIT)));

        PolicyDecisionPoint pdpMock = mock(PolicyDecisionPoint.class);
        when(pdpMock.decide(any(MultiAuthorizationSubscription.class)))
                .thenReturn(subscriptionClientMqttConnectionDecisionFlux)
                .thenReturn(subscriptionClientMqttSubscriptionDecisionFlux)
                .thenReturn(subscriptionClientSecondMqttSubscribeDecisionFlux);

        Mqtt5Subscribe mqttSubscribeMessage       = buildMqttSubscribeMessage(TOPIC);
        Mqtt5Subscribe secondMqttSubscribeMessage = buildMqttSubscribeMessage(secondTopic);

        // WHEN
        EmbeddedHiveMQ      mqttBroker      = buildAndStartBroker(dataFolder, configFolder, extensionFolder, pdpMock,
                EXTENSIONS_CONFIG_PATH_SHORT_TIMEOUT);
        Mqtt5BlockingClient subscribeClient = buildAndStartMqttClient(SUBSCRIPTION_CLIENT_ID);
        subscribeClient.subscribe(mqttSubscribeMessage);
        subscribeClient.subscribe(secondMqttSubscribeMessage);

        // THEN
        verify(pdpMock, times(3)).decide(any(MultiAuthorizationSubscription.class));
        assertTrue(subscribeClient.getState().isConnected());

        // FINALLY
        stopBroker(mqttBroker);
    }
}
