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
package io.sapl.axon.queryhandling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.util.ReflectionTestUtils.setField;

import java.lang.annotation.Annotation;
import java.lang.reflect.Executable;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.axonframework.messaging.Message;
import org.axonframework.messaging.annotation.AnnotatedMessageHandlingMember;
import org.axonframework.messaging.annotation.DefaultParameterResolverFactory;
import org.axonframework.messaging.annotation.MessageHandlingMember;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.GenericQueryMessage;
import org.axonframework.queryhandling.GenericSubscriptionQueryMessage;
import org.axonframework.queryhandling.QueryHandler;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.queryhandling.SubscriptionQueryMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import io.sapl.api.model.Value;
import io.sapl.api.model.ValueJsonMarshaller;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.axon.annotation.EnforceDropUpdatesWhileDenied;
import io.sapl.axon.annotation.EnforceRecoverableUpdatesIfDenied;
import io.sapl.axon.annotation.PostHandleEnforce;
import io.sapl.axon.annotation.PreHandleEnforce;
import io.sapl.axon.configuration.SaplAxonProperties;
import io.sapl.axon.constrainthandling.ConstraintHandlerService;
import io.sapl.axon.constrainthandling.QueryConstraintHandlerBundle;
import io.sapl.axon.subscription.AuthorizationSubscriptionBuilderService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;
import reactor.core.publisher.Flux;

@DisplayName("Query policy enforcement point")
class QueryPolicyEnforcementPointTests {

    private static final String                MAPPER_FIELD_NAME = "mapper";
    private static final AccessDeniedException ACCESS_DENIED     = new AccessDeniedException("Access denied");
    private static final TestQueryPayload      DEFAULT_PAYLOAD   = new TestQueryPayload();
    private static final TestResponseType      DEFAULT_RESPONSE  = new TestResponseType();

    @SuppressWarnings("rawtypes")
    private static final QueryMessage DEFAULT_QUERY_MESSAGE = new GenericQueryMessage<TestQueryPayload, TestResponseType>(
            DEFAULT_PAYLOAD, ResponseTypes.instanceOf(TestResponseType.class));

    @SuppressWarnings("rawtypes")
    private static final SubscriptionQueryMessage DEFAULT_SUBSCRIPTION_QUERY_MESSAGE = new GenericSubscriptionQueryMessage<TestQueryPayload, TestResponseType, TestResponseType>(
            DEFAULT_PAYLOAD, ResponseTypes.instanceOf(TestResponseType.class),
            ResponseTypes.instanceOf(TestResponseType.class));

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class TestQueryPayload {
        private Object someField = "some text";
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class TestResponseType {
        private Object someField = "some text";
    }

    @Setter
    @AllArgsConstructor
    private static class HandlingObject {

        private TestResponseType response;

        @QueryHandler
        @PreHandleEnforce
        public TestResponseType handle1(TestQueryPayload query) {
            return response;
        }

        @QueryHandler
        public TestResponseType handle2(TestQueryPayload query) {
            return response;
        }

        @QueryHandler
        @PreHandleEnforce
        @EnforceDropUpdatesWhileDenied
        public TestResponseType handle3(TestQueryPayload query) {
            return response;
        }

        @QueryHandler
        @PreHandleEnforce
        public CompletableFuture<TestResponseType> handle4(TestQueryPayload query) {
            return CompletableFuture.completedFuture(response);
        }

        @QueryHandler
        @EnforceDropUpdatesWhileDenied
        public TestResponseType handle5(TestQueryPayload query) {
            return response;
        }

        @QueryHandler
        @EnforceRecoverableUpdatesIfDenied
        public TestResponseType handle6(TestQueryPayload query) {
            return response;
        }

        @QueryHandler
        @PostHandleEnforce
        public TestResponseType handle7(TestQueryPayload query) {
            return response;
        }
    }

    private JsonMapper               mapper;
    private ParameterResolverFactory factory;

    private MessageHandlingMember<HandlingObject>   delegate;
    private PolicyDecisionPoint                     pdp;
    private ConstraintHandlerService                axonConstraintEnforcementService;
    private SaplQueryUpdateEmitter                  emitter;
    private AuthorizationSubscriptionBuilderService subscriptionBuilder;
    private SaplAxonProperties                      properties;

    private QueryPolicyEnforcementPoint<HandlingObject> queryPEP;

    @SuppressWarnings("rawtypes")
    private QueryConstraintHandlerBundle queryConstraintHandlerBundle;
    private HandlingObject               handlingInstance;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void beforeEach() throws NoSuchMethodException {
        mapper = JsonMapper.builder().build();
        var executable = HandlingObject.class.getDeclaredMethod("handle1", TestQueryPayload.class);
        factory = new DefaultParameterResolverFactory();

        delegate                         = spy(
                new AnnotatedMessageHandlingMember<>(executable, QueryMessage.class, TestQueryPayload.class, factory));
        pdp                              = mock(PolicyDecisionPoint.class);
        axonConstraintEnforcementService = mock(ConstraintHandlerService.class);
        emitter                          = mock(SaplQueryUpdateEmitter.class);
        subscriptionBuilder              = mock(AuthorizationSubscriptionBuilderService.class);
        properties                       = mock(SaplAxonProperties.class);
        queryConstraintHandlerBundle     = mock(QueryConstraintHandlerBundle.class);
        handlingInstance                 = spy(new HandlingObject(DEFAULT_RESPONSE));

        setField(axonConstraintEnforcementService, MAPPER_FIELD_NAME, mapper);
        when(axonConstraintEnforcementService.deserializeResource(any(Value.class), any(ResponseType.class)))
                .thenCallRealMethod();
        when(axonConstraintEnforcementService.buildQueryPreHandlerBundle(any(AuthorizationDecision.class),
                any(ResponseType.class), any(Optional.class))).thenReturn(queryConstraintHandlerBundle);
        when(axonConstraintEnforcementService.buildQueryPostHandlerBundle(any(AuthorizationDecision.class),
                any(ResponseType.class))).thenReturn(queryConstraintHandlerBundle);
        when(emitter.activeSubscriptions()).thenReturn(Set.of());
        when(subscriptionBuilder.constructAuthorizationSubscriptionForQuery(any(QueryMessage.class),
                any(Annotation.class), any(Executable.class), any(Optional.class)))
                .thenReturn(AuthorizationSubscription.of(Value.UNDEFINED, Value.UNDEFINED, Value.UNDEFINED,
                        Value.UNDEFINED));
        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just());
        when(queryConstraintHandlerBundle.executePreHandlingHandlers(DEFAULT_QUERY_MESSAGE))
                .thenReturn(DEFAULT_QUERY_MESSAGE);
        when(queryConstraintHandlerBundle.executePreHandlingHandlers(DEFAULT_SUBSCRIPTION_QUERY_MESSAGE))
                .thenReturn(DEFAULT_SUBSCRIPTION_QUERY_MESSAGE);
        when(queryConstraintHandlerBundle.executePostHandlingHandlers(DEFAULT_RESPONSE)).thenReturn(DEFAULT_RESPONSE);

        queryPEP = new QueryPolicyEnforcementPoint<>(delegate, pdp, axonConstraintEnforcementService, emitter,
                subscriptionBuilder, properties);
    }

