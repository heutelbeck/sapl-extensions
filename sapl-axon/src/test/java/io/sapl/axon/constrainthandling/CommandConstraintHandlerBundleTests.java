/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Function;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.messaging.Message;
import org.junit.jupiter.api.Test;

import io.sapl.api.pdp.AuthorizationDecision;

class CommandConstraintHandlerBundleTests {

    @Test
    void testAllInvocations() {
        var onDecisionCounter      = new AtomicInteger();
        var handlerOnObjectCounter = new AtomicInteger();
        var decision               = new AuthorizationDecision();
        var message                = new GenericCommandMessage<>("message payload");
        var exception              = new Exception("another exception message");
        var result                 = "some result";

        BiConsumer<AuthorizationDecision, Message<?>>  onDecision       = (decisionInternal, messageInternal) -> {
                                                                            assertEquals(decision, decisionInternal);
                                                                            assertEquals(message, messageInternal);
                                                                            onDecisionCounter.getAndIncrement();
                                                                        };
        Function<Throwable, Throwable>                 errorMapper      = t -> new Exception("some spectial message");
        Function<CommandMessage<?>, CommandMessage<?>> commandMapper    = c -> new GenericCommandMessage<>(
                "special payload");
        Function<String, String>                       resultMapper     = r -> "special result";
        Runnable                                       handlersOnObject = handlerOnObjectCounter::getAndIncrement;
        var                                            bundle           = new CommandConstraintHandlerBundle<>(
                onDecision, errorMapper, commandMapper, resultMapper, handlersOnObject);

        bundle.executeOnDecisionHandlers(decision, message);
        assertEquals(1, onDecisionCounter.get());

        var mappedException = bundle.executeOnErrorHandlers(exception);
        assertEquals(Exception.class, mappedException.getClass());
        assertEquals("some spectial message", mappedException.getLocalizedMessage());

        bundle.executeAggregateConstraintHandlerMethods();
        assertEquals(1, handlerOnObjectCounter.get());

        var mappedCommandMessage = bundle.executeCommandMappingHandlers(message);
        assertEquals(GenericCommandMessage.class, mappedCommandMessage.getClass());
        assertEquals(String.class, mappedCommandMessage.getPayloadType());
        assertEquals("special payload", mappedCommandMessage.getPayload());

        var mappedResult = bundle.executePostHandlingHandlers(result);
        assertEquals("special result", mappedResult);
    }

    @Test
    void when_mappedErrorIsThrowable_convertToRuntimeException() {
        BiConsumer<AuthorizationDecision, Message<?>>  onDecision       = (decision, message) -> {};
        Function<Throwable, Throwable>                 errorMapper      = t -> new Throwable("some spectial message");
        Function<CommandMessage<?>, CommandMessage<?>> commandMapper    = c -> null;
        Function<String, String>                       resultMapper     = r -> null;
        Runnable                                       handlersOnObject = () -> {};
        var                                            bundle           = new CommandConstraintHandlerBundle<>(
                onDecision, errorMapper, commandMapper, resultMapper, handlersOnObject);

        var exception       = new Exception("another exception message");
        var mappedException = bundle.executeOnErrorHandlers(exception);
        assertEquals(RuntimeException.class, mappedException.getClass());
        assertEquals("Error: another exception message", mappedException.getLocalizedMessage());
        assertEquals(Exception.class, mappedException.getCause().getClass());
        assertEquals("another exception message", mappedException.getCause().getLocalizedMessage());
    }
}
