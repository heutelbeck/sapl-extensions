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
package io.sapl.mqtt.pep.cache;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.hivemq.extension.sdk.api.services.subscription.TopicSubscription;

import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.AuthorizationSubscriptionElements;
import io.sapl.api.pdp.IdentifiableAuthorizationDecision;
import io.sapl.api.pdp.MultiAuthorizationSubscription;
import io.sapl.mqtt.pep.constraint.ConstraintDetails;
import lombok.Getter;
import lombok.Setter;
import reactor.core.Disposable;
import reactor.core.Disposables;
import reactor.core.publisher.Flux;

/**
 * Used to cache the state of a mqtt client to enforce different mqtt actions.
 */
public final class MqttClientState {

    @Getter
    private final String clientId;
    @Setter
    @Getter
    private String       userName;

    private final MultiAuthorizationSubscription                     multiAuthzSubscription = new MultiAuthorizationSubscription();
    private final HashMap<String, IdentifiableAuthorizationDecision> identAuthzDecisionMap  = new HashMap<>();
    private final HashMap<String, ConstraintDetails>                 constraintDetailsMap   = new HashMap<>();

    @Setter
    @Getter
    private Flux<Map<String, IdentifiableAuthorizationDecision>> sharedClientDecisionFlux;
    private Disposable.Composite                                 sharedClientDecisionFluxDisposable = Disposables
            .composite();

    private final HashMap<String, Flux<IdentifiableAuthorizationDecision>> mqttActionDecisionFluxesMap                  = new HashMap<>();
    private final ConcurrentMap<String, Disposable>                        mqttActionDecisionFluxesDisposablesMap       = new ConcurrentHashMap<>();
    private Disposable.Composite                                           mqttActionDecisionFluxesDisposablesComposite = Disposables
            .composite();

    private final HashMap<String, Long> mqttActionStartTimeMap = new HashMap<>();
    private final HashMap<String, Long> lastSignalTimeMap      = new HashMap<>();

    private final HashMap<String, TopicSubscription> mqttTopicSubscriptionMap    = new HashMap<>();
    private final HashMap<Integer, List<String>>     unsubscribeMessageTopicsMap = new HashMap<>();

    /**
     * Creates an object to cache the current state of a mqtt client.
     *
     * @param clientId the unique identifier of the mqtt client
     */
    public MqttClientState(String clientId) {
        this.clientId = clientId;
    }

    /**
     * Returns a copy of the identifiable authorization decision map.
     *
     * @return returns an unmodifiable copy
     */
    public Map<String, IdentifiableAuthorizationDecision> getIdentAuthzDecisionMap() {
        return new HashMap<>(identAuthzDecisionMap);
    }

    /**
     * Adds the {@link IdentifiableAuthorizationDecision} to the cache under the
     * given reference. Any existing value under the reference will be hereby
     * overridden.
     *
     * @param id the reference for the {@link IdentifiableAuthorizationDecision}
     * @param identAuthzDecision the {@link IdentifiableAuthorizationDecision} to
     * add
     */
    public void addIdentAuthzDecisionToMap(String id, IdentifiableAuthorizationDecision identAuthzDecision) {
        identAuthzDecisionMap.put(id, identAuthzDecision);
    }

    /**
     * Removes the referenced {@link IdentifiableAuthorizationDecision} from the
     * {@link Map}.
     *
     * @param id references the {@link IdentifiableAuthorizationDecision}
     */
    public void removeIdentAuthzDecisionFromMap(String id) {
        identAuthzDecisionMap.remove(id);
    }

    /**
     * Adds the given unsubscribe message topics list to the state.
     *
     * @param id the reference under which the topics list is to add
     * @param messageTopics unsubscribe message topics list to add
     */
    public void addUnsubscribeMessageTopicsToMap(Integer id, List<String> messageTopics) {
        unsubscribeMessageTopicsMap.put(id, messageTopics);
    }

    /**
     * Removes the referenced list of unsubscribe message topics from the
     * {@link Map}.
     *
     * @param id the reference for unsubscribe message topics list
     * @return the previous list or null in case there was no mapping
     */
    public List<String> removeUnsubscribeMessageTopicsFromMap(Integer id) {
        return unsubscribeMessageTopicsMap.remove(id);
    }

    /**
     * Checks whether the list of unsubscribe message topics {@link Map} is empty or
     * not.
     *
     * @return true if there is no mapping
     */
    public boolean isUnsubscribeMessageTopicsMapEmpty() {
        return unsubscribeMessageTopicsMap.isEmpty();
    }

