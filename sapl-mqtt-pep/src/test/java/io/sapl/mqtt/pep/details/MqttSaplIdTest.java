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

package io.sapl.mqtt.pep.details;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrowsExactly;


class MqttSaplIdTest {

    @Test
    void when_mqttSaplIdConstructorIsCalledWithMqttClientIdEqualsNull_then_throwNullPointerException() {
        assertThrowsExactly(NullPointerException.class,
                () -> new MqttSaplId(null, "subscriptionId"));
    }

    @Test
    void when_mqttSaplIdConstructorIsCalledWithSubscriptionIdEqualsNull_then_throwNullPointerException() {
        assertThrowsExactly(NullPointerException.class,
                () -> new MqttSaplId("clientId", null));
    }

    @Test
    void when_mqttSaplIdConstructorIsCalledWithSubscriptionIdAndClientIdEqualsNull_then_throwNullPointerException() {
        assertThrowsExactly(NullPointerException.class,
                () -> new MqttSaplId(null, null));
    }
}