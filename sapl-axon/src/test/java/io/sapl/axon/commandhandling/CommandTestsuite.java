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
package io.sapl.axon.commandhandling;

import static io.sapl.axon.TestUtilities.isAccessDenied;
import static io.sapl.axon.TestUtilities.isCausedBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.function.BiConsumer;
import java.util.function.UnaryOperator;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MetaData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import io.sapl.api.model.TextValue;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.axon.annotation.ConstraintHandler;
import io.sapl.axon.annotation.PreHandleEnforce;
import io.sapl.axon.commandhandling.CommandTestsuite.ScenarioConfiguration;
import io.sapl.axon.commandhandling.model.TestAggregateAPI.CreateAggregate;
import io.sapl.axon.commandhandling.model.TestAggregateAPI.ModifyAggregate;
import io.sapl.axon.commandhandling.model.TestAggregateAPI.UpdateMember;
import io.sapl.axon.configuration.SaplAutoConfiguration;
import io.sapl.axon.constrainthandling.api.CommandConstraintHandlerProvider;
import io.sapl.axon.constrainthandling.api.OnDecisionConstraintHandlerProvider;
import io.sapl.spring.constraints.api.ErrorMappingConstraintHandlerProvider;
import io.sapl.spring.constraints.api.MappingConstraintHandlerProvider;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@SpringBootTest
@Import(ScenarioConfiguration.class)
@DisplayName("Command handling test suite")
abstract class CommandTestsuite {

    private static final String MODIFY_ERROR     = "modify error";
    private static final String MODIFY_RESULT    = "modify result";
    private static final String MODIFIED_RESULT  = "this is a modified result";
    private static final String MODIFIED_COMMAND = "modifiedCommand";
    private static final String MODIFY_COMMAND   = "modifyCommand";
    private static final String ON_DECISION_DO   = "onDecisionDo";

    private static final long COMMAND_HANDLER_REGISTRATION_WAIT_TIME_MS = 750L;
    static boolean            isIntegrationTest                         = false;
    private static boolean    waitedForCommandHandlerRegistration       = false;

    @MockitoBean
    PolicyDecisionPoint pdp;

    @Autowired
    CommandGateway commandGateway;

    @Autowired
    CommandBus commandBus;

    @Autowired
    CommandHandlingService commandService;

    @MockitoSpyBean
    OnDecisionProvider onDecisionProvider;

    @MockitoSpyBean
    CommandMappingProvider querMappingProvider;

    @MockitoSpyBean
    ResultMappingProvider resultMappingProvider;

    @MockitoSpyBean
    ErrorMappingProvider errorMappingProvider;

