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

import static com.hivemq.extension.sdk.api.async.Async.Status.RUNNING;
import static com.hivemq.extension.sdk.api.packets.disconnect.DisconnectReasonCode.NOT_AUTHORIZED;
import static io.sapl.api.pdp.Decision.INDETERMINATE;
import static io.sapl.api.pdp.Decision.NOT_APPLICABLE;
import static io.sapl.api.pdp.Decision.PERMIT;
import static io.sapl.mqtt.pep.mqtt_action.HiveMqEnforcement.enforceMqttConnectionByHiveMqBroker;
import static io.sapl.mqtt.pep.mqtt_action.HiveMqEnforcement.enforceMqttPublishByHiveMqBroker;
import static io.sapl.mqtt.pep.mqtt_action.HiveMqEnforcement.enforceMqttSubscriptionByHiveMqBroker;
import static io.sapl.mqtt.pep.util.DecisionFluxUtility.addSaplAuthzSubscriptionToMultiSubscription;
import static io.sapl.mqtt.pep.util.DecisionFluxUtility.cacheConstraintDetails;
import static io.sapl.mqtt.pep.util.DecisionFluxUtility.cacheCurrentTimeAsStartTimeOfMqttAction;
import static io.sapl.mqtt.pep.util.DecisionFluxUtility.cacheCurrentTimeAsTimeOfLastSignalEvent;
import static io.sapl.mqtt.pep.util.DecisionFluxUtility.cacheMqttActionDecisionFlux;
import static io.sapl.mqtt.pep.util.DecisionFluxUtility.cacheMqttTopicSubscription;
import static io.sapl.mqtt.pep.util.DecisionFluxUtility.cacheTopicsOfUnsubscribeMessage;
import static io.sapl.mqtt.pep.util.DecisionFluxUtility.containsAuthzDecisionOfSubscriptionIdOrNull;
import static io.sapl.mqtt.pep.util.DecisionFluxUtility.disposeMqttActionDecisionFluxAndRemoveDisposableFromCache;
import static io.sapl.mqtt.pep.util.DecisionFluxUtility.disposeMqttActionDecisionFluxes;
import static io.sapl.mqtt.pep.util.DecisionFluxUtility.disposeSharedClientDecisionFlux;
import static io.sapl.mqtt.pep.util.DecisionFluxUtility.getAuthzSubscriptionFromCachedMultiAuthzSubscription;
import static io.sapl.mqtt.pep.util.DecisionFluxUtility.getAuthzSubscriptionTimeoutDuration;
import static io.sapl.mqtt.pep.util.DecisionFluxUtility.getConstraintDetailsFromCache;
import static io.sapl.mqtt.pep.util.DecisionFluxUtility.getIdentAuthzDecision;
import static io.sapl.mqtt.pep.util.DecisionFluxUtility.getMqttActionStartTimeFromCache;
import static io.sapl.mqtt.pep.util.DecisionFluxUtility.getMqttTopicSubscriptionFromCache;
import static io.sapl.mqtt.pep.util.DecisionFluxUtility.getRemainingTimeLimitMillis;
import static io.sapl.mqtt.pep.util.DecisionFluxUtility.hasHandledObligationsSuccessfully;
import static io.sapl.mqtt.pep.util.DecisionFluxUtility.removeConstraintDetailsFromCache;
import static io.sapl.mqtt.pep.util.DecisionFluxUtility.removeIdentAuthzDecisionFromCache;
import static io.sapl.mqtt.pep.util.DecisionFluxUtility.removeLastSignalTimeFromCache;
import static io.sapl.mqtt.pep.util.DecisionFluxUtility.removeMqttActionDecisionFluxFromCache;
import static io.sapl.mqtt.pep.util.DecisionFluxUtility.removeMqttTopicSubscriptionFromCache;
import static io.sapl.mqtt.pep.util.DecisionFluxUtility.removeSaplAuthzSubscriptionFromMultiSubscription;
import static io.sapl.mqtt.pep.util.DecisionFluxUtility.removeStartTimeOfMqttActionFromCache;
import static io.sapl.mqtt.pep.util.DecisionFluxUtility.removeTopicsOfUnsubscribeMessageFromCache;
import static io.sapl.mqtt.pep.util.DecisionFluxUtility.saveIdentAuthzDecisionAndTransformToMap;
import static io.sapl.mqtt.pep.util.DecisionFluxUtility.subscribeMqttActionDecisionFluxAndCacheDisposable;
import static io.sapl.mqtt.pep.util.DecisionFluxUtility.subscribeToMqttActionDecisionFluxes;
import static io.sapl.mqtt.pep.util.HiveMqUtility.buildTopicSubscription;
import static io.sapl.mqtt.pep.util.HiveMqUtility.getUnsubackReasonCode;
import static io.sapl.mqtt.pep.util.HiveMqUtility.getUserName;
import static io.sapl.mqtt.pep.util.HiveMqUtility.isMqttSubscriptionExisting;
import static io.sapl.mqtt.pep.util.SaplSubscriptionUtility.ENVIRONMENT_AUTHZ_ACTION_TYPE;
import static io.sapl.mqtt.pep.util.SaplSubscriptionUtility.ENVIRONMENT_TOPIC;
import static io.sapl.mqtt.pep.util.SaplSubscriptionUtility.buildSaplAuthzSubscriptionForMqttConnection;
import static io.sapl.mqtt.pep.util.SaplSubscriptionUtility.buildSaplAuthzSubscriptionForMqttPublish;
import static io.sapl.mqtt.pep.util.SaplSubscriptionUtility.buildSaplAuthzSubscriptionForMqttSubscription;
import static io.sapl.mqtt.pep.util.SaplSubscriptionUtility.buildSubscriptionId;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeoutException;

import javax.annotation.Nullable;

import com.hivemq.extension.sdk.api.annotations.NotNull;
import com.hivemq.extension.sdk.api.async.Async;
import com.hivemq.extension.sdk.api.async.TimeoutFallback;
import com.hivemq.extension.sdk.api.auth.SimpleAuthenticator;
import com.hivemq.extension.sdk.api.auth.SubscriptionAuthorizer;
import com.hivemq.extension.sdk.api.auth.parameter.SimpleAuthInput;
import com.hivemq.extension.sdk.api.auth.parameter.SimpleAuthOutput;
import com.hivemq.extension.sdk.api.auth.parameter.SubscriptionAuthorizerInput;
import com.hivemq.extension.sdk.api.auth.parameter.SubscriptionAuthorizerOutput;
import com.hivemq.extension.sdk.api.events.client.ClientLifecycleEventListener;
import com.hivemq.extension.sdk.api.events.client.parameters.AuthenticationSuccessfulInput;
import com.hivemq.extension.sdk.api.events.client.parameters.ConnectionStartInput;
import com.hivemq.extension.sdk.api.events.client.parameters.DisconnectEventInput;
import com.hivemq.extension.sdk.api.interceptor.publish.parameter.PublishInboundInput;
import com.hivemq.extension.sdk.api.interceptor.publish.parameter.PublishInboundOutput;
import com.hivemq.extension.sdk.api.interceptor.unsuback.parameter.UnsubackOutboundInput;
import com.hivemq.extension.sdk.api.interceptor.unsubscribe.parameter.UnsubscribeInboundInput;
import com.hivemq.extension.sdk.api.packets.connect.ConnackReasonCode;
import com.hivemq.extension.sdk.api.packets.publish.AckReasonCode;
import com.hivemq.extension.sdk.api.packets.unsuback.UnsubackReasonCode;
import com.hivemq.extension.sdk.api.services.Services;
import com.hivemq.extension.sdk.api.services.session.SessionInformation;
import com.hivemq.extension.sdk.api.services.subscription.SubscriptionsForClientResult;
import com.hivemq.extension.sdk.api.services.subscription.TopicSubscription;

import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.IdentifiableAuthorizationDecision;
import io.sapl.api.pdp.MultiAuthorizationSubscription;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.mqtt.pep.cache.MqttClientState;
import io.sapl.mqtt.pep.config.SaplMqttExtensionConfig;
import io.sapl.mqtt.pep.constraint.ConstraintDetails;
import io.sapl.mqtt.pep.constraint.ConstraintHandler;
import io.sapl.mqtt.pep.details.MqttSaplId;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * This PEP enforces mqtt actions as an extension to the HiveMq broker.
 */
