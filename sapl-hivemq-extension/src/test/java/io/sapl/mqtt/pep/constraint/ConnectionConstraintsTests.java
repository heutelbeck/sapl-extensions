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

import static io.sapl.mqtt.pep.constraint.Constraints.ENVIRONMENT_LIMIT_MQTT_ACTION_DURATION;
import static io.sapl.mqtt.pep.constraint.Constraints.ENVIRONMENT_TIME_LIMIT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.IdentifiableAuthorizationDecision;

@DisplayName("Connection constraints")
class ConnectionConstraintsTests {

    @Test
    void when_specifiedConstraintTypeIsNotTextual_then_signalConstraintCouldNotBeHandled() {
        // GIVEN
        var constraintDetails = new ConstraintDetails("clientId", IdentifiableAuthorizationDecision.INDETERMINATE);
        var constraint        = ObjectValue.builder().put(Constraints.ENVIRONMENT_CONSTRAINT_TYPE, Value.of(4)).build();

        // WHEN
        var wasSuccessfullyHandled = ConnectionConstraints.enforceConnectionConstraintEntries(constraintDetails,
                constraint);

        // THEN
        assertThat(wasSuccessfullyHandled).isFalse();
    }

    @Test
    void when_settingTimeLimitAndTimeLimitIsNotANumber_then_signalConstraintCouldNotBeHandled() {
        // GIVEN
        var constraintDetails    = new ConstraintDetails("clientId", IdentifiableAuthorizationDecision.INDETERMINATE);
        var constraintDetailsSpy = spy(constraintDetails);
        var constraint           = ObjectValue.builder()
                .put(Constraints.ENVIRONMENT_CONSTRAINT_TYPE, Value.of(ENVIRONMENT_LIMIT_MQTT_ACTION_DURATION))
                .put(ENVIRONMENT_TIME_LIMIT, Value.of("five")).build();

        // WHEN
        var wasSuccessfullyHandled = ConnectionConstraints.enforceConnectionConstraintEntries(constraintDetailsSpy,
                constraint);

        // THEN
        verify(constraintDetailsSpy, never()).setTimeLimitSec(anyLong());
        assertThat(wasSuccessfullyHandled).isFalse();
    }

    @Test
    void when_settingTimeLimitAndTimeLimitIsBelowOne_then_signalConstraintCouldNotBeHandled() {
        // GIVEN
        var constraintDetails    = new ConstraintDetails("clientId", IdentifiableAuthorizationDecision.INDETERMINATE);
        var constraintDetailsSpy = spy(constraintDetails);
        var constraint           = ObjectValue.builder()
                .put(Constraints.ENVIRONMENT_CONSTRAINT_TYPE, Value.of(ENVIRONMENT_LIMIT_MQTT_ACTION_DURATION))
                .put(ENVIRONMENT_TIME_LIMIT, Value.of(0)).build();

        // WHEN
        var wasSuccessfullyHandled = ConnectionConstraints.enforceConnectionConstraintEntries(constraintDetailsSpy,
                constraint);

        // THEN
        verify(constraintDetailsSpy, never()).setTimeLimitSec(anyLong());
        assertThat(wasSuccessfullyHandled).isFalse();
    }

    @Test
    void when_settingTimeLimitAndTimeLimitIsNotSpecified_then_signalConstraintCouldNotBeHandled() {
        // GIVEN
        var constraintDetails    = new ConstraintDetails("clientId", IdentifiableAuthorizationDecision.INDETERMINATE);
        var constraintDetailsSpy = spy(constraintDetails);
        var constraint           = ObjectValue.builder()
                .put(Constraints.ENVIRONMENT_CONSTRAINT_TYPE, Value.of(ENVIRONMENT_LIMIT_MQTT_ACTION_DURATION)).build();

        // WHEN
        var wasSuccessfullyHandled = ConnectionConstraints.enforceConnectionConstraintEntries(constraintDetailsSpy,
                constraint);

        // THEN
        verify(constraintDetailsSpy, never()).setTimeLimitSec(anyLong());
        assertThat(wasSuccessfullyHandled).isFalse();
    }
}
