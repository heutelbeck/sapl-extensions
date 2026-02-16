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
import static org.assertj.core.api.Assertions.assertThatCode;

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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.sapl.api.pdp.AuthorizationDecision;

@DisplayName("Query constraint handler bundle")
class QueryConstraintHandlerBundleTests {

    @Test
    void testAllInvocations() {
        var onDecisionCounter = new AtomicInteger();
        var decision          = AuthorizationDecision.INDETERMINATE;
        var message           = new GenericQueryMessage<>("message payload", ResponseTypes.instanceOf(String.class));
        var exception         = new Exception("another exception message");
        var result            = "some result";
        var filterBehaviour   = new AtomicBoolean();

        BiConsumer<AuthorizationDecision, Message<?>>    onDecision           = (decisionInternal, messageInternal) -> {
                                                                                  assertThat(decisionInternal)
                                                                                          .isEqualTo(decision);
                                                                                  assertThat(messageInternal)
                                                                                          .isEqualTo(message);
                                                                                  onDecisionCounter.getAndIncrement();
                                                                              };
        Function<QueryMessage<?, ?>, QueryMessage<?, ?>> queryMappers         = x -> new GenericQueryMessage<>(
                "special payload", ResponseTypes.instanceOf(String.class));
        Function<Throwable, Throwable>                   errorMapper          = x -> new Exception(
                "some special message");
        Function<String, String>                         initialResultMappers = x -> "special initial result";
        Function<ResultMessage<?>, ResultMessage<?>>     updateMappers        = x -> new GenericResultMessage<>(
                "special update result");
        Predicate<ResultMessage<?>>                      filterPredicates     = x -> filterBehaviour.get();
        var                                              bundle               = new QueryConstraintHandlerBundle<>(
                onDecision, queryMappers, errorMapper, initialResultMappers, updateMappers, filterPredicates);

        bundle.executeOnDecisionHandlers(decision, message);
        assertThat(onDecisionCounter.get()).isEqualTo(1);

        var mappedException = bundle.executeOnErrorHandlers(exception);
        assertThat(mappedException.getClass()).isEqualTo(Exception.class);
        assertThat(mappedException.getLocalizedMessage()).isEqualTo("some special message");

        var mappedQueryMessage = bundle.executePreHandlingHandlers(message);
        assertThat(mappedQueryMessage.getClass()).isEqualTo(GenericQueryMessage.class);
        assertThat(mappedQueryMessage.getPayloadType()).isEqualTo(String.class);
        assertThat(mappedQueryMessage.getPayload()).isEqualTo("special payload");
        assertThat(mappedQueryMessage.getResponseType()).isEqualTo(ResponseTypes.instanceOf(String.class));

        var mappedResult = bundle.executePostHandlingHandlers(result);
        assertThat(mappedResult).isEqualTo("special initial result");

        filterBehaviour.set(true);
        var presentMappedResult = bundle.executeOnNextHandlers(new GenericResultMessage<>(result));
        assertThat(presentMappedResult).isPresent();
        assertThat(presentMappedResult.get().getClass()).isEqualTo(GenericResultMessage.class);
        assertThat(presentMappedResult.get().getPayloadType()).isEqualTo(String.class);
        assertThat(presentMappedResult.get().getPayload()).isEqualTo("special update result");

        filterBehaviour.set(false);
        var emptyMappedResult = bundle.executeOnNextHandlers(new GenericResultMessage<>(result));
        assertThat(emptyMappedResult).isEmpty();
    }

    @Test
    void whenResultIsCompletableFuture_then_returnMappedCompletableFuture() {
        BiConsumer<AuthorizationDecision, Message<?>>    onDecision           = (decisionInternal,
                messageInternal) -> {};
        Function<QueryMessage<?, ?>, QueryMessage<?, ?>> queryMappers         = x -> null;
        Function<Throwable, Throwable>                   errorMapper          = x -> null;
        Function<String, String>                         initialResultMappers = x -> "special initial result";
        Function<ResultMessage<?>, ResultMessage<?>>     updateMappers        = x -> null;
        Predicate<ResultMessage<?>>                      filterPredicates     = x -> false;
        var                                              bundle               = new QueryConstraintHandlerBundle<>(
                onDecision, queryMappers, errorMapper, initialResultMappers, updateMappers, filterPredicates);

        var result = new CompletableFuture<String>();
        result.complete("some result");
        var futureMappedResult = bundle.executePostHandlingHandlers(result);
        assertThat(futureMappedResult.getClass()).isEqualTo(CompletableFuture.class);
        @SuppressWarnings("unchecked")
        var castFuture = (CompletableFuture<String>) futureMappedResult;
        assertThatCode(() -> {
            var mappedResult = castFuture.get();
            assertThat(mappedResult).isEqualTo("special initial result");
        }).doesNotThrowAnyException();
    }
}
