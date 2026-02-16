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
package io.sapl.vaadin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.vaadin.flow.component.UI;

import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.spring.constraints.api.ConsumerConstraintHandlerProvider;
import io.sapl.spring.constraints.api.RunnableConstraintHandlerProvider;
import io.sapl.vaadin.constraint.VaadinFunctionConstraintHandlerProvider;
import reactor.core.publisher.Mono;

@DisplayName("Vaadin constraint enforcement service")
class VaadinConstraintEnforcementServiceTests {

    List<VaadinFunctionConstraintHandlerProvider> globalVaadinFunctionProvider;
    List<ConsumerConstraintHandlerProvider<UI>>   globalConsumerProviders;
    List<RunnableConstraintHandlerProvider>       globalRunnableProviders;
    VaadinConstraintEnforcementService            sut;

    @BeforeEach
    void beforeEach() {
        globalVaadinFunctionProvider = new ArrayList<>();
        globalConsumerProviders      = new ArrayList<>();
        globalRunnableProviders      = new ArrayList<>();

        sut = new VaadinConstraintEnforcementService(globalVaadinFunctionProvider, globalConsumerProviders,
                globalRunnableProviders);
    }

    @Test
    void when_addGlobalRunnableProviders_then_GlobalRunnableProviderSizeIsOne() {
        // GIVEN
        var provider = mock(RunnableConstraintHandlerProvider.class);

        // WHEN
        sut.addGlobalRunnableProviders(provider);

        // THEN
        assertThat(globalRunnableProviders).hasSize(1);
    }

    @Test
    void when_addGlobalVaadinFunctionProvider_then_GlobalVaadinFunctionProviderSizeIsOne() {
        // GIVEN
        var provider = mock(VaadinFunctionConstraintHandlerProvider.class);

        // WHEN
        sut.addGlobalVaadinFunctionProvider(provider);

        // THEN
        assertThat(globalVaadinFunctionProvider).hasSize(1);
    }

    @Test
    void when_addGlobalConsumerProviders_then_GlobalConsumerProvidersSizeIsOne() {
        // GIVEN
        @SuppressWarnings("unchecked") // suppress mock
        ConsumerConstraintHandlerProvider<UI> provider = mock(ConsumerConstraintHandlerProvider.class);

        // WHEN
        sut.addGlobalConsumerProviders(provider);

        // THEN
        assertThat(globalConsumerProviders).hasSize(1);
    }

    @Test
    void when_enforceConstraintsOfDecisionWithEmptyDecisionAndNoProvider_then_DoNothingAndReturnMonoWithDecision() {
        // GIVEN
        var decision      = new AuthorizationDecision(Decision.PERMIT, Value.EMPTY_ARRAY, Value.EMPTY_ARRAY,
                Value.UNDEFINED);
        var uiMock        = mock(UI.class);
        var vaadinPepMock = mock(VaadinPep.class);

        // WHEN
        Mono<AuthorizationDecision> returnValue = sut.enforceConstraintsOfDecision(decision, uiMock, vaadinPepMock);

        // THEN
        assertThat(returnValue.block()).isEqualTo(decision);
    }

    @Test
    void when_enforceConstraintsOfDecisionWithObligationInDecisionAndNoHandler_then_ThrowReturnEmptyDecisionWithDeny() {
        // GIVEN
        var decision      = decisionWithObligation();
        var uiMock        = mock(UI.class);
        var vaadinPepMock = mock(VaadinPep.class);

        // WHEN
        Mono<AuthorizationDecision> returnValue = sut.enforceConstraintsOfDecision(decision, uiMock, vaadinPepMock);

        // THEN
        assertThat(returnValue.block().decision()).isEqualTo(Decision.DENY);
    }