@Slf4j
public class MqttPep {

    /**
     * References the mqtt activity of establishing a connection between a client
     * and a broker.
     */
    public static final String   CONNECT_AUTHZ_ACTION                             = "mqtt.connect";
    /**
     * References the mqtt activity of establishing a mqtt subscription.
     */
    public static final String   SUBSCRIBE_AUTHZ_ACTION                           = "mqtt.subscribe";
    /**
     * References the mqtt activity of publishing a mqtt message.
     */
    public static final String   PUBLISH_AUTHZ_ACTION                             = "mqtt.publish";
    /**
     * This message is used to describe a denial further.
     */
    public static final String   NOT_AUTHORIZED_NOTICE                            = "Not authorized by SAPL-MQTT-PEP";
    private static final String  TIMEOUT_NOTICE                                   = "Authorization via SAPL-MQTT-PEP timed out";
    private static final boolean DEFAULT_IS_RESUBSCRIBE_MQTT_SUBSCRIPTION_ENABLED = false;

    private final PolicyDecisionPoint                    pdp;
    private final SaplMqttExtensionConfig                saplMqttExtensionConfig;
    private final ConcurrentMap<String, MqttClientState> mqttClientCache;

    /**
     * Enforces mqtt specific actions.
     *
     * @param pdp used by the pep to request authorization decisions
     * @param saplMqttExtensionConfig contains the configuration params for the pep
     */
    public MqttPep(PolicyDecisionPoint pdp, SaplMqttExtensionConfig saplMqttExtensionConfig) {
        this(pdp, saplMqttExtensionConfig, null);
    }

    /**
     * Enforces mqtt specific actions.
     *
     * @param pdp used by the pep to request authorization decisions
     * @param saplMqttExtensionConfig contains the configuration params for the pep
     * @param mqttClientCache used to cache the state of different enforcements
     */
    public MqttPep(PolicyDecisionPoint pdp, SaplMqttExtensionConfig saplMqttExtensionConfig,
            ConcurrentMap<String, MqttClientState> mqttClientCache) {
        this.pdp                     = pdp;
        this.saplMqttExtensionConfig = new SaplMqttExtensionConfig(saplMqttExtensionConfig);
        this.mqttClientCache         = Objects.requireNonNullElseGet(mqttClientCache, ConcurrentHashMap::new);
    }

    /**
     * Enforces existing MQTT actions and registers interceptors for future MQTT
     * action enforcements.
     */
    public void startEnforcement() {
        initializationAndCleanUpOfSaplMqttEnforcement();
        configureEnforcementForExistingConnections();
        configureConnectionEnforcement();
        configureEnforcementForExistingSubscriptions();
        configureSubscribeAndPublishEnforcement();
    }

    /**
     * This method sets a lifecycle listener to initialize and clean up the sapl
     * mqtt enforcement.
     */
    private void initializationAndCleanUpOfSaplMqttEnforcement() {
        Services.eventRegistry().setClientLifecycleEventListener(
                clientLifecycleEventListenerProviderInput -> new SaplMqttClientLifecycleEventListener());
    }

    /**
     * This method enforces all existing mqtt connections. Denied mqtt connections
     * will be disconnected.
     */
    private void configureEnforcementForExistingConnections() {
        log.info("Enforcing the authorization of all existing mqtt connections on extension start.");
        Services.clientService().iterateAllClients(((context, sessionInformation) -> {
            if (sessionInformation.isConnected()) {
                enforceMqttConnection(sessionInformation);
            }
        }));
    }

    /**
     * This method enforces each mqtt connection to the broker. Denied clients will
     * be disconnected.
     */
    private void configureConnectionEnforcement() {
        Services.securityRegistry()
                .setAuthenticatorProvider(authnProviderInput -> new SaplMqttClientConnectionHandler());
    }

    /**
     * This method enforces all existing mqtt subscriptions directly. Please notice
     * that HiveMq broker doesn't support notification of a client when
     * unsubscribing.
     */
    private void configureEnforcementForExistingSubscriptions() {
        log.info("Enforcing the authorization of all existing mqtt subscriptions on extension start.");
        // iterate over all mqtt subscriptions
        Services.subscriptionStore().iterateAllSubscriptions(
                (context, subscriptionsOfClient) -> enforceAllMqttSubscriptionsOfClient(subscriptionsOfClient));
    }

    /**
     * The method adds an authorizer for mqtt subscription and mqtt publish
     * requests. The authorizer enforces decisions of whether the mqtt subscription
     * respectively the mqtt publishing is allowed or not. Furthermore, unsubscribe
     * requests are intercepted to handle cancellation of subscriptions.
     */
    private void configureSubscribeAndPublishEnforcement() {
        Services.securityRegistry().setAuthorizerProvider(
                authorizerProviderInput -> (SubscriptionAuthorizer) this::buildSubscriptionEnforcementAuthorizer);
        Services.initializerRegistry().setClientInitializer((initializerInput, clientContext) -> {
            clientContext.addPublishInboundInterceptor(this::buildPublishEnforcementInterceptor);
            clientContext.addUnsubscribeInboundInterceptor((unsubscribeInboundInput,
                    unsubscribeInboundOutput) -> buildUnsubscribeInterceptorForSaplSubscriptionTimeout(
                            unsubscribeInboundInput));
            clientContext.addUnsubackOutboundInterceptor((unSubAckOutboundInput,
                    unSubAckOutboundOutput) -> buildUnsubscribeAckInterceptorForSaplSubscriptionTimeout(
                            unSubAckOutboundInput));
        });
    }

    private void enforceMqttConnection(SessionInformation sessionInformation) {
        String clientId = sessionInformation.getClientIdentifier();
        initializeClientCache(clientId);
        enforceMqttConnection(clientId, null, null);
    }

    private void enforceAllMqttSubscriptionsOfClient(SubscriptionsForClientResult subscriptionsOfClient) {
        String clientId        = subscriptionsOfClient.getClientId();
        var    mqttClientState = mqttClientCache.get(clientId);

        Set<TopicSubscription> topicSubscriptions = subscriptionsOfClient.getSubscriptions();
        // build multiSubscription
        topicSubscriptions.iterator().forEachRemaining(topicSubscription -> {
            String                    subscriptionId    = buildSubscriptionId(clientId, SUBSCRIBE_AUTHZ_ACTION,
                    topicSubscription.getTopicFilter());
            AuthorizationSubscription authzSubscription = buildSaplAuthzSubscriptionForMqttSubscription(mqttClientState,
                    topicSubscription);
            addSaplAuthzSubscriptionToMultiSubscription(subscriptionId, mqttClientState, authzSubscription);
            cacheCurrentTimeAsStartTimeOfMqttAction(mqttClientState, subscriptionId);
            cacheMqttTopicSubscription(mqttClientState, subscriptionId, topicSubscription); // necessary for resubscribe
                                                                                            // constraint
        });

        disposeAllDecisionFluxesOfClientAndSubscribeNewSharedClientDecisionFlux(mqttClientState);

        // build mqtt subscription decision fluxes
        topicSubscriptions.iterator().forEachRemaining(topicSubscription -> {
            String                                  topic                     = topicSubscription.getTopicFilter();
            String                                  subscriptionId            = buildSubscriptionId(clientId,
                    SUBSCRIBE_AUTHZ_ACTION, topic);
            Flux<IdentifiableAuthorizationDecision> mqttSubscriptionAuthzFlux = buildMqttSubscriptionAuthzFlux(
                    new MqttSaplId(clientId, subscriptionId, topic));
            cacheMqttActionDecisionFlux(mqttClientState, subscriptionId, mqttSubscriptionAuthzFlux);
        });

        subscribeToMqttActionDecisionFluxes(mqttClientState);
    }

