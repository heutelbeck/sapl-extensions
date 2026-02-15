/*
 * Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import io.sapl.api.model.NumberValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;

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
    private interface SubscriptionConstraintHandlingFunction<C, V> {
        boolean fulfill(C constraintDetails, V constraint);
    }

    private static final Map<String, SubscriptionConstraintHandlingFunction<ConstraintDetails, ObjectValue>> SUBSCRIPTION_CONSTRAINTS = new HashMap<>();

    static {
        SUBSCRIPTION_CONSTRAINTS.put(Constraints.ENVIRONMENT_LIMIT_MQTT_ACTION_DURATION,
                SubscriptionConstraints::setTimeLimit);
        SUBSCRIPTION_CONSTRAINTS.put(ENVIRONMENT_RESUBSCRIBE_MQTT_SUBSCRIPTION,
                SubscriptionConstraints::setStayUnsubscribed);
    }

    static boolean enforceSubscriptionConstraintEntries(ConstraintDetails constraintDetails, Value constraint) {
        if (!(constraint instanceof ObjectValue obj)) {
            log.warn("No valid constraint handler found for client '{}' and constraint '{}'. "
                    + "Please check your policy specification.", constraintDetails.getClientId(), constraint);
            return false;
        }

        SubscriptionConstraintHandlingFunction<ConstraintDetails, ObjectValue> subscriptionConstraintHandlingFunction = null;
        if (obj.containsKey(Constraints.ENVIRONMENT_CONSTRAINT_TYPE)
                && obj.get(Constraints.ENVIRONMENT_CONSTRAINT_TYPE) instanceof TextValue(var constraintType)) {
            subscriptionConstraintHandlingFunction = SUBSCRIPTION_CONSTRAINTS.get(constraintType);
        }

        if (subscriptionConstraintHandlingFunction == null) {
            log.warn("No valid constraint handler found for client '{}' and constraint '{}'. "
                    + "Please check your policy specification.", constraintDetails.getClientId(), constraint);
            return false;
        } else {
            return subscriptionConstraintHandlingFunction.fulfill(constraintDetails, obj);
        }
    }

    private static boolean setTimeLimit(ConstraintDetails constraintDetails, ObjectValue constraint) {
        var timeLimitValue = constraint.get(Constraints.ENVIRONMENT_TIME_LIMIT);
        if (timeLimitValue instanceof NumberValue(var timeLimitNum)) {
            var timeLimitLong = timeLimitNum.longValue();
            if (timeLimitLong < 1) {
                log.warn("Illegal time limit for mqtt subscription of topic '{}' for client '{}' specified: {}",
                        constraintDetails.getTopic(), constraintDetails.getClientId(), timeLimitValue);
                return false;
            }
            constraintDetails.setTimeLimitSec(timeLimitLong);
            log.info("Limit the mqtt subscription time of topic '{}' for client '{}'  to '{}' seconds.",
                    constraintDetails.getTopic(), constraintDetails.getClientId(), timeLimitLong);
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

    private static boolean setStayUnsubscribed(ConstraintDetails constraintDetails, ObjectValue constraint) {
        var statusValue = constraint.get(Constraints.ENVIRONMENT_STATUS);
        if (statusValue instanceof TextValue(var status)) {
            if (Constraints.ENVIRONMENT_ENABLED.equals(status)) {
                constraintDetails.setIsResubscribeMqttSubscriptionEnabled(Boolean.TRUE);
                log.info("Enabled the resubscription of the client '{}' to topic '{}'.",
                        constraintDetails.getClientId(), constraintDetails.getTopic());
                return true;
            }
            if (Constraints.ENVIRONMENT_DISABLED.equals(status)) {
                constraintDetails.setIsResubscribeMqttSubscriptionEnabled(Boolean.FALSE);
                log.info("Disabled the resubscription of the client '{}' to topic '{}'.",
                        constraintDetails.getClientId(), constraintDetails.getTopic());
                return true;
            }
        }
        log.warn("Specified an illegal status for a potential resubscription of client '{}' to topic '{}': '{}'",
                constraintDetails.getClientId(), constraintDetails.getTopic(), statusValue);
        return false;
    }
}