    @Test
    void when_enforceConstraintsOfDecisionWithObligationInDecisionAndRunnableHandler_then_runnableIsCalledAndDecisionIsReturned() {
        // GIVEN
        var decision                              = decisionWithObligation();
        var uiMock                                = mock(UI.class);
        var vaadinPepMock                         = mock(VaadinPep.class);
        var runnableConstraintHandlerProviderMock = mock(RunnableConstraintHandlerProvider.class);
        var runnableMock                          = mock(Runnable.class);
        when(runnableConstraintHandlerProviderMock.getHandler(any())).thenReturn(runnableMock);
        when(runnableConstraintHandlerProviderMock.isResponsible(any())).thenReturn(true);
        sut.addGlobalRunnableProviders(runnableConstraintHandlerProviderMock);

        // WHEN
        Mono<AuthorizationDecision> returnValue = sut.enforceConstraintsOfDecision(decision, uiMock, vaadinPepMock);

        // THEN
        verify(runnableMock, times(1)).run();
        assertThat(returnValue.block()).isEqualTo(decision);
    }

    @Test
    void when_enforceConstraintsOfDecisionWithObligationInDecisionAndNullRunnableHandler_then_throwAccessDeniedAndReturnDenied() {
        // GIVEN
        var decision                              = decisionWithObligation();
        var uiMock                                = mock(UI.class);
        var vaadinPepMock                         = mock(VaadinPep.class);
        var runnableConstraintHandlerProviderMock = mock(RunnableConstraintHandlerProvider.class);
        when(runnableConstraintHandlerProviderMock.getHandler(any())).thenReturn(null);
        when(runnableConstraintHandlerProviderMock.isResponsible(any())).thenReturn(true);
        sut.addGlobalRunnableProviders(runnableConstraintHandlerProviderMock);

        // WHEN
        Mono<AuthorizationDecision> returnValue = sut.enforceConstraintsOfDecision(decision, uiMock, vaadinPepMock);

        // THEN
        assertThat(returnValue.block().decision()).isEqualTo(Decision.DENY);
    }

    @Test
    void when_enforceConstraintsOfDecisionWithAdviceInDecisionAndNullRunnableHandler_then_resumeWorkflowAndReturnAuthroizationDecision() {
        // GIVEN
        var decision                              = decisionWithAdvice();
        var uiMock                                = mock(UI.class);
        var vaadinPepMock                         = mock(VaadinPep.class);
        var runnableConstraintHandlerProviderMock = mock(RunnableConstraintHandlerProvider.class);
        when(runnableConstraintHandlerProviderMock.getHandler(any())).thenReturn(null);
        when(runnableConstraintHandlerProviderMock.isResponsible(any())).thenReturn(true);
        sut.addGlobalRunnableProviders(runnableConstraintHandlerProviderMock);

        // WHEN
        Mono<AuthorizationDecision> returnValue = sut.enforceConstraintsOfDecision(decision, uiMock, vaadinPepMock);

        // THEN
        assertThat(returnValue.block()).isEqualTo(decision);
    }

    @Test
    void when_enforceConstraintsOfDecisionWithObligationInDecisionAndNullVaadinConstraintHandler_then_throwAccessDeniedAndReturnDenied() {
        // GIVEN
        var decision                                    = decisionWithObligation();
        var uiMock                                      = mock(UI.class);
        var vaadinPepMock                               = mock(VaadinPep.class);
        var vaadinFunctionConstraintHandlerProviderMock = mock(VaadinFunctionConstraintHandlerProvider.class);
        when(vaadinFunctionConstraintHandlerProviderMock.getHandler(any())).thenReturn(null);
        when(vaadinFunctionConstraintHandlerProviderMock.isResponsible(any())).thenReturn(true);
        sut.addGlobalVaadinFunctionProvider(vaadinFunctionConstraintHandlerProviderMock);

        // WHEN
        Mono<AuthorizationDecision> returnValue = sut.enforceConstraintsOfDecision(decision, uiMock, vaadinPepMock);

        // THEN
        assertThat(returnValue.block().decision()).isEqualTo(Decision.DENY);
    }

