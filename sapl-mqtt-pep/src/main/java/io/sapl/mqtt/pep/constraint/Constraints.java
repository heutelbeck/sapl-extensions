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

/**
 * This class provides common variables used to handle constraints of mqtt action enforcements.
 */
class Constraints {
    static final String ENVIRONMENT_STATUS = "status";
    static final String ENVIRONMENT_ENABLED = "enabled";
    static final String ENVIRONMENT_DISABLED = "disabled";
    static final String ENVIRONMENT_CONSTRAINT_TYPE = "type";
    static final String ENVIRONMENT_LIMIT_MQTT_ACTION_DURATION = "limitMqttActionDuration";
    static final String ENVIRONMENT_TIME_LIMIT = "timeLimit";

    /**
     * This class is not allowed to initialize. In case this method is called a {@link UnsupportedOperationException}
     * will be thrown.
     */
    Constraints() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
}