    private class SaplMqttClientConnectionHandler implements SimpleAuthenticator {
        @Override
        public void onConnect(SimpleAuthInput authnInput, SimpleAuthOutput authnOutput) {
            String clientId = authnInput.getClientInformation().getClientId();

            // Makes the output object async
            Async<SimpleAuthOutput> asyncOutput = authnOutput.async(
                    Duration.ofMillis(saplMqttExtensionConfig.getConnectionEnforcementTimeoutMillis()),
                    TimeoutFallback.FAILURE, ConnackReasonCode.NOT_AUTHORIZED, TIMEOUT_NOTICE);

            // Submit task to extension executor service
            Services.extensionExecutorService().submit(() -> enforceMqttConnection(clientId, asyncOutput, authnInput));
        }
    }

    private void buildSubscriptionEnforcementAuthorizer(SubscriptionAuthorizerInput subscriptionAuthorizerInput,
            SubscriptionAuthorizerOutput subscriptionAuthorizerOutput) {
        // Makes the output object async
        Async<SubscriptionAuthorizerOutput> asyncOutput = subscriptionAuthorizerOutput.async(
                Duration.ofMillis(saplMqttExtensionConfig.getSubscriptionEnforcementTimeoutMillis()),
                TimeoutFallback.FAILURE);

        // Submit task to extension executor service
        Services.extensionExecutorService()
                .submit(() -> enforceMqttSubscription(asyncOutput, subscriptionAuthorizerInput));
    }

    private void buildPublishEnforcementInterceptor(PublishInboundInput publishInboundInput,
            PublishInboundOutput publishInboundOutput) {
        // Makes the output object async
        Async<PublishInboundOutput> asyncOutput = publishInboundOutput.async(
                Duration.ofMillis(saplMqttExtensionConfig.getPublishEnforcementTimeoutMillis()),
                TimeoutFallback.FAILURE, AckReasonCode.NOT_AUTHORIZED, TIMEOUT_NOTICE);

        // Submit task to extension executor service
        Services.extensionExecutorService().submit(() -> enforceMqttPublish(asyncOutput, publishInboundInput));
    }

    private void buildUnsubscribeInterceptorForSaplSubscriptionTimeout(
            UnsubscribeInboundInput unsubscribeInboundInput) {
        String       clientId          = unsubscribeInboundInput.getClientInformation().getClientId();
        int          packetId          = unsubscribeInboundInput.getUnsubscribePacket().getPacketIdentifier();
        List<String> unsubscribeTopics = unsubscribeInboundInput.getUnsubscribePacket().getTopicFilters();

        // cache for usage in unsubscribe ack interceptor method for sapl subscription
        // timeout purposes
        cacheTopicsOfUnsubscribeMessage(mqttClientCache.get(clientId), packetId, unsubscribeTopics);
    }

    private void buildUnsubscribeAckInterceptorForSaplSubscriptionTimeout(UnsubackOutboundInput unsubackOutboundInput) {
        String       clientId        = unsubackOutboundInput.getClientInformation().getClientId();
        var          mqttClientState = mqttClientCache.get(clientId);
        int          packetId        = unsubackOutboundInput.getUnsubackPacket().getPacketIdentifier();
        List<String> topics          = removeTopicsOfUnsubscribeMessageFromCache(mqttClientState, packetId);

        for (var i = 0; i < topics.size(); i++) { // for every topic
            String topic = topics.get(i);
            if (getUnsubackReasonCode(unsubackOutboundInput, i) == UnsubackReasonCode.SUCCESS) {
                log.debug("Client '{}' unsubscribed topic '{}'.", clientId, topic);
                String subscriptionId = buildSubscriptionId(clientId, SUBSCRIBE_AUTHZ_ACTION, topic);
                cacheCurrentTimeAsTimeOfLastSignalEvent(mqttClientState, subscriptionId);
                removeStartTimeOfMqttActionFromCache(mqttClientState, subscriptionId);

                Flux<IdentifiableAuthorizationDecision> subscriptionAuthzTimeoutFlux = buildMqttSubscriptionAuthzFlux(
                        new MqttSaplId(clientId, subscriptionId, topic));
                resubscribeMqttActionDecisionFlux(subscriptionId, mqttClientState, subscriptionAuthzTimeoutFlux);
            }
        }
    }

    private void enforceMqttConnection(String clientId, @Nullable Async<SimpleAuthOutput> asyncOutput,
            @Nullable SimpleAuthInput authnInput) {
        var mqttClientState = mqttClientCache.get(clientId);
        if (mqttClientState == null) { // client already disconnected
            return;
        }

        if (authnInput != null) {
            mqttClientState.setUserName(getUserName(authnInput));
        }
        var                                     mqttSaplId              = new MqttSaplId(clientId,
                buildSubscriptionId(clientId, CONNECT_AUTHZ_ACTION));
        Flux<IdentifiableAuthorizationDecision> mqttConnectionAuthzFlux = buildMqttConnectionAuthzFlux(mqttSaplId,
                asyncOutput);
        AuthorizationSubscription               saplAuthzSubscription   = buildSaplAuthzSubscriptionForMqttConnection(
                mqttClientState, authnInput);
        enforceMqttAction(mqttClientState, mqttSaplId, saplAuthzSubscription, mqttConnectionAuthzFlux);
    }

    private void enforceMqttSubscription(Async<SubscriptionAuthorizerOutput> asyncOutput,
            SubscriptionAuthorizerInput subscriptionAuthorizerInput) {
        String clientId        = subscriptionAuthorizerInput.getClientInformation().getClientId();
        var    mqttClientState = mqttClientCache.get(clientId);
        if (mqttClientState == null) {
            return;
        }

        String topic          = subscriptionAuthorizerInput.getSubscription().getTopicFilter();
        String subscriptionId = buildSubscriptionId(clientId, SUBSCRIBE_AUTHZ_ACTION, topic);
        cacheCurrentTimeAsTimeOfLastSignalEvent(mqttClientState, subscriptionId); // for sapl subscription timeout
        cacheCurrentTimeAsStartTimeOfMqttAction(mqttClientState, subscriptionId);
        cacheMqttTopicSubscription(mqttClientState, subscriptionId,
                buildTopicSubscription(subscriptionAuthorizerInput)); // for resubscribe constraint

        // in case an enforcement doesn't already happen
        var mqttSaplId = new MqttSaplId(clientId, subscriptionId, topic);
        if (getAuthzSubscriptionFromCachedMultiAuthzSubscription(mqttClientState, subscriptionId) == null) {
            Flux<IdentifiableAuthorizationDecision> mqttSubscriptionAuthzFlux = buildMqttSubscriptionAuthzFlux(
                    mqttSaplId, asyncOutput);
            AuthorizationSubscription               saplAuthzSubscription     = buildSaplAuthzSubscriptionForMqttSubscription(
                    mqttClientState, subscriptionAuthorizerInput);
            enforceMqttAction(mqttClientState, mqttSaplId, saplAuthzSubscription, mqttSubscriptionAuthzFlux);
        } else { // reset the timeout of the mqttSubscriptionAuthzTimeoutFlux
            Flux<IdentifiableAuthorizationDecision> subscriptionAuthzTimeoutFlux = buildMqttSubscriptionAuthzFlux(
                    mqttSaplId, asyncOutput);
            resubscribeMqttActionDecisionFlux(subscriptionId, mqttClientState, subscriptionAuthzTimeoutFlux);
        }
    }

    private void enforceMqttPublish(Async<PublishInboundOutput> asyncOutput, PublishInboundInput publishInboundInput) {
        String clientId        = publishInboundInput.getClientInformation().getClientId();
        var    mqttClientState = mqttClientCache.get(clientId);
        if (mqttClientState == null) {
            return;
        }

        String topic          = publishInboundInput.getPublishPacket().getTopic();
        String subscriptionId = buildSubscriptionId(clientId, PUBLISH_AUTHZ_ACTION, topic);
        cacheCurrentTimeAsTimeOfLastSignalEvent(mqttClientState, subscriptionId); // for sapl subscription timeout

        // in case an enforcement doesn't already happen
        var mqttSaplId = new MqttSaplId(clientId, subscriptionId, topic);
        if (getAuthzSubscriptionFromCachedMultiAuthzSubscription(mqttClientState, subscriptionId) == null) {
            Flux<IdentifiableAuthorizationDecision> mqttPublishAuthzFlux  = buildMqttPublishAuthzFlux(mqttSaplId,
                    asyncOutput);
            AuthorizationSubscription               saplAuthzSubscription = buildSaplAuthzSubscriptionForMqttPublish(
                    mqttClientState, publishInboundInput);
            enforceMqttAction(mqttClientState, mqttSaplId, saplAuthzSubscription, mqttPublishAuthzFlux);
        } else { // reset the timeout of the mqttPublishAuthzTimeoutFlux
            Flux<IdentifiableAuthorizationDecision> publishAuthzTimeoutFlux = buildMqttPublishAuthzFlux(mqttSaplId,
                    asyncOutput);
            resubscribeMqttActionDecisionFlux(subscriptionId, mqttClientState, publishAuthzTimeoutFlux);
        }
    }

