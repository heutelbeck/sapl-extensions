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

import static io.sapl.axon.TestUtilities.isAccessDenied;
import static io.sapl.axon.TestUtilities.isCausedBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.axonframework.messaging.responsetypes.ResponseTypes.instanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static reactor.test.StepVerifier.create;

import java.time.Duration;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

import org.axonframework.messaging.Message;
import org.axonframework.messaging.ResultMessage;
import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.QueryHandler;
import org.axonframework.queryhandling.QueryUpdateEmitter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import tools.jackson.databind.json.JsonMapper;

import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.UndefinedValue;
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
import io.sapl.axon.configuration.SaplAutoConfiguration;
import io.sapl.axon.constrainthandling.api.CollectionAndOptionalFilterPredicateProvider;
import io.sapl.axon.constrainthandling.api.OnDecisionConstraintHandlerProvider;
import io.sapl.axon.constrainthandling.api.QueryConstraintHandlerProvider;
import io.sapl.axon.constrainthandling.api.ResultConstraintHandlerProvider;
import io.sapl.axon.constrainthandling.api.UpdateFilterConstraintHandlerProvider;
import io.sapl.axon.constrainthandling.provider.ResponseMessagePayloadFilterProvider;
import io.sapl.axon.queryhandling.QueryTestsuite.TestScenarioConfiguration;
import io.sapl.spring.constraints.api.ErrorMappingConstraintHandlerProvider;
import io.sapl.spring.constraints.api.MappingConstraintHandlerProvider;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@SpringBootTest
@Import(TestScenarioConfiguration.class)
@DisplayName("Query handling test suite")
abstract class QueryTestsuite {
    private static final String REMOVE_YOUNGER_THAN18            = "removeYoungerThan18";
    private static final String LIST_RESPONSE_QUERY              = "ListResponseQuery";
    private static final String ONLY_EVEN_NUMBERS                = "only even numbers in string";
    private static final String ANONYMOUS                        = "anonymous";
    private static final String BAD_ANNOTATIONS1                 = "BadAnnotations1";
    private static final String BAD_ANNOTATIONS2                 = "BadAnnotations2";
    private static final String BAD_ANNOTATIONS3                 = "BadAnnotations3";
    private static final String BAD_RESOURCE_SERIALIZATION_QUERY = "BadResourceSerializationQuery";
    private static final String DROP_QUERY                       = "DropQuery";
    private static final String I_WAS_REPLACED                   = "I was replaced";
    private static final String POST_HANDLE_NO_RESOURCE_QUERY    = "PostHandleNoResourceQuery";
    private static final String POST_HANDLE_QUERY                = "PostHandleEnforceQuery";
    private static final String PRE_HANDLE_QUERY                 = "PreHandleEnforceQuery";
    private static final String QUERY                            = "Query Content";
    private static final String RECOVERABLE_QUERY                = "RecoverableQuery";
    private static final String RESOURCE                         = "Resource Description";
    private static final String RESOURCE_EXPR                    = "'" + RESOURCE + "'";
    private static final String UNSECURED_QUERY                  = "UnsecuredQuery";
    private static final String FAILING_PRE_QUERY                = "failingPreQuery";
    private static final String FAILING_POST_QUERY               = "failingPostQuery";
    private static final String MODIFY_ERROR                     = "modify error";
    private static final String MODIFY_RESULT                    = "modify result";
    private static final String MODIFIED_RESULT                  = "this is a modified result";
    private static final String MODIFIED_QUERY                   = "modifiedQuery";
    private static final String MODIFY_QUERY                     = "modifyQuery";
    private static final String ON_DECISION_DO                   = "onDecisionDo";
    private static final String MAP_UPDATE_PAYLOAD_TO_UPPERCASE  = "map update payload to uppercase";

    @MockitoBean
    PolicyDecisionPoint pdp;

    @Autowired
    SaplQueryGateway queryGateway;

    @Autowired
    QueryUpdateEmitter emitter;

    @MockitoSpyBean
    OnDecisionProvider onDecisionProvider;

    @MockitoSpyBean
    QueryMappingProvider querMappingProvider;

    @MockitoSpyBean
    ResultMappingProvider resultMappingProvider;

    @MockitoSpyBean
    ErrorMappingProvider errorMappingProvider;

    @MockitoSpyBean
    ResultFilterProvider filterUpdatesProvider;

    @MockitoSpyBean
    ResultMessageMappingProvider resultMessageMappingProvider;

    @Autowired
    ResponseMessagePayloadFilterProvider responseMessagePayloadFilterProvider;

    @MockitoSpyBean
    FilterPredicateExampleProvider filterPredicateExampleProvider;

    @Test
    void when_unsecuredQuery_then_resultReturnsAndPdpNotCalled() {
        var result = Mono.fromFuture(() -> queryGateway.query(UNSECURED_QUERY, QUERY, instanceOf(String.class)));
        create(result).expectNext(QUERY).verifyComplete();
        verifyNoInteractions(pdp);
    }

