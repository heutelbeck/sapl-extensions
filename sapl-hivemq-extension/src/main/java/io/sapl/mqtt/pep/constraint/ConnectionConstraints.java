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
 * connections.
 */
@Slf4j
@UtilityClass
public class ConnectionConstraints {

    @FunctionalInterface
    private interface ConnectionConstraintHandlingFunction<C, V> {
        boolean fulfill(C constraintDetails, V constraint);
    }

    private static final Map<String, ConnectionConstraintHandlingFunction<ConstraintDetails, ObjectValue>> CONNECTION_CONSTRAINTS = new HashMap<>();

    static {
        CONNECTION_CONSTRAINTS.put(Constraints.ENVIRONMENT_LIMIT_MQTT_ACTION_DURATION,
                ConnectionConstraints::setTimeLimit);
    }

    static boolean enforceConnectionConstraintEntries(ConstraintDetails constraintDetails, Value constraint) {
        if (!(constraint instanceof ObjectValue obj)) {
            log.warn("No valid constraint handler found for client '{}' and constraint '{}'. "
                    + "Please check your policy specification.", constraintDetails.getClientId(), constraint);
            return false;
        }

        ConnectionConstraintHandlingFunction<ConstraintDetails, ObjectValue> connectionConstraintHandlingFunction = null;
        if (obj.containsKey(Constraints.ENVIRONMENT_CONSTRAINT_TYPE)
                && obj.get(Constraints.ENVIRONMENT_CONSTRAINT_TYPE) instanceof TextValue(var constraintType)) {
            connectionConstraintHandlingFunction = CONNECTION_CONSTRAINTS.get(constraintType);
        }

        if (connectionConstraintHandlingFunction == null) {
            log.warn("No valid constraint handler found for client '{}' and constraint '{}'. "
                    + "Please check your policy specification.", constraintDetails.getClientId(), constraint);
            return false;
        } else {
            return connectionConstraintHandlingFunction.fulfill(constraintDetails, obj);
        }
    }

    private static boolean setTimeLimit(ConstraintDetails constraintDetails, ObjectValue constraint) {
        var timeLimitValue = constraint.get(Constraints.ENVIRONMENT_TIME_LIMIT);
        if (timeLimitValue instanceof NumberValue(var timeLimitNum)) {
            var timeLimitLong = timeLimitNum.longValue();
            if (timeLimitLong < 1) {
                log.warn("Illegal time limit for connection of client '{}' specified: {}",
                        constraintDetails.getClientId(), constraint);
                return false;
            }
            constraintDetails.setTimeLimitSec(timeLimitLong);
            log.info("Limit the connection time for client '{}' to '{}' seconds.", constraintDetails.getClientId(),
                    timeLimitLong);
            return true;
        } else {
            log.warn("No time limit specified for mqtt connection constraint of type '{}' for client '{}'.",
                    constraint.get(Constraints.ENVIRONMENT_CONSTRAINT_TYPE), constraintDetails.getClientId());
            return false;
        }
    }
}