    private Flux<IdentifiableAuthorizationDecision> buildMqttConnectionAuthzFlux(MqttSaplId mqttSaplId,
            @Nullable Async<SimpleAuthOutput> asyncOutput) {
        var mqttClientState = mqttClientCache.get(mqttSaplId.getMqttClientId());
        if (mqttClientState == null) { // client already disconnected
            return Flux.empty();
        }

        String subscriptionId = mqttSaplId.getSaplSubscriptionId();
        return Flux.defer(mqttClientState::getSharedClientDecisionFlux)
                .filter(identAuthzDecisionMap -> containsAuthzDecisionOfSubscriptionIdOrNull(subscriptionId,
                        identAuthzDecisionMap))
                .map(identAuthzDecisionMap -> getIdentAuthzDecision(subscriptionId, identAuthzDecisionMap))
                .transform(identAuthzDecisionFlux -> buildMqttConnectionTimeLimitFlux(identAuthzDecisionFlux,
                        mqttClientState, subscriptionId))
                .transform(identAuthzDecisionFlux -> buildMqttConnectionEnforcementFlux(identAuthzDecisionFlux,
                        mqttClientState, mqttSaplId, asyncOutput));
    }

    private Flux<IdentifiableAuthorizationDecision> buildMqttSubscriptionAuthzFlux(MqttSaplId mqttSaplId) {
        return buildMqttSubscriptionAuthzFlux(mqttSaplId, null);
    }

    private Flux<IdentifiableAuthorizationDecision> buildMqttSubscriptionAuthzFlux(MqttSaplId mqttSaplId,
            @Nullable Async<SubscriptionAuthorizerOutput> asyncOutput) {
        var mqttClientState = mqttClientCache.get(mqttSaplId.getMqttClientId());
        if (mqttClientState == null) { // client already disconnected
            return Flux.empty();
        }

        String subscriptionId = mqttSaplId.getSaplSubscriptionId();
        return Flux.defer(mqttClientState::getSharedClientDecisionFlux)
                .filter(identAuthzDecisionMap -> containsAuthzDecisionOfSubscriptionIdOrNull(subscriptionId,
                        identAuthzDecisionMap))
                .map(identAuthzDecisionMap -> getIdentAuthzDecision(subscriptionId, identAuthzDecisionMap))
                .transform(identAuthzFlux -> buildMqttSubscriptionTimeLimitFlux(mqttClientState, mqttSaplId,
                        identAuthzFlux, asyncOutput))
                .transform(identAuthzDecisionFlux -> buildSaplSubscriptionTimeoutFlux(identAuthzDecisionFlux,
                        mqttClientState, mqttSaplId))
                .transform(identAuthzDecisionFlux -> buildMqttSubscriptionEnforcementFlux(identAuthzDecisionFlux,
                        mqttSaplId, asyncOutput));
    }

    private Flux<IdentifiableAuthorizationDecision> buildMqttPublishAuthzFlux(MqttSaplId mqttSaplId,
            Async<PublishInboundOutput> asyncOutput) {
        var mqttClientState = mqttClientCache.get(mqttSaplId.getMqttClientId());
        if (mqttClientState == null) { // client already disconnected
            return Flux.empty();
        }

        String subscriptionId       = mqttSaplId.getSaplSubscriptionId();
        var    publishInboundOutput = asyncOutput.getOutput();
        return Flux.defer(mqttClientState::getSharedClientDecisionFlux)
                .filter(identAuthzDecisionMap -> containsAuthzDecisionOfSubscriptionIdOrNull(subscriptionId,
                        identAuthzDecisionMap))
                .map(identAuthzDecisionMap -> getIdentAuthzDecision(subscriptionId, identAuthzDecisionMap))
                .timeout(getAuthzSubscriptionTimeoutDuration(saplMqttExtensionConfig, mqttClientState, subscriptionId))
                .doOnError(TimeoutException.class,
                        timeoutException -> handleSaplAuthzSubscriptionTimeout(mqttClientState, mqttSaplId))
                .onErrorResume(TimeoutException.class, timeoutException -> Flux.empty())
                .transform(identAuthzDecisionFlux -> buildMqttPublishEnforcementFlux(identAuthzDecisionFlux,
                        publishInboundOutput, asyncOutput, mqttSaplId));
    }

    private void enforceMqttAction(MqttClientState mqttClientState, MqttSaplId mqttSaplId,
            AuthorizationSubscription saplAuthzSubscription,
            Flux<IdentifiableAuthorizationDecision> mqttActionAuthzFlux) {
        subscribeToNewSharedClientDecisionFlux(mqttClientState, mqttSaplId, saplAuthzSubscription);
        cacheMqttActionDecisionFlux(mqttClientState, mqttSaplId.getSaplSubscriptionId(), mqttActionAuthzFlux);
        subscribeToMqttActionDecisionFluxes(mqttClientState);
    }

    private void resubscribeMqttActionDecisionFlux(String subscriptionId, MqttClientState mqttClientState,
            Flux<IdentifiableAuthorizationDecision> mqttActionFlux) {
        cacheMqttActionDecisionFlux(mqttClientState, subscriptionId, mqttActionFlux);
        disposeMqttActionDecisionFluxAndRemoveDisposableFromCache(mqttClientState, subscriptionId);
        subscribeMqttActionDecisionFluxAndCacheDisposable(mqttClientState, subscriptionId, mqttActionFlux);
    }

    private Flux<IdentifiableAuthorizationDecision> buildMqttConnectionTimeLimitFlux(
            Flux<IdentifiableAuthorizationDecision> identAuthzFlux, MqttClientState mqttClientState,
            String subscriptionId) {
        return identAuthzFlux
                .transformDeferred(identAuthzDecisionFlux -> addMqttConnectionTimeoutBeforeFirstSaplDecision(
                        mqttClientState, subscriptionId, identAuthzDecisionFlux))
                .transform(identAuthzDecisionFlux -> addMqttConnectionTimeoutOnSaplDecision(identAuthzDecisionFlux,
                        mqttClientState))
                .doOnError(TimeoutException.class, timeoutException -> handleMqttConnectionTimeout(mqttClientState))
                .onErrorResume(TimeoutException.class, timeoutException -> Flux.empty());
    }

    private void handleMqttConnectionTimeout(MqttClientState mqttClientState) {
        String clientId = mqttClientState.getClientId();
        log.info("The connection of client '{}' timed out.", clientId);
        Services.clientService().disconnectClient(clientId, false, NOT_AUTHORIZED, NOT_AUTHORIZED_NOTICE); // does
                                                                                                           // notify
                                                                                                           // client
        String subscriptionId = buildSubscriptionId(clientId, CONNECT_AUTHZ_ACTION);
        removeIdentAuthzDecisionFromCache(mqttClientState, subscriptionId);
        log.info("Disconnected client '{}'.", clientId);
    }

    private Flux<IdentifiableAuthorizationDecision> addMqttConnectionTimeoutBeforeFirstSaplDecision(
            MqttClientState mqttClientState, String subscriptionId,
            Flux<IdentifiableAuthorizationDecision> identAuthzDecisionFlux) {
        var constraintDetails = getConstraintDetailsFromCache(mqttClientState, subscriptionId);
        if (constraintDetails != null && constraintDetails.getTimeLimitSec() > 0) {
            return identAuthzDecisionFlux
                    .timeout(Duration.ofMillis(getRemainingTimeLimitMillis(constraintDetails.getTimeLimitSec(),
                            getMqttActionStartTimeFromCache(mqttClientState, subscriptionId))))
                    .takeUntilOther(identAuthzDecisionFlux).mergeWith(identAuthzDecisionFlux);
        } else {
            return identAuthzDecisionFlux;
        }
    }

