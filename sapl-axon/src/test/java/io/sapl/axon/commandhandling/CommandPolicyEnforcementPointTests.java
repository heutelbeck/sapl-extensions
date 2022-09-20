package io.sapl.axon.commandhandling;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.util.ReflectionTestUtils.setField;

import java.util.Optional;
import java.util.function.Function;

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.messaging.annotation.AnnotatedMessageHandlingMember;
import org.axonframework.messaging.annotation.DefaultParameterResolverFactory;
import org.axonframework.messaging.annotation.MessageHandlingMember;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.axonframework.messaging.annotation.WrappedMessageHandlingMember;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.TargetAggregateIdentifier;
import org.axonframework.spring.stereotype.Aggregate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.axon.annotation.PostHandleEnforce;
import io.sapl.axon.annotation.PreHandleEnforce;
import io.sapl.axon.constrainthandling.CommandConstraintHandlerBundle;
import io.sapl.axon.constrainthandling.ConstraintHandlerService;
import io.sapl.axon.subscription.AuthorizationSubscriptionBuilderService;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Value;
import reactor.core.publisher.Flux;

public class CommandPolicyEnforcementPointTests {

	private static final String MAPPER_FILED_NAME = "mapper";
	private static final String COMMAND_MAPPER_FILED_NAME = "commandMapper";
	private static final String RESULT_MAPPER_FILED_NAME = "resultMapper";
	private static final String DEFAULT_AGGREGATE_IDENTIFIER = "defaultIdentifier";
	private static final TestCommand DEFAULT_COMMAND = new TestCommand(DEFAULT_AGGREGATE_IDENTIFIER);
	private static final CommandMessage<?> DEFAULT_COMMAND_MESSAGE = new GenericCommandMessage<>(DEFAULT_COMMAND);
	private static final RuntimeException DEFAULT_HANDLED_EXCEPTION = new RuntimeException("default exception message");

	@Value
	private static class TestCommand {
		@TargetAggregateIdentifier
		String targetAggregateIdentifier;
		Object someField = "someText";
	}

	@Aggregate
	@EqualsAndHashCode
	@AllArgsConstructor
	private static class HandlingObject {

		@AggregateIdentifier
		String aggregateIdentifier;

		@CommandHandler
		@PreHandleEnforce
		public String handle1(TestCommand cmd) {
			return "defaultResult";
		}

		@CommandHandler
		public void handle2(TestCommand cmd) {
		}

		@CommandHandler
		@PostHandleEnforce
		public void handle3(TestCommand cmd) {
		}

		@CommandHandler
		@PreHandleEnforce
		public void handle4(TestCommand cmd) {
			throw new RuntimeException("some exception thrown in handler");
		}
	}

	private ParameterResolverFactory factory;

	private MessageHandlingMember<HandlingObject> delegate;
	private PolicyDecisionPoint pdp;
	private ConstraintHandlerService axonConstraintEnforcementService;
	private AuthorizationSubscriptionBuilderService subscriptionBuilder;
	private CommandConstraintHandlerBundle<HandlingObject> commandConstraintHandlerBundle;

