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

import java.util.function.BiConsumer;
import java.util.function.Function;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.messaging.Message;

import io.sapl.api.pdp.AuthorizationDecision;
import lombok.RequiredArgsConstructor;

/**
 *
 * The class is a container to collect all aggregated constraint handlers for a
 * specific decision applicable to a command handling scenario.
 *
 * @author Dominic Heutelbeck
 * @since 2.1.0
 *
 * @param <R> result type
 */
@RequiredArgsConstructor
public class CommandConstraintHandlerBundle<R> {
    /**
     * A bundle which only contains handlers performing no operation.
     */
    public static final CommandConstraintHandlerBundle<?> NOOP_BUNDLE = new CommandConstraintHandlerBundle<>();

    private final BiConsumer<AuthorizationDecision, Message<?>>  onDecision;
    private final Function<Throwable, Throwable>                 errorMapper;
    private final Function<CommandMessage<?>, CommandMessage<?>> commandMapper;
    private final Function<R, R>                                 resultMapper;
    private final Runnable                                       handlersOnObject;

    /**
     * Constructs a bundle with all no operation functions.
     */
    private CommandConstraintHandlerBundle() {
        // @formatter:off
		this.onDecision = (decision,message)->{};
		this.commandMapper = Function.identity();
		this.errorMapper = Function.identity();
		this.resultMapper = Function.identity();
		this.handlersOnObject = ()->{};
		// @formatter:on
    }

    /**
     * Execute all handlers assigned to be executed after each decision.
     *
     * @param decision The authorization decision.
     * @param message The command message under authorization.
     */
    public void executeOnDecisionHandlers(AuthorizationDecision decision, Message<?> message) {
        onDecision.accept(decision, message);
    }

    /**
     * Execute error constraint handlers.
     *
     * @param t An error.
     * @return The error after application of potential transformations.
     */
    public Exception executeOnErrorHandlers(Exception t) {
        var mapped = errorMapper.apply(t);
        if (mapped instanceof Exception exception) {
            return exception;
        }
        return new RuntimeException("Error: " + t.getMessage(), t);
    }

    /**
     * Executes all responsible {@code @ConstraintHandler} methods on the handler
     * object.
     */
    public void executeAggregateConstraintHandlerMethods() {
        handlersOnObject.run();
    }

    /**
     * Executes all constraint handler transforming the command before handling.
     *
     * @param message The original {@code CommandMessage}.
     * @return The transformed command.
     */
    public CommandMessage<?> executeCommandMappingHandlers(CommandMessage<?> message) {
        return commandMapper.apply(message);
    }

    /**
     * Execute all result transforming handlers.
     *
     * @param result The original result.
     * @return The transformed result.
     */
    @SuppressWarnings("unchecked")
    public Object executePostHandlingHandlers(Object result) {
        return resultMapper.apply((R) result);
    }

}
