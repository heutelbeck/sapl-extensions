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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import lombok.extern.slf4j.Slf4j;

/**
 * This enumeration is used to distinguish between constraints of different mqtt
 * actions.
 */
@Slf4j
enum ActionType {

    CONNECT {
        /**
         * Enforces the provided constraint for a mqtt connection.
         *
         * @param constraintDetails used to cache details about the constraints of a
         *                          sapl authorization decision
         * @param constraint        the constraint to enforce
         * @return returns {@code true} in case the constraint was successfully handled,
         *         otherwise {@code false}
         */
        @Override
        public boolean enforceConstraint(ConstraintDetails constraintDetails, JsonNode constraint) {
            return ConnectionConstraints.enforceConnectionConstraintEntries(constraintDetails, constraint);
        }

        /**
         * Logs the provided constraints.
         *
         * @param constraints       the constraints to log
         * @param constraintDetails used to cache details about the constraints of a
         *                          sapl authorization decision
         */
        @Override
        public void loggingConstraints(ArrayNode constraints, ConstraintDetails constraintDetails) {
            log.debug("Enforcing constraints for mqtt connection of client '{}': {}", constraintDetails.getClientId(),
                    constraints);
        }
    },

    SUBSCRIBE {
        /**
         * Enforces the provided constraint for a mqtt subscription.
         *
         * @param constraintDetails used to cache details about the constraints of a
         *                          sapl authorization decision
         * @param constraint        the constraint to enforce
         * @return returns {@code true} in case the constraint was successfully handled,
         *         otherwise {@code false}
         */
        @Override
        public boolean enforceConstraint(ConstraintDetails constraintDetails, JsonNode constraint) {
            return SubscriptionConstraints.enforceSubscriptionConstraintEntries(constraintDetails, constraint);
        }

        /**
         * Logs the provided constraints.
         *
         * @param constraints       the constraints to log
         * @param constraintDetails used to cache details about the constraints of a
         *                          sapl authorization decision
         */
        @Override
        public void loggingConstraints(ArrayNode constraints, ConstraintDetails constraintDetails) {
            log.debug("Enforcing constraints for mqtt subscription of topic '{}' of client '{}': {}",
                    constraintDetails.getTopic(), constraintDetails.getClientId(), constraints);
        }
    },

    PUBLISH {
        /**
         * Enforces the provided constraint for mqtt publishing.
         *
         * @param constraintDetails used to cache details about the constraints of a
         *                          sapl authorization decision
         * @param constraint        the constraint to enforce
         * @return returns {@code true} in case the constraint was successfully handled,
         *         otherwise {@code false}
         */
        @Override
        public boolean enforceConstraint(ConstraintDetails constraintDetails, JsonNode constraint) {
            return PublishConstraints.enforcePublishConstraintEntries(constraintDetails, constraint);
        }

        /**
         * Logs the provided constraints.
         *
         * @param constraints       the constraints to log
         * @param constraintDetails used to cache details about the constraints of a
         *                          sapl authorization decision
         */
        @Override
        public void loggingConstraints(ArrayNode constraints, ConstraintDetails constraintDetails) {
            log.debug("Enforcing constraints for mqtt publish message of topic '{}' of client '{}': {}",
                    constraintDetails.getTopic(), constraintDetails.getClientId(), constraints);
        }
    };

    /**
     * Enforces the provided constraint for a mqtt action.
     *
     * @param constraintDetails used to cache details about the constraints of a
     *                          sapl authorization decision
     * @param constraint        the constraint to enforce
     * @return returns {@code true} in case the constraint was successfully handled,
     *         otherwise {@code false}
     */
    public abstract boolean enforceConstraint(ConstraintDetails constraintDetails, JsonNode constraint);

    /**
     * Logs the provided constraints.
     *
     * @param constraints       the constraints to log
     * @param constraintDetails used to cache details about the constraints of a
     *                          sapl authorization decision
     */
    public abstract void loggingConstraints(ArrayNode constraints, ConstraintDetails constraintDetails);
}
