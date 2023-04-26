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

/**
 * This utility class provides functions to handle obligations and advices of the sapl authorization decision.
 */
@Slf4j
@UtilityClass
public class ConstraintHandler {

    /**
     *  Enforces the cached constraints for a mqtt connection.
     * @param constraintDetails used to cache details about the constraints of a sapl authorization decision
     */
    public static void enforceMqttConnectionConstraints(ConstraintDetails constraintDetails) {
        constraintDetails.setActionType(ActionType.CONNECT);
        enforceConstraints(constraintDetails);
    }

    /**
     * Enforces the cached constraints for a mqtt subscription.
     * @param constraintDetails used to cache details about the constraints of a sapl authorization decision
     */
    public static void enforceMqttSubscriptionConstraints(ConstraintDetails constraintDetails) {
        constraintDetails.setActionType(ActionType.SUBSCRIBE);
        enforceConstraints(constraintDetails);
    }

    /**
     * Enforces the cached constraints for mqtt publishing.
     * @param constraintDetails used to cache details about the constraints of a sapl authorization decision
     */
    public static void enforceMqttPublishConstraints(ConstraintDetails constraintDetails) {
        constraintDetails.setActionType(ActionType.PUBLISH);
        enforceConstraints(constraintDetails);
    }

    private static void enforceConstraints(ConstraintDetails constraintDetails) {
        enforceObligations(constraintDetails);
        enforceAdvices(constraintDetails);
        enforceResourceObligation(constraintDetails);
    }

    private static void enforceObligations(ConstraintDetails constraintDetails) {
        constraintDetails.getAuthzDecision().getObligations().ifPresent(obligations -> {
            constraintDetails.getActionType().loggingConstraints(obligations, constraintDetails);
            obligations.forEach(obligation ->
                    enforceObligation(constraintDetails, obligation));
        });
    }

    private static void enforceAdvices(ConstraintDetails constraintDetails) {
        constraintDetails.getAuthzDecision().getAdvice().ifPresent(advices -> {
            constraintDetails.getActionType().loggingConstraints(advices, constraintDetails);
            advices.forEach(advice ->
                    enforceAdvice(constraintDetails, advice));
        });
    }

    private static void enforceObligation(ConstraintDetails constraintDetails, JsonNode obligation) {
        boolean isSuccessful =
                constraintDetails.getActionType().enforceConstraint(constraintDetails, obligation);
        if (!isSuccessful) {
            constraintDetails.setHasHandledObligationsSuccessfully(false);
        }
    }

    private static void enforceAdvice(ConstraintDetails constraintDetails, JsonNode advice) {
        constraintDetails.getActionType().enforceConstraint(constraintDetails, advice);
    }

    private static void enforceResourceObligation(ConstraintDetails constraintDetails) {
        if (constraintDetails.getAuthzDecision().getResource().isPresent()) {  // resource constraints aren't supported
            log.warn("The authorization decision for client '{}' for mqtt action '{}'" +
                            "contained a resource. Handling of resource obligations isn't supported.",
                    constraintDetails.getClientId(), constraintDetails.getActionType().name().toLowerCase());
            constraintDetails.setHasHandledObligationsSuccessfully(false);
        }
    }
}
