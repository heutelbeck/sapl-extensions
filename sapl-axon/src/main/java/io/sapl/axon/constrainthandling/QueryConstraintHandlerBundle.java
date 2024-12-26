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

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;

import org.axonframework.messaging.Message;
import org.axonframework.messaging.ResultMessage;
import org.axonframework.queryhandling.QueryMessage;

import io.sapl.api.pdp.AuthorizationDecision;
import lombok.RequiredArgsConstructor;

/**
 *
 * The class is a container to collect all aggregated constraint handlers for a
 * specific decision applicable to a query handling scenario.
 *
 * @author Dominic Heutelbeck
 * @since 2.1.0
 *
 * @param <I> initial result type
 */
@RequiredArgsConstructor
public class QueryConstraintHandlerBundle<I> {
    /**
     * A bundle which only contains handlers performing no operation.
     */
    public static final QueryConstraintHandlerBundle<?> NOOP_BUNDLE = new QueryConstraintHandlerBundle<>();

    private final BiConsumer<AuthorizationDecision, Message<?>>    onDecisionHandlers;
    private final Function<QueryMessage<?, ?>, QueryMessage<?, ?>> queryMappers;
    private final Function<Throwable, Throwable>                   errorMappers;
    private final Function<I, I>                                   initialResultMappers;
    private final Function<ResultMessage<?>, ResultMessage<?>>     updateMappers;
    private final Predicate<ResultMessage<?>>                      filterPredicates;

    /**
     * Constructs a bundle with all no operation functions.
     */
    private QueryConstraintHandlerBundle() {
        // @formatter:off
		this.onDecisionHandlers   = (decision,message)->{};
		this.queryMappers         = Function.identity();
		this.errorMappers         = Function.identity();
		this.initialResultMappers = Function.identity();
		this.updateMappers        = Function.identity();
		this.filterPredicates     = any -> true;
		// @formatter:on
    }

    /**
     * Execute all handlers assigned to be executed after each decision.
     *
     * @param decision The authorization decision.
     * @param message The command message under authorization.
     */
    public void executeOnDecisionHandlers(AuthorizationDecision decision, Message<?> message) {
        onDecisionHandlers.accept(decision, message);
    }

    /**
     * Execute error constraint handlers.
     *
     * @param t An error.
     * @return The error after application of potential transformations.
     */
    public Throwable executeOnErrorHandlers(Throwable t) {
        return errorMappers.apply(t);
    }

    /**
     * Executes all constraint handler transforming the query before handling.
     *
     * @param message The original {@code QueryMessage}.
     * @return The transformed command.
     */
    public QueryMessage<?, ?> executePreHandlingHandlers(QueryMessage<?, ?> message) {
        return queryMappers.apply(message);
    }

    /**
     * Execute all result transforming handlers.
     *
     * @param result The original result.
     * @return The transformed result.
     */
    @SuppressWarnings("unchecked") // The handlers have been validated to support the returnType
    public Object executePostHandlingHandlers(Object result) {
        if (result instanceof CompletableFuture) {
            return ((CompletableFuture<?>) result).thenApply(this::executePostHandlingHandlers);
        }

        return initialResultMappers.apply((I) result);
    }

    /**
     * Execute all update transforming handlers.
     *
     * @param message The original result.
     * @return The transformed result.
     */
    public Optional<ResultMessage<?>> executeOnNextHandlers(ResultMessage<?> message) {
        var updated = updateMappers.apply(message);

        if (!filterPredicates.test(message))
            return Optional.empty();

        return Optional.ofNullable(updated);
    }
}
