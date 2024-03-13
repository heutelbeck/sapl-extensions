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
package io.sapl.axon.queryhandling;

import static org.axonframework.messaging.GenericMessage.asMessage;
import static org.axonframework.queryhandling.QueryMessage.queryName;

import java.util.List;
import java.util.Map;

import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.DefaultQueryGateway;
import org.axonframework.queryhandling.DefaultSubscriptionQueryResult;
import org.axonframework.queryhandling.GenericSubscriptionQueryMessage;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.queryhandling.SubscriptionQueryMessage;
import org.axonframework.queryhandling.SubscriptionQueryResult;

import reactor.util.concurrent.Queues;

/**
 * A custom QueryGateway for sending recoverable subscription queries.
 * <p>
 * Usage:
 *
 * <pre>{@code
 * var result = queryGateway.recoverableSubscriptionQuery(
 *                queryName, queryPayload,instanceOf(String.class),
 *                instanceOf(String.class), accessDeniedHandler);
 * result.initialResult().subscribe();
 * result.updates().onErrorContinue((t, o) -> { \/* do something *\/}).subscribe();
 * }</pre>
 *
 * @author Dominic Heutelbeck
 * @since 2.1.0
 */
public class SaplQueryGateway extends DefaultQueryGateway {

    /**
     * Creates the SaplQueryGateway
     *
     * @param queryBus             A QueryBus.
     * @param dispatchInterceptors Available interceptors.
     */
    public SaplQueryGateway(QueryBus queryBus,
            List<MessageDispatchInterceptor<? super QueryMessage<?, ?>>> dispatchInterceptors) {
        super(DefaultQueryGateway.builder().dispatchInterceptors(dispatchInterceptors).queryBus(queryBus));
    }

    /**
     * Sends given {@code query} over the {@link QueryBus} and returns result
     * containing initial response and incremental updates (received at the moment
     * the query is sent, until it is cancelled by the caller or closed by the
     * emitting side).
     *
     * @param query               The {@code query} to be sent
     * @param initialResponseType The initial response type used for this query
     * @param updateResponseType  The update response type used for this query
     * @param accessDeniedHandler The runner called whenever an access-denied event
     *                            occurs in the update stream
     * @param <Q>                 The type of the query
     * @param <I>                 The type of the initial response
     * @param <U>                 The type of the incremental update
     * @return registration which can be used to cancel receiving updates
     * @see QueryBus#subscriptionQuery(SubscriptionQueryMessage)
     * @see QueryBus#subscriptionQuery(SubscriptionQueryMessage, int)
     */
    public <Q, I, U> SubscriptionQueryResult<I, U> recoverableSubscriptionQuery(Q query, Class<I> initialResponseType,
            Class<U> updateResponseType, Runnable accessDeniedHandler) {
        return recoverableSubscriptionQuery(queryName(query), query, initialResponseType, updateResponseType,
                accessDeniedHandler);
    }

    /**
     * Sends given {@code query} over the {@link QueryBus} and returns result
     * containing initial response and incremental updates (received at the moment
     * the query is sent, until it is cancelled by the caller or closed by the
     * emitting side).
     *
     * @param queryName           A {@link String} describing query to be executed
     * @param query               The {@code query} to be sent
     * @param initialResponseType The initial response type used for this query
     * @param updateResponseType  The update response type used for this query
     * @param accessDeniedHandler The runner called whenever an access-denied event
     *                            occurs in the update stream
     * @param <Q>                 The type of the query
     * @param <I>                 The type of the initial response
     * @param <U>                 The type of the incremental update
     * @return registration which can be used to cancel receiving updates
     * @see QueryBus#subscriptionQuery(SubscriptionQueryMessage)
     * @see QueryBus#subscriptionQuery(SubscriptionQueryMessage, int)
     */
    public <Q, I, U> SubscriptionQueryResult<I, U> recoverableSubscriptionQuery(String queryName, Q query,
            Class<I> initialResponseType, Class<U> updateResponseType, Runnable accessDeniedHandler) {
        return recoverableSubscriptionQuery(queryName, query, ResponseTypes.instanceOf(initialResponseType),
                ResponseTypes.instanceOf(updateResponseType), accessDeniedHandler);
    }

