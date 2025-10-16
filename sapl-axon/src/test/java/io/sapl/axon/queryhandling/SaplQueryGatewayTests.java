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
package io.sapl.axon.queryhandling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.DefaultSubscriptionQueryResult;
import org.axonframework.queryhandling.QueryUpdateEmitter;
import org.axonframework.queryhandling.SimpleQueryBus;
import org.axonframework.queryhandling.SubscriptionQueryMessage;
import org.axonframework.queryhandling.UpdateHandlerRegistration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class SaplQueryGatewayTests {

    private static final AccessDeniedException ACCESS_DENIED         = new AccessDeniedException("Access denied");
    private static final String                DEFAULT_QUERY_MESSAGE = "default test query message";
    private static final ResponseType<?>       DEFAULT_RESPONSE_TYPE = ResponseTypes.instanceOf(String.class);

    private SaplQueryGateway gateway;
    private AtomicInteger    accessDeniedCounter;
    private Runnable         accessDeniedHandler = () -> accessDeniedCounter.getAndIncrement();

    @BeforeEach
    void beforeEach() {
        gateway             = spy(new SaplQueryGateway(SimpleQueryBus.builder().build(), List.of()));
        accessDeniedCounter = new AtomicInteger(0);
    }

    @Test
    void when_recoverableSubscriptionQuery_with_responseTypes_then_subscriptionQueryResult() {
        gateway.recoverableSubscriptionQuery(DEFAULT_QUERY_MESSAGE, DEFAULT_RESPONSE_TYPE, DEFAULT_RESPONSE_TYPE,
                accessDeniedHandler);
        verify(gateway, times(1)).subscriptionQuery(eq(String.class.getName()), any(SubscriptionQueryMessage.class),
                eq(DEFAULT_RESPONSE_TYPE), eq(ResponseTypes.instanceOf(RecoverableResponse.class)), any(int.class));
        assertEquals(0, accessDeniedCounter.get());
    }

    @Test
    void when_recoverableSubscriptionQuery_with_types_then_subscriptionQueryResult() {
        gateway.recoverableSubscriptionQuery(DEFAULT_QUERY_MESSAGE, String.class, String.class, accessDeniedHandler);
        verify(gateway, times(1)).subscriptionQuery(eq(String.class.getName()), any(SubscriptionQueryMessage.class),
                eq(DEFAULT_RESPONSE_TYPE), eq(ResponseTypes.instanceOf(RecoverableResponse.class)), any(int.class));
        assertEquals(0, accessDeniedCounter.get());
    }

    @Test
    @SuppressWarnings("unchecked")
    void when_wrappedSubscriptionQuery_with_accessDenied_then_executeAccessDeniedHandler() {
        var emitter      = mock(QueryUpdateEmitter.class);
        var registration = new UpdateHandlerRegistration<>(() -> false, Flux.just(), () -> {});
        when(emitter.registerUpdateHandler(any(SubscriptionQueryMessage.class), anyInt())).thenReturn(registration);
        var queryBus = spy(SimpleQueryBus.builder().queryUpdateEmitter(emitter).build());
        var aGateway = spy(new SaplQueryGateway(queryBus, List.of()));

        doReturn(new DefaultSubscriptionQueryResult<>(Mono.empty(), Flux.error(ACCESS_DENIED), () -> false))
                .when(aGateway).subscriptionQuery(any(String.class), any(SubscriptionQueryMessage.class),
                        any(ResponseType.class), any(ResponseType.class), anyInt());
        var result = aGateway.recoverableSubscriptionQuery(DEFAULT_QUERY_MESSAGE, DEFAULT_RESPONSE_TYPE,
                DEFAULT_RESPONSE_TYPE, accessDeniedHandler);

        assertNull(result.initialResult().block());
        var updates = result.updates();
        assertThrows(AccessDeniedException.class, updates::blockLast);
        assertEquals(1, accessDeniedCounter.get());
    }
}
