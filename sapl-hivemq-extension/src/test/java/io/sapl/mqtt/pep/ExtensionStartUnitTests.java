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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashSet;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import com.hivemq.extension.sdk.api.client.parameter.ClientInformation;
import com.hivemq.extension.sdk.api.client.parameter.ServerInformation;
import com.hivemq.extension.sdk.api.events.EventRegistry;
import com.hivemq.extension.sdk.api.events.client.ClientLifecycleEventListener;
import com.hivemq.extension.sdk.api.events.client.ClientLifecycleEventListenerProvider;
import com.hivemq.extension.sdk.api.events.client.parameters.ClientLifecycleEventListenerProviderInput;
import com.hivemq.extension.sdk.api.events.client.parameters.ConnectionStartInput;
import com.hivemq.extension.sdk.api.services.Services;
import com.hivemq.extension.sdk.api.services.admin.AdminService;
import com.hivemq.extension.sdk.api.services.admin.LicenseEdition;
import com.hivemq.extension.sdk.api.services.admin.LicenseInformation;
import com.hivemq.extension.sdk.api.services.auth.SecurityRegistry;
import com.hivemq.extension.sdk.api.services.general.IterationCallback;
import com.hivemq.extension.sdk.api.services.general.IterationContext;
import com.hivemq.extension.sdk.api.services.intializer.InitializerRegistry;
import com.hivemq.extension.sdk.api.services.session.ClientService;
import com.hivemq.extension.sdk.api.services.session.SessionInformation;
import com.hivemq.extension.sdk.api.services.subscription.SubscriptionStore;
import com.hivemq.extension.sdk.api.services.subscription.SubscriptionsForClientResult;
import com.hivemq.extension.sdk.api.services.subscription.TopicSubscription;
import com.hivemq.extensions.services.subscription.TopicSubscriptionImpl;
import com.hivemq.mqtt.message.subscribe.Topic;

import io.sapl.api.pdp.MultiAuthorizationSubscription;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.mqtt.pep.cache.MqttClientState;
import io.sapl.mqtt.pep.config.SaplMqttExtensionConfig;
import io.sapl.mqtt.pep.details.MqttSaplId;
import io.sapl.mqtt.pep.util.DecisionFluxUtility;
import io.sapl.mqtt.pep.util.HiveMqUtility;
import reactor.core.publisher.Flux;

class ExtensionStartUnitTests {

    @Test
    void when_startingMqttPepExtension_then_enforceExistingMqttConnections() {
        // GIVEN
        var saplMqttExtensionConfigMock = mock(SaplMqttExtensionConfig.class);
        var eventRegistryMock           = mock(EventRegistry.class);
        var securityRegistryMock        = mock(SecurityRegistry.class);
        var subscriptionStoreMock       = mock(SubscriptionStore.class);
        var initializerRegistryMock     = mock(InitializerRegistry.class);

        // mock pdp
        var pdpMock = mock(PolicyDecisionPoint.class);
        when(pdpMock.decide(any(MultiAuthorizationSubscription.class))).thenReturn(Flux.never());

        // mocking existing connection
        var clientServiceMock      = mock(ClientService.class);
        var iterationContextMock   = mock(IterationContext.class);
        var sessionInformationMock = mock(SessionInformation.class);
        when(sessionInformationMock.isConnected()).thenReturn(Boolean.TRUE);
        when(sessionInformationMock.getClientIdentifier()).thenReturn("testClient");
        when(clientServiceMock.iterateAllClients(any())).thenAnswer(answer -> {
            IterationCallback<SessionInformation> iterationCallback = answer.getArgument(0);
            iterationCallback.iterate(iterationContextMock, sessionInformationMock);
            return null;
        });
        var adminServiceMock = mockHiveMqServerInformation();

        try (var servicesMockedStatic = mockStatic(Services.class);
                var decisionFluxUtilityMockedStatic = mockStatic(DecisionFluxUtility.class)) {
            servicesMockedStatic.when(Services::eventRegistry).thenReturn(eventRegistryMock);
            servicesMockedStatic.when(Services::securityRegistry).thenReturn(securityRegistryMock);
            servicesMockedStatic.when(Services::subscriptionStore).thenReturn(subscriptionStoreMock);
            servicesMockedStatic.when(Services::initializerRegistry).thenReturn(initializerRegistryMock);
            servicesMockedStatic.when(Services::clientService).thenReturn(clientServiceMock);
            servicesMockedStatic.when(Services::adminService).thenReturn(adminServiceMock);

            // WHEN
            new MqttPep(pdpMock, saplMqttExtensionConfigMock).startEnforcement();

            // THEN
            decisionFluxUtilityMockedStatic
                    .verify(() -> DecisionFluxUtility.subscribeToMqttActionDecisionFluxes(any(MqttClientState.class)));
        }
    }

