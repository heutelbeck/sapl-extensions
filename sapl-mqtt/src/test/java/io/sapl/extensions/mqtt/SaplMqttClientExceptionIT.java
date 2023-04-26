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

package io.sapl.extensions.mqtt;

import static io.sapl.extensions.mqtt.MqttTestUtil.buildAndStartBroker;
import static io.sapl.extensions.mqtt.MqttTestUtil.buildVariables;
import static io.sapl.extensions.mqtt.MqttTestUtil.stopBroker;
import static org.mockito.ArgumentMatchers.any;

import java.nio.file.Path;
import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import com.hivemq.embedded.EmbeddedHiveMQ;

import io.sapl.api.interpreter.Val;
import io.sapl.extensions.mqtt.util.DefaultResponseUtility;
import reactor.test.StepVerifier;

class SaplMqttClientExceptionIT {

	private final static long DELAY_MS = 1000L;

	@TempDir
	Path configDir;

	@TempDir
	Path dataDir;

	@TempDir
	Path extensionsDir;

	EmbeddedHiveMQ mqttBroker;
	SaplMqttClient saplMqttClient;

	@BeforeEach
	void beforeEach() {
		this.mqttBroker     = buildAndStartBroker(configDir, dataDir, extensionsDir);
		this.saplMqttClient = new SaplMqttClient();
	}

	@AfterEach
	void afterEach() {
		stopBroker(mqttBroker);
	}

	@Test
	void when_exceptionOccursWhileBuildingMessageFlux_then_returnFluxWithValOfError() {
		// GIVEN
		var topics = "topic";

		// WHEN
		var saplMqttMessageFlux = saplMqttClient.buildSaplMqttMessageFlux(Val.of(topics), null);

		// THEN
		StepVerifier.create(saplMqttMessageFlux)
				.thenAwait(Duration.ofMillis(1 * DELAY_MS))
				.expectNext(Val.error("Failed to build stream of messages."))
				.thenCancel()
				.verify();
	}

	@Test
	void when_exceptionOccursInTheMessageFlux_then_returnFluxWithValOfError() {
		// GIVEN
		var topics = "topic";

		// WHEN
		var saplMqttMessageFlux = saplMqttClient.buildSaplMqttMessageFlux(Val.of(topics), buildVariables());

		try (MockedStatic<DefaultResponseUtility> defaultResponseUtilityMockedStatic = Mockito
				.mockStatic(DefaultResponseUtility.class)) {
			defaultResponseUtilityMockedStatic.when(() -> DefaultResponseUtility.getDefaultResponseConfig(any(), any()))
					.thenThrow(new RuntimeException("Error in stream"));
			// THEN
			StepVerifier.create(saplMqttMessageFlux)
					.thenAwait(Duration.ofMillis(1 * DELAY_MS))
					.expectNext(Val.error("Error in stream"))
					.thenCancel()
					.verify();
		}
	}
}