    @Test
    void when_enforceConstraintsOfDecisionWithAdviceInDecisionAndNullVaadinConstraintHandler_then_resumeWorkflowAndReturnAuthorizationDecision() {
        // GIVEN
        var                         decision                                    = decisionWithAdvice();
        var                         uiMock                                      = mock(UI.class);
        var                         vaadinPepMock                               = mock(VaadinPep.class);
        var                         vaadinFunctionConstraintHandlerProviderMock = mock(
                VaadinFunctionConstraintHandlerProvider.class);
        @SuppressWarnings("unchecked") // suppress mock
        Function<UI, Mono<Boolean>> functionMock                                = mock(Function.class);
        Mono<Boolean>               monoMock                                    = Mono.just(true);
        when(functionMock.apply(any())).thenReturn(monoMock);
        when(vaadinFunctionConstraintHandlerProviderMock.getHandler(any())).thenReturn(functionMock);
        when(vaadinFunctionConstraintHandlerProviderMock.isResponsible(any())).thenReturn(true);
        sut.addGlobalVaadinFunctionProvider(vaadinFunctionConstraintHandlerProviderMock);

        // WHEN
        Mono<AuthorizationDecision> returnValue = sut.enforceConstraintsOfDecision(decision, uiMock, vaadinPepMock);

        // THEN
        assertThat(returnValue.block()).isEqualTo(decision);
    }

    @Test
    void when_enforceConstraintsOfDecisionWithAdviceInDecisionAndNullVaadinConstraintHandler_then_resumeWorkflowAndDenyAuthroizationDecision() {
        // GIVEN
        var decision                                    = decisionWithAdvice();
        var uiMock                                      = mock(UI.class);
        var vaadinPepMock                               = mock(VaadinPep.class);
        var vaadinFunctionConstraintHandlerProviderMock = mock(VaadinFunctionConstraintHandlerProvider.class);
        when(vaadinFunctionConstraintHandlerProviderMock.getHandler(any())).thenReturn(null);
        when(vaadinFunctionConstraintHandlerProviderMock.isResponsible(any())).thenReturn(true);
        sut.addGlobalVaadinFunctionProvider(vaadinFunctionConstraintHandlerProviderMock);

        // WHEN
        Mono<AuthorizationDecision> returnValue = sut.enforceConstraintsOfDecision(decision, uiMock, vaadinPepMock);

        // THEN
        assertThat(returnValue.block().decision()).isEqualTo(Decision.DENY);
    }

    @Test
    @SuppressWarnings("unchecked") // suppress mocks
    void when_enforceConstraintsOfDecisionWithObligationInDecisionAndConsumerConstraintHandler_then_runnableIsCalledAndDecisionIsReturned() {
        // GIVEN
        var                                   decision                              = decisionWithObligation();
        var                                   uiMock                                = mock(UI.class);
        var                                   vaadinPepMock                         = mock(VaadinPep.class);
        ConsumerConstraintHandlerProvider<UI> consumerConstraintHandlerProviderMock = mock(
                ConsumerConstraintHandlerProvider.class);
        Consumer<UI>                          consumerMock                          = mock(Consumer.class);
        when(consumerConstraintHandlerProviderMock.getHandler(any())).thenReturn(consumerMock);
        when(consumerConstraintHandlerProviderMock.isResponsible(any())).thenReturn(true);
        sut.addGlobalConsumerProviders(consumerConstraintHandlerProviderMock);

        // WHEN
        Mono<AuthorizationDecision> returnValue = sut.enforceConstraintsOfDecision(decision, uiMock, vaadinPepMock);

        // THEN
        verify(consumerMock, times(1)).accept(any());
        assertThat(returnValue.block()).isEqualTo(decision);
    }

