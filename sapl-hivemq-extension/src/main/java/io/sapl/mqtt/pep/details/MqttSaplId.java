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
package io.sapl.mqtt.pep.details;

import lombok.NonNull;
import lombok.Value;

/**
 * These data objects cache unique specifications to identify a decision flux of mqtt action enforcement.
 */
@Value
public class MqttSaplId {
    @NonNull String mqttClientId;
    @NonNull String saplSubscriptionId;
    String topic;

    /**
     * Caches unique specifications to identify a decision flux of mqtt action enforcement.
     * @param mqttClientId the id uniquely identifies a mqtt client
     * @param saplSubscriptionId this id uniquely identifies a decision flux for mqtt action enforcement
     */
    public MqttSaplId(String mqttClientId, String saplSubscriptionId) {
        this(mqttClientId, saplSubscriptionId, null);
    }

    /**
     * Caches unique specifications to identify a decision flux of mqtt action enforcement.
     * @param mqttClientId the id uniquely identifies a mqtt client
     * @param saplSubscriptionId this id uniquely identifies a decision flux for mqtt action enforcement
     * @param topic the topic in focus of the mqtt action enforcement
     */
    public MqttSaplId(@NonNull String mqttClientId, @NonNull String saplSubscriptionId, String topic) {
        this.mqttClientId = mqttClientId;
        this.saplSubscriptionId = saplSubscriptionId;
        this.topic = topic;
    }
}