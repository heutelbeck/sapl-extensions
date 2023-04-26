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

package io.sapl.mqtt.pep.mqtt_action;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

import com.hivemq.extension.sdk.api.interceptor.publish.parameter.PublishInboundOutput;
import com.hivemq.extension.sdk.api.packets.publish.AckReasonCode;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.mqtt.pep.details.MqttSaplId;

class HiveMqEnforcementTests {

	@Test
	void when_enforcingMqttPublishAfterAsyncProcessingTimeout_then_catchException() {
		// GIVEN
		var publishInboundOutputMock = mock(PublishInboundOutput.class);
		doThrow(UnsupportedOperationException.class).when(publishInboundOutputMock)
				.preventPublishDelivery(any(AckReasonCode.class), anyString());

		var mqttSaplId = new MqttSaplId("clientId", "subscriptionId");

		// WHEN
		HiveMqEnforcement.enforceMqttPublishByHiveMqBroker(publishInboundOutputMock, AuthorizationDecision.DENY,
				false, mqttSaplId);

		// THEN
		verify(publishInboundOutputMock, times(1))
				.preventPublishDelivery(any(AckReasonCode.class), anyString());

	}
}
