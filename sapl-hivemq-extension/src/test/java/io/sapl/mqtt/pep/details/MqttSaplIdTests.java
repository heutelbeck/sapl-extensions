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
package io.sapl.mqtt.pep.details;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("MQTT SAPL ID")
class MqttSaplIdTests {

    @Test
    void when_mqttSaplIdConstructorIsCalledWithMqttClientIdEqualsNull_then_throwNullPointerException() {
        assertThatThrownBy(() -> new MqttSaplId(null, "subscriptionId"))
                .isExactlyInstanceOf(NullPointerException.class);
    }

    @Test
    void when_mqttSaplIdConstructorIsCalledWithSubscriptionIdEqualsNull_then_throwNullPointerException() {
        assertThatThrownBy(() -> new MqttSaplId("clientId", null)).isExactlyInstanceOf(NullPointerException.class);
    }

    @Test
    void when_mqttSaplIdConstructorIsCalledWithSubscriptionIdAndClientIdEqualsNull_then_throwNullPointerException() {
        assertThatThrownBy(() -> new MqttSaplId(null, null)).isExactlyInstanceOf(NullPointerException.class);
    }
}
