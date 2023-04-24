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

package io.sapl.interpreter.pip;

import static org.mockito.ArgumentMatchers.any;

import java.time.Duration;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import com.hivemq.client.mqtt.mqtt5.Mqtt5BlockingClient;
import com.hivemq.embedded.EmbeddedHiveMQ;

import ch.qos.logback.classic.Level;
import io.sapl.api.interpreter.Val;
import io.sapl.interpreter.InitializationException;
import io.sapl.interpreter.pip.util.DefaultResponseUtility;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

class SaplMqttClientExceptionTest extends SaplMqttClientTest{
    private static EmbeddedHiveMQ embeddedHiveMqBroker;
    private static Mqtt5BlockingClient hiveMqClient;

    @BeforeAll
    static void setUp() throws InitializationException {
        // set logging level
        logger.setLevel(Level.DEBUG);

        embeddedHiveMqBroker = startEmbeddedHiveMqBroker();
        hiveMqClient = startMqttClient();
    }

    @AfterAll
    static void tearDown() {
        hiveMqClient.disconnect();
        embeddedHiveMqBroker.stop().join();
    }

    @Test
    void when_exceptionOccursWhileBuildingMessageFlux_then_returnFluxWithValOfError() {
        // GIVEN
        var topics = "topic";

        // WHEN
        Flux<Val> saplMqttMessageFlux = saplMqttClient.buildSaplMqttMessageFlux(Val.of(topics), null);

        // THEN
        StepVerifier.create(saplMqttMessageFlux)
                .thenAwait(Duration.ofMillis(1000))
                .expectNext(Val.error("Failed to build stream of messages."))
                .thenCancel()
                .verify();
    }

    @Test
    void when_exceptionOccursInTheMessageFlux_then_returnFluxWithValOfError() {
        // GIVEN
        var topics = "topic";

        // WHEN
        Flux<Val> saplMqttMessageFlux = saplMqttClient.buildSaplMqttMessageFlux(Val.of(topics), config);

        try (MockedStatic<DefaultResponseUtility> defaultResponseUtilityMockedStatic =
                     Mockito.mockStatic(DefaultResponseUtility.class)) {
            defaultResponseUtilityMockedStatic.when(()->
                    DefaultResponseUtility.getDefaultResponseConfig(any(), any()))
                    .thenThrow(new RuntimeException("Error in stream"));
            // THEN
            StepVerifier.create(saplMqttMessageFlux)
                    .thenAwait(Duration.ofMillis(1000))
                    .expectNext(Val.error("Error in stream"))
                    .thenCancel()
                    .verify();
        }
    }
}