    private Flux<IdentifiableAuthorizationDecision> addMqttConnectionTimeoutOnSaplDecision(
            Flux<IdentifiableAuthorizationDecision> identAuthzDecisionFlux, MqttClientState mqttClientState) {
        Flux<IdentifiableAuthorizationDecision> sharedFlux = identAuthzDecisionFlux.share();
        return sharedFlux.switchMap(identAuthzDecision -> buildMqttConnectionTimeoutFluxOfSaplDecision(mqttClientState,
                sharedFlux, identAuthzDecision));
    }

    private Flux<IdentifiableAuthorizationDecision> buildMqttConnectionTimeoutFluxOfSaplDecision(
            MqttClientState mqttClientState, Flux<IdentifiableAuthorizationDecision> sharedFlux,
            IdentifiableAuthorizationDecision identAuthzDecision) {
        String subscriptionId    = identAuthzDecision.getAuthorizationSubscriptionId();
        var    constraintDetails = getConstraintDetailsFromCache(mqttClientState, subscriptionId);
        if (constraintDetails != null && constraintDetails.getTimeLimitSec() > 0) {
            Mono<IdentifiableAuthorizationDecision> timeoutFlux = sharedFlux.ignoreElements()
                    .timeout(Duration.ofMillis(getRemainingTimeLimitMillis(constraintDetails.getTimeLimitSec(),
                            getMqttActionStartTimeFromCache(mqttClientState, subscriptionId))));
            return Flux.merge(Flux.just(identAuthzDecision), timeoutFlux);
        } else {
            return Flux.just(identAuthzDecision);
        }
    }

    private Flux<IdentifiableAuthorizationDecision> buildMqttConnectionEnforcementFlux(
            Flux<IdentifiableAuthorizationDecision> identAuthzDecisionFlux, MqttClientState mqttClientState,
            MqttSaplId mqttSaplId, @Nullable Async<SimpleAuthOutput> asyncOutput) {
        if (asyncOutput != null) { // on connection event
            return identAuthzDecisionFlux.doOnNext(
                    identAuthzDecision -> enforceMqttConnectionOnDecision(asyncOutput, mqttSaplId, identAuthzDecision));
        } else { // existing connection
            cacheCurrentTimeAsStartTimeOfMqttAction(mqttClientState, mqttSaplId.getSaplSubscriptionId());
            return identAuthzDecisionFlux;
        }
    }

    private void enforceMqttConnectionOnDecision(Async<SimpleAuthOutput> asyncOutput, MqttSaplId mqttSaplId,
            IdentifiableAuthorizationDecision identAuthzDecision) {
        var mqttClientState = mqttClientCache.get(mqttSaplId.getMqttClientId());
        if (mqttClientState == null) {
            return;
        }
        var authzDecision = identAuthzDecision.getAuthorizationDecision();
        log.debug("Enforcing authorization decision of mqtt connection  of client '{}': {}",
                mqttSaplId.getMqttClientId(), authzDecision);

        String  subscriptionId                    = identAuthzDecision.getAuthorizationSubscriptionId();
        boolean hasHandledObligationsSuccessfully = hasHandledObligationsSuccessfully(mqttClientState, subscriptionId);
        boolean isSuccessfullyGranted             = enforceMqttConnectionByHiveMqBroker(asyncOutput.getOutput(),
                authzDecision, hasHandledObligationsSuccessfully, mqttSaplId);
        if (isSuccessfullyGranted) {
            cacheCurrentTimeAsStartTimeOfMqttAction(mqttClientState, subscriptionId);
        }

        // Resume output to tell the HiveMQ broker that asynchronous processing is done
        asyncOutput.resume();
    }

    private Flux<IdentifiableAuthorizationDecision> buildMqttSubscriptionTimeLimitFlux(MqttClientState mqttClientState,
            MqttSaplId mqttSaplId, Flux<IdentifiableAuthorizationDecision> identAuthzFlux,
            Async<SubscriptionAuthorizerOutput> asyncOutput) {
        return identAuthzFlux
                .transformDeferred(identAuthzDecisionFlux -> addMqttSubscriptionTimeoutBeforeFirstSaplDecision(
                        identAuthzDecisionFlux, mqttClientState, mqttSaplId))
                .transform(identAuthzDecisionFlux -> addMqttSubscriptionTimeoutOnSaplDecision(identAuthzDecisionFlux,
                        mqttClientState, asyncOutput, mqttSaplId))
                .doOnError(TimeoutException.class,
                        timeoutException -> handleMqttSubscriptionTimeout(mqttClientState, mqttSaplId))
                .onErrorResume(TimeoutException.class, timeoutException -> Flux.empty());
    }

    private Flux<IdentifiableAuthorizationDecision> addMqttSubscriptionTimeoutBeforeFirstSaplDecision(
            Flux<IdentifiableAuthorizationDecision> identAuthzDecisionFlux, MqttClientState mqttClientState,
            MqttSaplId mqttSaplId) {
        var constraintDetails = getConstraintDetailsFromCache(mqttClientState, mqttSaplId.getSaplSubscriptionId());
        if (constraintDetails != null && constraintDetails.getTimeLimitSec() > 0) {
            if (!isMqttSubscriptionExisting(mqttSaplId)) {
                return identAuthzDecisionFlux;
            }
            return identAuthzDecisionFlux
                    .timeout(Duration.ofMillis(getRemainingTimeLimitMillis(constraintDetails.getTimeLimitSec(),
                            getMqttActionStartTimeFromCache(mqttClientState, mqttSaplId.getSaplSubscriptionId()))))
                    .takeUntilOther(identAuthzDecisionFlux).mergeWith(identAuthzDecisionFlux);
        } else {
            return identAuthzDecisionFlux;
        }
    }

    private Flux<IdentifiableAuthorizationDecision> addMqttSubscriptionTimeoutOnSaplDecision(
            Flux<IdentifiableAuthorizationDecision> identAuthzDecisionFlux, MqttClientState mqttClientState,
            Async<SubscriptionAuthorizerOutput> asyncOutput, MqttSaplId mqttSaplId) {
        Flux<IdentifiableAuthorizationDecision> sharedFlux = identAuthzDecisionFlux.share();
        return sharedFlux
                .switchMap(identAuthzDecision -> buildMqttSubscriptionTimeoutFluxOfSaplDecision(mqttClientState,
                        sharedFlux, identAuthzDecision, asyncOutput, mqttSaplId));
    }

    private Flux<IdentifiableAuthorizationDecision> buildMqttSubscriptionTimeoutFluxOfSaplDecision(
            MqttClientState mqttClientState, Flux<IdentifiableAuthorizationDecision> sharedFlux,
            IdentifiableAuthorizationDecision identAuthzDecision, Async<SubscriptionAuthorizerOutput> asyncOutput,
            MqttSaplId mqttSaplId) {
        String subscriptionId    = identAuthzDecision.getAuthorizationSubscriptionId();
        var    constraintDetails = getConstraintDetailsFromCache(mqttClientState, subscriptionId);
        if (constraintDetails != null && constraintDetails.getTimeLimitSec() > 0) {
            if (!isMqttSubscriptionExisting(mqttSaplId)
                    && (Objects.isNull(asyncOutput) || (asyncOutput.getStatus() != RUNNING))) {
                // in case no mqtt subscription existing and no mqtt subscription is pending to
                // be established
                return Flux.just(identAuthzDecision);
            }
            Mono<IdentifiableAuthorizationDecision> timeoutFlux = sharedFlux.ignoreElements()
                    .timeout(Duration.ofMillis(getRemainingTimeLimitMillis(constraintDetails.getTimeLimitSec(),
                            getMqttActionStartTimeFromCache(mqttClientState, subscriptionId))));
            return Flux.merge(Flux.just(identAuthzDecision), timeoutFlux);
        } else {
            return Flux.just(identAuthzDecision);
        }
    }

