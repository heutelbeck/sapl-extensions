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

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import com.hivemq.extension.sdk.api.auth.parameter.SimpleAuthInput;
import com.hivemq.extension.sdk.api.auth.parameter.SubscriptionAuthorizerInput;
import com.hivemq.extension.sdk.api.interceptor.unsuback.parameter.UnsubackOutboundInput;
import com.hivemq.extension.sdk.api.packets.unsuback.UnsubackReasonCode;
import com.hivemq.extension.sdk.api.services.Services;
import com.hivemq.extension.sdk.api.services.builder.Builders;
import com.hivemq.extension.sdk.api.services.subscription.TopicSubscription;

import io.sapl.mqtt.pep.details.MqttSaplId;
import lombok.experimental.UtilityClass;

/**
 * This utility class provides functions for HiveMq broker specific interactions or objects.
 */
@UtilityClass
public class HiveMqUtility {

    /**
     * Evaluates whether a mqtt subscription is existing or not.
     * @param mqttSaplId the id used to check against
     * @return true if an mqtt subscription exists
     */
    public static boolean isMqttSubscriptionExisting(MqttSaplId mqttSaplId) {
        var isMqttSubscriptionExisting = new AtomicBoolean(false);
        Set<TopicSubscription> topicSubscriptions =
                Services.subscriptionStore().getSubscriptions(mqttSaplId.getMqttClientId()).getNow(Set.of());
        topicSubscriptions.forEach(topicSubscription -> {
            if (topicSubscription.getTopicFilter().equals(mqttSaplId.getTopic())) {
                isMqttSubscriptionExisting.set(true);
            }
        });
        return isMqttSubscriptionExisting.get();
    }

    /**
     * Creates a mqtt topic subscription.
     * @param subscriptionAuthorizerInput contains mqtt subscription data
     * @return the mqtt topic subscription created from the input
     */
    public static TopicSubscription buildTopicSubscription(SubscriptionAuthorizerInput subscriptionAuthorizerInput) {
        return Builders
                .topicSubscription()
                .fromSubscription(subscriptionAuthorizerInput.getSubscription())
                .build();
    }

    /**
     * Extracts the acknowledgement reason code from a mqtt unsubscribe message.
     * @param unsubackOutboundInput the input to extract the reason code from
     * @param index to specify the right reason code
     * @return the acknowledgement reason code of a mqtt unsubscribe message
     */
    public static UnsubackReasonCode getUnsubackReasonCode(UnsubackOutboundInput unsubackOutboundInput, int index) {
        return unsubackOutboundInput.getUnsubackPacket().getReasonCodes().get(index);
    }

    /**
     * Extracts the username from the input.
     * @param authnInput the input to extract the username from
     * @return the extracted username
     */
    public static String getUserName(SimpleAuthInput authnInput) {
        return authnInput.getConnectPacket().getUserName().orElse(null);
    }
}
