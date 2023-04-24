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

import com.hivemq.extension.sdk.api.services.Services;
import com.hivemq.extension.sdk.api.services.subscription.SubscriptionStore;
import com.hivemq.extension.sdk.api.services.subscription.TopicSubscription;
import io.sapl.mqtt.pep.details.MqttSaplId;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.*;

class HiveMqUtilityTest {

    @Test
    void  whenNoMqttSubscriptionExisting_then_returnFalse() {
        // GIVEN
        MqttSaplId mqttSaplId = new MqttSaplId("clientId", "subscriptionId");
        SubscriptionStore subscriptionStoreMock = mock(SubscriptionStore.class);
        CompletableFuture<Set<TopicSubscription>> setCompletableFutureTopicSubscription =
                CompletableFuture.completedFuture(Set.of());
        when(subscriptionStoreMock.getSubscriptions(anyString())).thenReturn(setCompletableFutureTopicSubscription);

        try(MockedStatic<Services> servicesMockedStatic = mockStatic(Services.class)) {
            servicesMockedStatic.when(Services::subscriptionStore).thenReturn(subscriptionStoreMock);

            // WHEN
            boolean isMqttSubscriptionExisting = HiveMqUtility.isMqttSubscriptionExisting(mqttSaplId);

            // THEN
            assertFalse(isMqttSubscriptionExisting);
        }
    }
}