    @Test
    @WithMockUser(username = "user1", roles = "MANAGER")
    void when_preHandlerSecuredQueryAndPermit_then_resultReturnsAndPdpIsCalledWithSubscription() {
        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));

        var result = Mono.fromFuture(() -> queryGateway.query(PRE_HANDLE_QUERY, QUERY, instanceOf(String.class)));

        create(result).expectNext(QUERY).verifyComplete();

        verify(pdp, times(1)).decide(any(AuthorizationSubscription.class));
        var subscription = captureAuthzSubscription();
        assertThat((ObjectValue) subscription.subject()).containsEntry("username", Value.of("user1"));
        assertThat(subscription.action()).isEqualTo(Value.of(PRE_HANDLE_QUERY));
        assertThat(subscription.resource()).isEqualTo(Value.of(RESOURCE));
        assertThat(subscription.environment()).isInstanceOf(UndefinedValue.class);
    }

    @Test
    void when_dropSecuredQueryAndPermit_then_resultReturnsAndPdpIsCalledWithSubscription() {
        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));

        var result = Mono.fromFuture(() -> queryGateway.query(DROP_QUERY, QUERY, instanceOf(String.class)));

        create(result).expectNext(QUERY).verifyComplete();

        verify(pdp, times(1)).decide(any(AuthorizationSubscription.class));
        var subscription2 = captureAuthzSubscription();
        assertThat(subscription2.subject()).isEqualTo(Value.of(ANONYMOUS));
        assertThat(subscription2.action()).isEqualTo(Value.of(DROP_QUERY));
        assertThat(subscription2.resource()).isEqualTo(Value.of(RESOURCE));
        assertThat(subscription2.environment()).isInstanceOf(UndefinedValue.class);
    }

    @Test
    void when_recoverableSecuredQueryAndPermit_then_resultReturnsAndPdpIsCalledWithSubscription() {
        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));

        var result = Mono.fromFuture(() -> queryGateway.query(RECOVERABLE_QUERY, QUERY, instanceOf(String.class)));

        create(result).expectNext(QUERY).verifyComplete();

        verify(pdp, times(1)).decide(any(AuthorizationSubscription.class));
        var subscription3 = captureAuthzSubscription();
        assertThat(subscription3.subject()).isEqualTo(Value.of(ANONYMOUS));
        assertThat(subscription3.action()).isEqualTo(Value.of(RECOVERABLE_QUERY));
        assertThat(subscription3.resource()).isEqualTo(Value.of(RESOURCE));
        assertThat(subscription3.environment()).isInstanceOf(UndefinedValue.class);
    }

    @Test
    void when_preHandlerSecuredQueryAndDeny_then_accessDenied() {
        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.DENY));

        var result = Mono.fromFuture(() -> queryGateway.query(PRE_HANDLE_QUERY, QUERY, instanceOf(String.class)));

        create(result).expectErrorMatches(isAccessDenied()).verify();
    }

    @Test
    void when_dropSecuredHandlerAndDeny_then_accessDenied() {
        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.DENY));

        var result = Mono.fromFuture(() -> queryGateway.query(DROP_QUERY, QUERY, instanceOf(String.class)));

        create(result).expectErrorMatches(isAccessDenied()).verify();
    }

    @Test
    void when_recoverableSecuredQueryAndDeny_then_accessDenied() {
        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.DENY));

        var result = Mono.fromFuture(() -> queryGateway.query(RECOVERABLE_QUERY, QUERY, instanceOf(String.class)));

        create(result).expectErrorMatches(isAccessDenied()).verify();
    }

    @Test
    void when_postHandlerSecuredQueryAndPermitWithResource_then_resultReturnsAndPdpIsCalledWithSubscriptionAndReplacementIsReturned() {
        when(pdp.decide(any(AuthorizationSubscription.class)))
                .thenReturn(Flux.just(new AuthorizationDecision(Decision.PERMIT, Value.EMPTY_ARRAY, Value.EMPTY_ARRAY,
                        Value.of(I_WAS_REPLACED))));

        var result = Mono.fromFuture(() -> queryGateway.query(POST_HANDLE_QUERY, QUERY, instanceOf(String.class)));

        create(result).expectNext(I_WAS_REPLACED).verifyComplete();

        verify(pdp, times(1)).decide(any(AuthorizationSubscription.class));
        var subscription4 = captureAuthzSubscription();
        assertThat(subscription4.subject()).isEqualTo(Value.of(ANONYMOUS));
        assertThat(subscription4.action()).isEqualTo(Value.of(POST_HANDLE_QUERY));
        assertThat(subscription4.resource()).isEqualTo(Value.of(RESOURCE));
        assertThat(subscription4.environment()).isInstanceOf(UndefinedValue.class);
    }

    @Test
    void when_postHandlerSecuredQueryAndPermit_then_resultReturns() {
        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));

        var result = Mono
                .fromFuture(() -> queryGateway.query(POST_HANDLE_NO_RESOURCE_QUERY, QUERY, instanceOf(String.class)));

        create(result).expectNext(QUERY).verifyComplete();

        verify(pdp, times(1)).decide(any(AuthorizationSubscription.class));
        var subscription5 = captureAuthzSubscription();
        assertThat(subscription5.subject()).isEqualTo(Value.of(ANONYMOUS));
        assertThat(subscription5.action()).isEqualTo(Value.of(POST_HANDLE_NO_RESOURCE_QUERY));
        assertThat(subscription5.resource()).isEqualTo(Value.of(RESOURCE));
        assertThat(subscription5.environment()).isInstanceOf(UndefinedValue.class);
    }

    @Test
    void when_postHandlerSecuredQueryAndPermitWithResourceAndResourceMarshallingPails_then_accessDenied() {
        when(pdp.decide(any(AuthorizationSubscription.class)))
                .thenReturn(Flux.just(new AuthorizationDecision(Decision.PERMIT, Value.EMPTY_ARRAY, Value.EMPTY_ARRAY,
                        Value.of(I_WAS_REPLACED))));

        var result = Mono.fromFuture(
                () -> queryGateway.query(BAD_RESOURCE_SERIALIZATION_QUERY, QUERY, instanceOf(Integer.class)));

        create(result).expectErrorMatches(isAccessDenied()).verify();

        verify(pdp, times(1)).decide(any(AuthorizationSubscription.class));
        var subscription6 = captureAuthzSubscription();
        assertThat(subscription6.subject()).isEqualTo(Value.of(ANONYMOUS));
        assertThat(subscription6.action()).isEqualTo(Value.of(BAD_RESOURCE_SERIALIZATION_QUERY));
        assertThat(subscription6.resource()).isEqualTo(Value.of(RESOURCE));
        assertThat(subscription6.environment()).isInstanceOf(UndefinedValue.class);
    }

    @Test
    void when_postHandlerSecuredQueryAndDeny_then_accessDenied() {
        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.DENY));

        var result = Mono.fromFuture(() -> queryGateway.query(POST_HANDLE_QUERY, QUERY, instanceOf(String.class)));

        create(result).expectErrorMatches(isAccessDenied()).verify();
    }

    @Test
    void when_handlerIsAnnotatedWithAnIllegalCombinationOfSaplAnnotations_then_accessDenied_case1() {
        var result = Mono.fromFuture(() -> queryGateway.query(BAD_ANNOTATIONS1, QUERY, instanceOf(String.class)));
        create(result).expectErrorMatches(isAccessDenied()).verify();
    }

    @Test
    void when_handlerIsAnnotatedWithAnIllegalCombinationOfSaplAnnotations_then_accessDenied_case2() {
        var result = Mono.fromFuture(() -> queryGateway.query(BAD_ANNOTATIONS2, QUERY, instanceOf(String.class)));
        create(result).expectErrorMatches(isAccessDenied()).verify();
    }

    @Test
    void when_handlerIsAnnotatedWithAnIllegalCombinationOfSaplAnnotations_then_accessDenied_case3() {
        var result = Mono.fromFuture(() -> queryGateway.query(BAD_ANNOTATIONS3, QUERY, instanceOf(String.class)));
        create(result).expectErrorMatches(isAccessDenied()).verify();
    }

    @Test
    void when_preHandlerSecuredSubscriptionQueryAndPermit_then_initialReturnAndUpdatesAreEmitted() {
        var emitIntervallMs = 100L;
        var queryPayload    = "case1";
        var numberOfUpdates = 5L;
        var timeout         = Duration.ofMillis(emitIntervallMs * (numberOfUpdates + 10L));

        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));

        var result = queryGateway.subscriptionQuery(PRE_HANDLE_QUERY, queryPayload, instanceOf(String.class),
                instanceOf(String.class));

        emitUpdates(queryPayload, emitIntervallMs, numberOfUpdates);

        create(result.initialResult().timeout(timeout)).expectNext(queryPayload).verifyComplete();
        create(result.updates().timeout(timeout).take(5)).expectNextCount(5L).verifyComplete();
        verify(pdp, times(1)).decide(any(AuthorizationSubscription.class));
        result.close();
    }

    @Test
    void when_preHandlerSecuredSubscriptionQueryAndDeny_then_bothStreamsAccessDenied() {
        var emitIntervallMs = 50L;
        var queryPayload    = "case2";
        var numberOfUpdates = 5L;
        var timeout         = Duration.ofMillis(emitIntervallMs * (numberOfUpdates + 2L));

        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.DENY));

        var result = queryGateway.subscriptionQuery(PRE_HANDLE_QUERY, queryPayload, instanceOf(String.class),
                instanceOf(String.class));

        emitUpdates(queryPayload, emitIntervallMs, numberOfUpdates);

        create(result.initialResult().timeout(timeout)).expectErrorMatches(isAccessDenied()).verify();
        create(result.updates().timeout(timeout)).expectErrorMatches(isAccessDenied()).verify();
        verify(pdp, times(1)).decide(any(AuthorizationSubscription.class));

        result.close();
    }

    @Test
    void when_preHandlerSecuredSubscriptionQueryAndPermitPermitDeny_then_initialReturnAndUpdatesAreEmittedAndDenyForUpdatesLater() {
        var emitIntervallMs = 50L;
        var queryPayload    = "case3";
        var numberOfUpdates = 5L;
        var timeout         = Duration.ofMillis(emitIntervallMs * (numberOfUpdates + 2L));

        when(pdp.decide(any(AuthorizationSubscription.class)))
                .thenReturn(Flux.concat(Flux.just(AuthorizationDecision.PERMIT),
                        Flux.just(AuthorizationDecision.PERMIT, AuthorizationDecision.DENY)
                                .delayElements(Duration.ofMillis(emitIntervallMs * 5L + emitIntervallMs / 4L))));

        var result = queryGateway.subscriptionQuery(PRE_HANDLE_QUERY, queryPayload, instanceOf(String.class),
                instanceOf(String.class));

        emitUpdates(queryPayload, emitIntervallMs, numberOfUpdates);

        create(result.initialResult().timeout(timeout)).expectNext(queryPayload).verifyComplete();
        create(result.updates().timeout(timeout)).expectNextCount(5).expectErrorMatches(isAccessDenied()).verify();
        verify(pdp, times(1)).decide(any(AuthorizationSubscription.class));

        result.close();
    }

    @Test
    void when_dropHandlerSecuredSubscriptionQueryAndPermitDenyPermit_then_initialReturnAndUpdatesAreEmittedAndDroppedWhileDenied() {
        var initialEmitDelayMs = 250L;
        var emitIntervallMs    = 250L;
        var queryPayload       = "case4";
        var numberOfUpdates    = 14L;
        var timeout            = Duration.ofMillis(initialEmitDelayMs + emitIntervallMs * (numberOfUpdates + 2L));

        // @formatter:off
		when(pdp.decide(any(AuthorizationSubscription.class)))
				.thenReturn(Flux.concat(
						Flux.just(AuthorizationDecision.PERMIT),
						// next half-time between 5th and 6th
						Flux.just(AuthorizationDecision.DENY).delayElements(Duration.ofMillis(initialEmitDelayMs + emitIntervallMs * 5L + emitIntervallMs / 2L)),
						// next half-time between 10th and 11th
						Flux.just(AuthorizationDecision.PERMIT).delayElements(Duration.ofMillis(emitIntervallMs * 5L))
						));
		// @formatter:on

        var result = queryGateway.subscriptionQuery(DROP_QUERY, queryPayload, instanceOf(String.class),
                instanceOf(String.class));

        emitUpdates(queryPayload, emitIntervallMs, numberOfUpdates, initialEmitDelayMs);

        create(result.initialResult().timeout(timeout)).expectNext(queryPayload).verifyComplete();
        create(result.updates().take(7).timeout(timeout)).expectNext(queryPayload + "-0", queryPayload + "-1",
                queryPayload + "-2", queryPayload + "-3", queryPayload + "-4",
                /* ... DROP 5-9 ... , */ queryPayload + "-10", queryPayload + "-11" /* , IGNORE 12-13 */)
                .verifyComplete();
        verify(pdp, times(1)).decide(any(AuthorizationSubscription.class));
        result.close();
    }

    @Test
    void when_recoverableHandlerSecuredSubscriptionQueryAndPermitDenyPermitNoContiniue_then_initialReturnAndUpdatesAreEmittedAndAccessDeniedTerminatesUpdates() {
        var emitIntervallMs = 50L;
        var queryPayload    = "case5";
        var numberOfUpdates = 14L;
        var timeout         = Duration.ofMillis(emitIntervallMs * (numberOfUpdates + 2L));

        // @formatter:off
		when(pdp.decide(any(AuthorizationSubscription.class)))
				.thenReturn(Flux.concat(
						Flux.just(AuthorizationDecision.PERMIT),
						// next half-time between 5th and 6th
						Flux.just(AuthorizationDecision.DENY).delayElements(Duration.ofMillis(emitIntervallMs * 5L + emitIntervallMs / 2L)),
						// next half-time between 10th and 11th
						Flux.just(AuthorizationDecision.PERMIT).delayElements(Duration.ofMillis(emitIntervallMs * 5L))
						));
		// @formatter:on

        var accessDeniedHandler = spy(new Runnable() {
            @Override
            public void run() {
                // NOOP
            }
        });

        var result = queryGateway.recoverableSubscriptionQuery(RECOVERABLE_QUERY, queryPayload,
                instanceOf(String.class), instanceOf(String.class), accessDeniedHandler);
        emitUpdates(queryPayload, emitIntervallMs, numberOfUpdates);

        create(result.initialResult().timeout(timeout)).expectNext(queryPayload).verifyComplete();
        create(result.updates().take(6)).expectNext(queryPayload + "-0", queryPayload + "-1", queryPayload + "-2",
                queryPayload + "-3", queryPayload + "-4").expectError(AccessDeniedException.class).verify(timeout);

        verify(pdp, times(1)).decide(any(AuthorizationSubscription.class));
        verify(accessDeniedHandler, times(1)).run();

        result.close();
    }

    @Test
    void when_recoverableHandlerSecuredSubscriptionQueryAndPermitDenyPermitWithContiniue_then_initialReturnAndUpdatesAreEmittedAndAccessDeniedThenResumesOnPermit() {
        var emitIntervallMs = 100L;
        var queryPayload    = "case6";
        var numberOfUpdates = 14L;
        var timeout         = Duration.ofMillis(emitIntervallMs * (numberOfUpdates + 2L));

        // @formatter:off
		when(pdp.decide(any(AuthorizationSubscription.class)))
				.thenReturn(Flux.concat(
						Flux.just(AuthorizationDecision.PERMIT),
						// next half-time between 5th and 6th
						Flux.just(AuthorizationDecision.DENY).delayElements(Duration.ofMillis(emitIntervallMs * 5L + emitIntervallMs / 2L)),
						// next half-time between 10th and 11th
						Flux.just(AuthorizationDecision.PERMIT).delayElements(Duration.ofMillis(emitIntervallMs * 5L))
						));
		// @formatter:on

        var accessDeniedHandler = spy(new Runnable() {
                                    @Override
                                    public void run() {
                                        // NOOP
                                    }
                                });
        var result              = queryGateway.recoverableSubscriptionQuery(RECOVERABLE_QUERY, queryPayload,
                instanceOf(String.class), instanceOf(String.class), accessDeniedHandler);
        emitUpdates(queryPayload, emitIntervallMs, numberOfUpdates);

        create(result.initialResult().timeout(timeout)).expectNext(queryPayload).verifyComplete();
        create(result.updates().onErrorContinue((t, o) -> accessDeniedHandler.run()).take(6).timeout(timeout))
                .expectNext(queryPayload + "-0", queryPayload + "-1", queryPayload + "-2", queryPayload + "-3",
                        queryPayload + "-4", queryPayload + "-10")
                .verifyComplete();

        verify(pdp, times(1)).decide(any(AuthorizationSubscription.class));
        verify(accessDeniedHandler, times(1)).run();

        result.close();
    }

    @Test
    void when_preHandlerSecuredQueryAndPermitWithUnknownAdvice_then_accessGranted() {
        var advice = Value.ofArray(Value.of("unknown constraint"));
        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(
                Flux.just(new AuthorizationDecision(Decision.PERMIT, Value.EMPTY_ARRAY, advice, Value.UNDEFINED)));

        var result = Mono.fromFuture(() -> queryGateway.query(PRE_HANDLE_QUERY, QUERY, instanceOf(String.class)));

        create(result).expectNext(QUERY).verifyComplete();

        verify(pdp, times(1)).decide(any(AuthorizationSubscription.class));
    }

    @Test
    void when_preHandlerSecuredQueryAndPermitWithUnknownObligation_then_accessDenied() {
        var obligations = Value.ofArray(Value.of("unknown obligation"));
        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(
                Flux.just(new AuthorizationDecision(Decision.PERMIT, obligations, Value.EMPTY_ARRAY, Value.UNDEFINED)));

        var result = Mono.fromFuture(() -> queryGateway.query(PRE_HANDLE_QUERY, QUERY, instanceOf(String.class)));

        create(result).expectErrorMatches(isAccessDenied()).verify();

        verify(pdp, times(1)).decide(any(AuthorizationSubscription.class));
    }

    @Test
    void when_preHandlerSecuredQueryAndPermitWithOnDecisionObligation_then_accessGranted() {
        var obligations = Value.ofArray(Value.of(ON_DECISION_DO));
        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(
                Flux.just(new AuthorizationDecision(Decision.PERMIT, obligations, Value.EMPTY_ARRAY, Value.UNDEFINED)));

        var result = Mono.fromFuture(() -> queryGateway.query(PRE_HANDLE_QUERY, QUERY, instanceOf(String.class)));

        create(result).expectNext(QUERY).verifyComplete();

        verify(pdp, times(1)).decide(any(AuthorizationSubscription.class));
        verify(onDecisionProvider, times(1)).accept(any(), any());
    }

    @Test
    void when_preHandlerSecuredQueryAndPermitWithQueryMapperObligation_then_accessGrantedAndHandlerEnforced() {
        var obligations = Value.ofArray(Value.of(MODIFY_QUERY));
        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(
                Flux.just(new AuthorizationDecision(Decision.PERMIT, obligations, Value.EMPTY_ARRAY, Value.UNDEFINED)));

        var result = Mono.fromFuture(() -> queryGateway.query(PRE_HANDLE_QUERY, QUERY, instanceOf(String.class)));

        create(result).expectNext(MODIFIED_QUERY).verifyComplete();

        verify(pdp, times(1)).decide(any(AuthorizationSubscription.class));
        verify(querMappingProvider, times(1)).mapPayload(any(), any(), any());
    }

    @Test
    void when_preHandlerSecuredQueryAndPermitWithResultMapperObligation_then_accessGrantedAndHandlerEnforced() {
        var obligations = Value.ofArray(Value.of(MAP_UPDATE_PAYLOAD_TO_UPPERCASE));
        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(
                Flux.just(new AuthorizationDecision(Decision.PERMIT, obligations, Value.EMPTY_ARRAY, Value.UNDEFINED)));

        var result = Mono.fromFuture(() -> queryGateway.query(PRE_HANDLE_QUERY, QUERY, instanceOf(String.class)));

        create(result).expectNext(QUERY.toUpperCase()).verifyComplete();

        verify(pdp, times(1)).decide(any(AuthorizationSubscription.class));
        verify(resultMessageMappingProvider, times(1)).mapPayload(any(), any(), any());
    }

    @Test
    void when_preHandlerSecuredQueryAndPermitWithErrorObligation_then_failsWithModifiedError() {
        var obligations = Value.ofArray(Value.of(MODIFY_ERROR));
        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(
                Flux.just(new AuthorizationDecision(Decision.PERMIT, obligations, Value.EMPTY_ARRAY, Value.UNDEFINED)));

        var result = Mono.fromFuture(() -> queryGateway.query(FAILING_PRE_QUERY, QUERY, instanceOf(String.class)));

        create(result).expectErrorMatches(isCausedBy(IllegalArgumentException.class)).verify();

        verify(pdp, times(1)).decide(any(AuthorizationSubscription.class));
        verify(errorMappingProvider, times(1)).map(any());
    }

    @Test
    void when_postHandlerSecuredQueryAndPermitWithUnknownAdvice_then_accessGranted() {
        var advice = Value.ofArray(Value.of("unknown constraint"));
        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(
                Flux.just(new AuthorizationDecision(Decision.PERMIT, Value.EMPTY_ARRAY, advice, Value.UNDEFINED)));

        var result = Mono.fromFuture(() -> queryGateway.query(POST_HANDLE_QUERY, QUERY, instanceOf(String.class)));

        create(result).expectNext(QUERY).verifyComplete();

        verify(pdp, times(1)).decide(any(AuthorizationSubscription.class));
    }

    @Test
    void when_postHandlerSecuredQueryAndPermitWithUnknownObligation_then_accessDenied() {
        var obligations = Value.ofArray(Value.of("unknown constraint"));
        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(
                Flux.just(new AuthorizationDecision(Decision.PERMIT, obligations, Value.EMPTY_ARRAY, Value.UNDEFINED)));

        var result = Mono.fromFuture(() -> queryGateway.query(POST_HANDLE_QUERY, QUERY, instanceOf(String.class)));

        create(result).expectErrorMatches(isAccessDenied()).verify();

        verify(pdp, times(1)).decide(any(AuthorizationSubscription.class));
    }

    @Test
    void when_postHandlerSecuredQueryAndPermitWithQueryMapperObligation_then_accessDeniedCauseOfNotAbleToHandle() {
        var obligations = Value.ofArray(Value.of(MODIFY_QUERY));
        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(
                Flux.just(new AuthorizationDecision(Decision.PERMIT, obligations, Value.EMPTY_ARRAY, Value.UNDEFINED)));

        var result = Mono.fromFuture(() -> queryGateway.query(POST_HANDLE_QUERY, QUERY, instanceOf(String.class)));

        create(result).expectErrorMatches(isAccessDenied()).verify();

        verify(pdp, times(1)).decide(any(AuthorizationSubscription.class));
        verify(querMappingProvider, times(0)).mapPayload(any(), any(), any());
    }

    @Test
    void when_postHandlerSecuredQueryAndPermitWithResultMapperObligation_then_accessGrantedAndHandlerEnforced() {
        var obligations = Value.ofArray(Value.of(MAP_UPDATE_PAYLOAD_TO_UPPERCASE));
        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(
                Flux.just(new AuthorizationDecision(Decision.PERMIT, obligations, Value.EMPTY_ARRAY, Value.UNDEFINED)));

        var result = Mono.fromFuture(() -> queryGateway.query(POST_HANDLE_QUERY, QUERY, instanceOf(String.class)));

        create(result).expectNext(QUERY.toUpperCase()).verifyComplete();

        verify(pdp, times(1)).decide(any(AuthorizationSubscription.class));
        verify(resultMessageMappingProvider, times(1)).mapPayload(any(), any(), any());
    }

    @Test
    void when_postHandlerSecuredQueryAndPermitWithErrorObligation_then_failsWithModifiedError() {
        var obligations = Value.ofArray(Value.of(MODIFY_ERROR));
        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(
                Flux.just(new AuthorizationDecision(Decision.PERMIT, obligations, Value.EMPTY_ARRAY, Value.UNDEFINED)));

        var result = Mono.fromFuture(() -> queryGateway.query(FAILING_POST_QUERY, QUERY, instanceOf(String.class)));

        create(result).expectErrorMatches(isCausedBy(IllegalArgumentException.class)).verify();

        verify(pdp, times(1)).decide(any(AuthorizationSubscription.class));
        verify(errorMappingProvider, times(1)).map(any());
    }

    private void emitUpdates(String queryPayload, long emitIntervallMs, long numberOfEmittedUpdates) {
        emitUpdates(queryPayload, emitIntervallMs, numberOfEmittedUpdates, 0L);
    }

    private void emitUpdates(String queryPayload, long emitIntervallMs, long numberOfEmittedUpdates,
            long initialEmitDelayMs) {
        Mono.delay(Duration.ofMillis(initialEmitDelayMs))
                .flatMapMany(x -> Flux.interval(Duration.ofMillis(emitIntervallMs))
                        .doOnNext(i -> emitter.emit(query -> query.getPayload().toString().equals(queryPayload),
                                queryPayload + "-" + i))
                        .take(Duration.ofMillis(emitIntervallMs * numberOfEmittedUpdates + emitIntervallMs / 2L)))
                .subscribe();
    }

    @Test
    void when_preHandlerSecuredSubscriptionQueryAndPermitWithConstraints_then_initialReturnAndUpdatesAreEmitted() {
        var emitIntervallMs = 50L;
        var queryPayload    = "caseC1";
        var numberOfUpdates = 20L;
        var timeout         = Duration.ofMillis(emitIntervallMs * (numberOfUpdates + 2L));

        var constraints  = Value.ofArray(Value.of(MAP_UPDATE_PAYLOAD_TO_UPPERCASE), Value.of(ONLY_EVEN_NUMBERS),
                Value.of(MODIFY_ERROR));
        var constraints2 = Value.ofArray(Value.of(MAP_UPDATE_PAYLOAD_TO_UPPERCASE), Value.of(MODIFY_ERROR));

        Flux<AuthorizationDecision> decisions = Flux.concat(
                Flux.just(new AuthorizationDecision(Decision.PERMIT, constraints, Value.EMPTY_ARRAY, Value.UNDEFINED)),
                Flux.just(new AuthorizationDecision(Decision.PERMIT, constraints2, Value.EMPTY_ARRAY, Value.UNDEFINED))
                        .delayElements(Duration.ofMillis(10 * emitIntervallMs + emitIntervallMs / 2)));

        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(decisions);

        var result = queryGateway.subscriptionQuery(PRE_HANDLE_QUERY, queryPayload, instanceOf(String.class),
                instanceOf(String.class));

        emitUpdates(queryPayload, emitIntervallMs, numberOfUpdates);
        create(result.initialResult().timeout(timeout)).expectNext(queryPayload.toUpperCase()).verifyComplete();
        create(result.updates().timeout(timeout).take(15)).expectNext("CASEC1-0", "CASEC1-2", "CASEC1-4", "CASEC1-6",
                "CASEC1-8", "CASEC1-10", "CASEC1-11", "CASEC1-12", "CASEC1-13", "CASEC1-14", "CASEC1-15", "CASEC1-16",
                "CASEC1-17", "CASEC1-18", "CASEC1-19").verifyComplete();
        verify(pdp, times(1)).decide(any(AuthorizationSubscription.class));
        verify(resultMessageMappingProvider, times(21)).mapPayload(any(), any(), any());
        result.close();
    }

    @Test
    void when_dropHandlerSubscriptionQueryAndPermitWithConstraints_then_initialReturnAndUpdatesAreEmitted() {
        var emitIntervallMs = 100L;
        var queryPayload    = "caseC2";
        var numberOfUpdates = 15L;
        var timeout         = Duration.ofMillis(emitIntervallMs * (numberOfUpdates + 2L));

        var constraints  = Value.ofArray(Value.of(MAP_UPDATE_PAYLOAD_TO_UPPERCASE), Value.of(ONLY_EVEN_NUMBERS),
                Value.of(MODIFY_ERROR));
        var constraints2 = Value.ofArray(Value.of(MAP_UPDATE_PAYLOAD_TO_UPPERCASE), Value.of(MODIFY_ERROR));

        Flux<AuthorizationDecision> decisions = Flux.concat(
                Flux.just(new AuthorizationDecision(Decision.PERMIT, constraints, Value.EMPTY_ARRAY, Value.UNDEFINED)),
                Flux.just(AuthorizationDecision.DENY)
                        .delayElements(Duration.ofMillis(5 * emitIntervallMs + emitIntervallMs / 2)),
                Flux.just(new AuthorizationDecision(Decision.PERMIT, constraints2, Value.EMPTY_ARRAY, Value.UNDEFINED))
                        .delayElements(Duration.ofMillis(3 * emitIntervallMs)));

        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(decisions);

        var result = queryGateway.subscriptionQuery(DROP_QUERY, queryPayload, instanceOf(String.class),
                instanceOf(String.class));

        emitUpdates(queryPayload, emitIntervallMs, numberOfUpdates);
        create(result.initialResult().timeout(timeout)).expectNext(queryPayload.toUpperCase()).verifyComplete();
        create(result.updates().timeout(timeout).take(7))
                .expectNext("CASEC2-0", "CASEC2-2", "CASEC2-4", "CASEC2-8", "CASEC2-9", "CASEC2-10", "CASEC2-11")
                .verifyComplete();
        verify(pdp, times(1)).decide(any(AuthorizationSubscription.class));
        verify(resultMessageMappingProvider, times(10)).mapPayload(any(), any(), any());
        result.close();
    }

    @Test
    void when_recoverableHandlerSubscriptionQueryAndPermitWithConstraints_then_initialReturnAndUpdatesAreEmitted() {
        var emitIntervallMs = 50L;
        var queryPayload    = "caseC3";
        var numberOfUpdates = 20L;
        var timeout         = Duration.ofMillis(emitIntervallMs * (numberOfUpdates + 2L));

        var constraints = Value.ofArray(Value.of(MAP_UPDATE_PAYLOAD_TO_UPPERCASE), Value.of(ONLY_EVEN_NUMBERS),
                Value.of(MODIFY_ERROR));

        var constraints2 = Value.ofArray(Value.of(MAP_UPDATE_PAYLOAD_TO_UPPERCASE), Value.of(MODIFY_ERROR));

        Flux<AuthorizationDecision> decisions = Flux.concat(
                Flux.just(new AuthorizationDecision(Decision.PERMIT, constraints, Value.EMPTY_ARRAY, Value.UNDEFINED)),
                Flux.just(AuthorizationDecision.DENY)
                        .delayElements(Duration.ofMillis(5 * emitIntervallMs + emitIntervallMs / 2)),
                Flux.just(new AuthorizationDecision(Decision.PERMIT, constraints2, Value.EMPTY_ARRAY, Value.UNDEFINED))
                        .delayElements(Duration.ofMillis(3 * emitIntervallMs)));

        var accessDeniedHandler = mock(Runnable.class);
        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(decisions);

        var result = queryGateway.recoverableSubscriptionQuery(RECOVERABLE_QUERY, queryPayload,
                instanceOf(String.class), instanceOf(String.class), accessDeniedHandler);

        emitUpdates(queryPayload, emitIntervallMs, numberOfUpdates);
        create(result.initialResult().timeout(timeout)).expectNext(queryPayload.toUpperCase()).verifyComplete();
        create(result.updates().timeout(timeout).take(7)).expectNext("CASEC3-0", "CASEC3-2", "CASEC3-4")
                .expectErrorMatches(isAccessDenied()).verify();
        verify(pdp, times(1)).decide(any(AuthorizationSubscription.class));
        verify(accessDeniedHandler, times(1)).run();
        verify(resultMessageMappingProvider, times(6)).mapPayload(any(), any(), any());
        result.close();
    }

    @Test
    void when_recoverableHandlerSubscriptionQueryAndPermitWithConstraintsWithRecovery_then_initialReturnAndUpdatesAreEmitted() {
        var emitIntervallMs = 75L;
        var queryPayload    = "caseC4";
        var numberOfUpdates = 20L;
        var timeout         = Duration.ofMillis(emitIntervallMs * (numberOfUpdates + 2L));

        var constraints  = Value.ofArray(Value.of(MAP_UPDATE_PAYLOAD_TO_UPPERCASE), Value.of(ONLY_EVEN_NUMBERS),
                Value.of(MODIFY_ERROR));
        var constraints2 = Value.ofArray(Value.of(MAP_UPDATE_PAYLOAD_TO_UPPERCASE), Value.of(MODIFY_ERROR));

        Flux<AuthorizationDecision> decisions                  = Flux.concat(
                Flux.just(new AuthorizationDecision(Decision.PERMIT, constraints, Value.EMPTY_ARRAY, Value.UNDEFINED)),
                Flux.just(AuthorizationDecision.DENY)
                        .delayElements(Duration.ofMillis(5 * emitIntervallMs + emitIntervallMs / 2)),
                Flux.just(new AuthorizationDecision(Decision.PERMIT, constraints2, Value.EMPTY_ARRAY, Value.UNDEFINED))
                        .delayElements(Duration.ofMillis(3 * emitIntervallMs - emitIntervallMs / 4)));
        var                         accessDeniedHandler        = mock(Runnable.class);
        @SuppressWarnings("unchecked")
        var                         accessDeniedHandlerOnError = (BiConsumer<Throwable, Object>) mock(BiConsumer.class);

        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(decisions);

        var result = queryGateway.recoverableSubscriptionQuery(RECOVERABLE_QUERY, queryPayload,
                instanceOf(String.class), instanceOf(String.class), accessDeniedHandler);

        emitUpdates(queryPayload, emitIntervallMs, numberOfUpdates);
        create(result.initialResult().timeout(timeout)).expectNext(queryPayload.toUpperCase()).verifyComplete();

        create(result.updates().onErrorContinue(accessDeniedHandlerOnError).timeout(timeout).take(7))
                .expectNext("CASEC4-0", "CASEC4-2", "CASEC4-4", "CASEC4-8", "CASEC4-9", "CASEC4-10", "CASEC4-11")
                .verifyComplete();
        verify(accessDeniedHandler, times(0)).run();
        verify(accessDeniedHandlerOnError, times(1)).accept(any(), any());
        verify(pdp, times(1)).decide(any(AuthorizationSubscription.class));
        verify(resultMessageMappingProvider, times(10)).mapPayload(any(), any(), any());
        result.close();
    }

    @Test
    void when_constraintWantsCollectionFilter_then_CollectionsAreFiltered() {
        var emitIntervallMs = 20L;
        var queryPayload    = "caseCX1";
        var numberOfUpdates = 20L;
        var timeout         = Duration.ofMillis(emitIntervallMs * (numberOfUpdates + 2L));

        var filterConstraint = ValueJsonMarshaller.json("""
                {
                  "type"    : "filterMessagePayloadContent",
                  "actions" : [
                    {
                       "type" : "blacken",
                       "path" : "$.name",
                       "discloseLeft": 2
                    },
                    {
                       "type" : "delete",
                       "path" : "$.age"
                    }
                  ]
                 }
                """);
        var constraints      = Value.ofArray(filterConstraint, Value.of(REMOVE_YOUNGER_THAN18));

        var decisions = Flux
                .just(new AuthorizationDecision(Decision.PERMIT, constraints, Value.EMPTY_ARRAY, Value.UNDEFINED));

        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(decisions);

        // Normal Query

        var result = Mono.fromFuture(() -> queryGateway.query(LIST_RESPONSE_QUERY, QUERY,
                ResponseTypes.multipleInstancesOf(DataPoint.class)));
        StepVerifier.create(result)
                .expectNext(List.of(new DataPoint("Al\u2588\u2588", null), new DataPoint("Al\u2588\u2588\u2588", null)))
                .verifyComplete();

        // Subscription Query

        var subscriptionResult = queryGateway.subscriptionQuery(LIST_RESPONSE_QUERY, queryPayload,
                ResponseTypes.multipleInstancesOf(DataPoint.class), ResponseTypes.multipleInstancesOf(DataPoint.class));

        StepVerifier.create(subscriptionResult.initialResult())
                .expectNext(List.of(new DataPoint("Al\u2588\u2588", null), new DataPoint("Al\u2588\u2588\u2588", null)))
                .verifyComplete();

        // Attention: Do not use List.of() the returned data type is sometimes not
        // handled
        // correctly by XStream Serialization for AxonServer
        var updateList = new LinkedList<DataPoint>();
        updateList.addAll(List.of(new DataPoint("Gerald", 22), new DataPoint("Tina", 5)));

        Flux.interval(Duration.ofMillis(emitIntervallMs))
                .doOnNext(i -> emitter.emit(query -> query.getPayload().toString().equals(queryPayload), updateList))
                .take(Duration.ofMillis(emitIntervallMs * numberOfUpdates + emitIntervallMs / 2L)).subscribe();

        StepVerifier.create(subscriptionResult.updates().take(2).timeout(timeout))
                .expectNext(List.of(new DataPoint("Ge\u2588\u2588\u2588\u2588", null)))
                .expectNext(List.of(new DataPoint("Ge\u2588\u2588\u2588\u2588", null))).verifyComplete();
    }

    // @formatter:off
	static class Projection {

		@QueryHandler(queryName = UNSECURED_QUERY)
		public String handleUnsecured(String query) { return query; }

		@QueryHandler(queryName = PRE_HANDLE_QUERY)
		@PreHandleEnforce(action="'"+PRE_HANDLE_QUERY+"'", resource=RESOURCE_EXPR)
		public String handlePreEnforce(String query) { return query; }

		@QueryHandler(queryName = DROP_QUERY)
		@EnforceDropUpdatesWhileDenied(action="'"+DROP_QUERY+"'", resource=RESOURCE_EXPR)
		public String handleDrop(String query) { return query; }

		@QueryHandler(queryName = RECOVERABLE_QUERY)
		@EnforceRecoverableUpdatesIfDenied(action="'"+RECOVERABLE_QUERY+"'", resource=RESOURCE_EXPR)
		public String handleRecoverable(String query) { return query; }

		@QueryHandler(queryName = POST_HANDLE_QUERY)
		@PostHandleEnforce(action="'"+POST_HANDLE_QUERY+"'", resource=RESOURCE_EXPR)
		public String handlePostEnforce(String query) { return query; }

		@QueryHandler(queryName = POST_HANDLE_NO_RESOURCE_QUERY)
		@PostHandleEnforce(action="'"+POST_HANDLE_NO_RESOURCE_QUERY+"'", resource=RESOURCE_EXPR)
		public String handlePostEnforceNoResource(String query) { return query; }

		@QueryHandler(queryName = BAD_RESOURCE_SERIALIZATION_QUERY)
		@PostHandleEnforce(action="'"+BAD_RESOURCE_SERIALIZATION_QUERY+"'", resource=RESOURCE_EXPR)
		public Integer handlePostEnforceFailing(String query) { return 0; }

		@PreHandleEnforce
		@EnforceDropUpdatesWhileDenied
		@QueryHandler(queryName = BAD_ANNOTATIONS1)
		public String handleBadAnnotations1(String query) { return query; }

		@PreHandleEnforce
		@EnforceRecoverableUpdatesIfDenied
		@QueryHandler(queryName = BAD_ANNOTATIONS2)
		public String handleBadAnnotations2(String query) { return query; }

		@EnforceDropUpdatesWhileDenied
		@EnforceRecoverableUpdatesIfDenied
		@QueryHandler(queryName = BAD_ANNOTATIONS3)
		public String handleBadAnnotations3(String query) { return query; }

		@QueryHandler(queryName = RECOVERABLE_QUERY)
		@EnforceRecoverableUpdatesIfDenied(action="'"+RECOVERABLE_QUERY+"'", resource=RESOURCE_EXPR)
		public String handleRecoverableQuery(String query) { return query; }

		@QueryHandler(queryName = FAILING_PRE_QUERY)
		@PreHandleEnforce(action="'"+FAILING_PRE_QUERY+"'", resource=RESOURCE_EXPR)
		public String handlePreEnforceFail(String query) { throw new RuntimeException("PANIC"); }

		@QueryHandler(queryName = FAILING_POST_QUERY)
		@PostHandleEnforce(action="'"+FAILING_POST_QUERY+"'", resource=RESOURCE_EXPR)
		public String handlePostEnforceFail(String query) { throw new RuntimeException("PANIC"); }

		@QueryHandler(queryName = LIST_RESPONSE_QUERY)
		@PreHandleEnforce(action="'"+LIST_RESPONSE_QUERY+"'", resource=RESOURCE_EXPR)
		public List<DataPoint> handleListResponse(String query) {
			return List.of(new DataPoint("Ada", 11), new DataPoint("Alan", 45), new DataPoint("Alice", 23), new DataPoint("Bob",8));
		}

	}
	// @formatter:on

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DataPoint {
        String  name;
        Integer age;
    }

    static class ResultFilterProvider implements UpdateFilterConstraintHandlerProvider {

        @Override
        public boolean isResponsible(Value constraint) {
            return constraint instanceof TextValue(var text) && ONLY_EVEN_NUMBERS.equals(text);
        }

        @Override
        public Set<ResponseType<?>> getSupportedResponseTypes() {
            return Set.of(ResponseTypes.instanceOf(String.class));
        }

        @Override
        public Predicate<ResultMessage<?>> getHandler(Value constraint) {
            return update -> {
                String[] split = ((String) update.getPayload()).split("-");
                return Integer.parseInt(split[1]) % 2 == 0;
            };
        }

    }

    static class ResultMessageMappingProvider implements ResultConstraintHandlerProvider {

        @Override
        public boolean isResponsible(Value constraint) {
            return constraint instanceof TextValue(var text) && MAP_UPDATE_PAYLOAD_TO_UPPERCASE.equals(text);
        }

        @Override
        public Set<ResponseType<?>> getSupportedResponseTypes() {
            return Set.of(ResponseTypes.instanceOf(String.class));
        }

        @Override
        public Object mapPayload(Object payload, Class<?> clazz, Value constraint) {
            return ((String) payload).toUpperCase();
        }
    }

    static class OnDecisionProvider implements OnDecisionConstraintHandlerProvider {

        @Override
        public boolean isResponsible(Value constraint) {
            return constraint instanceof TextValue(var text) && ON_DECISION_DO.equals(text);
        }

        @Override
        public BiConsumer<AuthorizationDecision, Message<?>> getHandler(Value constraint) {
            return this::accept;
        }

        public void accept(AuthorizationDecision decision, Message<?> message) {
            // NOOP
        }

    }

    static class QueryMappingProvider implements QueryConstraintHandlerProvider {

        @Override
        public boolean isResponsible(Value constraint) {
            return constraint instanceof TextValue(var text) && MODIFY_QUERY.equals(text);
        }

        @Override
        public Object mapPayload(Object payload, Class<?> clazz, Value constraint) {
            return MODIFIED_QUERY;
        }

    }

    public static class ResultMappingProvider implements MappingConstraintHandlerProvider<String> {

        @Override
        public boolean isResponsible(Value constraint) {
            return constraint instanceof TextValue(var text) && MODIFY_RESULT.equals(text);
        }

        @Override
        public Class<String> getSupportedType() {
            return String.class;
        }

        @Override
        public UnaryOperator<String> getHandler(Value constraint) {
            return this::map;
        }

        public String map(String original) {
            return MODIFIED_RESULT;
        }

    }

    public static class ErrorMappingProvider implements ErrorMappingConstraintHandlerProvider {

        @Override
        public boolean isResponsible(Value constraint) {
            return constraint instanceof TextValue(var text) && MODIFY_ERROR.equals(text);
        }

        @Override
        public UnaryOperator<Throwable> getHandler(Value constraint) {
            return this::map;
        }

        public Throwable map(Throwable original) {
            return new IllegalArgumentException(original.getMessage(), original.getCause());
        }

    }

    private static class FilterPredicateExampleProvider
            implements CollectionAndOptionalFilterPredicateProvider<DataPoint> {

        @Override
        public boolean isResponsible(Value constraint) {
            return constraint instanceof TextValue(var text) && REMOVE_YOUNGER_THAN18.equals(text);
        }

        @Override
        public Class<DataPoint> getContainedType() {
            return DataPoint.class;
        }

        @Override
        public boolean test(DataPoint o, Value constraint) {
            return o.getAge() >= 18;
        }

    }

    private AuthorizationSubscription captureAuthzSubscription() {
        var argumentCaptor = ArgumentCaptor.forClass(AuthorizationSubscription.class);
        verify(pdp).decide(argumentCaptor.capture());
        return argumentCaptor.getValue();
    }

    @Configuration
    @Import({ SaplAutoConfiguration.class })
    static class TestScenarioConfiguration {

        @Bean
        Projection projection() {
            return new Projection();
        }

        @Bean
        FilterPredicateExampleProvider FilterPredicateExampleProvider() {
            return new FilterPredicateExampleProvider();
        }

        @Bean
        ResponseMessagePayloadFilterProvider responseMessagePayloadFilterProvider(JsonMapper mapper) {
            return new ResponseMessagePayloadFilterProvider(mapper);
        }

        @Bean
        ResultMessageMappingProvider resultMessageMappingProvider() {
            return new ResultMessageMappingProvider();
        }

        @Bean
        ResultFilterProvider filterUpdatesProvider() {
            return new ResultFilterProvider();
        }

        @Bean
        OnDecisionProvider onDecisionProvider() {
            return new OnDecisionProvider();
        }

        @Bean
        QueryMappingProvider querMappingProvider() {
            return new QueryMappingProvider();
        }

        @Bean
        ResultMappingProvider resultMappingProvider() {
            return new ResultMappingProvider();
        }

        @Bean
        ErrorMappingProvider errorMappingProvider() {
            return new ErrorMappingProvider();
        }

    }

}
