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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;

import com.hivemq.extension.sdk.api.services.Services;
import com.hivemq.extension.sdk.api.services.subscription.SubscriptionStore;
import com.hivemq.extension.sdk.api.services.subscription.TopicSubscription;

import io.sapl.mqtt.pep.details.MqttSaplId;

class HiveMqUtilityTests {

	@Test
	void whenNoMqttSubscriptionExisting_then_returnFalse() {
		// GIVEN
		var mqttSaplId                            = new MqttSaplId("clientId", "subscriptionId");
		var subscriptionStoreMock                 = mock(SubscriptionStore.class);
		var setCompletableFutureTopicSubscription = CompletableFuture.<Set<TopicSubscription>>completedFuture(Set.of());
		when(subscriptionStoreMock.getSubscriptions(anyString())).thenReturn(setCompletableFutureTopicSubscription);

		try (var servicesMockedStatic = mockStatic(Services.class)) {
			servicesMockedStatic.when(Services::subscriptionStore).thenReturn(subscriptionStoreMock);

			// WHEN
			var isMqttSubscriptionExisting = HiveMqUtility.isMqttSubscriptionExisting(mqttSaplId);

			// THEN
			assertFalse(isMqttSubscriptionExisting);
		}
	}
}
