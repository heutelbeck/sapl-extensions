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
package io.sapl.mqtt.pep.constraint;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

/**
 * This utility class provides constraints handling functions for mqtt
 * subscriptions.
 */
@Slf4j
@UtilityClass
public class SubscriptionConstraints {

    static final String ENVIRONMENT_RESUBSCRIBE_MQTT_SUBSCRIPTION = "resubscribeMqttSubscription";

    @FunctionalInterface
    private interface SubscriptionConstraintHandlingFunction<C, J> {
        boolean fulfill(C constraintDetails, J constraint);
    }

    private static final Map<String, SubscriptionConstraintHandlingFunction<ConstraintDetails, JsonNode>> SUBSCRIPTION_CONSTRAINTS = new HashMap<>();

    static {
        SUBSCRIPTION_CONSTRAINTS.put(Constraints.ENVIRONMENT_LIMIT_MQTT_ACTION_DURATION,
                SubscriptionConstraints::setTimeLimit);
        SUBSCRIPTION_CONSTRAINTS.put(ENVIRONMENT_RESUBSCRIBE_MQTT_SUBSCRIPTION,
                SubscriptionConstraints::setStayUnsubscribed);
    }

    static boolean enforceSubscriptionConstraintEntries(ConstraintDetails constraintDetails, JsonNode constraint) {
        SubscriptionConstraintHandlingFunction<ConstraintDetails, JsonNode> subscriptionConstraintHandlingFunction = null;
        if (constraint.has(Constraints.ENVIRONMENT_CONSTRAINT_TYPE)
                && constraint.get(Constraints.ENVIRONMENT_CONSTRAINT_TYPE).isTextual()) {
            String constraintType = constraint.get(Constraints.ENVIRONMENT_CONSTRAINT_TYPE).asText();
            subscriptionConstraintHandlingFunction = SUBSCRIPTION_CONSTRAINTS.get(constraintType);
        }

        if (subscriptionConstraintHandlingFunction == null) { // returns false if the constraint entry couldn't be
                                                              // handled
            log.warn("No valid constraint handler found for client '{}' and constraint '{}'. "
                    + "Please check your policy specification.", constraintDetails.getClientId(), constraint);
            return false;
        } else {
            return subscriptionConstraintHandlingFunction.fulfill(constraintDetails, constraint);
        }
    }

    private static boolean setTimeLimit(ConstraintDetails constraintDetails, JsonNode constraint) {
        JsonNode timeLimitJson = constraint.get(Constraints.ENVIRONMENT_TIME_LIMIT);
        if (timeLimitJson != null) {
            if (!timeLimitJson.canConvertToExactIntegral() || timeLimitJson.asLong() < 1) {
                log.warn("Illegal time limit for mqtt subscription of topic '{}' for client '{}' specified: {}",
                        constraintDetails.getTopic(), constraintDetails.getClientId(), timeLimitJson);
                return false;
            }
            var timeLimitSec = timeLimitJson.asLong();
            constraintDetails.setTimeLimitSec(timeLimitSec);
            log.info("Limit the mqtt subscription time of topic '{}' for client '{}'  to '{}' seconds.",
                    constraintDetails.getTopic(), constraintDetails.getClientId(), timeLimitSec);
            return true;
        } else {
            log.warn(
                    "No time limit specified for mqtt subscription constraint of type '{}' for topic '{}' "
                            + "of client '{}'.",
                    constraint.get(Constraints.ENVIRONMENT_CONSTRAINT_TYPE), constraintDetails.getTopic(),
                    constraintDetails.getClientId());
            return false;
        }
    }

    private static boolean setStayUnsubscribed(ConstraintDetails constraintDetails, JsonNode constraint) {
        JsonNode resubscribeMqttSubscriptionStatusJson = constraint.get(Constraints.ENVIRONMENT_STATUS);
        if (resubscribeMqttSubscriptionStatusJson != null && resubscribeMqttSubscriptionStatusJson.isTextual()) {
            var resubscribeMqttSubscriptionStatus = resubscribeMqttSubscriptionStatusJson.textValue();
            if (Constraints.ENVIRONMENT_ENABLED.equals(resubscribeMqttSubscriptionStatus)) {
                constraintDetails.setIsResubscribeMqttSubscriptionEnabled(Boolean.TRUE);
                log.info("Enabled the resubscription of the client '{}' to topic '{}'.",
                        constraintDetails.getClientId(), constraintDetails.getTopic());
                return true;
            }
            if (Constraints.ENVIRONMENT_DISABLED.equals(resubscribeMqttSubscriptionStatus)) {
                constraintDetails.setIsResubscribeMqttSubscriptionEnabled(Boolean.FALSE);
                log.info("Disabled the resubscription of the client '{}' to topic '{}'.",
                        constraintDetails.getClientId(), constraintDetails.getTopic());
                return true;
            }
        }
        log.warn("Specified an illegal status for a potential resubscription of client '{}' to topic '{}': '{}'",
                constraintDetails.getClientId(), constraintDetails.getTopic(), resubscribeMqttSubscriptionStatusJson);
        return false;
    }
}