    @Test
    void when_securedCommandHandler_and_Permit_then_accessGranted() {
        waitForCommandHandlerRegistration();
        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));
        assertThat((Object) commandGateway.sendAndWait(new CommandOne("foo"))).isEqualTo("OK (foo)");
    }

    @Test
    void when_securedCommandHandler_and_Deny_then_accessDenied() {
        waitForCommandHandlerRegistration();
        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.DENY));
        assertThatThrownBy(() -> commandGateway.sendAndWait(new CommandOne("foo")))
                .satisfies(thrown -> assertThat(isAccessDenied().test(thrown)).isTrue());
    }

    @Test
    void when_securedCommandHandler_and_PermitWithUnknownObligation_then_accessDenied() {
        waitForCommandHandlerRegistration();
        var obligations = io.sapl.api.model.Value.ofArray(io.sapl.api.model.Value.of("unknown"));
        when(pdp.decide(any(AuthorizationSubscription.class)))
                .thenReturn(Flux.just(new AuthorizationDecision(Decision.PERMIT, obligations,
                        io.sapl.api.model.Value.EMPTY_ARRAY, io.sapl.api.model.Value.UNDEFINED)));
        assertThatThrownBy(() -> commandGateway.sendAndWait(new CommandOne("foo")))
                .satisfies(thrown -> assertThat(isAccessDenied().test(thrown)).isTrue());
    }

    @Test
    void when_securedCommandHandler_and_PermitWithObligations_then_accessGrantedAndObligationsMet() {
        waitForCommandHandlerRegistration();
        var obligations = io.sapl.api.model.Value.ofArray(io.sapl.api.model.Value.of(MODIFY_RESULT),
                io.sapl.api.model.Value.of(ON_DECISION_DO), io.sapl.api.model.Value.of(MODIFY_COMMAND));
        when(pdp.decide(any(AuthorizationSubscription.class)))
                .thenReturn(Flux.just(new AuthorizationDecision(Decision.PERMIT, obligations,
                        io.sapl.api.model.Value.EMPTY_ARRAY, io.sapl.api.model.Value.UNDEFINED)));
        assertThat((Object) commandGateway.sendAndWait(new CommandOne("foo"))).isEqualTo(MODIFIED_RESULT);
        verify(resultMappingProvider, times(1)).map(any());
        verify(onDecisionProvider, times(1)).accept(any(), any());
        verify(querMappingProvider, times(1)).mapPayload(any(), any(), any());
    }

    @Test
    void when_securedCommandHandler_and_PermitWithErrorMapObligations_then_accessGrantedAndChangedError() {
        waitForCommandHandlerRegistration();
        var obligations = io.sapl.api.model.Value.ofArray(io.sapl.api.model.Value.of(MODIFY_ERROR));
        when(pdp.decide(any(AuthorizationSubscription.class)))
                .thenReturn(Flux.just(new AuthorizationDecision(Decision.PERMIT, obligations,
                        io.sapl.api.model.Value.EMPTY_ARRAY, io.sapl.api.model.Value.UNDEFINED)));

        assertThatThrownBy(() -> commandGateway.sendAndWait(new CommandTwo("foo")))
                .satisfies(thrown -> assertThat(isCausedBy(IllegalArgumentException.class).test(thrown)).isTrue());
        verify(errorMappingProvider, times(1)).map(any());
    }

    @Test
    void when_securedAggregateCreationCommand_and_Permit_then_accessGranted() {
        waitForCommandHandlerRegistration();
        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));
        assertThat((Object) commandGateway.sendAndWait(new CreateAggregate("id2"))).isEqualTo("id2");
    }

    @Test
    void when_securedAggregateCreationAndFollowUpCommand_and_Permit_then_accessGranted() {
        waitForCommandHandlerRegistration();
        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));
        assertThat((Object) commandGateway.sendAndWait(new CreateAggregate("id1"))).isEqualTo("id1");
        assertThat((Object) commandGateway.sendAndWait(new ModifyAggregate("id1"))).isNull();
    }

    @SuppressWarnings("unchecked")
    @Test
    void when_securedAggregateCreationAndFollowUpCommand_and_PermitWithObligation_then_accessGranted() {
        waitForCommandHandlerRegistration();
        var decisionsForCreate = Flux.just(AuthorizationDecision.PERMIT);
        var obligations        = io.sapl.api.model.Value.ofArray(io.sapl.api.model.Value.of("something"));
        var decisionsForModify = Flux.just(new AuthorizationDecision(Decision.PERMIT, obligations,
                io.sapl.api.model.Value.EMPTY_ARRAY, io.sapl.api.model.Value.UNDEFINED));
        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(decisionsForCreate, decisionsForModify);
        assertThat((Object) commandGateway.sendAndWait(new CreateAggregate("id4"))).isEqualTo("id4");
        assertThat((Object) commandGateway.sendAndWait(new ModifyAggregate("id4"))).isNull();
    }

    @Test
    void when_securedCommandHandler_and_PermitWithObligation_then_accessGranted() {
        waitForCommandHandlerRegistration();
        var obligations = io.sapl.api.model.Value.ofArray(io.sapl.api.model.Value.of("serviceConstraint"));
        var decisions   = Flux.just(new AuthorizationDecision(Decision.PERMIT, obligations,
                io.sapl.api.model.Value.EMPTY_ARRAY, io.sapl.api.model.Value.UNDEFINED));
        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(decisions);
        assertThat((Object) commandGateway.sendAndWait(new CommandOne("foo"))).isEqualTo("OK (foo)");
    }

    @Test
    void when_securedCommandHandler_and_PermitWithObligationFailing_then_accessDenied() {
        waitForCommandHandlerRegistration();
        var obligations = io.sapl.api.model.Value.ofArray(io.sapl.api.model.Value.of("failConstraint"));
        var decisions   = Flux.just(new AuthorizationDecision(Decision.PERMIT, obligations,
                io.sapl.api.model.Value.EMPTY_ARRAY, io.sapl.api.model.Value.UNDEFINED));
        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(decisions);
        assertThatThrownBy(() -> commandGateway.sendAndWait(new CommandOne("foo")))
                .satisfies(thrown -> assertThat(isAccessDenied().test(thrown)).isTrue());
    }

    @Test
    void when_securedAggregateCreationCommand_and_Deny_then_accessDenies() {
        waitForCommandHandlerRegistration();
        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.DENY));
        assertThatThrownBy(() -> commandGateway.sendAndWait(new CreateAggregate("id3")))
                .satisfies(thrown -> assertThat(isAccessDenied().test(thrown)).isTrue());
    }

    @SuppressWarnings("unchecked")
    @Test
    void when_securedAggregateCreationAndFollowUpCommandToEntity_and_Permit_then_accessGranted() {
        waitForCommandHandlerRegistration();
        var decisionsForCreate       = Flux.just(AuthorizationDecision.PERMIT);
        var obligations              = io.sapl.api.model.Value
                .ofArray(io.sapl.api.model.Value.of("somethingWithMember"));
        var decisionsForMemberAccess = Flux.just(new AuthorizationDecision(Decision.PERMIT, obligations,
                io.sapl.api.model.Value.EMPTY_ARRAY, io.sapl.api.model.Value.UNDEFINED));
        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(decisionsForCreate, decisionsForMemberAccess);
        assertThat((Object) commandGateway.sendAndWait(new CreateAggregate("id7"))).isEqualTo("id7");
        assertThat((Object) commandGateway.sendAndWait(new UpdateMember("id7", "A"))).isNull();
        assertThat((Object) commandGateway.sendAndWait(new UpdateMember("id7", "B"))).isNull();
    }

    static void waitForCommandHandlerRegistration() {
        if (!isIntegrationTest || waitedForCommandHandlerRegistration)
            return;
        log.info("Waiting for " + COMMAND_HANDLER_REGISTRATION_WAIT_TIME_MS / 1000f
                + "s for Axon Server to register command handlers.");
        Mono.delay(Duration.ofMillis(COMMAND_HANDLER_REGISTRATION_WAIT_TIME_MS)).block();
        log.info("Waited for " + COMMAND_HANDLER_REGISTRATION_WAIT_TIME_MS / 1000f
                + "s for Axon Server to register command handlers.");
        waitedForCommandHandlerRegistration = true;
    }

    @Value
    static class CommandOne {
        final String value;
    }

    @Value
    static class CommandTwo {
        final String value;
    }

    @Slf4j
    public static class CommandHandlingService {

        public String data = "service data";

        @CommandHandler
        @PreHandleEnforce
        public String handle(CommandOne command) {
            return "OK (" + command.getValue() + ")";
        }

        @CommandHandler
        @PreHandleEnforce
        public String handle(CommandTwo command) {
            throw new RuntimeException("I was a RuntimeException and now should be an IllegalArgumentException");
        }

        @ConstraintHandler("#constraint.value() == 'serviceConstraint' && data == 'service data'")
        public void handleConstraint(CommandOne command, io.sapl.api.model.Value constraint,
                AuthorizationDecision decision, CommandBus commandBus, MetaData metaData) {
            log.trace("ConstraintHandler invoked");
            log.trace("command: {}", command);
            log.trace("constraint: {}", constraint);
            log.trace("decision: {}", decision);
            log.trace("commandBus: {}", commandBus);
            log.trace("meta: {}", metaData);
        }

        @ConstraintHandler("#constraint.value() == 'failConstraint'")
        public void handleConstraint() {
            throw new IllegalStateException("ERROR");
        }

    }

    static class OnDecisionProvider implements OnDecisionConstraintHandlerProvider {

        @Override
        public boolean isResponsible(io.sapl.api.model.Value constraint) {
            return constraint instanceof TextValue(var text) && ON_DECISION_DO.equals(text);
        }

        @Override
        public BiConsumer<AuthorizationDecision, Message<?>> getHandler(io.sapl.api.model.Value constraint) {
            return this::accept;
        }

        public void accept(AuthorizationDecision decision, Message<?> message) {
            // NOOP
        }

    }

    static class CommandMappingProvider implements CommandConstraintHandlerProvider {

        @Override
        public boolean isResponsible(io.sapl.api.model.Value constraint) {
            return constraint instanceof TextValue(var text) && MODIFY_COMMAND.equals(text);
        }

        @Override
        public Object mapPayload(Object payload, Class<?> clazz, io.sapl.api.model.Value constraint) {
            if (payload instanceof CommandOne) {
                return new CommandOne(MODIFIED_COMMAND);
            }
            return payload;
        }

    }

    public static class ResultMappingProvider implements MappingConstraintHandlerProvider<String> {

        @Override
        public boolean isResponsible(io.sapl.api.model.Value constraint) {
            return constraint instanceof TextValue(var text) && MODIFY_RESULT.equals(text);
        }

        @Override
        public Class<String> getSupportedType() {
            return String.class;
        }

        @Override
        public UnaryOperator<String> getHandler(io.sapl.api.model.Value constraint) {
            return this::map;
        }

        public String map(String original) {
            return MODIFIED_RESULT;
        }

    }

    public static class ErrorMappingProvider implements ErrorMappingConstraintHandlerProvider {

        @Override
        public boolean isResponsible(io.sapl.api.model.Value constraint) {
            return constraint instanceof TextValue(var text) && MODIFY_ERROR.equals(text);
        }

        @Override
        public UnaryOperator<Throwable> getHandler(io.sapl.api.model.Value constraint) {
            return this::map;
        }

        public Throwable map(Throwable original) {
            return new IllegalArgumentException(original.getMessage(), original.getCause());
        }

    }

    @Configuration
    @Import({ SaplAutoConfiguration.class })
    static class ScenarioConfiguration {
        @Bean
        CommandHandlingService commandHandlingService() {
            return new CommandHandlingService();
        }

        @Bean
        OnDecisionProvider onDecisionProvider() {
            return new OnDecisionProvider();
        }

        @Bean
        CommandMappingProvider querMappingProvider() {
            return new CommandMappingProvider();
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
