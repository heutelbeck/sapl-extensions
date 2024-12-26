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
package io.sapl.mqtt.pep.util;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.hivemq.extension.sdk.api.services.subscription.TopicSubscription;

import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.IdentifiableAuthorizationDecision;
import io.sapl.mqtt.pep.cache.MqttClientState;
import io.sapl.mqtt.pep.config.SaplMqttExtensionConfig;
import io.sapl.mqtt.pep.constraint.ConstraintDetails;
import lombok.experimental.UtilityClass;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

/**
 * This class provides utility functions in the context of the sapl mqtt
 * decision fluxes, especially for interactions with the cached mqtt client
 * states.
 */
@UtilityClass
public class DecisionFluxUtility {

    /**
     * Adds the given SAPL authorization subscription to the cached multi
     * subscription.
     *
     * @param subscriptionId the id to identify the decision flux for mqtt action
     * enforcement
     * @param mqttClientState the cached state of the client
     * @param authzSubscription the SAPL authorization subscription to add
     */
    public static void addSaplAuthzSubscriptionToMultiSubscription(String subscriptionId,
            MqttClientState mqttClientState, AuthorizationSubscription authzSubscription) {
        mqttClientState.addSaplAuthzSubscriptionToMultiSubscription(subscriptionId, authzSubscription);
    }

    /**
     * Removes the SAPL authorization subscription of the referenced mqtt action
     * enforcement decision flux from the multi subscription.
     *
     * @param subscriptionId the id to identify the decision flux for mqtt action
     * enforcement
     * @param mqttClientState the cached state of the client
     */
    public static void removeSaplAuthzSubscriptionFromMultiSubscription(String subscriptionId,
            MqttClientState mqttClientState) {
        mqttClientState.removeSaplAuthzSubscriptionFromMultiSubscription(subscriptionId);
    }

    /**
     * Subscribes to all decision fluxes for mqtt action enforcement. For each
     * reactor subscription the disposable will be cached for later disposing.
     *
     * @param mqttClientState the cached state of the client
     */
    public static void subscribeToMqttActionDecisionFluxes(MqttClientState mqttClientState) {
        Set<Map.Entry<String, Flux<IdentifiableAuthorizationDecision>>> mqttActionDecisionFluxes = mqttClientState
                .getMqttActionDecisionFluxesFromMap();
        for (var mqttActionDecisionFluxEntry : mqttActionDecisionFluxes) {
            var     disposable = mqttActionDecisionFluxEntry.getValue().subscribe();
            boolean isAdded    = mqttClientState.addMqttActionDecisionFluxDisposableToComposite(disposable);
            if (!isAdded) {
                break;
            }
            mqttClientState.addMqttActionDecisionFluxDisposableToMap(mqttActionDecisionFluxEntry.getKey(), disposable);
        }
    }

    /**
     * Disposes all decision fluxes for mqtt action enforcement. Further, a new
     * {@link reactor.core.Disposable.Composite} will be set in the
     * {@link MqttClientState} for potentially new subscriptions of mqtt action
     * enforcement decision fluxes.
     *
     * @param mqttClientState the cached state of the client
     */
    public static void disposeMqttActionDecisionFluxes(MqttClientState mqttClientState) {
        if (!mqttClientState.areMqttActionDecisionFluxesDisposed()) {
            mqttClientState.clearMqttActionDecisionFluxDisposableMap();
            mqttClientState.disposeMqttActionDecisionFluxesAndSetNewComposite();
        } // if the disposable is disposed it indicates that the client is already
          // disconnected
    }

    /**
     * Disposes the shared client decision flux for all mqtt action enforcements of
     * a client. Further, a new {@link reactor.core.Disposable.Composite} will be
     * set in the {@link MqttClientState} for a potentially new subscription of a
     * shared client decision flux.
     *
     * @param mqttClientState the cached state of the client
     */
    public static void disposeSharedClientDecisionFlux(MqttClientState mqttClientState) {
        if (!mqttClientState.isSharedClientDecisionFluxDisposed()) {
            mqttClientState.disposeSharedClientDecisionFluxAndSetNewComposite();
        } // if the disposable is disposed it indicates that the client is already
          // disconnected
    }