	private HandlingObject handlingObject;
	private CommandPolicyEnforcementPoint<HandlingObject> commandPEP;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void beforeEach() throws NoSuchMethodException, SecurityException {
		var mapper = new ObjectMapper();
		factory = new DefaultParameterResolverFactory();
		var executable = HandlingObject.class.getDeclaredMethod("handle1", TestCommand.class);
		handlingObject = spy(new HandlingObject(DEFAULT_AGGREGATE_IDENTIFIER));
		delegate = spy(
				new AnnotatedMessageHandlingMember<>(executable, CommandMessage.class, TestCommand.class, factory));
		pdp = mock(PolicyDecisionPoint.class);
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just());
		commandConstraintHandlerBundle = mock(CommandConstraintHandlerBundle.class);
		axonConstraintEnforcementService = mock(ConstraintHandlerService.class);
		when(axonConstraintEnforcementService.buildPreEnforceCommandConstraintHandlerBundle(
				any(AuthorizationDecision.class), eq(handlingObject), any(Optional.class), eq(DEFAULT_COMMAND_MESSAGE)))
				.thenReturn(commandConstraintHandlerBundle);
		subscriptionBuilder = mock(AuthorizationSubscriptionBuilderService.class);
		when(subscriptionBuilder.constructAuthorizationSubscriptionForCommand(eq(DEFAULT_COMMAND_MESSAGE),
				eq(handlingObject), any(PreHandleEnforce.class))).thenCallRealMethod();
		setField(subscriptionBuilder, MAPPER_FILED_NAME, mapper);
		when(commandConstraintHandlerBundle.executeOnErrorHandlers(any(AccessDeniedException.class)))
				.thenReturn(DEFAULT_HANDLED_EXCEPTION);
		when(commandConstraintHandlerBundle.executeCommandMappingHandlers(eq(DEFAULT_COMMAND_MESSAGE)))
				.thenCallRealMethod();
		when(commandConstraintHandlerBundle.executePostHandlingHandlers(any()))
			.thenCallRealMethod();
		setField(commandConstraintHandlerBundle, COMMAND_MAPPER_FILED_NAME, Function.identity());
		setField(commandConstraintHandlerBundle, RESULT_MAPPER_FILED_NAME, Function.identity());
		commandPEP = spy(new CommandPolicyEnforcementPoint<>(delegate, pdp, axonConstraintEnforcementService,
				subscriptionBuilder));
	}

	@Test
	void when_noAnnotation_then_noEnforcement() throws NoSuchMethodException, SecurityException {
		var executable = HandlingObject.class.getDeclaredMethod("handle2", TestCommand.class);
		delegate = spy(
				new AnnotatedMessageHandlingMember<>(executable, CommandMessage.class, TestCommand.class, factory));
		commandPEP = spy(new CommandPolicyEnforcementPoint<>(delegate, pdp, axonConstraintEnforcementService,
				subscriptionBuilder));

		var result = assertDoesNotThrow(() -> commandPEP.handle(DEFAULT_COMMAND_MESSAGE, handlingObject));
		assertNull(result);
		assertDoesNotThrow(() -> verify((WrappedMessageHandlingMember<HandlingObject>) commandPEP, times(1))
				.handle(DEFAULT_COMMAND_MESSAGE, handlingObject));
	}

	@Test
	void when_wrongAnnotation_then_noEnforcement() throws NoSuchMethodException, SecurityException {
		var executable = HandlingObject.class.getDeclaredMethod("handle3", TestCommand.class);
		delegate = spy(
				new AnnotatedMessageHandlingMember<>(executable, CommandMessage.class, TestCommand.class, factory));
		commandPEP = spy(new CommandPolicyEnforcementPoint<>(delegate, pdp, axonConstraintEnforcementService,
				subscriptionBuilder));

		var result = assertDoesNotThrow(() -> commandPEP.handle(DEFAULT_COMMAND_MESSAGE, handlingObject));
		assertNull(result);
		assertDoesNotThrow(() -> verify((WrappedMessageHandlingMember<HandlingObject>) commandPEP, times(1))
				.handle(DEFAULT_COMMAND_MESSAGE, handlingObject));
	}

	@Test
	void when_pdpNoDecision_then_accessDenied() {
		assertThrows(AccessDeniedException.class, () -> commandPEP.handle(DEFAULT_COMMAND_MESSAGE, handlingObject));
	}

	@Test
	void when_permit_and_onDecisionHandlersThrow_then_handledException() {
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));
		doThrow(new RuntimeException("some error message")).when(commandConstraintHandlerBundle)
				.executeOnDecisionHandlers(any(AuthorizationDecision.class), eq(DEFAULT_COMMAND_MESSAGE));

		var exception = assertThrows(RuntimeException.class,
				() -> commandPEP.handle(DEFAULT_COMMAND_MESSAGE, handlingObject));
		assertEquals(DEFAULT_HANDLED_EXCEPTION.getLocalizedMessage(), exception.getLocalizedMessage());
	}

	@Test
	void when_deny_then_handledException() {
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.DENY));

		var exception = assertThrows(RuntimeException.class,
				() -> commandPEP.handle(DEFAULT_COMMAND_MESSAGE, handlingObject));
		assertEquals(DEFAULT_HANDLED_EXCEPTION.getLocalizedMessage(), exception.getLocalizedMessage());
	}

	@Test
	void when_permit_and_executeAggregateConstraintHandlerMethodsThrows_then_handledException() {
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));
		doThrow(new RuntimeException("some error message")).when(commandConstraintHandlerBundle)
				.executeAggregateConstraintHandlerMethods();

		var exception = assertThrows(RuntimeException.class,
				() -> commandPEP.handle(DEFAULT_COMMAND_MESSAGE, handlingObject));
		assertEquals(DEFAULT_HANDLED_EXCEPTION.getLocalizedMessage(), exception.getLocalizedMessage());
	}

	@Test
	void when_permit_and_executeCommandMappingHandlersThrows_then_handledException() {
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));
		when(commandConstraintHandlerBundle.executeCommandMappingHandlers(eq(DEFAULT_COMMAND_MESSAGE)))
				.thenThrow(new RuntimeException("some error message"));

		var exception = assertThrows(RuntimeException.class,
				() -> commandPEP.handle(DEFAULT_COMMAND_MESSAGE, handlingObject));
		assertEquals(DEFAULT_HANDLED_EXCEPTION.getLocalizedMessage(), exception.getLocalizedMessage());
	}

	@Test
	void when_permit_then_mapCommands() {
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));

		var result = assertDoesNotThrow(() -> commandPEP.handle(DEFAULT_COMMAND_MESSAGE, handlingObject));
		assertEquals("defaultResult", result);
		verify(commandConstraintHandlerBundle, times(1)).executeCommandMappingHandlers(eq(DEFAULT_COMMAND_MESSAGE));
	}

	@Test
	void when_permit_and_delegateHandleThrows_then_handledException() throws NoSuchMethodException, SecurityException {
		var executable = HandlingObject.class.getDeclaredMethod("handle4", TestCommand.class);
		delegate = spy(
				new AnnotatedMessageHandlingMember<>(executable, CommandMessage.class, TestCommand.class, factory));
		commandPEP = spy(new CommandPolicyEnforcementPoint<>(delegate, pdp, axonConstraintEnforcementService,
				subscriptionBuilder));

		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));
		when(commandConstraintHandlerBundle.executeOnErrorHandlers(any(RuntimeException.class)))
				.thenReturn(DEFAULT_HANDLED_EXCEPTION);

		var exception = assertThrows(RuntimeException.class,
				() -> commandPEP.handle(DEFAULT_COMMAND_MESSAGE, handlingObject));
		assertEquals(DEFAULT_HANDLED_EXCEPTION.getLocalizedMessage(), exception.getLocalizedMessage());
	}
	
	@Test
	void when_permit_and_executePostHandlingHandlersThrows_then_handledException() {
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));
		when(commandConstraintHandlerBundle.executePostHandlingHandlers(eq("defaultResult")))
				.thenThrow(new RuntimeException("some error message"));

		var exception = assertThrows(RuntimeException.class,
				() -> commandPEP.handle(DEFAULT_COMMAND_MESSAGE, handlingObject));
		assertEquals(DEFAULT_HANDLED_EXCEPTION.getLocalizedMessage(), exception.getLocalizedMessage());
	}
	
	@Test
	void when_permit_then_mapResult() {
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));

		var result = assertDoesNotThrow(() -> commandPEP.handle(DEFAULT_COMMAND_MESSAGE, handlingObject));
		assertEquals("defaultResult", result);
		verify(commandConstraintHandlerBundle, times(1)).executePostHandlingHandlers(eq("defaultResult"));
	}
}
