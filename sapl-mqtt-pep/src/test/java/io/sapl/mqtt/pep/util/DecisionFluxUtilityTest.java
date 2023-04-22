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

package io.sapl.mqtt.pep.util;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.IdentifiableAuthorizationDecision;
import io.sapl.mqtt.pep.cache.MqttClientState;
import io.sapl.mqtt.pep.config.SaplMqttExtensionConfig;
import org.junit.jupiter.api.Test;
import reactor.core.Disposable;
import reactor.core.Disposables;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class DecisionFluxUtilityTest {

     @Test
    void when_identAuthzDecisionMapContainsIndeterminateDecision_then_getIndeterminateDecision() {
         // GIVEN
         String subscriptionId = "testSubscription";
         HashMap<String, IdentifiableAuthorizationDecision> identAuthzDecisionMap = new HashMap<>();
         identAuthzDecisionMap.put(null,
                 new IdentifiableAuthorizationDecision(null, AuthorizationDecision.INDETERMINATE));
         identAuthzDecisionMap.put(subscriptionId,
                 new IdentifiableAuthorizationDecision(subscriptionId, AuthorizationDecision.PERMIT));

         // WHEN
         IdentifiableAuthorizationDecision identAuthzDecision =
                 DecisionFluxUtility.getIdentAuthzDecision(subscriptionId, identAuthzDecisionMap);

         // THEN
         assertEquals(Decision.INDETERMINATE, identAuthzDecision.getAuthorizationDecision().getDecision());
     }

     @Test
    void when_mqttActionDecisionFluxesAlreadyDisposed_then_doNotCreateNewDisposable() {
         // GIVEN
         Disposable.Composite disposablesComposite = Disposables.composite();
         Disposable disposableMock = mock(Disposable.class);
         disposablesComposite.add(disposableMock);
         disposablesComposite.dispose();

         MqttClientState mqttClientState = new MqttClientState("testClient");
         mqttClientState.addMqttActionDecisionFluxDisposableToComposite(disposableMock);
         mqttClientState.disposeMqttActionDecisionFluxes();

         // WHEN
         DecisionFluxUtility.disposeMqttActionDecisionFluxes(mqttClientState);

         // THEN
         assertTrue(mqttClientState.areMqttActionDecisionFluxesDisposed());
     }

    @Test
    void when_sharedClientDecisionFluxAlreadyDisposed_then_doNotCreateNewDisposable() {
        // GIVEN
        Disposable disposableMock = mock(Disposable.class);
        MqttClientState mqttClientState = new MqttClientState("testClient");
        mqttClientState.addSharedClientDecisionFluxDisposableToComposite(disposableMock);
        mqttClientState.disposeSharedClientDecisionFlux();

        // WHEN
        DecisionFluxUtility.disposeSharedClientDecisionFlux(mqttClientState);

        // THEN
        assertTrue(mqttClientState.isSharedClientDecisionFluxDisposed());
    }

    @Test
    void when_remainingTimeLimitIsBelowZero_then_returnZeroAsRemainingTime() {
         // GIVEN
         long currentTime = Schedulers.parallel().now(TimeUnit.MILLISECONDS);
         long startTime = currentTime - 10000;
         long timeLimit = 1;

         // WHEN
         long remainingTimeLimit = DecisionFluxUtility.getRemainingTimeLimitMillis(timeLimit, startTime);

         // THEN
        assertEquals(0, remainingTimeLimit);
    }

    @Test
    void when_calculatingRemainingTimeLimitAndStartTimeIsZero_then_returnTimeLimit() {
         // GIVEN
         long timeLimit = 50;
         long startTime = 0L;

        // WHEN
        long remainingTimeLimit = DecisionFluxUtility.getRemainingTimeLimitMillis(timeLimit, startTime);

        // THEN
        assertEquals(timeLimit * 1_000, remainingTimeLimit);
    }

    @Test
    void when_calculatingTimeoutDurationAndLastSignalIsOlderThanTimeoutInterval_then_returnZeroAsTimeoutDuration() {
         // GIVEN
        String subscriptionId = "subscriptionId";
        long currentTime = Schedulers.parallel().now(TimeUnit.MILLISECONDS);
        long lastSignalTime = currentTime - 100;
        MqttClientState mqttClientState = new MqttClientState("clientId");
        mqttClientState.addLastSignalTimeToMap(subscriptionId, lastSignalTime);

        SaplMqttExtensionConfig saplMqttExtensionConfigMock = mock(SaplMqttExtensionConfig.class);
        when(saplMqttExtensionConfigMock.getAuthzSubscriptionTimeoutMillis()).thenReturn(50);

         // WHEN
        Duration timeoutDuration =
                DecisionFluxUtility.getAuthzSubscriptionTimeoutDuration(saplMqttExtensionConfigMock, mqttClientState, subscriptionId);

        // THEN
        assertEquals(0, timeoutDuration.getNano());
    }

    @Test
    void when_subscribingToMqttActionDecisionFluxesAndDisposableCouldNotBeAddedToCache_then_doNotAddDisposableToMap() {
         // GIVEN
         Flux<IdentifiableAuthorizationDecision> identAuthzDecisionFlux =
                 Flux.just(IdentifiableAuthorizationDecision.INDETERMINATE);
         MqttClientState mqttClientState = spy(new MqttClientState("clientId"));
         doReturn(false).when(mqttClientState)
                 .addMqttActionDecisionFluxDisposableToComposite(any(Disposable.class));
         mqttClientState.addMqttActionDecisionFluxToMap("id", identAuthzDecisionFlux);

         // WHEN
         DecisionFluxUtility.subscribeToMqttActionDecisionFluxes(mqttClientState);

         // THEN
         verify(mqttClientState, never()).addMqttActionDecisionFluxDisposableToMap(anyString(), any(Disposable.class));
    }
}
