package io.sapl.mqtt.pep;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.Set;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.slf4j.LoggerFactory;

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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import io.sapl.api.pdp.MultiAuthorizationSubscription;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.mqtt.pep.cache.MqttClientState;
import io.sapl.mqtt.pep.config.SaplMqttExtensionConfig;
import io.sapl.mqtt.pep.details.MqttSaplId;
import io.sapl.mqtt.pep.util.DecisionFluxUtility;
import io.sapl.mqtt.pep.util.HiveMqUtility;
import reactor.core.publisher.Flux;

class ExtensionStartUnitTest {

    protected static final Logger rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);

    @BeforeAll
    static void beforeAll() {
        // set logging level
        rootLogger.setLevel(Level.DEBUG);
    }

    @Test
    void when_startingMqttPepExtension_then_enforceExistingMqttConnections() {
        // GIVEN
        SaplMqttExtensionConfig saplMqttExtensionConfigMock = mock(SaplMqttExtensionConfig.class);
        EventRegistry eventRegistryMock = mock(EventRegistry.class);
        SecurityRegistry securityRegistryMock = mock(SecurityRegistry.class);
        SubscriptionStore subscriptionStoreMock = mock(SubscriptionStore.class);
        InitializerRegistry initializerRegistryMock = mock(InitializerRegistry.class);

        // mock pdp
        PolicyDecisionPoint pdpMock = mock(PolicyDecisionPoint.class);
        when(pdpMock.decide(any(MultiAuthorizationSubscription.class))).thenReturn(Flux.never());

        // mocking existing connection
        ClientService clientServiceMock = mock(ClientService.class);
        IterationContext iterationContextMock = mock(IterationContext.class);
        SessionInformation sessionInformationMock = mock(SessionInformation.class);
        when(sessionInformationMock.isConnected()).thenReturn(true);
        when(sessionInformationMock.getClientIdentifier()).thenReturn("testClient");
        when(clientServiceMock.iterateAllClients(any())).thenAnswer(answer -> {
            IterationCallback<SessionInformation> iterationCallback = answer.getArgument(0);
            iterationCallback.iterate(iterationContextMock, sessionInformationMock);
            return null;
        });
        AdminService adminServiceMock = mockHiveMqServerInformation();

        try (MockedStatic<Services> servicesMockedStatic = mockStatic(Services.class);
             MockedStatic<DecisionFluxUtility> decisionFluxUtilityMockedStatic = mockStatic(DecisionFluxUtility.class)) {
            servicesMockedStatic.when(Services::eventRegistry).thenReturn(eventRegistryMock);
            servicesMockedStatic.when(Services::securityRegistry).thenReturn(securityRegistryMock);
            servicesMockedStatic.when(Services::subscriptionStore).thenReturn(subscriptionStoreMock);
            servicesMockedStatic.when(Services::initializerRegistry).thenReturn(initializerRegistryMock);
            servicesMockedStatic.when(Services::clientService).thenReturn(clientServiceMock);
            servicesMockedStatic.when(Services::adminService).thenReturn(adminServiceMock);

            // WHEN
            new MqttPep(pdpMock, saplMqttExtensionConfigMock).startEnforcement();

            // THEN
            decisionFluxUtilityMockedStatic.verify(()->DecisionFluxUtility
                    .subscribeToMqttActionDecisionFluxes(any(MqttClientState.class)));
        }
    }

    @Test
    void when_startingMqttPepExtensionWithExistingClientSessionsAndNoClientConnection_then_doNotEnforceClientConnection() {
        // GIVEN
        SaplMqttExtensionConfig saplMqttExtensionConfigMock = mock(SaplMqttExtensionConfig.class);
        EventRegistry eventRegistryMock = mock(EventRegistry.class);
        SecurityRegistry securityRegistryMock = mock(SecurityRegistry.class);
        SubscriptionStore subscriptionStoreMock = mock(SubscriptionStore.class);
        InitializerRegistry initializerRegistryMock = mock(InitializerRegistry.class);

        // mock pdp
        PolicyDecisionPoint pdpMock = mock(PolicyDecisionPoint.class);
        when(pdpMock.decide(any(MultiAuthorizationSubscription.class))).thenReturn(Flux.never());

        // mocking existing client session
        ClientService clientServiceMock = mock(ClientService.class);
        IterationContext iterationContextMock = mock(IterationContext.class);
        SessionInformation sessionInformationMock = mock(SessionInformation.class);
        when(sessionInformationMock.isConnected()).thenReturn(false);
        when(clientServiceMock.iterateAllClients(any())).thenAnswer(answer -> {
            IterationCallback<SessionInformation> iterationCallback = answer.getArgument(0);
            iterationCallback.iterate(iterationContextMock, sessionInformationMock);
            return null;
        });

        try (MockedStatic<Services> servicesMockedStatic = mockStatic(Services.class)) {
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
        SaplMqttExtensionConfig saplMqttExtensionConfigMock = mock(SaplMqttExtensionConfig.class);
        SecurityRegistry securityRegistryMock = mock(SecurityRegistry.class);
        InitializerRegistry initializerRegistryMock = mock(InitializerRegistry.class);
        ClientService clientServiceMock = mock(ClientService.class);

        // mock pdp
        PolicyDecisionPoint pdpMock = mock(PolicyDecisionPoint.class);
        when(pdpMock.decide(any(MultiAuthorizationSubscription.class))).thenReturn(Flux.never());

        // mock hivemq server information
        AdminService adminServiceMock = mockHiveMqServerInformation();

        // mocking mqtt connection start
        EventRegistry eventRegistryMock = mock(EventRegistry.class);
        ClientLifecycleEventListenerProviderInput clientLifecycleEventListenerProviderInputMock =
                mock(ClientLifecycleEventListenerProviderInput.class);
        ConnectionStartInput connectionStartInputMock = mock(ConnectionStartInput.class);
        ClientInformation clientInformationMock = mock(ClientInformation.class);
        when(connectionStartInputMock.getClientInformation()).thenReturn(clientInformationMock);
        when(clientInformationMock.getClientId()).thenReturn("clientId");
        doAnswer(answer -> {
            ClientLifecycleEventListenerProvider clientLifecycleEventListenerProvider =
                    answer.getArgument(0);
            ClientLifecycleEventListener clientLifecycleEventListener  =
                    clientLifecycleEventListenerProvider
                            .getClientLifecycleEventListener(clientLifecycleEventListenerProviderInputMock);
            clientLifecycleEventListener.onMqttConnectionStart(connectionStartInputMock);
            return null;
        }).when(eventRegistryMock).setClientLifecycleEventListener(any(ClientLifecycleEventListenerProvider.class));

        // mocking subscriptions of client
        IterationContext iterationContextMock = mock(IterationContext.class);
        SubscriptionsForClientResult subscriptionsForClientResultMock = mock(SubscriptionsForClientResult.class);
        Set<TopicSubscription> topicSubscriptionSet = new HashSet<>();
        topicSubscriptionSet.add(new TopicSubscriptionImpl(Topic.topicFromString("testTopic")));
        when(subscriptionsForClientResultMock.getSubscriptions()).thenReturn(topicSubscriptionSet);
        when(subscriptionsForClientResultMock.getClientId()).thenReturn("clientId");

        // iterate over mocked client subscriptions
        SubscriptionStore subscriptionStoreMock = mock(SubscriptionStore.class);
        when(subscriptionStoreMock.iterateAllSubscriptions(any())).thenAnswer(answer-> {
            IterationCallback<SubscriptionsForClientResult> iterationCallback = answer.getArgument(0);
            iterationCallback.iterate(iterationContextMock, subscriptionsForClientResultMock);
            return null;
        });

        try (MockedStatic<Services> servicesMockedStatic = mockStatic(Services.class);
             MockedStatic<HiveMqUtility> hiveMqUtilityMockedStatic = mockStatic(HiveMqUtility.class);
             MockedStatic<DecisionFluxUtility> decisionFluxUtilityMockedStatic = mockStatic(DecisionFluxUtility.class)) {
            servicesMockedStatic.when(Services::securityRegistry).thenReturn(securityRegistryMock);
            servicesMockedStatic.when(Services::eventRegistry).thenReturn(eventRegistryMock);
            servicesMockedStatic.when(Services::initializerRegistry).thenReturn(initializerRegistryMock);
            servicesMockedStatic.when(Services::clientService).thenReturn(clientServiceMock);
            servicesMockedStatic.when(Services::subscriptionStore).thenReturn(subscriptionStoreMock);
            servicesMockedStatic.when(Services::adminService).thenReturn(adminServiceMock);
            hiveMqUtilityMockedStatic.when(()->HiveMqUtility.isMqttSubscriptionExisting(any(MqttSaplId.class)))
                    .thenReturn(true);

            // WHEN
            new MqttPep(pdpMock, saplMqttExtensionConfigMock).startEnforcement();

            // THEN
            decisionFluxUtilityMockedStatic.verify(()->DecisionFluxUtility
                    .subscribeToMqttActionDecisionFluxes(any(MqttClientState.class)));
        }
    }

    @NotNull
    private AdminService mockHiveMqServerInformation() {
        // mock hivemq server information
        AdminService adminServiceMock = mock(AdminService.class);
        ServerInformation serverInformationMock = mock(ServerInformation.class);
        LicenseInformation licenseInformationMock = mock(LicenseInformation.class);
        when(adminServiceMock.getServerInformation()).thenReturn(serverInformationMock);
        when(serverInformationMock.getVersion()).thenReturn("2019.1");
        when(adminServiceMock.getLicenseInformation()).thenReturn(licenseInformationMock);
        when(licenseInformationMock.getEdition()).thenReturn(LicenseEdition.COMMUNITY);
        return adminServiceMock;
    }
}