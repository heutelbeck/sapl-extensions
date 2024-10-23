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
package io.sapl.axon.constrainthandling;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;

import org.axonframework.messaging.GenericResultMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.ResultMessage;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.GenericQueryMessage;
import org.axonframework.queryhandling.QueryMessage;
import org.junit.jupiter.api.Test;

import io.sapl.api.pdp.AuthorizationDecision;

class QueryConstraintHandlerBundleTests {

    @Test
    void testAllInvokations() {
        var onDecisionCounter = new AtomicInteger();
        var decision          = new AuthorizationDecision();
        var message           = new GenericQueryMessage<>("message payload", ResponseTypes.instanceOf(String.class));
        var exception         = new Exception("another exception message");
        var result            = "some result";
        var filterBehaviour   = new AtomicBoolean();

        BiConsumer<AuthorizationDecision, Message<?>>    onDecision           = (decisionInternal, messageInternal) -> {
                                                                                  assertEquals(decision,
                                                                                          decisionInternal);
                                                                                  assertEquals(message,
                                                                                          messageInternal);
                                                                                  onDecisionCounter.getAndIncrement();
                                                                              };
        Function<QueryMessage<?, ?>, QueryMessage<?, ?>> queryMappers         = x -> new GenericQueryMessage<>(
                "special payload", ResponseTypes.instanceOf(String.class));
        Function<Throwable, Throwable>                   errorMapper          = x -> new Exception(
                "some spectial message");
        Function<String, String>                         initialResultMappers = x -> "spectial initial result";
        Function<ResultMessage<?>, ResultMessage<?>>     updateMappers        = x -> new GenericResultMessage<>(
                "special update result");
        Predicate<ResultMessage<?>>                      filterPredicates     = x -> filterBehaviour.get();
        var                                              bundle               = new QueryConstraintHandlerBundle<>(
                onDecision, queryMappers, errorMapper, initialResultMappers, updateMappers, filterPredicates);

        bundle.executeOnDecisionHandlers(decision, message);
        assertEquals(1, onDecisionCounter.get());

        var mappedException = bundle.executeOnErrorHandlers(exception);
        assertEquals(Exception.class, mappedException.getClass());
        assertEquals("some spectial message", mappedException.getLocalizedMessage());

        var mappedQueryMessage = bundle.executePreHandlingHandlers(message);
        assertEquals(GenericQueryMessage.class, mappedQueryMessage.getClass());
        assertEquals(String.class, mappedQueryMessage.getPayloadType());
        assertEquals("special payload", mappedQueryMessage.getPayload());
        assertEquals(ResponseTypes.instanceOf(String.class), mappedQueryMessage.getResponseType());

        var mappedResult = bundle.executePostHandlingHandlers(result);
        assertEquals("spectial initial result", mappedResult);

        filterBehaviour.set(true);
        var presentMappedResult = bundle.executeOnNextHandlers(new GenericResultMessage<>(result));
        assertTrue(presentMappedResult.isPresent());
        assertEquals(GenericResultMessage.class, presentMappedResult.get().getClass());
        assertEquals(String.class, presentMappedResult.get().getPayloadType());
        assertEquals("special update result", presentMappedResult.get().getPayload());

        filterBehaviour.set(false);
        var emptyMappedResult = bundle.executeOnNextHandlers(new GenericResultMessage<>(result));
        assertTrue(emptyMappedResult.isEmpty());
    }

    @Test
    void whenresultIsCompletableFurute_then_returnMappedCompletableFuture() {
        BiConsumer<AuthorizationDecision, Message<?>>    onDecision           = (decisionInternal,
                messageInternal) -> {};
        Function<QueryMessage<?, ?>, QueryMessage<?, ?>> queryMappers         = x -> null;
        Function<Throwable, Throwable>                   errorMapper          = x -> null;
        Function<String, String>                         initialResultMappers = x -> "spectial initial result";
        Function<ResultMessage<?>, ResultMessage<?>>     updateMappers        = x -> null;
        Predicate<ResultMessage<?>>                      filterPredicates     = x -> false;
        var                                              bundle               = new QueryConstraintHandlerBundle<>(
                onDecision, queryMappers, errorMapper, initialResultMappers, updateMappers, filterPredicates);

        var result = new CompletableFuture<String>();
        result.complete("some result");
        var futureMappedResult = bundle.executePostHandlingHandlers(result);
        assertEquals(CompletableFuture.class, futureMappedResult.getClass());
        @SuppressWarnings("unchecked")
        var mappedResult = assertDoesNotThrow(() -> ((CompletableFuture<String>) futureMappedResult).get());
        assertEquals("spectial initial result", mappedResult);
    }
}