    /**
     * Gets the referenced {@link TopicSubscription} form the state.
     *
     * @param id the reference of the topic subscription to return
     * @return the referenced topic subscription
     */
    public TopicSubscription getTopicSubscriptionFromMap(String id) {
        return mqttTopicSubscriptionMap.get(id);
    }

    /**
     * Adds the given {@link TopicSubscription} to the state.
     *
     * @param id the reference under which the {@link TopicSubscription} is to add
     * @param topicSubscription the {@link TopicSubscription} to add
     */
    public void addTopicSubscriptionToMap(String id, TopicSubscription topicSubscription) {
        mqttTopicSubscriptionMap.put(id, topicSubscription);
    }

    /**
     * Removes the referenced {@link TopicSubscription} from the state.
     *
     * @param id the reference of the {@link TopicSubscription} to remove
     */
    public void removeTopicSubscriptionFromMap(String id) {
        mqttTopicSubscriptionMap.remove(id);
    }

    /**
     * Returns the time of the last signal from the state.
     *
     * @param id the reference of the last signal time
     * @return the last signal time
     */
    public Long getLastSignalTimeFromMap(String id) {
        return lastSignalTimeMap.get(id);
    }

    /**
     * Removes the time of the last signal from the state.
     *
     * @param id the reference of the last signal time to remove
     */
    public void removeLastSignalTimeFromMap(String id) {
        lastSignalTimeMap.remove(id);
    }

    /**
     * Adds the given last signal time to the state.
     *
     * @param id the reference under which the given time is to add
     * @param lastSignalTime the last signal time to add
     */
    public void addLastSignalTimeToMap(String id, Long lastSignalTime) {
        lastSignalTimeMap.put(id, lastSignalTime);
    }

    /**
     * Gets the referenced mqtt action start from the state.
     *
     * @param id the reference of the mqtt actions start time to return
     * @return the referenced mqtt action start time
     */
    public Long getMqttActionStartTimeFromMap(String id) {
        return mqttActionStartTimeMap.get(id);
    }

    /**
     * Adds the given mqtt action start time to the state.
     *
     * @param id the reference under which the mqtt action start time is to add
     * @param mqttActionStartTime the mqtt action start time to add
     */
    public void addMqttActionStartTimeToMap(String id, Long mqttActionStartTime) {
        mqttActionStartTimeMap.put(id, mqttActionStartTime);
    }

    /**
     * Removes the referenced mqtt action start time from the state.
     *
     * @param id the reference of the mqtt action start time to remove
     */
    public void removeMqttActionStartTimeFromMap(String id) {
        mqttActionStartTimeMap.remove(id);
    }

    /**
     * Removes the mqtt action decision flux {@link Disposable} from the state.
     *
     * @param id the reference of the mqtt action decision flux {@link Disposable}
     * to remove
     * @return the removed mqtt action decision flux {@link Disposable} or null in
     * case there was no mapping
     */
    public Disposable removeMqttActionDecisionFluxDisposableFromMap(String id) {
        return mqttActionDecisionFluxesDisposablesMap.remove(id);
    }

    /**
     * Gets the mqtt action decision flux {@link Disposable} from the state.
     *
     * @param id the reference of the mqtt action decision flux {@link Disposable}
     * to return
     * @return the referenced mqtt action decision flux {@link Disposable}
     */
    public Disposable getMqttActionDecisionFluxDisposableFromMap(String id) {
        return mqttActionDecisionFluxesDisposablesMap.get(id);
    }

    /**
     * Adds the mqtt action decision flux {@link Disposable} to the state.
     *
     * @param id the reference under which the {@link Disposable} is to add
     * @param disposable the {@link Disposable} to add
     */
    public void addMqttActionDecisionFluxDisposableToMap(String id, Disposable disposable) {
        mqttActionDecisionFluxesDisposablesMap.put(id, disposable);
    }

    /**
     * Removes all mqtt action decision flux {@link Disposable} from the state.
     */
    public void clearMqttActionDecisionFluxDisposableMap() {
        mqttActionDecisionFluxesDisposablesMap.clear();
    }