    /**
     * Takes the identifiable authorization decision and caches it in a map with
     * other identifiable authorization decisions of the client.
     *
     * @param mqttClientState the cached state of the client
     * @param identAuthzDecision the returned identifiable authorization decision of
     * the pdp
     * @return map of identifiable authorization decisions of the client
     */
    public static Map<String, IdentifiableAuthorizationDecision> saveIdentAuthzDecisionAndTransformToMap(
            MqttClientState mqttClientState, IdentifiableAuthorizationDecision identAuthzDecision) {
        if (identAuthzDecision.getAuthorizationSubscriptionId() != null) {
            // remove indeterminate decision (id is always null)
            mqttClientState.removeIdentAuthzDecisionFromMap(null);
        }
        mqttClientState.addIdentAuthzDecisionToMap(identAuthzDecision.getAuthorizationSubscriptionId(),
                identAuthzDecision);
        return mqttClientState.getIdentAuthzDecisionMap();
    }

    /**
     * Looks up the identifiable authorization decision referenced by the
     * subscription id from the cache.
     *
     * @param subscriptionId the id to identify the decision flux for mqtt action
     * enforcement
     * @param identAuthzDecisionMap map of identifiable authorization decisions of
     * the client
     * @return the referenced identifiable authorization decision
     */
    public static IdentifiableAuthorizationDecision getIdentAuthzDecision(String subscriptionId,
            Map<String, IdentifiableAuthorizationDecision> identAuthzDecisionMap) {
        if (identAuthzDecisionMap.containsKey(null)) {
            return identAuthzDecisionMap.get(null);
        } else {
            return identAuthzDecisionMap.get(subscriptionId);
        }
    }

    /**
     * Evaluates whether the obligations were successfully handled or not.
     *
     * @param mqttClientState the cached state of the client
     * @param subscriptionId the id to identify the decision flux for mqtt action
     * enforcement
     * @return true, if all obligations were handled successfully or no obligations
     * had to be evaluated
     */
    public static boolean hasHandledObligationsSuccessfully(MqttClientState mqttClientState, String subscriptionId) {
        var hasHandledObligationsSuccessfully = false;
        if (subscriptionId != null) {
            var constraintDetails = getConstraintDetailsFromCache(mqttClientState, subscriptionId);
            if (constraintDetails != null) {
                hasHandledObligationsSuccessfully = constraintDetails.isHasHandledObligationsSuccessfully();
            }
        }
        return hasHandledObligationsSuccessfully;
    }

    /**
     * Evaluates whether an identifiable authorization subscription is cached under
     * the subscription id or null.
     *
     * @param subscriptionId the id to identify the decision flux for mqtt action
     * enforcement
     * @param identAuthzDecisionMap map of identifiable authorization decisions of
     * the client
     * @return true, if an identifiable authorization subscription is cached under
     * the subscription id or null
     */
    public static boolean containsAuthzDecisionOfSubscriptionIdOrNull(String subscriptionId,
            Map<String, IdentifiableAuthorizationDecision> identAuthzDecisionMap) {
        return identAuthzDecisionMap.containsKey(subscriptionId) || identAuthzDecisionMap.containsKey(null);
    }

    /**
     * Looks up the referenced authorization subscription from the cached multi
     * authorization subscription.
     *
     * @param mqttClientState the cached state of the client
     * @param subscriptionId the id to identify the decision flux for mqtt action
     * enforcement
     * @return returns the referenced authorization subscription
     */
    public static AuthorizationSubscription getAuthzSubscriptionFromCachedMultiAuthzSubscription(
            MqttClientState mqttClientState, String subscriptionId) {
        return mqttClientState.getSaplAuthzSubscriptionFromMultiSubscription(subscriptionId);
    }

