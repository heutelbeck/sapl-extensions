package io.sapl.axon.commandhandling;

import static io.sapl.axon.TestUtilities.isAccessDenied;
import static io.sapl.axon.TestUtilities.isCausedBy;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.function.BiConsumer;
import java.util.function.Function;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MetaData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
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

@SpringBootTest
@Import(ScenarioConfiguration.class)
public abstract class CommandTestsuite {

	private static final String MODIFY_ERROR     = "modify error";
	private static final String MODIFY_RESULT    = "modify result";
	private static final String MODIFIED_RESULT  = "this is a modified result";
	private static final String MODIFIED_COMMAND = "modifiedCommand";
	private static final String MODIFY_COMMAND   = "modifyCommand";
	private static final String ON_DECISION_DO   = "onDecisionDo";

	private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

	@MockBean
	PolicyDecisionPoint pdp;

	@Autowired
	CommandGateway commandGateway;

	@Autowired
	CommandBus commandBus;

	@Autowired
	CommandHandlingService commandService;

	@SpyBean
	OnDecisionProvider onDecisionProvider;

	@SpyBean
	CommandMappingProvider querMappingProvider;

	@SpyBean
	ResultMappingProvider resultMappingProvider;

	@SpyBean
	ErrorMappingProvider errorMappingProvider;

