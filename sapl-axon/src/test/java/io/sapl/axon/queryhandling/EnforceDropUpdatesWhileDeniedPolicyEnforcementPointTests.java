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

import static io.sapl.axon.TestUtilities.matchesIgnoringIdentifier;
import static io.sapl.axon.queryhandling.EnforceDropUpdatesWhileDeniedPolicyEnforcementPoint.of;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.util.ReflectionTestUtils.setField;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Function;

import org.axonframework.messaging.Message;
import org.axonframework.messaging.ResultMessage;
import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.GenericSubscriptionQueryMessage;
import org.axonframework.queryhandling.GenericSubscriptionQueryUpdateMessage;
import org.axonframework.queryhandling.SubscriptionQueryUpdateMessage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.axon.constrainthandling.ConstraintHandlerService;
import io.sapl.axon.constrainthandling.QueryConstraintHandlerBundle;
import lombok.EqualsAndHashCode;
import lombok.experimental.StandardException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class EnforceDropUpdatesWhileDeniedPolicyEnforcementPointTests {

    private static final String                                                 MAPPER_FILED_NAME        = "mapper";
    private static final String                                                 ERROR_MAPPERS_FILED_NAME = "errorMappers";
    private static final Duration                                               DEFAULT_TIMEOUT          = Duration
            .ofMillis(250);
    private static final Duration                                               DEFAULT_TIMESTEP         = Duration
            .ofMillis(10);
    private static final SubscriptionQueryUpdateMessage<TestUpdateResponseType> DEFAULT_UPDATE_MESSAGE   = new GenericSubscriptionQueryUpdateMessage<TestUpdateResponseType>(
            new TestUpdateResponseType());

    private static class TestQueryPayload {
    }

    private static class TestInitialResponse {
    }

    @EqualsAndHashCode
    @JsonIgnoreProperties("hibernateLazyInitializer")
    private static class TestUpdateResponseType {
    }

    @StandardException
    private static class TestAccessDeniedException extends AccessDeniedException {
        private static final long serialVersionUID = -1236246641335607721L;
    }

    private static ConstraintHandlerService constraintHandlerService;
    private static JsonNode                 defaultResource;

    @BeforeAll
    @SuppressWarnings("unchecked")
    static void beforeAll() {
        var mapper = new ObjectMapper();
        constraintHandlerService = mock(ConstraintHandlerService.class);
        setField(constraintHandlerService, MAPPER_FILED_NAME, mapper);
        when(constraintHandlerService.buildQueryPreHandlerBundle(any(AuthorizationDecision.class),
                any(ResponseType.class), any(Optional.class))).thenCallRealMethod();
        when(constraintHandlerService.deserializeResource(any(JsonNode.class), any(ResponseType.class)))
                .thenCallRealMethod();
        defaultResource = mapper.valueToTree(new TestUpdateResponseType());
    }

    @Test
    void when_pep_empty_then_noEvent() {
        var resultResponseType = ResponseTypes.instanceOf(TestInitialResponse.class);
        var updateResponseType = ResponseTypes.multipleInstancesOf(TestUpdateResponseType.class);
        var query              = new GenericSubscriptionQueryMessage<>(new TestQueryPayload(), resultResponseType,
                updateResponseType);

        Flux<AuthorizationDecision>                                  decisions         = Flux.just();
        Flux<SubscriptionQueryUpdateMessage<TestUpdateResponseType>> updateMessageFlux = Flux.just();

        var enforcedUpdateMessageFlux = of(query, decisions, updateMessageFlux, constraintHandlerService,
                resultResponseType, updateResponseType);
        StepVerifier.create(enforcedUpdateMessageFlux).expectSubscription().verifyTimeout(DEFAULT_TIMEOUT);
    }

    @Test
    void when_pep_subscribedTwice_then_illegalState() {
        var resultResponseType = ResponseTypes.instanceOf(TestInitialResponse.class);
        var updateResponseType = ResponseTypes.multipleInstancesOf(TestUpdateResponseType.class);
        var query              = new GenericSubscriptionQueryMessage<>(new TestQueryPayload(), resultResponseType,
                updateResponseType);

        Flux<AuthorizationDecision>                                  decisions         = Flux.just();
        Flux<SubscriptionQueryUpdateMessage<TestUpdateResponseType>> updateMessageFlux = Flux.just();

        var enforcedUpdateMessageFlux = of(query, decisions, updateMessageFlux, constraintHandlerService,
                resultResponseType, updateResponseType);
        StepVerifier.create(enforcedUpdateMessageFlux).expectSubscription().verifyTimeout(DEFAULT_TIMEOUT);
        StepVerifier.create(enforcedUpdateMessageFlux).expectError(IllegalStateException.class)
                .verify(DEFAULT_TIMEOUT.multipliedBy(2));
    }

    @Test
    void when_pep_decisionError_and_noUpdate_then_noEvent() {
        var resultResponseType = ResponseTypes.instanceOf(TestInitialResponse.class);
        var updateResponseType = ResponseTypes.multipleInstancesOf(TestUpdateResponseType.class);
        var query              = new GenericSubscriptionQueryMessage<>(new TestQueryPayload(), resultResponseType,
                updateResponseType);

        Flux<AuthorizationDecision>                                  decisions         = Flux
                .error(new TestAccessDeniedException());
        Flux<SubscriptionQueryUpdateMessage<TestUpdateResponseType>> updateMessageFlux = Flux.just();

        var enforcedUpdateMessageFlux = of(query, decisions, updateMessageFlux, constraintHandlerService,
                resultResponseType, updateResponseType);
        StepVerifier.create(enforcedUpdateMessageFlux).expectSubscription().verifyTimeout(DEFAULT_TIMEOUT);
    }

    @Test
    void when_pep_decisionError_and_singleUpdate_then_noEvent() {
        var resultResponseType = ResponseTypes.instanceOf(TestInitialResponse.class);
        var updateResponseType = ResponseTypes.multipleInstancesOf(TestUpdateResponseType.class);
        var query              = new GenericSubscriptionQueryMessage<>(new TestQueryPayload(), resultResponseType,
                updateResponseType);

        Flux<AuthorizationDecision>                                  decisions         = Flux
                .error(new TestAccessDeniedException());
        Flux<SubscriptionQueryUpdateMessage<TestUpdateResponseType>> updateMessageFlux = Flux
                .just(DEFAULT_UPDATE_MESSAGE);

        var enforcedUpdateMessageFlux = of(query, decisions, updateMessageFlux, constraintHandlerService,
                resultResponseType, updateResponseType);
        StepVerifier.create(enforcedUpdateMessageFlux).expectSubscription().verifyTimeout(DEFAULT_TIMEOUT);
    }

    @Test
    void when_pep_noDecision_and_updateError_then_noEvent() {
        var resultResponseType = ResponseTypes.instanceOf(TestInitialResponse.class);
        var updateResponseType = ResponseTypes.multipleInstancesOf(TestUpdateResponseType.class);
        var query              = new GenericSubscriptionQueryMessage<>(new TestQueryPayload(), resultResponseType,
                updateResponseType);

        Flux<AuthorizationDecision>                                  decisions         = Flux.just();
        Flux<SubscriptionQueryUpdateMessage<TestUpdateResponseType>> updateMessageFlux = Flux
                .error(new TestAccessDeniedException());

        var enforcedUpdateMessageFlux = of(query, decisions, updateMessageFlux, constraintHandlerService,
                resultResponseType, updateResponseType);
        StepVerifier.create(enforcedUpdateMessageFlux).expectSubscription().verifyTimeout(DEFAULT_TIMEOUT);
    }

    @Test
    void when_pep_singleDecision_and_updateError_then_accessDenied() {
        var resultResponseType = ResponseTypes.instanceOf(TestInitialResponse.class);
        var updateResponseType = ResponseTypes.multipleInstancesOf(TestUpdateResponseType.class);
        var query              = new GenericSubscriptionQueryMessage<>(new TestQueryPayload(), resultResponseType,
                updateResponseType);

        Flux<AuthorizationDecision>                                  decisions         = Flux
                .just(new AuthorizationDecision(Decision.PERMIT));
        Flux<SubscriptionQueryUpdateMessage<TestUpdateResponseType>> updateMessageFlux = Flux
                .error(new TestAccessDeniedException());

        var enforcedUpdateMessageFlux = of(query, decisions, updateMessageFlux, constraintHandlerService,
                resultResponseType, updateResponseType);
        StepVerifier.create(enforcedUpdateMessageFlux).expectError(TestAccessDeniedException.class)
                .verify(DEFAULT_TIMEOUT);
    }

    @Test
    void when_pep_singleDeny_and_noUpdate_then_noEvent() {
        var resultResponseType = ResponseTypes.instanceOf(TestInitialResponse.class);
        var updateResponseType = ResponseTypes.multipleInstancesOf(TestUpdateResponseType.class);
        var query              = new GenericSubscriptionQueryMessage<>(new TestQueryPayload(), resultResponseType,
                updateResponseType);
        var decisions          = Flux.just(new AuthorizationDecision(Decision.DENY));

        Flux<SubscriptionQueryUpdateMessage<TestUpdateResponseType>> updateMessageFlux = Flux.just();

        var enforcedUpdateMessageFlux = of(query, decisions, updateMessageFlux, constraintHandlerService,
                resultResponseType, updateResponseType);
        StepVerifier.create(enforcedUpdateMessageFlux).expectSubscription().verifyTimeout(DEFAULT_TIMEOUT);
    }

    @Test
    void when_pep_singleIndeterminate_and_noUpdate_then_noEvent() {
        var resultResponseType = ResponseTypes.instanceOf(TestInitialResponse.class);
        var updateResponseType = ResponseTypes.multipleInstancesOf(TestUpdateResponseType.class);
        var query              = new GenericSubscriptionQueryMessage<>(new TestQueryPayload(), resultResponseType,
                updateResponseType);
        var decisions          = Flux.just(new AuthorizationDecision(Decision.INDETERMINATE));

        Flux<SubscriptionQueryUpdateMessage<TestUpdateResponseType>> updateMessageFlux = Flux.just();

        var enforcedUpdateMessageFlux = of(query, decisions, updateMessageFlux, constraintHandlerService,
                resultResponseType, updateResponseType);
        StepVerifier.create(enforcedUpdateMessageFlux).expectSubscription().verifyTimeout(DEFAULT_TIMEOUT);
    }

    @Test
    void when_pep_singleNotApplicable_and_noUpdate_then_noEvent() {
        var resultResponseType = ResponseTypes.instanceOf(TestInitialResponse.class);
        var updateResponseType = ResponseTypes.multipleInstancesOf(TestUpdateResponseType.class);
        var query              = new GenericSubscriptionQueryMessage<>(new TestQueryPayload(), resultResponseType,
                updateResponseType);
        var decisions          = Flux.just(new AuthorizationDecision(Decision.NOT_APPLICABLE));

        Flux<SubscriptionQueryUpdateMessage<TestUpdateResponseType>> updateMessageFlux = Flux.just();

        var enforcedUpdateMessageFlux = of(query, decisions, updateMessageFlux, constraintHandlerService,
                resultResponseType, updateResponseType);
        StepVerifier.create(enforcedUpdateMessageFlux).expectSubscription().verifyTimeout(DEFAULT_TIMEOUT);
    }

    @Test
    void when_pep_singlePermit_and_noUpdate_then_complete() {
        var resultResponseType = ResponseTypes.instanceOf(TestInitialResponse.class);
        var updateResponseType = ResponseTypes.multipleInstancesOf(TestUpdateResponseType.class);
        var query              = new GenericSubscriptionQueryMessage<>(new TestQueryPayload(), resultResponseType,
                updateResponseType);
        var decisions          = Flux.just(new AuthorizationDecision(Decision.PERMIT));

        Flux<SubscriptionQueryUpdateMessage<TestUpdateResponseType>> updateMessageFlux = Flux.just();

        var enforcedUpdateMessageFlux = of(query, decisions, updateMessageFlux, constraintHandlerService,
                resultResponseType, updateResponseType);
        StepVerifier.create(enforcedUpdateMessageFlux).verifyComplete();
    }

    @Test
    void when_pep_noDecision_and_singleUpdate_then_noEvent() {
        var resultResponseType = ResponseTypes.instanceOf(TestInitialResponse.class);
        var updateResponseType = ResponseTypes.multipleInstancesOf(TestUpdateResponseType.class);
        var query              = new GenericSubscriptionQueryMessage<>(new TestQueryPayload(), resultResponseType,
                updateResponseType);

        Flux<AuthorizationDecision>                                  decisions         = Flux.just();
        Flux<SubscriptionQueryUpdateMessage<TestUpdateResponseType>> updateMessageFlux = Flux
                .just(DEFAULT_UPDATE_MESSAGE);

        var enforcedUpdateMessageFlux = of(query, decisions, updateMessageFlux, constraintHandlerService,
                resultResponseType, updateResponseType);
        StepVerifier.create(enforcedUpdateMessageFlux).expectSubscription().verifyTimeout(DEFAULT_TIMEOUT);
    }

    @Test
    void when_pep_singleDeny_and_singleUpdate_then_noEvent() {
        var resultResponseType = ResponseTypes.instanceOf(TestInitialResponse.class);
        var updateResponseType = ResponseTypes.multipleInstancesOf(TestUpdateResponseType.class);
        var query              = new GenericSubscriptionQueryMessage<>(new TestQueryPayload(), resultResponseType,
                updateResponseType);
        var decisions          = Flux.just(new AuthorizationDecision(Decision.DENY));

        Flux<SubscriptionQueryUpdateMessage<TestUpdateResponseType>> updateMessageFlux = Flux
                .just(DEFAULT_UPDATE_MESSAGE);

        var enforcedUpdateMessageFlux = of(query, decisions, updateMessageFlux, constraintHandlerService,
                resultResponseType, updateResponseType);
        StepVerifier.create(enforcedUpdateMessageFlux).expectSubscription().verifyTimeout(DEFAULT_TIMEOUT);
    }

    @Test
    void when_pep_singleIndeterminate_and_singleUpdate_then_noEvent() {
        var resultResponseType = ResponseTypes.instanceOf(TestInitialResponse.class);
        var updateResponseType = ResponseTypes.multipleInstancesOf(TestUpdateResponseType.class);
        var query              = new GenericSubscriptionQueryMessage<>(new TestQueryPayload(), resultResponseType,
                updateResponseType);
        var decisions          = Flux.just(new AuthorizationDecision(Decision.INDETERMINATE));

        Flux<SubscriptionQueryUpdateMessage<TestUpdateResponseType>> updateMessageFlux = Flux
                .just(DEFAULT_UPDATE_MESSAGE);

        var enforcedUpdateMessageFlux = of(query, decisions, updateMessageFlux, constraintHandlerService,
                resultResponseType, updateResponseType);
        StepVerifier.create(enforcedUpdateMessageFlux).expectSubscription().verifyTimeout(DEFAULT_TIMEOUT);
    }

    @Test
    void when_pep_singleNotApplicable_and_singleUpdate_then_noEvent() {
        var resultResponseType = ResponseTypes.instanceOf(TestInitialResponse.class);
        var updateResponseType = ResponseTypes.multipleInstancesOf(TestUpdateResponseType.class);
        var query              = new GenericSubscriptionQueryMessage<>(new TestQueryPayload(), resultResponseType,
                updateResponseType);
        var decisions          = Flux.just(new AuthorizationDecision(Decision.NOT_APPLICABLE));

        Flux<SubscriptionQueryUpdateMessage<TestUpdateResponseType>> updateMessageFlux = Flux
                .just(DEFAULT_UPDATE_MESSAGE);

        var enforcedUpdateMessageFlux = of(query, decisions, updateMessageFlux, constraintHandlerService,
                resultResponseType, updateResponseType);
        StepVerifier.create(enforcedUpdateMessageFlux).expectSubscription().verifyTimeout(DEFAULT_TIMEOUT);
    }

    @Test
    void when_pep_singlePermit_and_singleUpdate_then_permit() {
        var resultResponseType = ResponseTypes.instanceOf(TestInitialResponse.class);
        var updateResponseType = ResponseTypes.multipleInstancesOf(TestUpdateResponseType.class);
        var query              = new GenericSubscriptionQueryMessage<>(new TestQueryPayload(), resultResponseType,
                updateResponseType);
        var decisions          = Flux.just(new AuthorizationDecision(Decision.PERMIT));

        Flux<SubscriptionQueryUpdateMessage<TestUpdateResponseType>> updateMessageFlux = Flux
                .just(DEFAULT_UPDATE_MESSAGE);

        var enforcedUpdateMessageFlux = of(query, decisions, updateMessageFlux, constraintHandlerService,
                resultResponseType, updateResponseType);
        StepVerifier.create(enforcedUpdateMessageFlux).expectNext(DEFAULT_UPDATE_MESSAGE).verifyComplete();
    }

    @Test
    void when_pep_singleDeny_and_singleUpdate_and_ressource_then_noEvent() {
        var resultResponseType = ResponseTypes.instanceOf(TestInitialResponse.class);
        var updateResponseType = ResponseTypes.multipleInstancesOf(TestUpdateResponseType.class);
        var query              = new GenericSubscriptionQueryMessage<>(new TestQueryPayload(), resultResponseType,
                updateResponseType);
        var decisions          = Flux.just(new AuthorizationDecision(Decision.DENY, Optional.of(defaultResource),
                Optional.empty(), Optional.empty()));

        Flux<SubscriptionQueryUpdateMessage<TestUpdateResponseType>> updateMessageFlux = Flux
                .just(DEFAULT_UPDATE_MESSAGE);

        var enforcedUpdateMessageFlux = of(query, decisions, updateMessageFlux, constraintHandlerService,
                resultResponseType, updateResponseType);
        StepVerifier.create(enforcedUpdateMessageFlux).expectSubscription().verifyTimeout(DEFAULT_TIMEOUT);
    }

    @Test
    void when_pep_singleIndeterminate_and_singleUpdate_and_ressource_then_noEvent() {
        var resultResponseType = ResponseTypes.instanceOf(TestInitialResponse.class);
        var updateResponseType = ResponseTypes.multipleInstancesOf(TestUpdateResponseType.class);
        var query              = new GenericSubscriptionQueryMessage<>(new TestQueryPayload(), resultResponseType,
                updateResponseType);
        var decisions          = Flux.just(new AuthorizationDecision(Decision.INDETERMINATE,
                Optional.of(defaultResource), Optional.empty(), Optional.empty()));

        Flux<SubscriptionQueryUpdateMessage<TestUpdateResponseType>> updateMessageFlux = Flux
                .just(DEFAULT_UPDATE_MESSAGE);

        var enforcedUpdateMessageFlux = of(query, decisions, updateMessageFlux, constraintHandlerService,
                resultResponseType, updateResponseType);
        StepVerifier.create(enforcedUpdateMessageFlux).expectSubscription().verifyTimeout(DEFAULT_TIMEOUT);
    }

    @Test
    void when_pep_singleNotApplicable_and_singleUpdate_and_ressource_then_noEvent() {
        var resultResponseType = ResponseTypes.instanceOf(TestInitialResponse.class);
        var updateResponseType = ResponseTypes.multipleInstancesOf(TestUpdateResponseType.class);
        var query              = new GenericSubscriptionQueryMessage<>(new TestQueryPayload(), resultResponseType,
                updateResponseType);
        var decisions          = Flux.just(new AuthorizationDecision(Decision.NOT_APPLICABLE,
                Optional.of(defaultResource), Optional.empty(), Optional.empty()));

        Flux<SubscriptionQueryUpdateMessage<TestUpdateResponseType>> updateMessageFlux = Flux
                .just(DEFAULT_UPDATE_MESSAGE);

        var enforcedUpdateMessageFlux = of(query, decisions, updateMessageFlux, constraintHandlerService,
                resultResponseType, updateResponseType);
        StepVerifier.create(enforcedUpdateMessageFlux).expectSubscription().verifyTimeout(DEFAULT_TIMEOUT);
    }

    @Test
    void when_pep_singlePermit_and_singleUpdate_and_ressource_then_permit() {
        var resultResponseType = ResponseTypes.instanceOf(TestInitialResponse.class);
        var updateResponseType = ResponseTypes.instanceOf(TestUpdateResponseType.class);
        var query              = new GenericSubscriptionQueryMessage<>(new TestQueryPayload(), resultResponseType,
                updateResponseType);
        var decisions          = Flux.just(new AuthorizationDecision(Decision.PERMIT, Optional.of(defaultResource),
                Optional.empty(), Optional.empty()));

        Flux<SubscriptionQueryUpdateMessage<TestUpdateResponseType>> updateMessageFlux = Flux
                .just(DEFAULT_UPDATE_MESSAGE);

        var enforcedUpdateMessageFlux = of(query, decisions, updateMessageFlux, constraintHandlerService,
                resultResponseType, updateResponseType);
        StepVerifier.create(enforcedUpdateMessageFlux)
                .expectNextMatches(matchesIgnoringIdentifier(DEFAULT_UPDATE_MESSAGE)).verifyComplete();
    }

    @Test
    void when_pep_permitThenDeny_and_singleUpdate_then_permit() {
        var resultResponseType = ResponseTypes.instanceOf(TestInitialResponse.class);
        var updateResponseType = ResponseTypes.multipleInstancesOf(TestUpdateResponseType.class);
        var query              = new GenericSubscriptionQueryMessage<>(new TestQueryPayload(), resultResponseType,
                updateResponseType);
        var decisions          = Flux.just(new AuthorizationDecision(Decision.PERMIT),
                new AuthorizationDecision(Decision.DENY));

        Flux<SubscriptionQueryUpdateMessage<TestUpdateResponseType>> updateMessageFlux = Flux
                .just(DEFAULT_UPDATE_MESSAGE);

        var enforcedUpdateMessageFlux = of(query, decisions, updateMessageFlux, constraintHandlerService,
                resultResponseType, updateResponseType);
        StepVerifier.create(enforcedUpdateMessageFlux)
                .expectNextMatches(matchesIgnoringIdentifier(DEFAULT_UPDATE_MESSAGE)).verifyComplete();
    }

    @Test
    void when_pep_denyThenPermit_and_singleUpdate_then_noEvent() {
        var resultResponseType = ResponseTypes.instanceOf(TestInitialResponse.class);
        var updateResponseType = ResponseTypes.multipleInstancesOf(TestUpdateResponseType.class);
        var query              = new GenericSubscriptionQueryMessage<>(new TestQueryPayload(), resultResponseType,
                updateResponseType);
        var decisions          = Flux.just(new AuthorizationDecision(Decision.DENY),
                new AuthorizationDecision(Decision.PERMIT));

        Flux<SubscriptionQueryUpdateMessage<TestUpdateResponseType>> updateMessageFlux = Flux
                .just(DEFAULT_UPDATE_MESSAGE);

        var enforcedUpdateMessageFlux = of(query, decisions, updateMessageFlux, constraintHandlerService,
                resultResponseType, updateResponseType);
        StepVerifier.create(enforcedUpdateMessageFlux).expectSubscription().verifyTimeout(DEFAULT_TIMEOUT);
    }

    @Test
    void when_pep_multiplePermit_and_multipleUpdates_then_permitAll() {
        var resultResponseType = ResponseTypes.instanceOf(TestInitialResponse.class);
        var updateResponseType = ResponseTypes.multipleInstancesOf(TestUpdateResponseType.class);
        var query              = new GenericSubscriptionQueryMessage<>(new TestQueryPayload(), resultResponseType,
                updateResponseType);
        var decisions          = Flux.concat(
                Mono.delay(DEFAULT_TIMESTEP.multipliedBy(0)).thenReturn(new AuthorizationDecision(Decision.PERMIT)),
                Mono.delay(DEFAULT_TIMESTEP.multipliedBy(1)).thenReturn(new AuthorizationDecision(Decision.PERMIT)));

        Flux<SubscriptionQueryUpdateMessage<TestUpdateResponseType>> updateMessageFlux = Flux.concat(
                Mono.delay(DEFAULT_TIMESTEP.multipliedBy(0)).thenReturn(DEFAULT_UPDATE_MESSAGE),
                Mono.delay(DEFAULT_TIMESTEP.multipliedBy(2)).thenReturn(DEFAULT_UPDATE_MESSAGE));

        var enforcedUpdateMessageFlux = of(query, decisions, updateMessageFlux, constraintHandlerService,
                resultResponseType, updateResponseType);
        StepVerifier.create(enforcedUpdateMessageFlux)
                .expectNextMatches(matchesIgnoringIdentifier(DEFAULT_UPDATE_MESSAGE))
                .expectNextMatches(matchesIgnoringIdentifier(DEFAULT_UPDATE_MESSAGE)).verifyComplete();
    }

    @Test
    void when_pep_permitThenDeny_and_multipleUpdates_then_permitThenNoEvent() {
        var                                                          resultResponseType = ResponseTypes
                .instanceOf(TestInitialResponse.class);
        var                                                          updateResponseType = ResponseTypes
                .multipleInstancesOf(TestUpdateResponseType.class);
        var                                                          query              = new GenericSubscriptionQueryMessage<>(
                new TestQueryPayload(), resultResponseType, updateResponseType);
        var                                                          decisions          = Flux.concat(
                Mono.delay(DEFAULT_TIMESTEP.multipliedBy(0)).thenReturn(new AuthorizationDecision(Decision.PERMIT)),
                Mono.delay(DEFAULT_TIMESTEP.multipliedBy(1)).thenReturn(new AuthorizationDecision(Decision.DENY)));
        Flux<SubscriptionQueryUpdateMessage<TestUpdateResponseType>> updateMessageFlux  = Flux.concat(
                Mono.delay(DEFAULT_TIMESTEP.multipliedBy(0)).thenReturn(DEFAULT_UPDATE_MESSAGE),
                Mono.delay(DEFAULT_TIMESTEP.multipliedBy(2)).thenReturn(DEFAULT_UPDATE_MESSAGE));

        var enforcedUpdateMessageFlux = of(query, decisions, updateMessageFlux, constraintHandlerService,
                resultResponseType, updateResponseType);
        StepVerifier.create(enforcedUpdateMessageFlux)
                .expectNextMatches(matchesIgnoringIdentifier(DEFAULT_UPDATE_MESSAGE)).expectNoEvent(DEFAULT_TIMESTEP)
                .verifyComplete();
    }

    @Test
    void when_pep_denyThenPermit_and_multipleUpdates_then_noEvent() {
        var                                                          resultResponseType = ResponseTypes
                .instanceOf(TestInitialResponse.class);
        var                                                          updateResponseType = ResponseTypes
                .multipleInstancesOf(TestUpdateResponseType.class);
        var                                                          query              = new GenericSubscriptionQueryMessage<>(
                new TestQueryPayload(), resultResponseType, updateResponseType);
        var                                                          decisions          = Flux.concat(
                Mono.delay(DEFAULT_TIMESTEP.multipliedBy(0)).thenReturn(new AuthorizationDecision(Decision.DENY)),
                Mono.delay(DEFAULT_TIMESTEP.multipliedBy(1)).thenReturn(new AuthorizationDecision(Decision.PERMIT)));
        Flux<SubscriptionQueryUpdateMessage<TestUpdateResponseType>> updateMessageFlux  = Flux.concat(
                Mono.delay(DEFAULT_TIMESTEP.multipliedBy(0)).thenReturn(DEFAULT_UPDATE_MESSAGE),
                Mono.delay(DEFAULT_TIMESTEP.multipliedBy(2)).thenReturn(DEFAULT_UPDATE_MESSAGE));

        var enforcedUpdateMessageFlux = of(query, decisions, updateMessageFlux, constraintHandlerService,
                resultResponseType, updateResponseType);
        StepVerifier.create(enforcedUpdateMessageFlux).expectSubscription().verifyTimeout(DEFAULT_TIMEOUT);
    }

    @Test
    void when_pep_permitThenDenyThenPermit_and_multipleUpdates_then_permitThenNoEventThenPermit() {
        var                                                          resultResponseType = ResponseTypes
                .instanceOf(TestInitialResponse.class);
        var                                                          updateResponseType = ResponseTypes
                .multipleInstancesOf(TestUpdateResponseType.class);
        var                                                          query              = new GenericSubscriptionQueryMessage<>(
                new TestQueryPayload(), resultResponseType, updateResponseType);
        var                                                          decisions          = Flux.concat(
                Mono.delay(DEFAULT_TIMESTEP.multipliedBy(0)).thenReturn(new AuthorizationDecision(Decision.PERMIT)),
                Mono.delay(DEFAULT_TIMESTEP.multipliedBy(1)).thenReturn(new AuthorizationDecision(Decision.DENY)),
                Mono.delay(DEFAULT_TIMESTEP.multipliedBy(2)).thenReturn(new AuthorizationDecision(Decision.PERMIT)));
        Flux<SubscriptionQueryUpdateMessage<TestUpdateResponseType>> updateMessageFlux  = Flux.concat(
                Mono.delay(DEFAULT_TIMESTEP.multipliedBy(0)).thenReturn(DEFAULT_UPDATE_MESSAGE),
                Mono.delay(DEFAULT_TIMESTEP.multipliedBy(2)).thenReturn(DEFAULT_UPDATE_MESSAGE),
                Mono.delay(DEFAULT_TIMESTEP.multipliedBy(2)).thenReturn(DEFAULT_UPDATE_MESSAGE));

        var enforcedUpdateMessageFlux = of(query, decisions, updateMessageFlux, constraintHandlerService,
                resultResponseType, updateResponseType);
        StepVerifier.create(enforcedUpdateMessageFlux)
                .expectNextMatches(matchesIgnoringIdentifier(DEFAULT_UPDATE_MESSAGE)).expectNoEvent(DEFAULT_TIMESTEP)
                .expectNextMatches(matchesIgnoringIdentifier(DEFAULT_UPDATE_MESSAGE)).verifyComplete();
    }

    @Test
    @SuppressWarnings("unchecked")
    void when_pep_accessDeniedOnBuildQueryPreHandlerBundle_then_accessDenied() {
        var                                                          resultResponseType = ResponseTypes
                .instanceOf(TestInitialResponse.class);
        var                                                          updateResponseType = ResponseTypes
                .multipleInstancesOf(TestUpdateResponseType.class);
        var                                                          query              = new GenericSubscriptionQueryMessage<>(
                new TestQueryPayload(), resultResponseType, updateResponseType);
        var                                                          decisions          = Flux
                .just(new AuthorizationDecision(Decision.PERMIT));
        Flux<SubscriptionQueryUpdateMessage<TestUpdateResponseType>> updateMessageFlux  = Flux
                .just(DEFAULT_UPDATE_MESSAGE);

        var constraintHandlerService = mock(ConstraintHandlerService.class);
        when(constraintHandlerService.buildQueryPreHandlerBundle(any(AuthorizationDecision.class),
                any(ResponseType.class), any(Optional.class))).thenThrow(AccessDeniedException.class);

        var enforcedUpdateMessageFlux = of(query, decisions, updateMessageFlux, constraintHandlerService,
                resultResponseType, updateResponseType);
        StepVerifier.create(enforcedUpdateMessageFlux).expectError(AccessDeniedException.class).verify(DEFAULT_TIMEOUT);
    }

    @Test
    @SuppressWarnings("unchecked")
    void when_pep_accessDeniedOnExecuteOnDecisionHandlers_then_noEvent() {
        var                                                          resultResponseType = ResponseTypes
                .instanceOf(TestInitialResponse.class);
        var                                                          updateResponseType = ResponseTypes
                .multipleInstancesOf(TestUpdateResponseType.class);
        var                                                          query              = new GenericSubscriptionQueryMessage<>(
                new TestQueryPayload(), resultResponseType, updateResponseType);
        var                                                          decisions          = Flux
                .just(new AuthorizationDecision(Decision.PERMIT));
        Flux<SubscriptionQueryUpdateMessage<TestUpdateResponseType>> updateMessageFlux  = Flux
                .just(DEFAULT_UPDATE_MESSAGE);

        var queryConstraintHandlerBundle = mock(QueryConstraintHandlerBundle.class);
        setField(queryConstraintHandlerBundle, ERROR_MAPPERS_FILED_NAME, Function.<Throwable>identity());
        doThrow(AccessDeniedException.class).when(queryConstraintHandlerBundle)
                .executeOnDecisionHandlers(any(AuthorizationDecision.class), any(Message.class));
        when(queryConstraintHandlerBundle.executeOnErrorHandlers(any(Throwable.class))).thenCallRealMethod();

        var constraintHandlerService = mock(ConstraintHandlerService.class);
        when(constraintHandlerService.buildQueryPreHandlerBundle(any(AuthorizationDecision.class),
                any(ResponseType.class), any(Optional.class))).thenReturn(queryConstraintHandlerBundle);

        var enforcedUpdateMessageFlux = of(query, decisions, updateMessageFlux, constraintHandlerService,
                resultResponseType, updateResponseType);
        StepVerifier.create(enforcedUpdateMessageFlux).expectSubscription().verifyTimeout(DEFAULT_TIMEOUT);
    }

    @Test
    @SuppressWarnings("unchecked")
    void when_pep_accessDeniedOnDeserializeResource_then_accessDenied() {
        var                                                          resultResponseType = ResponseTypes
                .instanceOf(TestInitialResponse.class);
        var                                                          updateResponseType = ResponseTypes
                .multipleInstancesOf(TestUpdateResponseType.class);
        var                                                          query              = new GenericSubscriptionQueryMessage<>(
                new TestQueryPayload(), resultResponseType, updateResponseType);
        var                                                          decisions          = Flux
                .just(new AuthorizationDecision(Decision.PERMIT, Optional.of(defaultResource), Optional.empty(),
                        Optional.empty()));
        Flux<SubscriptionQueryUpdateMessage<TestUpdateResponseType>> updateMessageFlux  = Flux
                .just(DEFAULT_UPDATE_MESSAGE);

        var constraintHandlerService = mock(ConstraintHandlerService.class);
        when(constraintHandlerService.buildQueryPreHandlerBundle(any(AuthorizationDecision.class),
                any(ResponseType.class), any(Optional.class))).thenCallRealMethod();
        when(constraintHandlerService.deserializeResource(any(JsonNode.class), any(ResponseType.class)))
                .thenThrow(AccessDeniedException.class);

        var enforcedUpdateMessageFlux = of(query, decisions, updateMessageFlux, constraintHandlerService,
                resultResponseType, updateResponseType);
        StepVerifier.create(enforcedUpdateMessageFlux).expectError(AccessDeniedException.class).verify(DEFAULT_TIMEOUT);
    }

    @Test
    @SuppressWarnings("unchecked")
    void when_pep_exceptionAtExecuteOnNextHandlers_then_complete() {
        var                                                          resultResponseType = ResponseTypes
                .instanceOf(TestInitialResponse.class);
        var                                                          updateResponseType = ResponseTypes
                .multipleInstancesOf(TestUpdateResponseType.class);
        var                                                          query              = new GenericSubscriptionQueryMessage<>(
                new TestQueryPayload(), resultResponseType, updateResponseType);
        var                                                          decisions          = Flux
                .just(new AuthorizationDecision(Decision.PERMIT));
        Flux<SubscriptionQueryUpdateMessage<TestUpdateResponseType>> updateMessageFlux  = Flux
                .just(DEFAULT_UPDATE_MESSAGE);

        var queryConstraintHandlerBundle = mock(QueryConstraintHandlerBundle.class);
        when(queryConstraintHandlerBundle.executeOnNextHandlers(any(ResultMessage.class)))
                .thenThrow(TestAccessDeniedException.class);
        when(queryConstraintHandlerBundle.executeOnErrorHandlers(any(Throwable.class))).thenCallRealMethod();

        var constraintHandlerService = mock(ConstraintHandlerService.class);
        when(constraintHandlerService.buildQueryPreHandlerBundle(any(AuthorizationDecision.class),
                any(ResponseType.class), any(Optional.class))).thenReturn(queryConstraintHandlerBundle);

        var enforcedUpdateMessageFlux = of(query, decisions, updateMessageFlux, constraintHandlerService,
                resultResponseType, updateResponseType);
        StepVerifier.create(enforcedUpdateMessageFlux).expectSubscription().verifyComplete();
    }

    @Test
    @SuppressWarnings("unchecked")
    void when_pep_exceptionAtExecuteOnErrorHandlers_then_exception() {
        var                                                          resultResponseType = ResponseTypes
                .instanceOf(TestInitialResponse.class);
        var                                                          updateResponseType = ResponseTypes
                .multipleInstancesOf(TestUpdateResponseType.class);
        var                                                          query              = new GenericSubscriptionQueryMessage<>(
                new TestQueryPayload(), resultResponseType, updateResponseType);
        Flux<AuthorizationDecision>                                  decisions          = Flux
                .just(new AuthorizationDecision(Decision.PERMIT));
        Flux<SubscriptionQueryUpdateMessage<TestUpdateResponseType>> updateMessageFlux  = Flux
                .error(new TestAccessDeniedException());

        var queryConstraintHandlerBundle = mock(QueryConstraintHandlerBundle.class);
        when(queryConstraintHandlerBundle.executeOnErrorHandlers(any(Throwable.class)))
                .thenThrow(TestAccessDeniedException.class);

        var constraintHandlerService = mock(ConstraintHandlerService.class);
        when(constraintHandlerService.buildQueryPreHandlerBundle(any(AuthorizationDecision.class),
                any(ResponseType.class), any(Optional.class))).thenReturn(queryConstraintHandlerBundle);

        var enforcedUpdateMessageFlux = of(query, decisions, updateMessageFlux, constraintHandlerService,
                resultResponseType, updateResponseType);
        StepVerifier.create(enforcedUpdateMessageFlux).expectError(TestAccessDeniedException.class)
                .verify(DEFAULT_TIMEOUT);
    }
}