    /**
     * Caches the current time as time of the last mqtt action message.
     *
     * @param mqttClientState the cached state of the client
     * @param subscriptionId the id to identify the decision flux for mqtt action
     * enforcement
     */
    public static void cacheCurrentTimeAsTimeOfLastSignalEvent(MqttClientState mqttClientState, String subscriptionId) {
        mqttClientState.addLastSignalTimeToMap(subscriptionId, Schedulers.parallel().now(TimeUnit.MILLISECONDS));
    }

    /**
     * Removes the time of the last mqtt action message from cache.
     *
     * @param mqttClientState the cached state of the client
     * @param subscriptionId the id to identify the decision flux for mqtt action
     * enforcement
     */
    public static void removeLastSignalTimeFromCache(MqttClientState mqttClientState, String subscriptionId) {
        mqttClientState.removeLastSignalTimeFromMap(subscriptionId);
    }

    /**
     * Caches a flux for enforcement of mqtt actions.
     *
     * @param mqttClientState the cached state of the client
     * @param subscriptionId the id to identify the decision flux for mqtt action
     * enforcement
     * @param mqttActionDecisionFlux the flux for mqtt action enforcement to cache
     */
    public static void cacheMqttActionDecisionFlux(MqttClientState mqttClientState, String subscriptionId,
            Flux<IdentifiableAuthorizationDecision> mqttActionDecisionFlux) {
        mqttClientState.addMqttActionDecisionFluxToMap(subscriptionId, mqttActionDecisionFlux);
    }

    /**
     * Removes the referenced mqtt action decision flux from cache.
     *
     * @param mqttClientState the cached state of the client
     * @param subscriptionId the id to identify the decision flux for mqtt action
     * enforcement
     */
    public static void removeMqttActionDecisionFluxFromCache(MqttClientState mqttClientState, String subscriptionId) {
        mqttClientState.removeMqttActionDecisionFluxFromMap(subscriptionId);
    }

    /**
     * Caches the current time as the start time of the mqtt action of the client.
     *
     * @param mqttClientState the cached state of the client
     * @param subscriptionId the id to identify the decision flux for mqtt action
     * enforcement
     */
    public static void cacheCurrentTimeAsStartTimeOfMqttAction(MqttClientState mqttClientState, String subscriptionId) {
        mqttClientState.addMqttActionStartTimeToMap(subscriptionId, Schedulers.parallel().now(TimeUnit.MILLISECONDS));
    }

    /**
     * Looks up the cached mqtt action start time.
     *
     * @param mqttClientState the cached state of the client
     * @param subscriptionId the id to identify the decision flux for mqtt action
     * enforcement
     * @return returns the cached mqtt action start time
     */
    public static Long getMqttActionStartTimeFromCache(MqttClientState mqttClientState, String subscriptionId) {
        return mqttClientState.getMqttActionStartTimeFromMap(subscriptionId);
    }

    /**
     * Removes the cached start time of the referenced mqtt action.
     *
     * @param mqttClientState the cached state of the client
     * @param subscriptionId the id to identify the decision flux for mqtt action
     * enforcement
     */
    public static void removeStartTimeOfMqttActionFromCache(MqttClientState mqttClientState, String subscriptionId) {
        mqttClientState.removeMqttActionStartTimeFromMap(subscriptionId);
    }

    /**
     * Caches {@link ConstraintDetails} as specifics about the constraint handling
     * for the referenced mqtt action enforcement.
     *
     * @param mqttClientState the cached state of the client
     * @param subscriptionId the id to identify the decision flux for mqtt action
     * enforcement
     * @param constraintDetails enforcement details of the specific constraint
     * handling
     */
    public static void cacheConstraintDetails(MqttClientState mqttClientState, String subscriptionId,
            ConstraintDetails constraintDetails) {
        mqttClientState.addConstraintDetailsToMap(subscriptionId, constraintDetails);
    }

    /**
     * Looks up the cached and referenced details about the constraint handling.
     *
     * @param mqttClientState the cached state of the client
     * @param subscriptionId the id to identify the decision flux for mqtt action
     * enforcement
     * @return returns the referenced {@link ConstraintDetails}
     */
    public static ConstraintDetails getConstraintDetailsFromCache(MqttClientState mqttClientState,
            String subscriptionId) {
        return mqttClientState.getConstraintDetailsFromMap(subscriptionId);
    }

