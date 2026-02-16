/*
 * Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.IdentifiableAuthorizationDecision;
import io.sapl.mqtt.pep.cache.MqttClientState;
import io.sapl.mqtt.pep.config.SaplMqttExtensionConfig;
import reactor.core.Disposable;
import reactor.core.Disposables;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

@DisplayName("Decision flux utility")
class DecisionFluxUtilityTests {

    @Test
    void when_identAuthzDecisionMapContainsIndeterminateDecision_then_getIndeterminateDecision() {
        // GIVEN
        var subscriptionId        = "testSubscription";
        var identAuthzDecisionMap = new HashMap<String, IdentifiableAuthorizationDecision>();
        identAuthzDecisionMap.put("", new IdentifiableAuthorizationDecision("", AuthorizationDecision.INDETERMINATE));
        identAuthzDecisionMap.put(subscriptionId,
                new IdentifiableAuthorizationDecision(subscriptionId, AuthorizationDecision.PERMIT));

        // WHEN
        var identAuthzDecision = DecisionFluxUtility.getIdentAuthzDecision(subscriptionId, identAuthzDecisionMap);

        // THEN
        assertThat(identAuthzDecision.decision().decision()).isEqualTo(Decision.INDETERMINATE);
    }

    @Test
    void when_mqttActionDecisionFluxesAlreadyDisposed_then_doNotCreateNewDisposable() {
        // GIVEN
        var disposablesComposite = Disposables.composite();
        var disposableMock       = mock(Disposable.class);
        disposablesComposite.add(disposableMock);
        disposablesComposite.dispose();

        var mqttClientState = new MqttClientState("testClient");
        mqttClientState.addMqttActionDecisionFluxDisposableToComposite(disposableMock);
        mqttClientState.disposeMqttActionDecisionFluxes();

        // WHEN
        DecisionFluxUtility.disposeMqttActionDecisionFluxes(mqttClientState);

        // THEN
        assertThat(mqttClientState.areMqttActionDecisionFluxesDisposed()).isTrue();
    }

    @Test
    void when_sharedClientDecisionFluxAlreadyDisposed_then_doNotCreateNewDisposable() {
        // GIVEN
        var disposableMock  = mock(Disposable.class);
        var mqttClientState = new MqttClientState("testClient");
        mqttClientState.addSharedClientDecisionFluxDisposableToComposite(disposableMock);
        mqttClientState.disposeSharedClientDecisionFlux();

        // WHEN
        DecisionFluxUtility.disposeSharedClientDecisionFlux(mqttClientState);

        // THEN
        assertThat(mqttClientState.isSharedClientDecisionFluxDisposed()).isTrue();
    }

    @Test
    void when_remainingTimeLimitIsBelowZero_then_returnZeroAsRemainingTime() {
        // GIVEN
        var currentTime = Schedulers.parallel().now(TimeUnit.MILLISECONDS);
        var startTime   = currentTime - 10000;
        var timeLimit   = 1;

        // WHEN
        var remainingTimeLimit = DecisionFluxUtility.getRemainingTimeLimitMillis(timeLimit, startTime);

        // THEN
        assertThat(remainingTimeLimit).isZero();
    }

    @Test
    void when_calculatingRemainingTimeLimitAndStartTimeIsZero_then_returnTimeLimit() {
        // GIVEN
        var timeLimit = 50L;
        var startTime = 0L;

        // WHEN
        var remainingTimeLimit = DecisionFluxUtility.getRemainingTimeLimitMillis(timeLimit, startTime);

        // THEN
        assertThat(remainingTimeLimit).isEqualTo(timeLimit * 1_000L);
    }

    @Test
    void when_calculatingTimeoutDurationAndLastSignalIsOlderThanTimeoutInterval_then_returnZeroAsTimeoutDuration() {
        // GIVEN
        var subscriptionId  = "subscriptionId";
        var currentTime     = Schedulers.parallel().now(TimeUnit.MILLISECONDS);
        var lastSignalTime  = currentTime - 100;
        var mqttClientState = new MqttClientState("clientId");
        mqttClientState.addLastSignalTimeToMap(subscriptionId, lastSignalTime);

        var saplMqttExtensionConfigMock = mock(SaplMqttExtensionConfig.class);
        when(saplMqttExtensionConfigMock.getAuthzSubscriptionTimeoutMillis()).thenReturn(50);

        // WHEN
        var timeoutDuration = DecisionFluxUtility.getAuthzSubscriptionTimeoutDuration(saplMqttExtensionConfigMock,
                mqttClientState, subscriptionId);

        // THEN
        assertThat(timeoutDuration.getNano()).isZero();
    }

    @Test
    void when_subscribingToMqttActionDecisionFluxesAndDisposableCouldNotBeAddedToCache_then_doNotAddDisposableToMap() {
        // GIVEN
        var identAuthzDecisionFlux = Flux.just(IdentifiableAuthorizationDecision.INDETERMINATE);
        var mqttClientState        = spy(new MqttClientState("clientId"));
        doReturn(Boolean.FALSE).when(mqttClientState)
                .addMqttActionDecisionFluxDisposableToComposite(any(Disposable.class));
        mqttClientState.addMqttActionDecisionFluxToMap("id", identAuthzDecisionFlux);

        // WHEN
        DecisionFluxUtility.subscribeToMqttActionDecisionFluxes(mqttClientState);

        // THEN
        verify(mqttClientState, never()).addMqttActionDecisionFluxDisposableToMap(anyString(), any(Disposable.class));
    }
}
