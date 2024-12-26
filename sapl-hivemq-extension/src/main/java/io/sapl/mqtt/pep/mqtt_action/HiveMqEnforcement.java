/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.mqtt.pep.mqtt_action;

import static io.sapl.api.pdp.Decision.PERMIT;
import static io.sapl.mqtt.pep.MqttPep.NOT_AUTHORIZED_NOTICE;

import com.hivemq.extension.sdk.api.auth.parameter.SimpleAuthOutput;
import com.hivemq.extension.sdk.api.auth.parameter.SubscriptionAuthorizerOutput;
import com.hivemq.extension.sdk.api.interceptor.publish.parameter.PublishInboundOutput;
import com.hivemq.extension.sdk.api.packets.connect.ConnackReasonCode;
import com.hivemq.extension.sdk.api.packets.publish.AckReasonCode;
import com.hivemq.extension.sdk.api.packets.subscribe.SubackReasonCode;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.mqtt.pep.details.MqttSaplId;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

/**
 * This utility class provides functions to enforce authorization decisions via
 * the HiveMq broker.
 */
@Slf4j
@UtilityClass
public class HiveMqEnforcement {

    /**
     * Tries to tell the HiveMq broker whether the mqtt connection shall be granted
     * or not. If the asynchronous processing of the enforcement timed out
     * beforehand, any enforcement in this method will fail.
     *
     * @return Returns true if the HiveMq broker will grant the mqtt connection. In
     * case the mqtt connection isn't granted or the asynchronous processing timed
     * out before granting, false will be returned.
     * @param authnOutput used to grand or refuse the mqtt connection
     * @param authzDecision the decision of whether the access shall be granted or
     * not
     * @param hasHandledObligationsSuccessfully whether all obligations are handled
     * successfully or not
     * @param mqttSaplId provides unique specifications used to identify the
     * decision flux
     */
    public static boolean enforceMqttConnectionByHiveMqBroker(SimpleAuthOutput authnOutput,
            AuthorizationDecision authzDecision, boolean hasHandledObligationsSuccessfully, MqttSaplId mqttSaplId) {
        // An exception will be thrown when the async operation timed out before the
        // authorization process finished
        try {
            if (authzDecision.getDecision() == PERMIT && hasHandledObligationsSuccessfully) {
                authnOutput.authenticateSuccessfully();
                log.info("Permit connection of client '{}'", mqttSaplId.getMqttClientId());
                return true;
            } else {
                authnOutput.failAuthentication(ConnackReasonCode.NOT_AUTHORIZED, NOT_AUTHORIZED_NOTICE);
                log.info("Denied connection of client '{}'", mqttSaplId.getMqttClientId());
                return false;
            }
        } catch (UnsupportedOperationException exception) { // async processing timeout will use default behaviour
            return false;
        }
    }

    /**
     * Tries to tell the HiveMq broker whether the mqtt subscription shall be
     * granted or not. If the asynchronous processing of the enforcement timed out
     * beforehand, any enforcement in this method will fail.
     *
     * @param subscriptionAuthorizerOutput used to grand or refuse the mqtt
     * subscription
     * @param authzDecision the decision of whether the access shall be granted or
     * not
     * @param hasHandledObligationsSuccessfully whether all obligations are handled
     * successfully or not
     * @param mqttSaplId provides unique specifications used to identify the
     * decision flux
     */
    public static void enforceMqttSubscriptionByHiveMqBroker(SubscriptionAuthorizerOutput subscriptionAuthorizerOutput,
            AuthorizationDecision authzDecision, boolean hasHandledObligationsSuccessfully, MqttSaplId mqttSaplId) {
        // An exception will be thrown when the async operation timed out before the
        // authorization process finished
        try {
            if (authzDecision.getDecision() == PERMIT && hasHandledObligationsSuccessfully) {
                subscriptionAuthorizerOutput.authorizeSuccessfully();
                log.info("Permit subscription of topic '{}' for client '{}'", mqttSaplId.getTopic(),
                        mqttSaplId.getMqttClientId());
            } else {
                subscriptionAuthorizerOutput.failAuthorization(SubackReasonCode.NOT_AUTHORIZED, NOT_AUTHORIZED_NOTICE);
                log.info("Denied subscription of topic '{}' for client '{}'", mqttSaplId.getTopic(),
                        mqttSaplId.getMqttClientId());
            }
        } catch (UnsupportedOperationException ignore) { // async processing timeout will use default behaviour
        }
    }

    /**
     * Tries to tell the HiveMq broker whether the mqtt publish shall be granted or
     * not. If the asynchronous processing of the enforcement timed out beforehand,
     * any enforcement in this method will fail.
     *
     * @param publishInboundOutput used to refuse the mqtt publish
     * @param authzDecision the decision of whether the access shall be granted or
     * not
     * @param hasHandledObligationsSuccessfully whether all obligations are handled
     * successfully or not
     * @param mqttSaplId provides unique specifications used to identify the
     * decision flux
     */
    public static void enforceMqttPublishByHiveMqBroker(PublishInboundOutput publishInboundOutput,
            AuthorizationDecision authzDecision, boolean hasHandledObligationsSuccessfully, MqttSaplId mqttSaplId) {
        // An exception will be thrown when the async operation timed out before the
        // authorization process finished
        try {
            if (authzDecision.getDecision() == PERMIT && hasHandledObligationsSuccessfully) {
                log.info("Permit publish of topic '{}' for client '{}'", mqttSaplId.getTopic(),
                        mqttSaplId.getMqttClientId());
            } else {
                // Prevent delivery with reason code success for client notification
                publishInboundOutput.preventPublishDelivery(AckReasonCode.NOT_AUTHORIZED, NOT_AUTHORIZED_NOTICE);
                log.info("Denied publish of topic '{}' for client '{}'", mqttSaplId.getTopic(),
                        mqttSaplId.getMqttClientId());
            }
        } catch (UnsupportedOperationException ignore) { // async processing timeout will use default behaviour
        }
    }
}