    /**
     * Sends given {@code query} over the {@link QueryBus} and returns result
     * containing initial response and incremental updates (received at the moment
     * the query is sent, until it is cancelled by the caller or closed by the
     * emitting side).
     *
     * @param query               The {@code query} to be sent
     * @param initialResponseType The initial response type used for this query
     * @param updateResponseType  The update response type used for this query
     * @param accessDeniedHandler The runner called whenever an access-denied event
     *                            occurs in the update stream
     * @param <Q>                 The type of the query
     * @param <I>                 The type of the initial response
     * @param <U>                 The type of the incremental update
     * @return registration which can be used to cancel receiving updates
     * @see QueryBus#subscriptionQuery(SubscriptionQueryMessage)
     * @see QueryBus#subscriptionQuery(SubscriptionQueryMessage, int)
     */
    public <Q, I, U> SubscriptionQueryResult<I, U> recoverableSubscriptionQuery(Q query,
            ResponseType<I> initialResponseType, ResponseType<U> updateResponseType, Runnable accessDeniedHandler) {
        return recoverableSubscriptionQuery(queryName(query), query, initialResponseType, updateResponseType,
                accessDeniedHandler);
    }

    /**
     * Sends given {@code query} over the {@link QueryBus} and returns result
     * containing initial response and incremental updates (received at the moment
     * the query is sent, until it is cancelled by the caller or closed by the
     * emitting side).
     *
     * @param queryName           a {@link String} describing query to be executed
     * @param query               the {@code query} to be sent
     * @param initialResponseType the initial response type used for this query
     * @param updateResponseType  the update response type used for this query
     * @param accessDeniedHandler The runner called whenever an access-denied event
     *                            occurs in the update stream
     * @param <Q>                 the type of the query
     * @param <I>                 the type of the initial response
     * @param <U>                 the type of the incremental update
     * @return registration which can be used to cancel receiving updates
     * @see QueryBus#subscriptionQuery(SubscriptionQueryMessage)
     * @see QueryBus#subscriptionQuery(SubscriptionQueryMessage, int)
     */
    public <Q, I, U> SubscriptionQueryResult<I, U> recoverableSubscriptionQuery(String queryName, Q query,
            ResponseType<I> initialResponseType, ResponseType<U> updateResponseType, Runnable accessDeniedHandler) {
        return recoverableSubscriptionQuery(queryName, query, initialResponseType, updateResponseType,
                Queues.SMALL_BUFFER_SIZE, accessDeniedHandler);
    }

    /**
     * Sends given {@code query} over the {@link QueryBus} and returns result
     * containing initial response and incremental updates (received at the moment
     * the query is sent, until it is cancelled by the caller or closed by the
     * emitting side).
     *
     * @param queryName           a {@link String} describing query to be executed
     * @param query               the {@code query} to be sent
     * @param initialResponseType the initial response type used for this query
     * @param updateResponseType  the update response type used for this query
     * @param updateBufferSize    the size of buffer which accumulates updates
     * @param accessDeniedHandler The runner called whenever an access-denied event
     *                            occurs in the update stream before subscription to
     *                            the flux is made
     * @param <Q>                 the type of the query
     * @param <I>                 the type of the initial response
     * @param <U>                 the type of the incremental update
     * @return registration which can be used to cancel receiving updates
     * @see QueryBus#subscriptionQuery(SubscriptionQueryMessage)
     * @see QueryBus#subscriptionQuery(SubscriptionQueryMessage, int)
     */
    @SuppressWarnings("rawtypes")
    public <Q, I, U> SubscriptionQueryResult<I, U> recoverableSubscriptionQuery(String queryName, Q query,
            ResponseType<I> initialResponseType, ResponseType<U> updateResponseType, int updateBufferSize,
            Runnable accessDeniedHandler) {
        SubscriptionQueryMessage<?, I, RecoverableResponse> recoverableSubscriptionQueryMessage = new GenericSubscriptionQueryMessage<>(
                asMessage(query), queryName, initialResponseType, ResponseTypes.instanceOf(RecoverableResponse.class))
                .andMetaData(Map.of(RecoverableResponse.RECOVERABLE_UPDATE_TYPE_KEY, updateResponseType));

        SubscriptionQueryResult<I, RecoverableResponse> wrappedResult = subscriptionQuery(queryName,
                recoverableSubscriptionQueryMessage, initialResponseType,
                ResponseTypes.instanceOf(RecoverableResponse.class), updateBufferSize);

        return unwrap(wrappedResult, updateResponseType, accessDeniedHandler);
    }

    @SuppressWarnings("rawtypes")
    private <I, U> SubscriptionQueryResult<I, U> unwrap(SubscriptionQueryResult<I, RecoverableResponse> wrappedResult,
            ResponseType<U> updateResponseType, Runnable accessDeniedHandler) {
        return new DefaultSubscriptionQueryResult<>(wrappedResult.initialResult(),
                wrappedResult.updates().map(RecoverableResponse::unwrap)
                        .doOnError(response -> accessDeniedHandler.run()).map(updateResponseType::convert),
                wrappedResult);
    }

}