    private void handleMqttSubscriptionTimeout(MqttClientState mqttClientState, MqttSaplId mqttSaplId) {
        log.info("The mqtt subscription of topic '{}' of client '{}' timed out.", mqttSaplId.getTopic(),
                mqttSaplId.getMqttClientId());
        unsubscribeClientOfTopic(mqttClientState, mqttSaplId);
    }

    private Flux<IdentifiableAuthorizationDecision> buildSaplSubscriptionTimeoutFlux(
            Flux<IdentifiableAuthorizationDecision> identAuthzDecisionFlux, MqttClientState mqttClientState,
            MqttSaplId mqttSaplId) {
        Flux<IdentifiableAuthorizationDecision> sharedIdentAuthzDecisionFlux = identAuthzDecisionFlux.publish()
                .refCount(2); // connectable flux is necessary to add cancellable timeout behaviour

        Flux<IdentifiableAuthorizationDecision> timeoutOnSaplSubscriptionStartFlux = Flux
                .<IdentifiableAuthorizationDecision>never()
                .transformDeferred(
                        neverFlux -> addSaplSubscriptionTimeoutBeforeFirstSaplDecision(mqttSaplId, neverFlux))
                .takeUntilOther(sharedIdentAuthzDecisionFlux);

        Flux<IdentifiableAuthorizationDecision> timeoutOnDecisionFlux = sharedIdentAuthzDecisionFlux
                .switchMap(identAuthzDecision -> addSaplSubscriptionTimeoutOnSaplDecision(mqttSaplId,
                        identAuthzDecision, sharedIdentAuthzDecisionFlux));

        return Flux.merge(timeoutOnSaplSubscriptionStartFlux, timeoutOnDecisionFlux)
                .doOnError(TimeoutException.class,
                        timeoutException -> handleSaplAuthzSubscriptionTimeout(mqttClientState, mqttSaplId))
                .onErrorResume(TimeoutException.class, timeoutException -> Flux.empty());
    }

    private Flux<IdentifiableAuthorizationDecision> addSaplSubscriptionTimeoutBeforeFirstSaplDecision(
            MqttSaplId mqttSaplId, Flux<IdentifiableAuthorizationDecision> decisionFlux) {
        if (!isMqttSubscriptionExisting(mqttSaplId)) { // mqtt subscription does not exist
            return decisionFlux.timeout(getAuthzSubscriptionTimeoutDuration(saplMqttExtensionConfig,
                    mqttClientCache.get(mqttSaplId.getMqttClientId()), mqttSaplId.getSaplSubscriptionId()));
        }
        return decisionFlux;
    }

    private Flux<IdentifiableAuthorizationDecision> addSaplSubscriptionTimeoutOnSaplDecision(MqttSaplId mqttSaplId,
            IdentifiableAuthorizationDecision identAuthzDecision,
            Flux<IdentifiableAuthorizationDecision> sharedIdentAuthzDecisionFlux) {
        String subscriptionId = identAuthzDecision.getAuthorizationSubscriptionId();
        if (identAuthzDecision.getAuthorizationDecision().getDecision() != PERMIT) { // mqtt subscription is denied
            return Flux.merge(Flux.just(identAuthzDecision),
                    Flux.<IdentifiableAuthorizationDecision>never()
                            .timeout(getAuthzSubscriptionTimeoutDuration(saplMqttExtensionConfig,
                                    mqttClientCache.get(mqttSaplId.getMqttClientId()), subscriptionId))
                            // stop timeout if underlying flux terminates
                            .takeUntilOther(sharedIdentAuthzDecisionFlux.ignoreElements()));
        } else { // mqtt subscription is permitted
            if (isMqttSubscriptionExisting(mqttSaplId)) { // mqtt subscription does exist
                return Flux.just(identAuthzDecision);
            } else { // mqtt subscription does not exist
                return Flux.merge(Flux.just(identAuthzDecision),
                        Flux.<IdentifiableAuthorizationDecision>never()
                                .timeout(getAuthzSubscriptionTimeoutDuration(saplMqttExtensionConfig,
                                        mqttClientCache.get(mqttSaplId.getMqttClientId()), subscriptionId))
                                // stop timeout if underlying flux terminates
                                .takeUntilOther(sharedIdentAuthzDecisionFlux.ignoreElements()));
            }
        }
    }

    private Flux<IdentifiableAuthorizationDecision> buildMqttSubscriptionEnforcementFlux(
            Flux<IdentifiableAuthorizationDecision> identAuthzDecisionFlux, MqttSaplId mqttSaplId,
            @Nullable Async<SubscriptionAuthorizerOutput> asyncOutput) {
        if (asyncOutput != null) {
            return identAuthzDecisionFlux.doOnNext(identAuthzDecision -> enforceMqttSubscriptionOnDecision(asyncOutput,
                    mqttSaplId, identAuthzDecision));
        } else {
            return identAuthzDecisionFlux;
        }
    }

    private void enforceMqttSubscriptionOnDecision(Async<SubscriptionAuthorizerOutput> asyncOutput,
            MqttSaplId mqttSaplId, IdentifiableAuthorizationDecision identAuthzDecision) {
        String clientId        = mqttSaplId.getMqttClientId();
        var    mqttClientState = mqttClientCache.get(clientId);
        if (mqttClientState == null) {
            return;
        }

        log.debug("Enforcing authorization decision of mqtt subscription of topic '{}' of client '{}': {}",
                mqttSaplId.getTopic(), clientId, identAuthzDecision.getAuthorizationDecision());
        boolean hasHandledObligationsSuccessfully = hasHandledObligationsSuccessfully(mqttClientState,
                mqttSaplId.getSaplSubscriptionId());
        var     subscriptionAuthorizerOutput      = asyncOutput.getOutput();
        enforceMqttSubscriptionByHiveMqBroker(subscriptionAuthorizerOutput,
                identAuthzDecision.getAuthorizationDecision(), hasHandledObligationsSuccessfully, mqttSaplId);

        // Resume output to tell the HiveMQ broker that asynchronous processing is done
        asyncOutput.resume();
    }

    private Flux<IdentifiableAuthorizationDecision> buildMqttPublishEnforcementFlux(
            Flux<IdentifiableAuthorizationDecision> identAuthzDecisionFlux, PublishInboundOutput publishInboundOutput,
            Async<PublishInboundOutput> asyncOutput, MqttSaplId mqttSaplId) {
        return identAuthzDecisionFlux.doOnNext(identAuthzDecision -> {
            if (asyncOutput.getStatus() == Async.Status.RUNNING) {
                enforcePublishConstraints(mqttSaplId, publishInboundOutput, identAuthzDecision);
                enforceMqttPublishOnDecision(asyncOutput, mqttSaplId, publishInboundOutput, identAuthzDecision);
            }
        });
    }

    private void enforceMqttPublishOnDecision(Async<PublishInboundOutput> asyncOutput, MqttSaplId mqttSaplId,
            PublishInboundOutput publishInboundOutput, IdentifiableAuthorizationDecision identAuthzDecision) {
        var mqttClientState = mqttClientCache.get(mqttSaplId.getMqttClientId());
        if (mqttClientState == null) {
            return;
        }

        String  subscriptionId                    = identAuthzDecision.getAuthorizationSubscriptionId();
        var     authzDecision                     = identAuthzDecision.getAuthorizationDecision();
        boolean hasHandledObligationsSuccessfully = hasHandledObligationsSuccessfully(mqttClientState, subscriptionId);
        enforceMqttPublishByHiveMqBroker(publishInboundOutput, authzDecision, hasHandledObligationsSuccessfully,
                mqttSaplId);

        // Resume output to tell the HiveMQ broker that asynchronous processing is done
        asyncOutput.resume();
    }

