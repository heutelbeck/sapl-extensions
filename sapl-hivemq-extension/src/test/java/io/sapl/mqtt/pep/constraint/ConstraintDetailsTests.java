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

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.IdentifiableAuthorizationDecision;
import io.sapl.mqtt.pep.cache.MqttClientState;

class ConstraintDetailsTests {

	@Test
	void when_subscriptionIdGetsLookedUp_then_returnSubscriptionId() {
		// GIVEN
		var mqttClientState    = new MqttClientState("clientId");
		var identAuthzDecision = new IdentifiableAuthorizationDecision("subscriptionId", AuthorizationDecision.PERMIT);
		var constraintDetails  = new ConstraintDetails(mqttClientState.getClientId(), identAuthzDecision);

		// WHEN
		var subscriptionId = constraintDetails.getSubscriptionId();

		// THEN
		assertEquals("subscriptionId", subscriptionId);
	}
}
