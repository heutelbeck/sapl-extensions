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
package io.sapl.axon.constrainthandling;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Function;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.messaging.Message;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.sapl.api.pdp.AuthorizationDecision;

@DisplayName("Command constraint handler bundle")
class CommandConstraintHandlerBundleTests {

    @Test
    void testAllInvocations() {
        var onDecisionCounter      = new AtomicInteger();
        var handlerOnObjectCounter = new AtomicInteger();
        var decision               = AuthorizationDecision.INDETERMINATE;
        var message                = new GenericCommandMessage<>("message payload");
        var exception              = new Exception("another exception message");
        var result                 = "some result";

        BiConsumer<AuthorizationDecision, Message<?>>  onDecision       = (decisionInternal, messageInternal) -> {
                                                                            assertThat(decisionInternal)
                                                                                    .isEqualTo(decision);
                                                                            assertThat(messageInternal)
                                                                                    .isEqualTo(message);
                                                                            onDecisionCounter.getAndIncrement();
                                                                        };
        Function<Throwable, Throwable>                 errorMapper      = t -> new Exception("some special message");
        Function<CommandMessage<?>, CommandMessage<?>> commandMapper    = c -> new GenericCommandMessage<>(
                "special payload");
        Function<String, String>                       resultMapper     = r -> "special result";
        Runnable                                       handlersOnObject = handlerOnObjectCounter::getAndIncrement;
        var                                            bundle           = new CommandConstraintHandlerBundle<>(
                onDecision, errorMapper, commandMapper, resultMapper, handlersOnObject);

        bundle.executeOnDecisionHandlers(decision, message);
        assertThat(onDecisionCounter.get()).isEqualTo(1);

        var mappedException = bundle.executeOnErrorHandlers(exception);
        assertThat(mappedException.getClass()).isEqualTo(Exception.class);
        assertThat(mappedException.getLocalizedMessage()).isEqualTo("some special message");

        bundle.executeAggregateConstraintHandlerMethods();
        assertThat(handlerOnObjectCounter.get()).isEqualTo(1);

        var mappedCommandMessage = bundle.executeCommandMappingHandlers(message);
        assertThat(mappedCommandMessage.getClass()).isEqualTo(GenericCommandMessage.class);
        assertThat(mappedCommandMessage.getPayloadType()).isEqualTo(String.class);
        assertThat(mappedCommandMessage.getPayload()).isEqualTo("special payload");

        var mappedResult = bundle.executePostHandlingHandlers(result);
        assertThat(mappedResult).isEqualTo("special result");
    }

    @Test
    void when_mappedErrorIsThrowable_convertToRuntimeException() {
        BiConsumer<AuthorizationDecision, Message<?>>  onDecision       = (decision, message) -> {};
        Function<Throwable, Throwable>                 errorMapper      = t -> new Throwable("some special message");
        Function<CommandMessage<?>, CommandMessage<?>> commandMapper    = c -> null;
        Function<String, String>                       resultMapper     = r -> null;
        Runnable                                       handlersOnObject = () -> {};
        var                                            bundle           = new CommandConstraintHandlerBundle<>(
                onDecision, errorMapper, commandMapper, resultMapper, handlersOnObject);

        var exception       = new Exception("another exception message");
        var mappedException = bundle.executeOnErrorHandlers(exception);
        assertThat(mappedException.getClass()).isEqualTo(RuntimeException.class);
        assertThat(mappedException.getLocalizedMessage()).isEqualTo("Error: another exception message");
        assertThat(mappedException.getCause().getClass()).isEqualTo(Exception.class);
        assertThat(mappedException.getCause().getLocalizedMessage()).isEqualTo("another exception message");
    }
}
