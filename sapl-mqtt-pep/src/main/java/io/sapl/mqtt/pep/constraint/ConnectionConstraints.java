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

package io.sapl.mqtt.pep.constraint;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * This utility class provides constraints handling functions for mqtt connections.
 */
@Slf4j
@UtilityClass
public class ConnectionConstraints extends Constraints {

    @FunctionalInterface
    private interface ConnectionConstraintHandlingFunction<C, J> {
        boolean fulfill(C constraintDetails, J constraint);
    }

    private static final Map<String, ConnectionConstraintHandlingFunction<ConstraintDetails, JsonNode>>
            CONNECTION_CONSTRAINTS = new HashMap<>();

    static {
        CONNECTION_CONSTRAINTS.put(ENVIRONMENT_LIMIT_MQTT_ACTION_DURATION, ConnectionConstraints::setTimeLimit);
    }

    static boolean enforceConnectionConstraintEntries(ConstraintDetails constraintDetails, JsonNode constraint) {
        ConnectionConstraintHandlingFunction<ConstraintDetails, JsonNode> connectionConstraintHandlingFunction = null;
        if (constraint.has(ENVIRONMENT_CONSTRAINT_TYPE) && constraint.get(ENVIRONMENT_CONSTRAINT_TYPE).isTextual()) {
            String constraintType = constraint.get(ENVIRONMENT_CONSTRAINT_TYPE).asText();
            connectionConstraintHandlingFunction = CONNECTION_CONSTRAINTS.get(constraintType);
        }

        if (connectionConstraintHandlingFunction == null) { // returns false if the constraint entry couldn't be handled
            log.warn("No valid constraint handler found for client '{}' and constraint '{}'. " +
                    "Please check your policy specification.", constraintDetails.getClientId(), constraint);
            return false;
        } else {
            return connectionConstraintHandlingFunction.fulfill(constraintDetails, constraint);
        }
    }

    private static boolean setTimeLimit(ConstraintDetails constraintDetails, JsonNode constraint) {
        JsonNode timeLimitJson = constraint.get(ENVIRONMENT_TIME_LIMIT);
        if (timeLimitJson != null) {
            if (!timeLimitJson.canConvertToExactIntegral() || timeLimitJson.asLong() < 1) {
                log.warn("Illegal time limit for connection of client '{}' specified: {}",
                        constraintDetails.getClientId(), constraint);
                return false;
            }
            var timeLimitSec = timeLimitJson.asLong();
            constraintDetails.setTimeLimitSec(timeLimitSec);
            log.info("Limit the connection time for client '{}' to '{}' seconds.",
                    constraintDetails.getClientId(), timeLimitSec);
            return true;
        } else {
            log.warn("No time limit specified for mqtt connection constraint of type '{}' for client '{}'.",
                    constraint.get(ENVIRONMENT_CONSTRAINT_TYPE), constraintDetails.getClientId());
            return false;
        }
    }
}