    @Test
    void when_startingMqttPepExtensionWithExistingClientSessionsAndNoClientConnection_then_doNotEnforceClientConnection() {
        // GIVEN
        var saplMqttExtensionConfigMock = mock(SaplMqttExtensionConfig.class);
        var eventRegistryMock           = mock(EventRegistry.class);
        var securityRegistryMock        = mock(SecurityRegistry.class);
        var subscriptionStoreMock       = mock(SubscriptionStore.class);
        var initializerRegistryMock     = mock(InitializerRegistry.class);

        // mock pdp
        var pdpMock = mock(PolicyDecisionPoint.class);
        when(pdpMock.decide(any(MultiAuthorizationSubscription.class))).thenReturn(Flux.never());

        // mocking existing client session
        var clientServiceMock      = mock(ClientService.class);
        var iterationContextMock   = mock(IterationContext.class);
        var sessionInformationMock = mock(SessionInformation.class);
        when(sessionInformationMock.isConnected()).thenReturn(Boolean.FALSE);
        when(clientServiceMock.iterateAllClients(any())).thenAnswer(answer -> {
            IterationCallback<SessionInformation> iterationCallback = answer.getArgument(0);
            iterationCallback.iterate(iterationContextMock, sessionInformationMock);
            return null;
        });

        try (var servicesMockedStatic = mockStatic(Services.class)) {
            servicesMockedStatic.when(Services::eventRegistry).thenReturn(eventRegistryMock);
            servicesMockedStatic.when(Services::securityRegistry).thenReturn(securityRegistryMock);
            servicesMockedStatic.when(Services::subscriptionStore).thenReturn(subscriptionStoreMock);
            servicesMockedStatic.when(Services::initializerRegistry).thenReturn(initializerRegistryMock);
            servicesMockedStatic.when(Services::clientService).thenReturn(clientServiceMock);

            // WHEN
            new MqttPep(pdpMock, saplMqttExtensionConfigMock).startEnforcement();

            // THEN
            verify(sessionInformationMock, never()).getClientIdentifier();
        }
    }