    @Test
    @SuppressWarnings("unchecked")
    void when_construct_and_delegateThrows_then_illegalStateException() {
        delegate = mock(MessageHandlingMember.class);
        when(delegate.unwrap(any(Class.class))).thenReturn(Optional.empty());
        assertThatThrownBy(() -> new QueryPolicyEnforcementPoint<>(delegate, pdp, axonConstraintEnforcementService,
                emitter, subscriptionBuilder, properties)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void when_handle_with_noSaplAnnotations_then_handleWithoutAuthZ() throws NoSuchMethodException {
        var executable = HandlingObject.class.getDeclaredMethod("handle2", TestQueryPayload.class);
        delegate = spy(
                new AnnotatedMessageHandlingMember<>(executable, QueryMessage.class, TestQueryPayload.class, factory));
        queryPEP = new QueryPolicyEnforcementPoint<>(delegate, pdp, axonConstraintEnforcementService, emitter,
                subscriptionBuilder, properties);

        assertThatCode(() -> queryPEP.handle(DEFAULT_QUERY_MESSAGE, handlingInstance)).doesNotThrowAnyException();

        assertThatCode(() -> verify(delegate, times(1)).handle(DEFAULT_QUERY_MESSAGE, handlingInstance))
                .doesNotThrowAnyException();
        verify(pdp, times(0)).decide(any(AuthorizationSubscription.class));
    }

    @Test
    void when_handle_with_multiplePreEnforceAnnotations_then_accessDenied() throws NoSuchMethodException {
        var executable = HandlingObject.class.getDeclaredMethod("handle3", TestQueryPayload.class);
        delegate = spy(
                new AnnotatedMessageHandlingMember<>(executable, QueryMessage.class, TestQueryPayload.class, factory));
        queryPEP = new QueryPolicyEnforcementPoint<>(delegate, pdp, axonConstraintEnforcementService, emitter,
                subscriptionBuilder, properties);

        assertThatCode(() -> {
            var response = queryPEP.handle(DEFAULT_QUERY_MESSAGE, handlingInstance);
            assertThat(response).isNotNull().isInstanceOf(CompletableFuture.class);
            var future = (CompletableFuture<?>) response;
            assertThat(future.isCompletedExceptionally()).isTrue();
            assertThatThrownBy(future::get).isInstanceOf(ExecutionException.class)
                    .satisfies(e -> assertThat(e.getCause()).isNotNull().isInstanceOf(AccessDeniedException.class));
        }).doesNotThrowAnyException();
    }

    @Test
    @SuppressWarnings("unchecked")
    void when_handle_with_preEnforce_and_buildQueryPreHandlerBundleThrowsAccessDenied_then_accessDenied() {
        when(axonConstraintEnforcementService.buildQueryPreHandlerBundle(any(AuthorizationDecision.class),
                any(ResponseType.class), any(Optional.class))).thenThrow(ACCESS_DENIED);

        assertThatCode(() -> {
            var response = queryPEP.handle(DEFAULT_QUERY_MESSAGE, handlingInstance);
            assertThat(response).isNotNull().isInstanceOf(CompletableFuture.class);
            var future = (CompletableFuture<?>) response;
            assertThat(future.isCompletedExceptionally()).isTrue();
            assertThatThrownBy(future::get).isInstanceOf(ExecutionException.class)
                    .satisfies(e -> assertThat(e.getCause()).isNotNull().isInstanceOf(AccessDeniedException.class));
        }).doesNotThrowAnyException();
    }

    @Test
    @SuppressWarnings("unchecked")
    void when_handle_with_preEnforce_and_executeOnDecisionHandlersThrowsAccessDenied_then_handledException() {
        doThrow(ACCESS_DENIED).when(queryConstraintHandlerBundle)
                .executeOnDecisionHandlers(any(AuthorizationDecision.class), any(Message.class));
        when(queryConstraintHandlerBundle.executeOnErrorHandlers(any(Throwable.class)))
                .thenReturn(new RuntimeException("A very special message!"));

        assertThatCode(() -> {
            var response = queryPEP.handle(DEFAULT_QUERY_MESSAGE, handlingInstance);
            assertThat(response).isNotNull().isInstanceOf(CompletableFuture.class);
            var future = (CompletableFuture<?>) response;
            assertThat(future.isCompletedExceptionally()).isTrue();
            assertThatThrownBy(future::get).isInstanceOf(ExecutionException.class)
                    .satisfies(e -> assertThat(e.getCause()).isNotNull().isInstanceOf(RuntimeException.class)
                            .satisfies(c -> assertThat(c.getLocalizedMessage()).isEqualTo("A very special message!")));
        }).doesNotThrowAnyException();
    }

    @Test
    void when_handle_with_preEnforce_and_decisionNotPermit_then_handledException() {
        when(queryConstraintHandlerBundle.executeOnErrorHandlers(any(Throwable.class)))
                .thenReturn(new RuntimeException("A very special message!"));

        assertThatCode(() -> {
            var response = queryPEP.handle(DEFAULT_QUERY_MESSAGE, handlingInstance);
            assertThat(response).isNotNull().isInstanceOf(CompletableFuture.class);
            var future = (CompletableFuture<?>) response;
            assertThat(future.isCompletedExceptionally()).isTrue();
            assertThatThrownBy(future::get).isInstanceOf(ExecutionException.class)
                    .satisfies(e -> assertThat(e.getCause()).isNotNull().isInstanceOf(RuntimeException.class)
                            .satisfies(c -> assertThat(c.getLocalizedMessage()).isEqualTo("A very special message!")));
        }).doesNotThrowAnyException();
    }

    @Test
    @SuppressWarnings("unchecked")
    void when_handle_with_preEnforce_and_executePreHandlingHandlersMapQuery_then_callHandlerWithManipulatedQuery() {
        var          specialPayload      = new TestQueryPayload("some special text");
        @SuppressWarnings("rawtypes")
        QueryMessage specialQueryMessage = new GenericQueryMessage<>(specialPayload,
                ResponseTypes.instanceOf(TestResponseType.class));
        when(queryConstraintHandlerBundle.executePreHandlingHandlers(DEFAULT_QUERY_MESSAGE))
                .thenReturn(specialQueryMessage);
        when(queryConstraintHandlerBundle.executeOnErrorHandlers(any(Throwable.class)))
                .thenReturn(new RuntimeException("A very special message!"));
        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));

        assertThatCode(() -> {
            var response = queryPEP.handle(DEFAULT_QUERY_MESSAGE, handlingInstance);
            assertThat(response).isNotNull().isInstanceOf(CompletableFuture.class);
            var future          = (CompletableFuture<?>) response;
            var completedResult = future.get();
            assertThat(completedResult).isEqualTo(DEFAULT_RESPONSE);
        }).doesNotThrowAnyException();
        verify(handlingInstance, times(1)).handle1(specialPayload);
    }