    @Test
    void when_enforceConstraintsOfDecisionWithObligationInDecisionAndNullConsumerConstraintHandler_then_throwAccessDeniedAndReturnDenied() {
        // GIVEN
        var                                   decision                              = decisionWithObligation();
        var                                   uiMock                                = mock(UI.class);
        var                                   vaadinPepMock                         = mock(VaadinPep.class);
        @SuppressWarnings("unchecked") // suppress mock
        ConsumerConstraintHandlerProvider<UI> consumerConstraintHandlerProviderMock = mock(
                ConsumerConstraintHandlerProvider.class);
        when(consumerConstraintHandlerProviderMock.getHandler(any())).thenReturn(null);
        when(consumerConstraintHandlerProviderMock.isResponsible(any())).thenReturn(true);
        sut.addGlobalConsumerProviders(consumerConstraintHandlerProviderMock);

        // WHEN
        Mono<AuthorizationDecision> returnValue = sut.enforceConstraintsOfDecision(decision, uiMock, vaadinPepMock);

        // THEN
        assertThat(returnValue.block().decision()).isEqualTo(Decision.DENY);
    }

    @Test
    void when_enforceConstraintsOfDecisionWithAdviceInDecisionAndNullConsumerConstraintHandler_then_resumeWorkflowAndReturnAuthroizationDecision() {
        // GIVEN
        var                                   decision                              = decisionWithAdvice();
        var                                   uiMock                                = mock(UI.class);
        var                                   vaadinPepMock                         = mock(VaadinPep.class);
        @SuppressWarnings("unchecked") // suppress mock
        ConsumerConstraintHandlerProvider<UI> consumerConstraintHandlerProviderMock = mock(
                ConsumerConstraintHandlerProvider.class);
        when(consumerConstraintHandlerProviderMock.getHandler(any())).thenReturn(null);
        when(consumerConstraintHandlerProviderMock.isResponsible(any())).thenReturn(true);
        sut.addGlobalConsumerProviders(consumerConstraintHandlerProviderMock);

        // WHEN
        Mono<AuthorizationDecision> returnValue = sut.enforceConstraintsOfDecision(decision, uiMock, vaadinPepMock);

        // THEN
        assertThat(returnValue.block()).isEqualTo(decision);
    }

    @Test
    void when_enforceConstraintsOfDecisionWithAllProvidersButEmptyDecision_then_DoNothingAndReturnMonoWithDecision() {
        // GIVEN
        var decision                              = new AuthorizationDecision(Decision.PERMIT, Value.EMPTY_ARRAY,
                Value.EMPTY_ARRAY, Value.UNDEFINED);
        var uiMock                                = mock(UI.class);
        var vaadinPepMock                         = mock(VaadinPep.class);
        var runnableConstraintHandlerProviderMock = mock(RunnableConstraintHandlerProvider.class);
        sut.addGlobalRunnableProviders(runnableConstraintHandlerProviderMock);
        var vaadinFunctionConstraintHandlerProviderMock = mock(VaadinFunctionConstraintHandlerProvider.class);
        sut.addGlobalVaadinFunctionProvider(vaadinFunctionConstraintHandlerProviderMock);
        @SuppressWarnings("unchecked") // suppress mock
        ConsumerConstraintHandlerProvider<UI> consumerConstraintHandlerProviderMock = mock(
                ConsumerConstraintHandlerProvider.class);
        sut.addGlobalConsumerProviders(consumerConstraintHandlerProviderMock);

        // WHEN
        Mono<AuthorizationDecision> returnValue = sut.enforceConstraintsOfDecision(decision, uiMock, vaadinPepMock);

        // THEN
        assertThat(returnValue.block()).isEqualTo(decision);
    }

    private AuthorizationDecision decisionWithObligation() {
        var obligations = Value.ofArray(Value.of("obligation"));
        return new AuthorizationDecision(Decision.PERMIT, obligations, Value.EMPTY_ARRAY, Value.UNDEFINED);
    }

    private AuthorizationDecision decisionWithAdvice() {
        var advice = Value.ofArray(Value.of("advice"));
        return new AuthorizationDecision(Decision.PERMIT, Value.EMPTY_ARRAY, advice, Value.UNDEFINED);
    }

}