    /**
     * Removes the referenced {@link ConstraintDetails} from the cache.
     *
     * @param mqttClientState the cached state of the client
     * @param subscriptionId the id to identify the decision flux for mqtt action
     * enforcement
     */
    public static void removeConstraintDetailsFromCache(MqttClientState mqttClientState, String subscriptionId) {
        mqttClientState.removeConstraintDetailsFromMap(subscriptionId);
    }

    /**
     * Caches the given mqtt topic subscription.
     *
     * @param mqttClientState the cached state of the client
     * @param subscriptionId the id to identify the decision flux for mqtt action
     * enforcement
     * @param topicSubscription specifics about a mqtt subscription of a topic
     */
    public static void cacheMqttTopicSubscription(MqttClientState mqttClientState, String subscriptionId,
            TopicSubscription topicSubscription) {
        mqttClientState.addTopicSubscriptionToMap(subscriptionId, topicSubscription);
    }

    /**
     * Looks up the referenced mqtt topic subscription from the cache.
     *
     * @param mqttClientState the cached state of the client
     * @param subscriptionId the id to identify the decision flux for mqtt action
     * enforcement
     * @return returns the referenced mqtt topic subscription
     */
    public static TopicSubscription getMqttTopicSubscriptionFromCache(MqttClientState mqttClientState,
            String subscriptionId) {
        return mqttClientState.getTopicSubscriptionFromMap(subscriptionId);
    }

    /**
     * Removes the referenced mqtt topic subscription from the cache.
     *
     * @param mqttClientState the cached state of the client
     * @param subscriptionId the id to identify the decision flux for mqtt action
     * enforcement
     */
    public static void removeMqttTopicSubscriptionFromCache(MqttClientState mqttClientState, String subscriptionId) {
        mqttClientState.removeTopicSubscriptionFromMap(subscriptionId);
    }

    /**
     * Removes the referenced identifiable authorization decision from the cache.
     *
     * @param mqttClientState the cached state of the client
     * @param subscriptionId the id to identify the decision flux for mqtt action
     * enforcement
     */
    public static void removeIdentAuthzDecisionFromCache(MqttClientState mqttClientState, String subscriptionId) {
        mqttClientState.removeIdentAuthzDecisionFromMap(subscriptionId);
    }

    /**
     * Subscribes the given mqtt action decision flux to enforce a mqtt action and
     * caches its disposable.
     *
     * @param mqttClientState the cached state of the client
     * @param subscriptionId the id to identify the decision flux for mqtt action
     * enforcement
     * @param mqttActionFlux the flux to enforce the mqtt action
     */
    public static void subscribeMqttActionDecisionFluxAndCacheDisposable(MqttClientState mqttClientState,
            String subscriptionId, Flux<IdentifiableAuthorizationDecision> mqttActionFlux) {
        var disposable = mqttActionFlux.subscribe();
        cacheMqttActionDecisionFluxDisposable(mqttClientState, subscriptionId, disposable);
    }

    /**
     * Caches the disposable of a mqtt action decision flux and adds it to a
     * {@link reactor.core.Disposable.Composite} in the cache.
     *
     * @param mqttClientState the cached state of the client
     * @param subscriptionId the id to identify the decision flux for mqtt action
     * enforcement
     * @param disposable the disposable of a mqtt action decision flux
     */
    public static void cacheMqttActionDecisionFluxDisposable(MqttClientState mqttClientState, String subscriptionId,
            Disposable disposable) {
        mqttClientState.addMqttActionDecisionFluxDisposableToComposite(disposable);
        mqttClientState.addMqttActionDecisionFluxDisposableToMap(subscriptionId, disposable);
    }