    @Test
    @SuppressWarnings("unchecked")
    void when_handle_with_preEnforce_and_executePreHandlingHandlersThrowsAccessDenied_then_handledException() {
        when(queryConstraintHandlerBundle.executePreHandlingHandlers(any(QueryMessage.class))).thenThrow(ACCESS_DENIED);
        when(queryConstraintHandlerBundle.executeOnErrorHandlers(any(Throwable.class)))
                .thenReturn(new RuntimeException("A very special message!"));
        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));

        assertThatCode(() -> {
            var response = queryPEP.handle(DEFAULT_QUERY_MESSAGE, handlingInstance);
            assertThat(response).isNotNull().isInstanceOf(CompletableFuture.class);
            var future = (CompletableFuture<?>) response;
            assertThat(future.isCompletedExceptionally()).isTrue();
            assertThatThrownBy(future::get).isInstanceOf(ExecutionException.class)
                    .satisfies(e -> assertThat(e.getCause()).isNotNull().isInstanceOf(RuntimeException.class)
                            .satisfies(c -> assertThat(c.getLocalizedMessage()).isEqualTo("A very special message!")));
        }).doesNotThrowAnyException();
    }

    @Test
    void when_handle_with_preEnforce_and_delegateReturnsNull_then_empty() {
        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));
        handlingInstance.setResponse(null);

        assertThatCode(() -> {
            var response = queryPEP.handle(DEFAULT_QUERY_MESSAGE, handlingInstance);
            assertThat(response).isNotNull().isInstanceOf(CompletableFuture.class);
            var future          = (CompletableFuture<?>) response;
            var completedResult = future.get();
            assertThat(completedResult).isNull();
        }).doesNotThrowAnyException();
    }

    @Test
    void when_handle_with_preEnforce_and_delegateThrows_then_handledException() {
        when(queryConstraintHandlerBundle.executeOnErrorHandlers(any(Throwable.class)))
                .thenReturn(new RuntimeException("An even more special message!"));
        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));
        when(handlingInstance.handle1(any(TestQueryPayload.class)))
                .thenThrow(new RuntimeException("A very special message!"));

        assertThatCode(() -> {
            var response = queryPEP.handle(DEFAULT_QUERY_MESSAGE, handlingInstance);
            assertThat(response).isNotNull().isInstanceOf(CompletableFuture.class);
            var future = (CompletableFuture<?>) response;
            assertThat(future.isCompletedExceptionally()).isTrue();
            assertThatThrownBy(future::get).isInstanceOf(ExecutionException.class)
                    .satisfies(e -> assertThat(e.getCause()).isNotNull().isInstanceOf(RuntimeException.class).satisfies(
                            c -> assertThat(c.getLocalizedMessage()).isEqualTo("An even more special message!")));
        }).doesNotThrowAnyException();
    }

    @Test
    void when_handle_with_preEnforce_and_delegateReturnsCompletableFuture_then_deferResponse()
            throws NoSuchMethodException {
        var executable = HandlingObject.class.getDeclaredMethod("handle4", TestQueryPayload.class);
        delegate = spy(
                new AnnotatedMessageHandlingMember<>(executable, QueryMessage.class, TestQueryPayload.class, factory));
        queryPEP = new QueryPolicyEnforcementPoint<>(delegate, pdp, axonConstraintEnforcementService, emitter,
                subscriptionBuilder, properties);

        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));

        assertThatCode(() -> {
            var response = queryPEP.handle(DEFAULT_QUERY_MESSAGE, handlingInstance);
            assertThat(response).isNotNull().isInstanceOf(CompletableFuture.class);
            var future          = (CompletableFuture<?>) response;
            var completedResult = future.get();
            assertThat(completedResult).isEqualTo(DEFAULT_RESPONSE);
        }).doesNotThrowAnyException();
    }

    @Test
    void when_handle_with_preEnforce_and_resourceInDecision_then_replaceResult() {
        var resource = new TestResponseType("some special text");
        var decision = new AuthorizationDecision(Decision.PERMIT, Value.EMPTY_ARRAY, Value.EMPTY_ARRAY,
                ValueJsonMarshaller.fromJsonNode(asTree(resource)));
        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(decision));
        when(queryConstraintHandlerBundle.executePostHandlingHandlers(resource)).thenReturn(resource);

        assertThatCode(() -> {
            var response = queryPEP.handle(DEFAULT_QUERY_MESSAGE, handlingInstance);
            assertThat(response).isNotNull().isInstanceOf(CompletableFuture.class);
            var future          = (CompletableFuture<?>) response;
            var completedResult = future.get();
            assertThat(completedResult).isEqualTo(resource);
        }).doesNotThrowAnyException();
    }

    @Test
    void when_handle_with_preEnforce_and_executePostHandlingHandlersMapResponse_then_handledException() {
        when(queryConstraintHandlerBundle.executePostHandlingHandlers(DEFAULT_RESPONSE)).thenReturn("special response");
        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));

        assertThatCode(() -> {
            var response = queryPEP.handle(DEFAULT_QUERY_MESSAGE, handlingInstance);
            assertThat(response).isNotNull().isInstanceOf(CompletableFuture.class);
            var future          = (CompletableFuture<?>) response;
            var completedResult = future.get();
            assertThat(completedResult).isEqualTo("special response");
        }).doesNotThrowAnyException();
    }

    @Test
    void when_handle_with_enforceDropUpdatesWhileDenied_then_preEnforceInitialResult() throws NoSuchMethodException {
        var executable = HandlingObject.class.getDeclaredMethod("handle5", TestQueryPayload.class);
        delegate = spy(
                new AnnotatedMessageHandlingMember<>(executable, QueryMessage.class, TestQueryPayload.class, factory));
        queryPEP = new QueryPolicyEnforcementPoint<>(delegate, pdp, axonConstraintEnforcementService, emitter,
                subscriptionBuilder, properties);

        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));

        assertThatCode(() -> {
            var response = queryPEP.handle(DEFAULT_QUERY_MESSAGE, handlingInstance);
            assertThat(response).isNotNull().isInstanceOf(CompletableFuture.class);
            var future          = (CompletableFuture<?>) response;
            var completedResult = future.get();
            assertThat(completedResult).isEqualTo(DEFAULT_RESPONSE);
        }).doesNotThrowAnyException();
    }

    @Test
    void when_handle_with_enforceRecoverableUpdatesIfDenied_then_preEnforceInitialResult()
            throws NoSuchMethodException {
        var executable = HandlingObject.class.getDeclaredMethod("handle6", TestQueryPayload.class);
        delegate = spy(
                new AnnotatedMessageHandlingMember<>(executable, QueryMessage.class, TestQueryPayload.class, factory));
        queryPEP = new QueryPolicyEnforcementPoint<>(delegate, pdp, axonConstraintEnforcementService, emitter,
                subscriptionBuilder, properties);

        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));

        assertThatCode(() -> {
            var response = queryPEP.handle(DEFAULT_QUERY_MESSAGE, handlingInstance);
            assertThat(response).isNotNull().isInstanceOf(CompletableFuture.class);
            var future          = (CompletableFuture<?>) response;
            var completedResult = future.get();
            assertThat(completedResult).isEqualTo(DEFAULT_RESPONSE);
        }).doesNotThrowAnyException();
    }

    @Test
    void when_handle_with_postEnforce_and_handlerThrows_buildQueryPostHandlerBundleThrowsAccessDenied_then_accessDenied()
            throws NoSuchMethodException {
        var executable = HandlingObject.class.getDeclaredMethod("handle7", TestQueryPayload.class);
        delegate = spy(
                new AnnotatedMessageHandlingMember<>(executable, QueryMessage.class, TestQueryPayload.class, factory));
        queryPEP = new QueryPolicyEnforcementPoint<>(delegate, pdp, axonConstraintEnforcementService, emitter,
                subscriptionBuilder, properties);

        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));
        when(handlingInstance.handle7(any(TestQueryPayload.class))).thenThrow(new RuntimeException("some Exception"));
        when(axonConstraintEnforcementService.buildQueryPostHandlerBundle(any(AuthorizationDecision.class),
                any(ResponseType.class))).thenThrow(ACCESS_DENIED);

        assertThatCode(() -> {
            var response = queryPEP.handle(DEFAULT_QUERY_MESSAGE, handlingInstance);
            assertThat(response).isNotNull().isInstanceOf(CompletableFuture.class);
            var future = (CompletableFuture<?>) response;
            assertThat(future.isCompletedExceptionally()).isTrue();
            assertThatThrownBy(future::get).isInstanceOf(ExecutionException.class)
                    .satisfies(e -> assertThat(e.getCause()).isNotNull().isInstanceOf(AccessDeniedException.class));
        }).doesNotThrowAnyException();
    }

    @Test
    @SuppressWarnings("unchecked")
    void when_handle_with_postEnforce_and_handlerThrows_and_executeOnDecisionHandlersThrowsAccessDenied_then_handledThrowable()
            throws NoSuchMethodException {
        var executable = HandlingObject.class.getDeclaredMethod("handle7", TestQueryPayload.class);
        delegate = spy(
                new AnnotatedMessageHandlingMember<>(executable, QueryMessage.class, TestQueryPayload.class, factory));
        queryPEP = new QueryPolicyEnforcementPoint<>(delegate, pdp, axonConstraintEnforcementService, emitter,
                subscriptionBuilder, properties);

        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));
        when(handlingInstance.handle7(any(TestQueryPayload.class))).thenThrow(new RuntimeException("some Exception"));
        doThrow(ACCESS_DENIED).when(queryConstraintHandlerBundle)
                .executeOnDecisionHandlers(any(AuthorizationDecision.class), any(Message.class));
        when(queryConstraintHandlerBundle.executeOnErrorHandlers(any(AccessDeniedException.class)))
                .thenReturn(new RuntimeException("some special throwable message"));

        assertThatCode(() -> {
            var response = queryPEP.handle(DEFAULT_QUERY_MESSAGE, handlingInstance);
            assertThat(response).isNotNull().isInstanceOf(CompletableFuture.class);
            var future = (CompletableFuture<?>) response;
            assertThat(future.isCompletedExceptionally()).isTrue();
            assertThatThrownBy(future::get).isInstanceOf(ExecutionException.class)
                    .satisfies(e -> assertThat(e.getCause()).isNotNull().isInstanceOf(RuntimeException.class).satisfies(
                            c -> assertThat(c.getLocalizedMessage()).isEqualTo("some special throwable message")));
        }).doesNotThrowAnyException();
    }

    @Test
    void when_handle_with_postEnforce_and_handlerThrows_and_pdpDeny_then_handledThrowable()
            throws NoSuchMethodException {
        var executable = HandlingObject.class.getDeclaredMethod("handle7", TestQueryPayload.class);
        delegate = spy(
                new AnnotatedMessageHandlingMember<>(executable, QueryMessage.class, TestQueryPayload.class, factory));
        queryPEP = new QueryPolicyEnforcementPoint<>(delegate, pdp, axonConstraintEnforcementService, emitter,
                subscriptionBuilder, properties);

        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.DENY));
        when(handlingInstance.handle7(any(TestQueryPayload.class))).thenThrow(new RuntimeException("some Exception"));
        when(queryConstraintHandlerBundle.executeOnErrorHandlers(any(AccessDeniedException.class)))
                .thenReturn(new RuntimeException("some special throwable message"));

        assertThatCode(() -> {
            var response = queryPEP.handle(DEFAULT_QUERY_MESSAGE, handlingInstance);
            assertThat(response).isNotNull().isInstanceOf(CompletableFuture.class);
            var future = (CompletableFuture<?>) response;
            assertThat(future.isCompletedExceptionally()).isTrue();
            assertThatThrownBy(future::get).isInstanceOf(ExecutionException.class)
                    .satisfies(e -> assertThat(e.getCause()).isNotNull().isInstanceOf(RuntimeException.class).satisfies(
                            c -> assertThat(c.getLocalizedMessage()).isEqualTo("some special throwable message")));
        }).doesNotThrowAnyException();
    }

    @Test
    void when_handle_with_postEnforce_and_handlerThrows_and_pdpPermit_then_handledThrowable()
            throws NoSuchMethodException {
        var executable = HandlingObject.class.getDeclaredMethod("handle7", TestQueryPayload.class);
        delegate = spy(
                new AnnotatedMessageHandlingMember<>(executable, QueryMessage.class, TestQueryPayload.class, factory));
        queryPEP = new QueryPolicyEnforcementPoint<>(delegate, pdp, axonConstraintEnforcementService, emitter,
                subscriptionBuilder, properties);

        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));
        when(handlingInstance.handle7(any(TestQueryPayload.class))).thenThrow(new RuntimeException("some Exception"));
        when(queryConstraintHandlerBundle.executeOnErrorHandlers(any(RuntimeException.class)))
                .thenReturn(new RuntimeException("some special throwable message"));

        assertThatCode(() -> {
            var response = queryPEP.handle(DEFAULT_QUERY_MESSAGE, handlingInstance);
            assertThat(response).isNotNull().isInstanceOf(CompletableFuture.class);
            var future = (CompletableFuture<?>) response;
            assertThat(future.isCompletedExceptionally()).isTrue();
            assertThatThrownBy(future::get).isInstanceOf(ExecutionException.class)
                    .satisfies(e -> assertThat(e.getCause()).isNotNull().isInstanceOf(RuntimeException.class).satisfies(
                            c -> assertThat(c.getLocalizedMessage()).isEqualTo("some special throwable message")));
        }).doesNotThrowAnyException();
    }

    @Test
    void when_handle_with_postEnforce_and_buildQueryPostHandlerBundleThrowsAccessDenied_pdpPermit_then_accessDenied()
            throws NoSuchMethodException {
        var executable = HandlingObject.class.getDeclaredMethod("handle7", TestQueryPayload.class);
        delegate = spy(
                new AnnotatedMessageHandlingMember<>(executable, QueryMessage.class, TestQueryPayload.class, factory));
        queryPEP = new QueryPolicyEnforcementPoint<>(delegate, pdp, axonConstraintEnforcementService, emitter,
                subscriptionBuilder, properties);

        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));
        when(axonConstraintEnforcementService.buildQueryPostHandlerBundle(any(AuthorizationDecision.class),
                any(ResponseType.class))).thenThrow(ACCESS_DENIED);

        assertThatCode(() -> {
            var response = queryPEP.handle(DEFAULT_QUERY_MESSAGE, handlingInstance);
            assertThat(response).isNotNull().isInstanceOf(CompletableFuture.class);
            var future = (CompletableFuture<?>) response;
            assertThat(future.isCompletedExceptionally()).isTrue();
            assertThatThrownBy(future::get).isInstanceOf(ExecutionException.class)
                    .satisfies(e -> assertThat(e.getCause()).isNotNull().isInstanceOf(AccessDeniedException.class));
        }).doesNotThrowAnyException();
    }

    @Test
    @SuppressWarnings("unchecked")
    void when_handle_with_postEnforce_and_executeOnDecisionHandlersThrowsAccessDenied_pdpPermit_then_handledThrowable()
            throws NoSuchMethodException {
        var executable = HandlingObject.class.getDeclaredMethod("handle7", TestQueryPayload.class);
        delegate = spy(
                new AnnotatedMessageHandlingMember<>(executable, QueryMessage.class, TestQueryPayload.class, factory));
        queryPEP = new QueryPolicyEnforcementPoint<>(delegate, pdp, axonConstraintEnforcementService, emitter,
                subscriptionBuilder, properties);

        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));
        doThrow(ACCESS_DENIED).when(queryConstraintHandlerBundle)
                .executeOnDecisionHandlers(any(AuthorizationDecision.class), any(Message.class));
        when(queryConstraintHandlerBundle.executeOnErrorHandlers(any(RuntimeException.class)))
                .thenReturn(new RuntimeException("some special throwable message"));

        assertThatCode(() -> {
            var response = queryPEP.handle(DEFAULT_QUERY_MESSAGE, handlingInstance);
            assertThat(response).isNotNull().isInstanceOf(CompletableFuture.class);
            var future = (CompletableFuture<?>) response;
            assertThat(future.isCompletedExceptionally()).isTrue();
            assertThatThrownBy(future::get).isInstanceOf(ExecutionException.class)
                    .satisfies(e -> assertThat(e.getCause()).isNotNull().isInstanceOf(RuntimeException.class).satisfies(
                            c -> assertThat(c.getLocalizedMessage()).isEqualTo("some special throwable message")));
        }).doesNotThrowAnyException();
    }

    @Test
    void when_handle_with_postEnforce_and_pdpDeny_then_handledThrowable() throws NoSuchMethodException {
        var executable = HandlingObject.class.getDeclaredMethod("handle7", TestQueryPayload.class);
        delegate = spy(
                new AnnotatedMessageHandlingMember<>(executable, QueryMessage.class, TestQueryPayload.class, factory));
        queryPEP = new QueryPolicyEnforcementPoint<>(delegate, pdp, axonConstraintEnforcementService, emitter,
                subscriptionBuilder, properties);

        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.DENY));
        when(queryConstraintHandlerBundle.executeOnErrorHandlers(any(RuntimeException.class)))
                .thenReturn(new RuntimeException("some special throwable message"));

        assertThatCode(() -> {
            var response = queryPEP.handle(DEFAULT_QUERY_MESSAGE, handlingInstance);
            assertThat(response).isNotNull().isInstanceOf(CompletableFuture.class);
            var future = (CompletableFuture<?>) response;
            assertThat(future.isCompletedExceptionally()).isTrue();
            assertThatThrownBy(future::get).isInstanceOf(ExecutionException.class)
                    .satisfies(e -> assertThat(e.getCause()).isNotNull().isInstanceOf(RuntimeException.class).satisfies(
                            c -> assertThat(c.getLocalizedMessage()).isEqualTo("some special throwable message")));
        }).doesNotThrowAnyException();
    }

    @Test
    void when_handle_with_postEnforce_and_resourceInDecision_then_resourceOfDecision() throws NoSuchMethodException {
        var executable = HandlingObject.class.getDeclaredMethod("handle7", TestQueryPayload.class);
        delegate = spy(
                new AnnotatedMessageHandlingMember<>(executable, QueryMessage.class, TestQueryPayload.class, factory));
        queryPEP = new QueryPolicyEnforcementPoint<>(delegate, pdp, axonConstraintEnforcementService, emitter,
                subscriptionBuilder, properties);

        var resourceResponse = new TestResponseType("ResourceText");
        var decision         = new AuthorizationDecision(Decision.PERMIT, Value.EMPTY_ARRAY, Value.EMPTY_ARRAY,
                ValueJsonMarshaller.fromJsonNode(asTree(resourceResponse)));
        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(decision));
        when(queryConstraintHandlerBundle.executePostHandlingHandlers(resourceResponse)).thenReturn(resourceResponse);

        assertThatCode(() -> {
            var response = queryPEP.handle(DEFAULT_QUERY_MESSAGE, handlingInstance);
            assertThat(response).isNotNull().isInstanceOf(CompletableFuture.class);
            var future = (CompletableFuture<?>) response;
            assertThat(future.isCompletedExceptionally()).isFalse();
            var resource = future.get();
            assertThat(resource).isNotNull().isEqualTo(resourceResponse);
        }).doesNotThrowAnyException();
    }

    @Test
    @SuppressWarnings("unchecked")
    void when_handle_with_postEnforce_and_resourceInDecision_and_deserializeResourceThrows_then_handledThrowable()
            throws NoSuchMethodException {
        var executable = HandlingObject.class.getDeclaredMethod("handle7", TestQueryPayload.class);
        delegate = spy(
                new AnnotatedMessageHandlingMember<>(executable, QueryMessage.class, TestQueryPayload.class, factory));
        queryPEP = new QueryPolicyEnforcementPoint<>(delegate, pdp, axonConstraintEnforcementService, emitter,
                subscriptionBuilder, properties);

        var resourceResponse = new TestResponseType("ResourceText");
        var decision         = new AuthorizationDecision(Decision.PERMIT, Value.EMPTY_ARRAY, Value.EMPTY_ARRAY,
                ValueJsonMarshaller.fromJsonNode(asTree(resourceResponse)));
        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(decision));
        when(axonConstraintEnforcementService.deserializeResource(any(Value.class), any(ResponseType.class)))
                .thenThrow(ACCESS_DENIED);
        when(queryConstraintHandlerBundle.executeOnErrorHandlers(any(RuntimeException.class)))
                .thenReturn(new RuntimeException("some special throwable message"));

        assertThatCode(() -> {
            var response = queryPEP.handle(DEFAULT_QUERY_MESSAGE, handlingInstance);
            assertThat(response).isNotNull().isInstanceOf(CompletableFuture.class);
            var future = (CompletableFuture<?>) response;
            assertThat(future.isCompletedExceptionally()).isTrue();
            assertThatThrownBy(future::get).isInstanceOf(ExecutionException.class)
                    .satisfies(e -> assertThat(e.getCause()).isNotNull().isInstanceOf(RuntimeException.class).satisfies(
                            c -> assertThat(c.getLocalizedMessage()).isEqualTo("some special throwable message")));
        }).doesNotThrowAnyException();
    }

    @Test
    void when_handle_with_postEnforce_then_handledResponse() throws NoSuchMethodException {
        var executable = HandlingObject.class.getDeclaredMethod("handle7", TestQueryPayload.class);
        delegate = spy(
                new AnnotatedMessageHandlingMember<>(executable, QueryMessage.class, TestQueryPayload.class, factory));
        queryPEP = new QueryPolicyEnforcementPoint<>(delegate, pdp, axonConstraintEnforcementService, emitter,
                subscriptionBuilder, properties);

        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));
        when(queryConstraintHandlerBundle.executePostHandlingHandlers(any(TestResponseType.class)))
                .thenReturn("SomeOtherResponse");

        assertThatCode(() -> {
            var response = queryPEP.handle(DEFAULT_QUERY_MESSAGE, handlingInstance);
            assertThat(response).isNotNull().isInstanceOf(CompletableFuture.class);
            var future = (CompletableFuture<?>) response;
            assertThat(future.isCompletedExceptionally()).isFalse();
            var resource = future.get();
            assertThat(resource).isNotNull().isEqualTo("SomeOtherResponse");
        }).doesNotThrowAnyException();
    }

    @Test
    void when_handle_with_postEnforce_and_executePostHandlingHandlersThrowsAccessDenied_then_handledResponse()
            throws NoSuchMethodException {
        var executable = HandlingObject.class.getDeclaredMethod("handle7", TestQueryPayload.class);
        delegate = spy(
                new AnnotatedMessageHandlingMember<>(executable, QueryMessage.class, TestQueryPayload.class, factory));
        queryPEP = new QueryPolicyEnforcementPoint<>(delegate, pdp, axonConstraintEnforcementService, emitter,
                subscriptionBuilder, properties);

        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));
        when(queryConstraintHandlerBundle.executePostHandlingHandlers(any(TestResponseType.class)))
                .thenThrow(ACCESS_DENIED);
        when(queryConstraintHandlerBundle.executeOnErrorHandlers(any(RuntimeException.class)))
                .thenReturn(new RuntimeException("some special throwable message"));

        assertThatCode(() -> {
            var response = queryPEP.handle(DEFAULT_QUERY_MESSAGE, handlingInstance);
            assertThat(response).isNotNull().isInstanceOf(CompletableFuture.class);
            var future = (CompletableFuture<?>) response;
            assertThat(future.isCompletedExceptionally()).isTrue();
            assertThatThrownBy(future::get).isInstanceOf(ExecutionException.class)
                    .satisfies(e -> assertThat(e.getCause()).isNotNull().isInstanceOf(RuntimeException.class).satisfies(
                            c -> assertThat(c.getLocalizedMessage()).isEqualTo("some special throwable message")));
        }).doesNotThrowAnyException();
    }

    @Test
    void when_handleSubscriptionQuery_with_noAnnotation_then_handleWithoutAuthZ() throws NoSuchMethodException {
        var executable = HandlingObject.class.getDeclaredMethod("handle2", TestQueryPayload.class);
        delegate = spy(
                new AnnotatedMessageHandlingMember<>(executable, QueryMessage.class, TestQueryPayload.class, factory));
        queryPEP = new QueryPolicyEnforcementPoint<>(delegate, pdp, axonConstraintEnforcementService, emitter,
                subscriptionBuilder, properties);

        when(emitter.activeSubscriptions()).thenReturn(Set.of(DEFAULT_SUBSCRIPTION_QUERY_MESSAGE));

        assertThatCode(() -> queryPEP.handle(DEFAULT_SUBSCRIPTION_QUERY_MESSAGE, handlingInstance))
                .doesNotThrowAnyException();

        assertThatCode(() -> verify(delegate, times(1)).handle(DEFAULT_SUBSCRIPTION_QUERY_MESSAGE, handlingInstance))
                .doesNotThrowAnyException();
        verify(emitter, times(1))
                .authorizeUpdatesForSubscriptionQueryWithId(DEFAULT_SUBSCRIPTION_QUERY_MESSAGE.getIdentifier());
        verify(pdp, times(0)).decide(any(AuthorizationSubscription.class));
    }

    @Test
    void when_handleSubscriptionQuery_with_postEnforce_then_accessDenied() throws NoSuchMethodException {
        var executable = HandlingObject.class.getDeclaredMethod("handle7", TestQueryPayload.class);
        delegate = spy(
                new AnnotatedMessageHandlingMember<>(executable, QueryMessage.class, TestQueryPayload.class, factory));
        queryPEP = new QueryPolicyEnforcementPoint<>(delegate, pdp, axonConstraintEnforcementService, emitter,
                subscriptionBuilder, properties);

        when(emitter.activeSubscriptions()).thenReturn(Set.of(DEFAULT_SUBSCRIPTION_QUERY_MESSAGE));

        assertThatThrownBy(() -> queryPEP.handle(DEFAULT_SUBSCRIPTION_QUERY_MESSAGE, handlingInstance))
                .isInstanceOf(AccessDeniedException.class);
        verify(emitter, times(1)).immediatelyDenySubscriptionWithId(DEFAULT_SUBSCRIPTION_QUERY_MESSAGE.getIdentifier());
    }

    @Test
    void when_handleSubscriptionQuery_with_multipleAnnotations_then_illegalState() throws NoSuchMethodException {
        var executable = HandlingObject.class.getDeclaredMethod("handle3", TestQueryPayload.class);
        delegate = spy(
                new AnnotatedMessageHandlingMember<>(executable, QueryMessage.class, TestQueryPayload.class, factory));
        queryPEP = new QueryPolicyEnforcementPoint<>(delegate, pdp, axonConstraintEnforcementService, emitter,
                subscriptionBuilder, properties);

        when(emitter.activeSubscriptions()).thenReturn(Set.of(DEFAULT_SUBSCRIPTION_QUERY_MESSAGE));

        assertThatThrownBy(() -> queryPEP.handle(DEFAULT_SUBSCRIPTION_QUERY_MESSAGE, handlingInstance))
                .isInstanceOf(IllegalStateException.class);
        verify(emitter, times(0)).authorizeUpdatesForSubscriptionQueryWithId(any(String.class));
    }

    @Test
    void when_handleSubscriptionQuery_with_enforceDropUpdatesWhileDenied_then_preEnforceInitialResult()
            throws NoSuchMethodException {
        var executable = HandlingObject.class.getDeclaredMethod("handle5", TestQueryPayload.class);
        delegate = spy(
                new AnnotatedMessageHandlingMember<>(executable, QueryMessage.class, TestQueryPayload.class, factory));
        queryPEP = new QueryPolicyEnforcementPoint<>(delegate, pdp, axonConstraintEnforcementService, emitter,
                subscriptionBuilder, properties);

        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));
        when(emitter.activeSubscriptions()).thenReturn(Set.of(DEFAULT_SUBSCRIPTION_QUERY_MESSAGE));

        assertThatCode(() -> {
            var response = queryPEP.handle(DEFAULT_SUBSCRIPTION_QUERY_MESSAGE, handlingInstance);
            assertThat(response).isNotNull().isInstanceOf(CompletableFuture.class);
            var future          = (CompletableFuture<?>) response;
            var completedResult = future.get();
            assertThat(completedResult).isEqualTo(DEFAULT_RESPONSE);
        }).doesNotThrowAnyException();
    }

    @Test
    void when_handleSubscriptionQuery_with_enforceRecoverableUpdatesIfDenied_then_preEnforceInitialResult()
            throws NoSuchMethodException {
        var executable = HandlingObject.class.getDeclaredMethod("handle6", TestQueryPayload.class);
        delegate = spy(
                new AnnotatedMessageHandlingMember<>(executable, QueryMessage.class, TestQueryPayload.class, factory));
        queryPEP = new QueryPolicyEnforcementPoint<>(delegate, pdp, axonConstraintEnforcementService, emitter,
                subscriptionBuilder, properties);

        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));
        when(emitter.activeSubscriptions()).thenReturn(Set.of(DEFAULT_SUBSCRIPTION_QUERY_MESSAGE));

        assertThatCode(() -> {
            var response = queryPEP.handle(DEFAULT_SUBSCRIPTION_QUERY_MESSAGE, handlingInstance);
            assertThat(response).isNotNull().isInstanceOf(CompletableFuture.class);
            var future          = (CompletableFuture<?>) response;
            var completedResult = future.get();
            assertThat(completedResult).isEqualTo(DEFAULT_RESPONSE);
        }).doesNotThrowAnyException();
    }

    private JsonNode asTree(Object obj) {
        return mapper.valueToTree(obj);
    }

}