    private void handleSaplAuthzSubscriptionTimeout(MqttClientState mqttClientState, MqttSaplId mqttSaplId) {
        String subscriptionId = mqttSaplId.getSaplSubscriptionId();
        log.debug("The sapl subscription to the pdp for client '{}' with action '{}' and topic '{}' timed out.",
                mqttClientState.getClientId(), subscriptionId, mqttSaplId.getTopic());
        removeSaplAuthzSubscriptionFromMultiSubscription(subscriptionId, mqttClientState);
        removeMqttActionDecisionFluxFromCache(mqttClientState, subscriptionId);
        disposeAllFluxesOfClient(mqttClientState); // dispose shared and mqtt action decision fluxes
        removeIdentAuthzDecisionFromCache(mqttClientState, subscriptionId);
        removeConstraintDetailsFromCache(mqttClientState, subscriptionId);
        removeStartTimeOfMqttActionFromCache(mqttClientState, subscriptionId);
        removeLastSignalTimeFromCache(mqttClientState, subscriptionId);
        removeMqttTopicSubscriptionFromCache(mqttClientState, subscriptionId);

        buildAndSubscribeNewSharedClientDecisionFlux(mqttClientState);
        subscribeToMqttActionDecisionFluxes(mqttClientState);
    }

    private void subscribeToNewSharedClientDecisionFlux(MqttClientState mqttClientState, MqttSaplId mqttSaplId,
            AuthorizationSubscription saplAuthzSubscription) {
        addSaplAuthzSubscriptionToMultiSubscription(mqttSaplId.getSaplSubscriptionId(), mqttClientState,
                saplAuthzSubscription);
        disposeAllDecisionFluxesOfClientAndSubscribeNewSharedClientDecisionFlux(mqttClientState);
    }

    private void disposeAllDecisionFluxesOfClientAndSubscribeNewSharedClientDecisionFlux(
            MqttClientState mqttClientState) {
        disposeAllFluxesOfClient(mqttClientState); // dispose shared client and mqtt action decision fluxes
        buildAndSubscribeNewSharedClientDecisionFlux(mqttClientState);
    }

    private void disposeAllFluxesOfClient(MqttClientState mqttClientState) {
        disposeMqttActionDecisionFluxes(mqttClientState);
        disposeSharedClientDecisionFlux(mqttClientState);
    }

    private void buildAndSubscribeNewSharedClientDecisionFlux(MqttClientState mqttClientState) {
        Flux<Map<String, IdentifiableAuthorizationDecision>> sharedClientAuthzDecisionFlux = buildSharedClientAuthzDecisionFlux(
                mqttClientState.getClientId());
        mqttClientState.setSharedClientDecisionFlux(sharedClientAuthzDecisionFlux);

        mqttClientState.addSharedClientDecisionFluxDisposableToComposite(
                mqttClientState.getSharedClientDecisionFlux().subscribe());
    }

    private Flux<Map<String, IdentifiableAuthorizationDecision>> buildSharedClientAuthzDecisionFlux(String clientId) {
        var mqttClientState = mqttClientCache.get(clientId);
        if (mqttClientState == null) { // client already disconnected
            return Flux.empty();
        }

        return pdp.decide(mqttClientState.getMultiAuthzSubscription())
                .doOnNext(identAuthzDecision -> enforceEstablishedMqttConnectionAndSubscription(clientId,
                        mqttClientState.getMultiAuthzSubscription(), identAuthzDecision))
                .map(identAuthzDecision -> saveIdentAuthzDecisionAndTransformToMap(mqttClientState, identAuthzDecision))
                .replay(1).refCount().onErrorResume(CancellationException.class, e -> Mono.empty()); // already
                                                                                                     // disconnected
    }

    private void enforceEstablishedMqttConnectionAndSubscription(String clientId,
            MultiAuthorizationSubscription multiAuthzSubscription,
            IdentifiableAuthorizationDecision identAuthzDecision) {
        String subscriptionId = identAuthzDecision.getAuthorizationSubscriptionId();
        var    decision       = identAuthzDecision.getAuthorizationDecision().getDecision();
        if ((decision == INDETERMINATE || decision == NOT_APPLICABLE) && subscriptionId == null) {
            log.warn("Received '{}' decision for client '{}'. Denying access.", decision, clientId);
            cancelExistingMqttSubscriptions(clientId);
            disconnectMqttClient(clientId, decision);

        } else {
            var authzSubscription = multiAuthzSubscription.getAuthorizationSubscriptionWithId(subscriptionId);
            if (authzSubscription == null) {
                return;
            }

            log.debug("Received authorization decision for mqtt action '{}' of client '{}': {}",
                    authzSubscription.getAction().get(ENVIRONMENT_AUTHZ_ACTION_TYPE).asText(), clientId,
                    identAuthzDecision.getAuthorizationDecision());
            if (CONNECT_AUTHZ_ACTION
                    .equals(authzSubscription.getAction().get(ENVIRONMENT_AUTHZ_ACTION_TYPE).asText())) {
                enforceConnectionConstraints(clientId, subscriptionId, identAuthzDecision);
                enforceEstablishedMqttConnection(clientId, identAuthzDecision);
            }
            if (SUBSCRIBE_AUTHZ_ACTION
                    .equals(authzSubscription.getAction().get(ENVIRONMENT_AUTHZ_ACTION_TYPE).asText())) {
                enforceSubscriptionConstraints(clientId, subscriptionId, authzSubscription, identAuthzDecision);
                enforceEstablishedMqttSubscription(clientId, subscriptionId, decision, authzSubscription);
            }
        }
    }

    private void enforceEstablishedMqttConnection(String clientId,
            IdentifiableAuthorizationDecision identAuthzDecision) {
        var mqttClientState = mqttClientCache.get(clientId);
        if (mqttClientState == null) {
            return;
        }

        String  subscriptionId                    = identAuthzDecision.getAuthorizationSubscriptionId();
        var     decision                          = identAuthzDecision.getAuthorizationDecision().getDecision();
        boolean hasHandledObligationsSuccessfully = hasHandledObligationsSuccessfully(mqttClientState, subscriptionId);
        if (decision != PERMIT || !hasHandledObligationsSuccessfully) { // in case access is denied
            Services.clientService().disconnectClient(clientId, false, NOT_AUTHORIZED, NOT_AUTHORIZED_NOTICE); // does
                                                                                                               // notify
                                                                                                               // client
            removeIdentAuthzDecisionFromCache(mqttClientState, subscriptionId);
            log.info("Disconnected client '{}'.", clientId);
        }
    }

    private void enforceEstablishedMqttSubscription(String clientId, String subscriptionId, Decision decision,
            AuthorizationSubscription authzSubscription) {
        var mqttClientState = mqttClientCache.get(clientId);
        if (mqttClientState == null) {
            return;
        }

        String  topic                             = authzSubscription.getResource().get(ENVIRONMENT_TOPIC).asText();
        var     mqttSaplId                        = new MqttSaplId(clientId, subscriptionId, topic);
        boolean hasHandledObligationsSuccessfully = hasHandledObligationsSuccessfully(mqttClientState,
                mqttSaplId.getSaplSubscriptionId());
        // unsubscribe in case a mqtt subscription is not allowed
        if (decision != PERMIT || !hasHandledObligationsSuccessfully) {
            unsubscribeClientOfTopic(mqttClientState, mqttSaplId);
        }

        // resubscribe in case a mqtt subscription is desired and not existing
        boolean           isResubscribeMqttSubscriptionEnabled = DEFAULT_IS_RESUBSCRIBE_MQTT_SUBSCRIPTION_ENABLED;
        ConstraintDetails constraintDetails                    = getConstraintDetailsFromCache(mqttClientState,
                mqttSaplId.getSaplSubscriptionId());
        if (constraintDetails != null && constraintDetails.getIsResubscribeMqttSubscriptionEnabled() != null) {
            isResubscribeMqttSubscriptionEnabled = constraintDetails.getIsResubscribeMqttSubscriptionEnabled();
        }
        boolean isMqttSubscriptionExisting = isMqttSubscriptionExisting(mqttSaplId);
        if (decision == PERMIT && hasHandledObligationsSuccessfully && !isMqttSubscriptionExisting
                && isResubscribeMqttSubscriptionEnabled) {
            resubscribeClientToTopic(mqttClientState, mqttSaplId);
        }
    }