    @Test
    void when_startingMqttPepExtension_then_enforceExistingMqttSubscriptions() {
        // GIVEN
        var saplMqttExtensionConfigMock = mock(SaplMqttExtensionConfig.class);
        var securityRegistryMock        = mock(SecurityRegistry.class);
        var initializerRegistryMock     = mock(InitializerRegistry.class);
        var clientServiceMock           = mock(ClientService.class);

        // mock pdp
        var pdpMock = mock(PolicyDecisionPoint.class);
        when(pdpMock.decide(any(MultiAuthorizationSubscription.class))).thenReturn(Flux.never());

        // mock hivemq server information
        var adminServiceMock = mockHiveMqServerInformation();

        // mocking mqtt connection start
        var eventRegistryMock                             = mock(EventRegistry.class);
        var clientLifecycleEventListenerProviderInputMock = mock(ClientLifecycleEventListenerProviderInput.class);
        var connectionStartInputMock                      = mock(ConnectionStartInput.class);
        var clientInformationMock                         = mock(ClientInformation.class);
        when(connectionStartInputMock.getClientInformation()).thenReturn(clientInformationMock);
        when(clientInformationMock.getClientId()).thenReturn("clientId");
        doAnswer(answer -> {
            ClientLifecycleEventListenerProvider clientLifecycleEventListenerProvider = answer.getArgument(0);
            ClientLifecycleEventListener         clientLifecycleEventListener         = clientLifecycleEventListenerProvider
                    .getClientLifecycleEventListener(clientLifecycleEventListenerProviderInputMock);
            clientLifecycleEventListener.onMqttConnectionStart(connectionStartInputMock);
            return null;
        }).when(eventRegistryMock).setClientLifecycleEventListener(any(ClientLifecycleEventListenerProvider.class));

        // mocking subscriptions of client
        var iterationContextMock             = mock(IterationContext.class);
        var subscriptionsForClientResultMock = mock(SubscriptionsForClientResult.class);
        var topicSubscriptionSet             = new HashSet<TopicSubscription>();
        topicSubscriptionSet.add(new TopicSubscriptionImpl(Topic.topicFromString("testTopic")));
        when(subscriptionsForClientResultMock.getSubscriptions()).thenReturn(topicSubscriptionSet);
        when(subscriptionsForClientResultMock.getClientId()).thenReturn("clientId");

        // iterate over mocked client subscriptions
        var subscriptionStoreMock = mock(SubscriptionStore.class);
        when(subscriptionStoreMock.iterateAllSubscriptions(any())).thenAnswer(answer -> {
            IterationCallback<SubscriptionsForClientResult> iterationCallback = answer.getArgument(0);
            iterationCallback.iterate(iterationContextMock, subscriptionsForClientResultMock);
            return null;
        });

        try (var servicesMockedStatic = mockStatic(Services.class);
                var hiveMqUtilityMockedStatic = mockStatic(HiveMqUtility.class);
                var decisionFluxUtilityMockedStatic = mockStatic(DecisionFluxUtility.class)) {
            servicesMockedStatic.when(Services::securityRegistry).thenReturn(securityRegistryMock);
            servicesMockedStatic.when(Services::eventRegistry).thenReturn(eventRegistryMock);
            servicesMockedStatic.when(Services::initializerRegistry).thenReturn(initializerRegistryMock);
            servicesMockedStatic.when(Services::clientService).thenReturn(clientServiceMock);
            servicesMockedStatic.when(Services::subscriptionStore).thenReturn(subscriptionStoreMock);
            servicesMockedStatic.when(Services::adminService).thenReturn(adminServiceMock);
            hiveMqUtilityMockedStatic.when(() -> HiveMqUtility.isMqttSubscriptionExisting(any(MqttSaplId.class)))
                    .thenReturn(Boolean.TRUE);

            // WHEN
            new MqttPep(pdpMock, saplMqttExtensionConfigMock).startEnforcement();

            // THEN
            decisionFluxUtilityMockedStatic
                    .verify(() -> DecisionFluxUtility.subscribeToMqttActionDecisionFluxes(any(MqttClientState.class)));
        }
    }

    @NotNull
    private AdminService mockHiveMqServerInformation() {
        var adminServiceMock       = mock(AdminService.class);
        var serverInformationMock  = mock(ServerInformation.class);
        var licenseInformationMock = mock(LicenseInformation.class);
        when(adminServiceMock.getServerInformation()).thenReturn(serverInformationMock);
        when(serverInformationMock.getVersion()).thenReturn("2019.1");
        when(adminServiceMock.getLicenseInformation()).thenReturn(licenseInformationMock);
        when(licenseInformationMock.getEdition()).thenReturn(LicenseEdition.COMMUNITY);
        return adminServiceMock;
    }
}
