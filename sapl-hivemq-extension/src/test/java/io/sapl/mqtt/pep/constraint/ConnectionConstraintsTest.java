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

import static io.sapl.mqtt.pep.constraint.Constraints.ENVIRONMENT_LIMIT_MQTT_ACTION_DURATION;
import static io.sapl.mqtt.pep.constraint.Constraints.ENVIRONMENT_TIME_LIMIT;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.pdp.IdentifiableAuthorizationDecision;
import org.slf4j.LoggerFactory;

class ConnectionConstraintsTest {

    protected static final Logger rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);

    @BeforeAll
    static void beforeAll() {
        // set logging level
        rootLogger.setLevel(Level.OFF);
    }

    @Test
    void when_specifiedConstraintTypeIsNotTextual_then_signalConstraintCouldNotBeHandled() {
        // GIVEN
        ConstraintDetails constraintDetails =
                new ConstraintDetails("clientId", new IdentifiableAuthorizationDecision());
        JsonNode constraint = JsonNodeFactory.instance.objectNode()
                .put(Constraints.ENVIRONMENT_CONSTRAINT_TYPE, 4);

        // WHEN
        boolean wasSuccessfullyHandled =
                ConnectionConstraints.enforceConnectionConstraintEntries(constraintDetails, constraint);

        // THEN
        assertFalse(wasSuccessfullyHandled);
    }

    @Test
    void when_settingTimeLimitAndTimeLimitIsNotANumber_then_signalConstraintCouldNotBeHandled() {
        // GIVEN
        ConstraintDetails constraintDetails =
                new ConstraintDetails("clientId", new IdentifiableAuthorizationDecision());
        ConstraintDetails constraintDetailsSpy = spy(constraintDetails);
        JsonNode constraint = JsonNodeFactory.instance.objectNode()
                .put(Constraints.ENVIRONMENT_CONSTRAINT_TYPE, ENVIRONMENT_LIMIT_MQTT_ACTION_DURATION)
                .put(ENVIRONMENT_TIME_LIMIT,  "five");

        // WHEN
        boolean wasSuccessfullyHandled =
                ConnectionConstraints.enforceConnectionConstraintEntries(constraintDetailsSpy, constraint);

        // THEN
        verify(constraintDetailsSpy, never()).setTimeLimitSec(anyLong());
        assertFalse(wasSuccessfullyHandled);
    }

    @Test
    void when_settingTimeLimitAndTimeLimitIsBelowOne_then_signalConstraintCouldNotBeHandled() {
        // GIVEN
        ConstraintDetails constraintDetails =
                new ConstraintDetails("clientId", new IdentifiableAuthorizationDecision());
        ConstraintDetails constraintDetailsSpy = spy(constraintDetails);
        JsonNode constraint = JsonNodeFactory.instance.objectNode()
                .put(Constraints.ENVIRONMENT_CONSTRAINT_TYPE, ENVIRONMENT_LIMIT_MQTT_ACTION_DURATION)
                .put(ENVIRONMENT_TIME_LIMIT,  0);

        // WHEN
        boolean wasSuccessfullyHandled =
                ConnectionConstraints.enforceConnectionConstraintEntries(constraintDetailsSpy, constraint);

        // THEN
        verify(constraintDetailsSpy, never()).setTimeLimitSec(anyLong());
        assertFalse(wasSuccessfullyHandled);
    }

    @Test
    void when_settingTimeLimitAndTimeLimitIsNotSpecified_then_signalConstraintCouldNotBeHandled() {
        // GIVEN
        ConstraintDetails constraintDetails =
                new ConstraintDetails("clientId", new IdentifiableAuthorizationDecision());
        ConstraintDetails constraintDetailsSpy = spy(constraintDetails);
        JsonNode constraint = JsonNodeFactory.instance.objectNode()
                .put(Constraints.ENVIRONMENT_CONSTRAINT_TYPE, ENVIRONMENT_LIMIT_MQTT_ACTION_DURATION);

        // WHEN
        boolean wasSuccessfullyHandled =
                ConnectionConstraints.enforceConnectionConstraintEntries(constraintDetailsSpy, constraint);

        // THEN
        verify(constraintDetailsSpy, never()).setTimeLimitSec(anyLong());
        assertFalse(wasSuccessfullyHandled);
    }
}