    private void enforceConnectionConstraints(String clientId, String subscriptionId,
            IdentifiableAuthorizationDecision identAuthzDecision) {
        var mqttClientState = mqttClientCache.get(clientId);
        if (mqttClientState == null) {
            return;
        }

        // handle constraints
        var authzDecision = identAuthzDecision.getAuthorizationDecision();
        log.debug("Enforcing constraints for mqtt connection of authorization decision connection of client '{}': {}",
                clientId, authzDecision);
        var constraintDetails = new ConstraintDetails(clientId, identAuthzDecision);
        ConstraintHandler.enforceMqttConnectionConstraints(constraintDetails);

        if (!constraintDetails.isHasHandledObligationsSuccessfully()) { // unsuccessful obligation handling
            log.warn("Handling of obligations of connection authorization decision '{}' failed "
                    + "for client '{}'. The access will be denied.", authzDecision, clientId);
        }
        cacheConstraintDetails(mqttClientState, subscriptionId, constraintDetails);
    }

    private void enforceSubscriptionConstraints(String clientId, String subscriptionId,
            AuthorizationSubscription authzSubscription, IdentifiableAuthorizationDecision identAuthzDecision) {
        var mqttClientState = mqttClientCache.get(clientId);
        if (mqttClientState == null) {
            return;
        }

        // handle constraints
        var    authzDecision = identAuthzDecision.getAuthorizationDecision();
        String topic         = authzSubscription.getResource().get(ENVIRONMENT_TOPIC).asText();
        log.debug(
                "Enforcing constraints of authorization decision for mqtt subscription of topic '{}' of client '{}': {}",
                topic, clientId, authzDecision);
        var constraintDetails = new ConstraintDetails(clientId, identAuthzDecision, topic);
        ConstraintHandler.enforceMqttSubscriptionConstraints(constraintDetails);

        if (!constraintDetails.isHasHandledObligationsSuccessfully()) { // unsuccessful obligation handling
            log.warn(
                    "Handling of obligations of subscription authorization decision '{}' failed "
                            + "for topic '{}' for client '{}'. The access will be denied.",
                    authzDecision, topic, clientId);
        }
        cacheConstraintDetails(mqttClientState, subscriptionId, constraintDetails);
    }

    private void enforcePublishConstraints(MqttSaplId mqttSaplId, PublishInboundOutput publishInboundOutput,
            IdentifiableAuthorizationDecision identAuthzDecision) {
        String clientId        = mqttSaplId.getMqttClientId();
        var    mqttClientState = mqttClientCache.get(clientId);
        if (mqttClientState == null) {
            return;
        }

        // handle constraints
        var    authzDecision = identAuthzDecision.getAuthorizationDecision();
        String topic         = publishInboundOutput.getPublishPacket().getTopic();
        log.debug("Enforcing constraints of authorization decision for publish of topic '{}' of client '{}': {}", topic,
                clientId, authzDecision);
        var constraintDetails = new ConstraintDetails(clientId, identAuthzDecision, topic, publishInboundOutput);
        ConstraintHandler.enforceMqttPublishConstraints(constraintDetails);

        if (!constraintDetails.isHasHandledObligationsSuccessfully()) { // unsuccessful obligation handling
            log.warn(
                    "Handling of obligations of publish authorization decision '{}' failed "
                            + "for message of topic '{}' for client '{}'. The access will be denied.",
                    authzDecision, publishInboundOutput.getPublishPacket().getTopic(), clientId);
        }
        cacheConstraintDetails(mqttClientState, mqttSaplId.getSaplSubscriptionId(), constraintDetails);
    }

    private void disconnectMqttClient(String clientId, Decision decision) {
        Services.clientService().disconnectClient(clientId, false, NOT_AUTHORIZED,
                String.format("%s. '%s' decision.", NOT_AUTHORIZED_NOTICE, decision));
        var mqttClientState = mqttClientCache.get(clientId);
        if (mqttClientState != null) {
            String subscriptionId = buildSubscriptionId(clientId, CONNECT_AUTHZ_ACTION);
            removeIdentAuthzDecisionFromCache(mqttClientState, subscriptionId);
        }
        log.debug("Disconnected client '{}'", clientId);
    }

    private void cancelExistingMqttSubscriptions(String clientId) {
        // iterate over all subscriptions of client
        Services.subscriptionStore().getSubscriptions(clientId)
                .whenComplete((topicSubscriptions, throwable) -> topicSubscriptions.iterator().forEachRemaining(
                        topicSubscription -> cancelExistingMqttSubscription(clientId, topicSubscription)));
    }

    private void cancelExistingMqttSubscription(String clientId, TopicSubscription topicSubscription) {
        Services.subscriptionStore().removeSubscription(clientId, topicSubscription.getTopicFilter());

        var mqttClientState = mqttClientCache.get(clientId);
        if (mqttClientState != null) {
            String subscriptionId = buildSubscriptionId(clientId, SUBSCRIBE_AUTHZ_ACTION,
                    topicSubscription.getTopicFilter());
            removeIdentAuthzDecisionFromCache(mqttClientState, subscriptionId);
        }
        log.debug("Canceled subscription of topic '{}' of client '{}'", topicSubscription.getTopicFilter(), clientId);
    }

    private void unsubscribeClientOfTopic(MqttClientState mqttClientState, MqttSaplId mqttSaplId) {
        String topic    = mqttSaplId.getTopic();
        String clientId = mqttSaplId.getMqttClientId();
        log.info("Unsubscribing client '{}' from topic '{}'.", clientId, topic);
        Services.subscriptionStore().removeSubscription(clientId, topic); // note that hivemq broker doesn't notify
                                                                          // client

        String subscriptionId = mqttSaplId.getSaplSubscriptionId();
        removeStartTimeOfMqttActionFromCache(mqttClientState, subscriptionId);
        cacheCurrentTimeAsTimeOfLastSignalEvent(mqttClientState, subscriptionId);

        Flux<IdentifiableAuthorizationDecision> subscriptionAuthzTimeoutFlux = buildMqttSubscriptionAuthzFlux(
                mqttSaplId);
        resubscribeMqttActionDecisionFlux(subscriptionId, mqttClientState, subscriptionAuthzTimeoutFlux);
    }

    private void resubscribeClientToTopic(MqttClientState mqttClientState, MqttSaplId mqttSaplId) {
        var mqttTopicSubscription = getMqttTopicSubscriptionFromCache(mqttClientState,
                mqttSaplId.getSaplSubscriptionId());
        Services.subscriptionStore().addSubscription(mqttSaplId.getMqttClientId(), mqttTopicSubscription);
        log.info("Resubscribed client '{}' to topic '{}'", mqttSaplId.getMqttClientId(), mqttSaplId.getTopic());

        Flux<IdentifiableAuthorizationDecision> subscriptionAuthzTimeoutFlux = buildMqttSubscriptionAuthzFlux(
                mqttSaplId);
        resubscribeMqttActionDecisionFlux(mqttSaplId.getSaplSubscriptionId(), mqttClientState,
                subscriptionAuthzTimeoutFlux);
    }

    private void initializeClientCache(String clientId) {
        mqttClientCache.put(clientId, new MqttClientState(clientId));
    }

    private class SaplMqttClientLifecycleEventListener implements ClientLifecycleEventListener {

        @Override
        public void onMqttConnectionStart(@NotNull ConnectionStartInput connectionStartInput) {
            // Initialize client cache
            String clientId = connectionStartInput.getClientInformation().getClientId();
            initializeClientCache(clientId);
            log.debug("Initialized cache for client: '{}'", clientId);
        }

        @Override
        public void onAuthenticationSuccessful(@NotNull AuthenticationSuccessfulInput authenticationSuccessfulInput) {
            log.debug("Client '{}' authenticated successful",
                    authenticationSuccessfulInput.getClientInformation().getClientId());
        }

        @Override
        public void onDisconnect(@NotNull DisconnectEventInput disconnectEventInput) {
            // Dispose existing enforcement when disconnecting client
            String clientId        = disconnectEventInput.getClientInformation().getClientId();
            var    mqttClientState = mqttClientCache.get(clientId);

            if (mqttClientState != null) {
                mqttClientCache.remove(clientId);
                mqttClientState.disposeMqttActionDecisionFluxes();
                mqttClientState.disposeSharedClientDecisionFlux();
            }
            log.debug("Disposed existing enforcements while disconnecting client '{}'", clientId);
        }
    }
}
