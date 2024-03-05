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
package io.sapl.mqtt.pep.util;

import static io.sapl.mqtt.pep.util.SaplSubscriptionUtility.ENVIRONMENT_LAST_WILL_LAST_WILL_PAYLOAD;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.junit.jupiter.api.Test;

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

import io.sapl.mqtt.pep.cache.MqttClientState;
import lombok.NonNull;

class SaplSubscriptionUtilityTests {

	@Test
	void when_buildingAuthzSubscriptionAndNoLastWillPayloadIsSpecified_then_setNullNodeAsLastWillPayloadInAuthzSubscription() {
		// GIVEN
		var mqttClientStateMock = mock(MqttClientState.class);

		var simpleAuthInputMock   = mock(SimpleAuthInput.class);
		var connectPacketMock     = mock(ConnectPacket.class);
		var willPublishPacketMock = mock(WillPublishPacket.class);
		when(willPublishPacketMock.getQos()).thenReturn(Qos.AT_MOST_ONCE);
		var willPublishPacketMockOption = Optional.of(willPublishPacketMock);
		when(connectPacketMock.getWillPublish()).thenReturn(willPublishPacketMockOption);
		when(connectPacketMock.getCleanStart()).thenReturn(true);
		when(simpleAuthInputMock.getConnectPacket()).thenReturn(connectPacketMock);
		mockConnectionInformation(simpleAuthInputMock);

		var adminServiceMock = mockAdminService();

		try (var servicesMockedStatic = mockStatic(Services.class)) {
			servicesMockedStatic.when(Services::adminService).thenReturn(adminServiceMock);

			// WHEN
			var authzSubscription = SaplSubscriptionUtility
					.buildSaplAuthzSubscriptionForMqttConnection(mqttClientStateMock, simpleAuthInputMock);

			// THEN
			assertTrue(authzSubscription.getAction().get(ENVIRONMENT_LAST_WILL_LAST_WILL_PAYLOAD).isNull());
		}
	}

	@Test
	void when_buildingAuthzSubscriptionAndLastWillPayloadIsUTF8_then_setNullNodeAsLastWillPayloadInAuthzSubscription() {
		// GIVEN
		var mqttClientStateMock            = mock(MqttClientState.class);
		var simpleAuthInputMock            = mock(SimpleAuthInput.class);
		var connectPacketMock              = mock(ConnectPacket.class);
		var willPublishPacketMock          = mock(WillPublishPacket.class);
		var optionalPayloadFormatIndicator = Optional.of(PayloadFormatIndicator.UTF_8);
		when(willPublishPacketMock.getPayloadFormatIndicator()).thenReturn(optionalPayloadFormatIndicator);
		var byteBuffer = ByteBuffer.wrap("test".getBytes(StandardCharsets.UTF_8));
		when(willPublishPacketMock.getPayload()).thenReturn(Optional.of(byteBuffer));
		when(willPublishPacketMock.getQos()).thenReturn(Qos.AT_MOST_ONCE);
		var willPublishPacketMockOption = Optional.of(willPublishPacketMock);
		when(connectPacketMock.getWillPublish()).thenReturn(willPublishPacketMockOption);
		when(connectPacketMock.getCleanStart()).thenReturn(true);
		when(simpleAuthInputMock.getConnectPacket()).thenReturn(connectPacketMock);
		mockConnectionInformation(simpleAuthInputMock);

		var adminServiceMock = mockAdminService();

		try (var servicesMockedStatic = mockStatic(Services.class)) {
			servicesMockedStatic.when(Services::adminService).thenReturn(adminServiceMock);

			// WHEN
			var authzSubscription = SaplSubscriptionUtility
					.buildSaplAuthzSubscriptionForMqttConnection(mqttClientStateMock, simpleAuthInputMock);

			// THEN
			assertTrue(authzSubscription.getAction().get(ENVIRONMENT_LAST_WILL_LAST_WILL_PAYLOAD).isTextual());
		}
	}

	private void mockConnectionInformation(SimpleAuthInput simpleAuthInputMock) {
		var connectionInformationMock = mock(ConnectionInformation.class);
		var mqttVersionMock           = mock(MqttVersion.class);
		when(mqttVersionMock.toString()).thenReturn("mqttVersion");
		when(connectionInformationMock.getMqttVersion()).thenReturn(mqttVersionMock);
		when(simpleAuthInputMock.getConnectionInformation()).thenReturn(connectionInformationMock);
	}

	@NonNull
	private AdminService mockAdminService() {
		var adminServiceMock      = mock(AdminService.class);
		var serverInformationMock = mock(ServerInformation.class);
		when(serverInformationMock.getVersion()).thenReturn("testVersion");
		when(adminServiceMock.getServerInformation()).thenReturn(serverInformationMock);
		var licenseInformationMock = mock(LicenseInformation.class);
		var licenseEditionMock     = mock(LicenseEdition.class);
		when(licenseEditionMock.toString()).thenReturn("licenseEdition");
		when(licenseInformationMock.getEdition()).thenReturn(licenseEditionMock);
		when(adminServiceMock.getLicenseInformation()).thenReturn(licenseInformationMock);
		return adminServiceMock;
	}
}
