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

import com.hivemq.extension.sdk.api.interceptor.publish.parameter.PublishInboundOutput;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.IdentifiableAuthorizationDecision;
import lombok.Data;

/**
 * These data objects are used to cache details about the constraints of a sapl authorization decision.
 */
@Data
public class ConstraintDetails {
    private long timeLimitSec;
    private final String topic;
    private final String clientId;
    private ActionType actionType;
    private final String subscriptionId;
    private final AuthorizationDecision authzDecision;
    private boolean hasHandledObligationsSuccessfully;
    private Boolean isResubscribeMqttSubscriptionEnabled;
    private final PublishInboundOutput publishInboundOutput;

    /**
     * Create a data object used to cache details about the constraints of a sapl authorization decision.
     * @param clientId used to identify the mqtt client
     * @param identAuthzDecision the authorization decision provided by the PDP
     */
    public ConstraintDetails(String clientId, IdentifiableAuthorizationDecision identAuthzDecision) {
        this(clientId, identAuthzDecision, null);
    }

    /**
     * Create a data object used to cache details about the constraints of a sapl authorization decision.
     * @param clientId used to identify the mqtt client
     * @param identAuthzDecision the authorization decision provided by the PDP
     * @param topic the topic of the current mqtt action
     */
    public ConstraintDetails(String clientId, IdentifiableAuthorizationDecision identAuthzDecision,
                             String topic) {
        this(clientId, identAuthzDecision, topic, null);
    }

    /**
     * Create a data object used to cache details about the constraints of a sapl authorization decision.
     * @param clientId used to identify the mqtt client
     * @param identAuthzDecision the authorization decision provided by the PDP
     * @param topic the topic of the current mqtt action
     * @param publishInboundOutput provides the mqtt publish message
     */
    public ConstraintDetails(String clientId, IdentifiableAuthorizationDecision identAuthzDecision,
                             String topic, PublishInboundOutput publishInboundOutput) {
        this.topic = topic;
        this.clientId = clientId;
        this.timeLimitSec = -1; // no value specified yet
        this.hasHandledObligationsSuccessfully = true;
        this.isResubscribeMqttSubscriptionEnabled = null;
        this.publishInboundOutput = publishInboundOutput;
        this.authzDecision = identAuthzDecision.getAuthorizationDecision();
        this.subscriptionId = identAuthzDecision.getAuthorizationSubscriptionId();
    }
}
