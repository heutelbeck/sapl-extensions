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

import com.hivemq.extension.sdk.api.auth.parameter.SimpleAuthInput;
import com.hivemq.extension.sdk.api.client.parameter.ConnectionInformation;
import com.hivemq.extension.sdk.api.client.parameter.ServerInformation;
import com.hivemq.extension.sdk.api.packets.connect.ConnectPacket;
import com.hivemq.extension.sdk.api.packets.connect.WillPublishPacket;
import com.hivemq.extension.sdk.api.packets.general.MqttVersion;
import com.hivemq.extension.sdk.api.packets.general.Qos;
import com.hivemq.extension.sdk.api.packets.publish.PayloadFormatIndicator;
import com.hivemq.extension.sdk.api.services.Services;
import com.hivemq.extension.sdk.api.services.admin.AdminService;
import com.hivemq.extension.sdk.api.services.admin.LicenseEdition;
import com.hivemq.extension.sdk.api.services.admin.LicenseInformation;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.mqtt.pep.cache.MqttClientState;
import lombok.NonNull;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static io.sapl.mqtt.pep.util.SaplSubscriptionUtility.ENVIRONMENT_LAST_WILL_LAST_WILL_PAYLOAD;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class SaplSubscriptionUtilityTest {

    @Test
    void when_buildingAuthzSubscriptionAndNoLastWillPayloadIsSpecified_then_setNullNodeAsLastWillPayloadInAuthzSubscription() {
        // GIVEN
        MqttClientState mqttClientStateMock = mock(MqttClientState.class);

        SimpleAuthInput simpleAuthInputMock = mock(SimpleAuthInput.class);
        ConnectPacket connectPacketMock = mock(ConnectPacket.class);
        WillPublishPacket willPublishPacketMock = mock(WillPublishPacket.class);
        when(willPublishPacketMock.getQos()).thenReturn(Qos.AT_MOST_ONCE);
        Optional<WillPublishPacket> willPublishPacketMockOption = Optional.of(willPublishPacketMock);
        when(connectPacketMock.getWillPublish()).thenReturn(willPublishPacketMockOption);
        when(connectPacketMock.getCleanStart()).thenReturn(true);
        when(simpleAuthInputMock.getConnectPacket()).thenReturn(connectPacketMock);
        mockConnectionInformation(simpleAuthInputMock);

        AdminService adminServiceMock = mockAdminService();

        try (MockedStatic<Services> servicesMockedStatic = mockStatic(Services.class)) {
            servicesMockedStatic.when(Services::adminService).thenReturn(adminServiceMock);

            // WHEN
            AuthorizationSubscription authzSubscription = SaplSubscriptionUtility
                    .buildSaplAuthzSubscriptionForMqttConnection(mqttClientStateMock, simpleAuthInputMock);

            // THEN
            assertTrue(authzSubscription.getAction().get(ENVIRONMENT_LAST_WILL_LAST_WILL_PAYLOAD).isNull());
        }
    }

    @Test
    void when_buildingAuthzSubscriptionAndLastWillPayloadIsUTF8_then_setNullNodeAsLastWillPayloadInAuthzSubscription() {
        // GIVEN
        MqttClientState mqttClientStateMock = mock(MqttClientState.class);

        SimpleAuthInput simpleAuthInputMock = mock(SimpleAuthInput.class);
        ConnectPacket connectPacketMock = mock(ConnectPacket.class);
        WillPublishPacket willPublishPacketMock = mock(WillPublishPacket.class);
        Optional<PayloadFormatIndicator> optionalPayloadFormatIndicator = Optional.of(PayloadFormatIndicator.UTF_8);
        when(willPublishPacketMock.getPayloadFormatIndicator()).thenReturn(optionalPayloadFormatIndicator);
        ByteBuffer byteBuffer = ByteBuffer.wrap("test".getBytes(StandardCharsets.UTF_8));
        when(willPublishPacketMock.getPayload())
                .thenReturn(Optional.of(byteBuffer));
        when(willPublishPacketMock.getQos()).thenReturn(Qos.AT_MOST_ONCE);
        Optional<WillPublishPacket> willPublishPacketMockOption = Optional.of(willPublishPacketMock);
        when(connectPacketMock.getWillPublish()).thenReturn(willPublishPacketMockOption);
        when(connectPacketMock.getCleanStart()).thenReturn(true);
        when(simpleAuthInputMock.getConnectPacket()).thenReturn(connectPacketMock);
        mockConnectionInformation(simpleAuthInputMock);

        AdminService adminServiceMock = mockAdminService();

        try (MockedStatic<Services> servicesMockedStatic = mockStatic(Services.class)) {
            servicesMockedStatic.when(Services::adminService).thenReturn(adminServiceMock);

            // WHEN
            AuthorizationSubscription authzSubscription = SaplSubscriptionUtility
                    .buildSaplAuthzSubscriptionForMqttConnection(mqttClientStateMock, simpleAuthInputMock);

            // THEN
            assertTrue(authzSubscription.getAction().get(ENVIRONMENT_LAST_WILL_LAST_WILL_PAYLOAD).isTextual());
        }
    }

    private void mockConnectionInformation(SimpleAuthInput simpleAuthInputMock) {
        ConnectionInformation connectionInformationMock = mock(ConnectionInformation.class);
        MqttVersion mqttVersionMock = mock(MqttVersion.class);
        when(mqttVersionMock.toString()).thenReturn("mqttVersion");
        when(connectionInformationMock.getMqttVersion()).thenReturn(mqttVersionMock);
        when(simpleAuthInputMock.getConnectionInformation()).thenReturn(connectionInformationMock);
    }

    @NonNull
    private AdminService mockAdminService() {
        AdminService adminServiceMock = mock(AdminService.class);
        ServerInformation serverInformationMock = mock(ServerInformation.class);
        when(serverInformationMock.getVersion()).thenReturn("testVersion");
        when(adminServiceMock.getServerInformation()).thenReturn(serverInformationMock);
        LicenseInformation licenseInformationMock = mock(LicenseInformation.class);
        LicenseEdition licenseEditionMock = mock(LicenseEdition.class);
        when(licenseEditionMock.toString()).thenReturn("licenseEdition");
        when(licenseInformationMock.getEdition()).thenReturn(licenseEditionMock);
        when(adminServiceMock.getLicenseInformation()).thenReturn(licenseInformationMock);
        return adminServiceMock;
    }
}