	@Test
	void when_securedCommandHandler_and_Permit_then_accessGranted() {
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));
		assertThat(commandGateway.sendAndWait(new CommandOne("foo")), is("OK (foo)"));
	}

	@Test
	void when_securedCommandHandler_and_Deny_then_accessDenied() {
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.DENY));
		var thrown = assertThrows(Exception.class, () -> commandGateway.sendAndWait(new CommandOne("foo")));
		assertTrue(isAccessDenied().test(thrown));
	}

	@Test
	void when_securedCommandHandler_and_PermitWithUnknownObligation_then_accessDenied() {
		var obligations = JSON.arrayNode();
		obligations.add(JSON.textNode("unknown"));
		when(pdp.decide(any(AuthorizationSubscription.class)))
				.thenReturn(Flux.just(AuthorizationDecision.PERMIT.withObligations(obligations)));
		var thrown = assertThrows(Exception.class, () -> commandGateway.sendAndWait(new CommandOne("foo")));
		assertTrue(isAccessDenied().test(thrown));
	}

	@Test
	void when_securedCommandHandler_and_PermitWithObligations_then_accessGrantedAndObligationsMet() {
		var obligations = JSON.arrayNode();
		obligations.add(JSON.textNode(MODIFY_RESULT));
		obligations.add(JSON.textNode(ON_DECISION_DO));
		obligations.add(JSON.textNode(MODIFY_COMMAND));
		when(pdp.decide(any(AuthorizationSubscription.class)))
				.thenReturn(Flux.just(AuthorizationDecision.PERMIT.withObligations(obligations)));
		assertThat(commandGateway.sendAndWait(new CommandOne("foo")), is(MODIFIED_RESULT));
		verify(resultMappingProvider, times(1)).map(any());
		verify(onDecisionProvider, times(1)).accept(any(), any());
		verify(querMappingProvider, times(1)).mapPayload(any(), any(), any());
	}

	@Test
	void when_securedCommandHandler_and_PermitWithErrorMapObligations_then_accessGrantedAndChangedError() {
		var obligations = JSON.arrayNode();
		obligations.add(JSON.textNode(MODIFY_ERROR));
		when(pdp.decide(any(AuthorizationSubscription.class)))
				.thenReturn(Flux.just(AuthorizationDecision.PERMIT.withObligations(obligations)));

		var thrown = assertThrows(Exception.class, () -> commandGateway.sendAndWait(new CommandTwo("foo")));
		assertTrue(isCausedBy(IllegalArgumentException.class).test(thrown));
		verify(errorMappingProvider, times(1)).map(any());
	}

	@Test
	void when_securedAggregateCreationCommand_and_Permit_then_accessGranted() {
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));
		assertThat(commandGateway.sendAndWait(new CreateAggregate("id2")), is("id2"));
	}

	@Test
	void when_securedAggregateCreationAndFollowUpCommand_and_Permit_then_accessGranted() {
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));
		assertThat(commandGateway.sendAndWait(new CreateAggregate("id1")), is("id1"));
		assertThat(commandGateway.sendAndWait(new ModifyAggregate("id1")), is(nullValue()));
	}

	@SuppressWarnings("unchecked")
	@Test
	void when_securedAggregateCreationAndFollowUpCommand_and_PermitWithObligation_then_accessGranted() {
		var decisionsForCreate = Flux.just(AuthorizationDecision.PERMIT);
		var obligations        = JSON.arrayNode();
		obligations.add(JSON.textNode("something"));
		var decisionsForModify = Flux.just(AuthorizationDecision.PERMIT.withObligations(obligations));
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(decisionsForCreate, decisionsForModify);
		assertThat(commandGateway.sendAndWait(new CreateAggregate("id4")), is("id4"));
		assertThat(commandGateway.sendAndWait(new ModifyAggregate("id4")), is(nullValue()));
	}

	@Test
	void when_securedCommandHandler_and_PermitWithObligation_then_accessGranted() {
		var obligations = JSON.arrayNode();
		obligations.add(JSON.textNode("serviceConstraint"));
		var decisions = Flux.just(AuthorizationDecision.PERMIT.withObligations(obligations));
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(decisions);
		assertThat(commandGateway.sendAndWait(new CommandOne("foo")), is("OK (foo)"));
	}

	@Test
	void when_securedCommandHandler_and_PermitWithObligationFailing_then_accessDenied() {
		var obligations = JSON.arrayNode();
		obligations.add(JSON.textNode("failConstraint"));
		var decisions = Flux.just(AuthorizationDecision.PERMIT.withObligations(obligations));
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(decisions);
		var thrown = assertThrows(Exception.class, () -> commandGateway.sendAndWait(new CommandOne("foo")));
		assertTrue(isAccessDenied().test(thrown));
		// nini!
		var logger = Logger.getLogger(getClass());
		logger.setLevel(Level.DEBUG);
		logger.log(Level.DEBUG, "PERMIT with obligations: " + obligations);
		logger.log(Level.DEBUG, "thrown: " + thrown);
		logger.setLevel(Level.OFF);
	}

	@Test
	void when_securedAggregateCreationCommand_and_Deny_then_accessDenies() {
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.DENY));
		var thrown = assertThrows(Exception.class, () -> commandGateway.sendAndWait(new CreateAggregate("id3")));
		assertTrue(isAccessDenied().test(thrown));
	}

	@SuppressWarnings("unchecked")
	@Test
	void when_securedAggregateCreationAndFollowUpCommandToEntity_and_Permit_then_accessGranted() {

		var decisionsForCreate = Flux.just(AuthorizationDecision.PERMIT);
		var obligations        = JSON.arrayNode();
		obligations.add(JSON.textNode("somethingWithMember"));
		var decisionsForMemberAccess = Flux.just(AuthorizationDecision.PERMIT.withObligations(obligations));
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(decisionsForCreate, decisionsForMemberAccess);
		assertThat(commandGateway.sendAndWait(new CreateAggregate("id7")), is("id7"));
		assertThat(commandGateway.sendAndWait(new UpdateMember("id7", "A")), is(nullValue()));
		assertThat(commandGateway.sendAndWait(new UpdateMember("id7", "B")), is(nullValue()));
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
	@Service
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

		@ConstraintHandler("#constraint.textValue() == 'serviceConstraint' && data == 'service data'")
		public void handleConstraint(CommandOne command, JsonNode constraint, AuthorizationDecision decision,
				CommandBus commandBus, MetaData metaData) {
			log.trace("ConstraintHandler invoked");
			log.trace("command: {}", command);
			log.trace("constraint: {}", constraint);
			log.trace("decision: {}", decision);
			log.trace("commandBus: {}", commandBus);
			log.trace("meta: {}", metaData);
		}

		@ConstraintHandler("#constraint.textValue() == 'failConstraint'")
		public void handleConstraint() {
			throw new IllegalStateException("ERROR");
		}

	}

	static class OnDecisionProvider implements OnDecisionConstraintHandlerProvider {

		@Override
		public boolean isResponsible(JsonNode constraint) {
			return constraint.isTextual() && ON_DECISION_DO.equals(constraint.textValue());
		}

		@Override
		public BiConsumer<AuthorizationDecision, Message<?>> getHandler(JsonNode constraint) {
			return this::accept;
		}

		public void accept(AuthorizationDecision decision, Message<?> message) {
			// NOOP
		}

	}

	static class CommandMappingProvider implements CommandConstraintHandlerProvider {

		@Override
		public boolean isResponsible(JsonNode constraint) {
			return constraint.isTextual() && MODIFY_COMMAND.equals(constraint.textValue());
		}

		@Override
		public Object mapPayload(Object payload, Class<?> clazz, JsonNode constraint) {
			if (payload instanceof CommandOne) {
				return new CommandOne(MODIFIED_COMMAND);
			}
			return payload;
		}

	}

	public static class ResultMappingProvider implements MappingConstraintHandlerProvider<String> {

		@Override
		public boolean isResponsible(JsonNode constraint) {
			return constraint.isTextual() && MODIFY_RESULT.equals(constraint.textValue());
		}

		@Override
		public Class<String> getSupportedType() {
			return String.class;
		}

		@Override
		public Function<String, String> getHandler(JsonNode constraint) {
			return this::map;
		}

		public String map(String original) {
			return MODIFIED_RESULT;
		}

	}

	public static class ErrorMappingProvider implements ErrorMappingConstraintHandlerProvider {

		@Override
		public boolean isResponsible(JsonNode constraint) {
			return constraint.isTextual() && MODIFY_ERROR.equals(constraint.textValue());
		}

		@Override
		public Function<Throwable, Throwable> getHandler(JsonNode constraint) {
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
		CommandHandlingService CommandHandlingService() {
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