    /**
     * Adds the given mqtt action decision {@link Flux} of
     * {@link IdentifiableAuthorizationDecision} to the state.
     *
     * @param id the reference under which the mqtt action decision {@link Flux} of
     * {@link IdentifiableAuthorizationDecision} s to add
     * @param identAuthzFlux the mqtt action decision {@link Flux} of
     * {@link IdentifiableAuthorizationDecision} to add
     */
    public void addMqttActionDecisionFluxToMap(String id, Flux<IdentifiableAuthorizationDecision> identAuthzFlux) {
        mqttActionDecisionFluxesMap.put(id, identAuthzFlux);
    }

    /**
     * Gets the referenced mqtt action decision {@link Flux} of
     * {@link IdentifiableAuthorizationDecision} from the state.
     *
     * @param id the reference of the mqtt action decision flux to return
     * @return the referenced mqtt action decision {@link Flux} of
     * {@link IdentifiableAuthorizationDecision}
     */
    public Flux<IdentifiableAuthorizationDecision> getMqttActionDecisionFluxFromMap(String id) {
        return mqttActionDecisionFluxesMap.get(id);
    }

    /**
     * Removes the referenced mqtt action decision {@link Flux} of
     * {@link IdentifiableAuthorizationDecision} from the state.
     *
     * @param id the reference of the mqtt action decision {@link Flux} of
     * {@link IdentifiableAuthorizationDecision} to remove
     */
    public void removeMqttActionDecisionFluxFromMap(String id) {
        mqttActionDecisionFluxesMap.remove(id);
    }

    /**
     * Gets all mqtt action decision {@link Flux} of
     * {@link IdentifiableAuthorizationDecision} from the state.
     *
     * @return all mqtt action decision {@link Flux} of
     * {@link IdentifiableAuthorizationDecision} from the state
     */
    public Set<Map.Entry<String, Flux<IdentifiableAuthorizationDecision>>> getMqttActionDecisionFluxesFromMap() {
        return mqttActionDecisionFluxesMap.entrySet();
    }

    /**
     * Gets the referenced {@link ConstraintDetails} from the state.
     *
     * @param id the reference of the {@link ConstraintDetails} to return
     * @return the referenced {@link ConstraintDetails}
     */
    public ConstraintDetails getConstraintDetailsFromMap(String id) {
        return constraintDetailsMap.get(id);
    }

    /**
     * Adds the given {@link ConstraintDetails} to the state.
     *
     * @param id the reference of the {@link ConstraintDetails} to add
     * @param constraintDetails the {@link ConstraintDetails} to add
     */
    public void addConstraintDetailsToMap(String id, ConstraintDetails constraintDetails) {
        constraintDetailsMap.put(id, constraintDetails);
    }

    /**
     * Removes the referenced {@link ConstraintDetails} from the state.
     *
     * @param id the reference of the {@link ConstraintDetails} to remove
     */
    public void removeConstraintDetailsFromMap(String id) {
        constraintDetailsMap.remove(id);
    }

    /**
     * Returns a copy of the {@link MultiAuthorizationSubscription} of the state.
     *
     * @return a copy of the {@link MultiAuthorizationSubscription}
     */
    public MultiAuthorizationSubscription getMultiAuthzSubscription() {
        var multiAuthzSubscriptionCopy = new MultiAuthorizationSubscription();
        multiAuthzSubscriptionCopy
                .setAuthorizationSubscriptions(multiAuthzSubscription.getAuthorizationSubscriptions());
        multiAuthzSubscriptionCopy.setSubjects(multiAuthzSubscription.getSubjects());
        multiAuthzSubscriptionCopy.setActions(multiAuthzSubscription.getActions());
        multiAuthzSubscriptionCopy.setResources(multiAuthzSubscription.getResources());
        multiAuthzSubscriptionCopy.setEnvironments(multiAuthzSubscription.getEnvironments());
        return multiAuthzSubscriptionCopy;
    }

    /**
     * Adds the given {@link AuthorizationSubscription} to the
     * {@link MultiAuthorizationSubscription}.
     *
     * @param id the reference under which the {@link AuthorizationSubscription} is
     * to add
     * @param authzSubscription the {@link AuthorizationSubscription} to add
     */
    public void addSaplAuthzSubscriptionToMultiSubscription(String id, AuthorizationSubscription authzSubscription) {
        multiAuthzSubscription.addAuthorizationSubscription(id, authzSubscription);
    }

