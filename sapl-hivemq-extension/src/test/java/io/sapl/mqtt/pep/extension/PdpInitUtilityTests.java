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

package io.sapl.mqtt.pep.extension;

import static io.sapl.mqtt.pep.MqttPep.SUBSCRIBE_AUTHZ_ACTION;
import static io.sapl.mqtt.pep.util.SaplSubscriptionUtility.ENVIRONMENT_AUTHZ_ACTION_TYPE;
import static io.sapl.mqtt.pep.util.SaplSubscriptionUtility.ENVIRONMENT_CLIENT_ID;
import static io.sapl.mqtt.pep.util.SaplSubscriptionUtility.ENVIRONMENT_TOPIC;
import static io.sapl.mqtt.pep.util.SaplSubscriptionUtility.ENVIRONMENT_USER_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

import java.io.File;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.interpreter.InitializationException;
import io.sapl.mqtt.pep.config.SaplMqttExtensionConfig;
import io.sapl.pdp.PolicyDecisionPointFactory;

class PdpInitUtilityTests {

	private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

	@Test
	void when_wrongTypeOfPdpImplementationSpecified_then_doNotBuildPdp() {
		// GIVEN
		var saplMqttExtensionConfigMock = Mockito.mock(SaplMqttExtensionConfig.class);
		when(saplMqttExtensionConfigMock.getPdpImplementation()).thenReturn("false");

		// WHEN
		var pdp = PdpInitUtility.buildPdp(saplMqttExtensionConfigMock, null, null);

		// THEN
		assertNull(pdp);
	}

	@Test
	void when_policiesPathIsNotProvidedAndPathFromConfigIsNotRelativeToExtensionHome_then_getPoliciesPathFromConfig() {
		// GIVEN
		var saplMqttExtensionConfigMock = Mockito.mock(SaplMqttExtensionConfig.class);
		when(saplMqttExtensionConfigMock.getPdpImplementation()).thenReturn(PdpInitUtility.EMBEDDED_PDP_IDENTIFIER);
		when(saplMqttExtensionConfigMock.isEmbeddedPdpPoliciesPathRelativeToExtensionHome()).thenReturn(false);
		when(saplMqttExtensionConfigMock.getEmbeddedPdpPoliciesPath())
				.thenReturn(new File("src/test/resources/policies").getAbsolutePath());
		var subject  = JSON.objectNode()
				.put(ENVIRONMENT_CLIENT_ID, "MQTT_CLIENT_SUBSCRIBE")
				.put(ENVIRONMENT_USER_NAME, "user1");
		var action   = JSON.objectNode().put(ENVIRONMENT_AUTHZ_ACTION_TYPE, SUBSCRIBE_AUTHZ_ACTION);
		var resource = JSON.objectNode().put(ENVIRONMENT_TOPIC, "topic");

		// WHEN
		var pdp = PdpInitUtility.buildPdp(saplMqttExtensionConfigMock, null, null);

		// THEN
		assertNotNull(pdp);
		var authzDecision = pdp.decide(AuthorizationSubscription.of(subject, action, resource)).blockFirst();
		assertNotNull(authzDecision);
		assertEquals(Decision.PERMIT, authzDecision.getDecision());
	}

	@Test
	void when_policiesPathIsNotProvidedAndPathFromConfigIsRelativeToExtensionHome_then_getPoliciesPathFromConfig() {
		// GIVEN
		var saplMqttExtensionConfigMock = Mockito.mock(SaplMqttExtensionConfig.class);
		when(saplMqttExtensionConfigMock.getPdpImplementation()).thenReturn(PdpInitUtility.EMBEDDED_PDP_IDENTIFIER);
		when(saplMqttExtensionConfigMock.isEmbeddedPdpPoliciesPathRelativeToExtensionHome()).thenReturn(true);
		when(saplMqttExtensionConfigMock.getEmbeddedPdpPoliciesPath()).thenReturn("/resources/policies");
		var subject  = JSON.objectNode()
				.put(ENVIRONMENT_CLIENT_ID, "MQTT_CLIENT_SUBSCRIBE")
				.put(ENVIRONMENT_USER_NAME, "user1");
		var action   = JSON.objectNode()
				.put(ENVIRONMENT_AUTHZ_ACTION_TYPE, SUBSCRIBE_AUTHZ_ACTION);
		var resource = JSON.objectNode()
				.put(ENVIRONMENT_TOPIC, "topic");

		// WHEN
		var pdp = PdpInitUtility.buildPdp(saplMqttExtensionConfigMock, new File("src/test"), null);

		// THEN
		assertNotNull(pdp);
		var authzDecision = pdp.decide(AuthorizationSubscription.of(subject, action, resource)).blockFirst();
		assertNotNull(authzDecision);
		assertEquals(Decision.PERMIT, authzDecision.getDecision());
	}

	@Test
	void when_buildingEmbeddedPdpThrowsInitializationException_then_returnNull() {
		// GIVEN
		var saplMqttExtensionConfigMock = Mockito.mock(SaplMqttExtensionConfig.class);
		when(saplMqttExtensionConfigMock.getPdpImplementation()).thenReturn(PdpInitUtility.EMBEDDED_PDP_IDENTIFIER);

		try (var pdpFactoryMock = Mockito.mockStatic(PolicyDecisionPointFactory.class)) {
			pdpFactoryMock.when(() -> PolicyDecisionPointFactory.filesystemPolicyDecisionPoint(Mockito.anyString()))
					.thenThrow(InitializationException.class);

			// WHEN
			var pdp = PdpInitUtility.buildPdp(saplMqttExtensionConfigMock, null, "/policies");

			// THEN
			assertNull(pdp);
		}
	}

	@Test
	void when_buildingRemotePdpAndClientKeyAndSecretAreSet_then_doNotUseDefaultKeyAndSecret() {
		// GIVEN
		var saplMqttExtensionConfigMock = Mockito.mock(SaplMqttExtensionConfig.class);
		when(saplMqttExtensionConfigMock.getPdpImplementation()).thenReturn(PdpInitUtility.REMOTE_PDP_IDENTIFIER);
		when(saplMqttExtensionConfigMock.getRemotePdpClientKey()).thenReturn("key");
		when(saplMqttExtensionConfigMock.getRemotePdpClientSecret()).thenReturn("secret");
		when(saplMqttExtensionConfigMock.getRemotePdpBaseUrl())
				.thenReturn(SaplMqttExtensionConfig.DEFAULT_REMOTE_PDP_BASE_URL);

		// WHEN
		var pdp = PdpInitUtility.buildPdp(saplMqttExtensionConfigMock, null, null);

		// THEN
		assertNotNull(pdp);
	}
}