    /**
     * Disposes the referenced mqtt action decision flux and removes its disposable
     * from the cache including removal from the
     * {@link reactor.core.Disposable.Composite}.
     *
     * @param mqttClientState the cached state of the client
     * @param subscriptionId the id to identify the decision flux for mqtt action
     * enforcement
     */
    public static void disposeMqttActionDecisionFluxAndRemoveDisposableFromCache(MqttClientState mqttClientState,
            String subscriptionId) {
        var oldDisposable = mqttClientState.removeMqttActionDecisionFluxDisposableFromMap(subscriptionId);
        if (oldDisposable != null) {
            oldDisposable.dispose();
            mqttClientState.removeMqttActionDecisionFluxDisposableFromComposite(oldDisposable);
        }
    }

    /**
     * Caches a list containing the topics of a mqtt unsubscribe message.
     *
     * @param mqttClientState the cached state of the client
     * @param packetId the id of a mqtt message
     * @param unsubscribeTopics a list of topics contained in the mqtt unsubscribe
     * message
     */
    public static void cacheTopicsOfUnsubscribeMessage(MqttClientState mqttClientState, int packetId,
            List<String> unsubscribeTopics) {
        mqttClientState.addUnsubscribeMessageTopicsToMap(packetId, unsubscribeTopics);
    }

    /**
     * Removes the referenced list of topics of the mqtt unsubscribe message from
     * the cache.
     *
     * @param mqttClientState the cached state of the client
     * @param packetId the id of a mqtt message
     * @return returns the list of topics to unsubscribe that were removed from the
     * cache
     */
    public static List<String> removeTopicsOfUnsubscribeMessageFromCache(MqttClientState mqttClientState,
            int packetId) {
        return mqttClientState.removeUnsubscribeMessageTopicsFromMap(packetId);
    }

    /**
     * Calculates the remaining duration in milliseconds until the set time limit
     * will be reached. If the time limit was already reached, 0 will be returned.
     *
     * @param timeLimitSec the defined time limit in seconds
     * @param startTimeMillis the start time in milliseconds from when the time
     * limit began
     * @return returns the remaining time in milliseconds until the time limit will
     * be reached.
     */
    public static long getRemainingTimeLimitMillis(long timeLimitSec, Long startTimeMillis) {
        long timeLimitMillis = timeLimitSec * 1_000;
        if (startTimeMillis == null || startTimeMillis == 0L) {
            return timeLimitMillis;
        }
        long currentTime        = Schedulers.parallel().now(TimeUnit.MILLISECONDS);
        long elapsedTime        = currentTime - startTimeMillis;
        long remainingTimeLimit = timeLimitMillis - elapsedTime;
        if (remainingTimeLimit < 0) {
            return 0;
        } else {
            return remainingTimeLimit;
        }
    }

    /**
     * Calculates the remaining duration in milliseconds until the set SAPL
     * authorization subscription timeout will be reached. If the timeout was
     * already reached, 0 will be returned.
     *
     * @param saplMqttExtensionConfig the sapl mqtt pep extension config used to
     * look up the timeout interval
     * @param mqttClientState the cached state of the client used to look up the
     * start time of the timeout duration
     * @param subscriptionId the id to identify the decision flux for mqtt action
     * enforcement
     * @return returns the remaining timeout duration in milliseconds
     */
    public static Duration getAuthzSubscriptionTimeoutDuration(SaplMqttExtensionConfig saplMqttExtensionConfig,
            MqttClientState mqttClientState, String subscriptionId) {
        long timeoutInterval = saplMqttExtensionConfig.getAuthzSubscriptionTimeoutMillis();
        long now             = Schedulers.parallel().now(TimeUnit.MILLISECONDS);
        if (mqttClientState != null) {
            Long lastSignalTimeFromMap = mqttClientState.getLastSignalTimeFromMap(subscriptionId);
            long lastSignalTime        = 0;
            if (lastSignalTimeFromMap != null) {
                lastSignalTime = lastSignalTimeFromMap;
            }

            long duration;
            if (now - lastSignalTime < timeoutInterval) {
                duration = timeoutInterval - (now - lastSignalTime);
            } else {
                duration = 0;
            }

            return Duration.ofMillis(duration);
        } else {
            return Duration.ofMillis(0);
        }
    }
}