    /**
     * Removes the referenced {@link AuthorizationSubscription} from the
     * {@link MultiAuthorizationSubscription} of the state.
     *
     * @param id the reference of the {@link AuthorizationSubscription} to remove
     */
    public void removeSaplAuthzSubscriptionFromMultiSubscription(String id) {
        Map<String, AuthorizationSubscriptionElements> authzSubscriptionElementsMap = multiAuthzSubscription
                .getAuthorizationSubscriptions();

        AuthorizationSubscriptionElements authzSubscriptionElementsToRemove = authzSubscriptionElementsMap.remove(id);
        if (authzSubscriptionElementsToRemove != null) {
            multiAuthzSubscription.getSubjects().remove((int) authzSubscriptionElementsToRemove.getSubjectId());
            multiAuthzSubscription.getActions().remove((int) authzSubscriptionElementsToRemove.getActionId());
            multiAuthzSubscription.getResources().remove((int) authzSubscriptionElementsToRemove.getResourceId());
            multiAuthzSubscription.getEnvironments().remove((int) authzSubscriptionElementsToRemove.getEnvironmentId());
        }
    }

    /**
     * Gets the {@link AuthorizationSubscription} from the
     * {@link MultiAuthorizationSubscription} of the state.
     *
     * @param id the reference of the {@link AuthorizationSubscription} to return
     * @return the referenced {@link AuthorizationSubscription} of the
     * {@link MultiAuthorizationSubscription}
     */
    public AuthorizationSubscription getSaplAuthzSubscriptionFromMultiSubscription(String id) {
        return multiAuthzSubscription.getAuthorizationSubscriptionWithId(id);
    }

    /**
     * Disposes the shared client decision flux.
     */
    public void disposeSharedClientDecisionFlux() {
        sharedClientDecisionFluxDisposable.dispose();
    }

    /**
     * Checks whether the client decision flux is disposed.
     *
     * @return true if the client decision flux is disposed, false otherwise
     */
    public boolean isSharedClientDecisionFluxDisposed() {
        return sharedClientDecisionFluxDisposable.isDisposed();
    }

    /**
     * Adds the given {@link Disposable} to the shared client decision flux
     * {@link reactor.core.Disposable.Composite}.
     *
     * @param disposable the {@link Disposable} to add
     */
    public void addSharedClientDecisionFluxDisposableToComposite(Disposable disposable) {
        sharedClientDecisionFluxDisposable.add(disposable);
    }

    /**
     * Disposes the shared client decision flux and sets a new
     * {@link reactor.core.Disposable.Composite}.
     */
    public void disposeSharedClientDecisionFluxAndSetNewComposite() {
        disposeSharedClientDecisionFlux();
        sharedClientDecisionFluxDisposable = Disposables.composite();
    }

    /**
     * Disposes the mqtt action decision fluxes.
     */
    public void disposeMqttActionDecisionFluxes() {
        mqttActionDecisionFluxesDisposablesComposite.dispose();
    }

    /**
     * Adds the given {@link Disposable} of the mqtt action decision flux to the
     * {@link reactor.core.Disposable.Composite}.
     *
     * @param disposable the {@link Disposable} to add
     * @return true if the {@link Disposable} could be added, false otherwise
     */
    public boolean addMqttActionDecisionFluxDisposableToComposite(Disposable disposable) {
        return mqttActionDecisionFluxesDisposablesComposite.add(disposable);
    }

    /**
     * Checks whether the mqtt action decision fluxes are disposed or not.
     *
     * @return true if the {@link reactor.core.Disposable.Composite} was disposed,
     * false otherwise
     */
    public boolean areMqttActionDecisionFluxesDisposed() {
        return mqttActionDecisionFluxesDisposablesComposite.isDisposed();
    }

    /**
     * Disposes the mqtt action decision fluxes and sets a new
     * {@link reactor.core.Disposable.Composite}.
     */
    public void disposeMqttActionDecisionFluxesAndSetNewComposite() {
        var fluxesToDispose = mqttActionDecisionFluxesDisposablesComposite;
        mqttActionDecisionFluxesDisposablesComposite = Disposables.composite();
        fluxesToDispose.dispose();
    }

    /**
     * Removes the given {@link Disposable} from the mqtt action decision flux
     * {@link reactor.core.Disposable.Composite}.
     *
     * @param disposable the {@link Disposable} to remove
     */
    public void removeMqttActionDecisionFluxDisposableFromComposite(Disposable disposable) {
        mqttActionDecisionFluxesDisposablesComposite.remove(disposable);
    }
}
