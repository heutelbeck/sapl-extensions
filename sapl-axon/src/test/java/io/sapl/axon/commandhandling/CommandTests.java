package io.sapl.axon.commandhandling;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.function.Function;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.eventsourcing.eventstore.EmbeddedEventStore;
import org.axonframework.eventsourcing.eventstore.EventStorageEngine;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import org.axonframework.spring.config.AxonConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.axon.annotation.PreHandleEnforce;
import io.sapl.axon.commandhandling.model.TestAggregateAPI.CreateAggregate;
import io.sapl.axon.commandhandling.model.TestAggregateAPI.ModifyAggregate;
import io.sapl.axon.configuration.SaplAutoConfiguration;
import io.sapl.axon.constrainthandling.api.CommandConstraintHandlerProvider;
import io.sapl.axon.constrainthandling.api.OnDecisionConstraintHandlerProvider;
import io.sapl.spring.constraints.api.ErrorMappingConstraintHandlerProvider;
import io.sapl.spring.constraints.api.MappingConstraintHandlerProvider;
import lombok.Value;
import reactor.core.publisher.Flux;

@SpringBootTest
public class CommandTests {

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
		assertThrows(AccessDeniedException.class, () -> commandGateway.sendAndWait(new CommandOne("foo")));
	}

	@Test
	void when_securedCommandHandler_and_PermitWithUnknownObligation_then_accessDenied() {
		var obligations = JSON.arrayNode();
		obligations.add(JSON.textNode("unknown"));
		when(pdp.decide(any(AuthorizationSubscription.class)))
				.thenReturn(Flux.just(AuthorizationDecision.PERMIT.withObligations(obligations)));
		assertThrows(AccessDeniedException.class, () -> commandGateway.sendAndWait(new CommandOne("foo")));
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
		verify(onDecisionProvider, times(1)).run();
		verify(querMappingProvider, times(1)).mapPayload(any(), any());
	}
	
	@Test
	void when_securedCommandHandler_and_PermitWithErrorMapObligations_then_accessGrantedAndChangedError() {
		var obligations = JSON.arrayNode();
		obligations.add(JSON.textNode(MODIFY_ERROR));
		when(pdp.decide(any(AuthorizationSubscription.class)))
				.thenReturn(Flux.just(AuthorizationDecision.PERMIT.withObligations(obligations)));
		
		assertThrows(IllegalArgumentException.class, () -> commandGateway.sendAndWait(new CommandTwo("foo")));
		verify(errorMappingProvider, times(1)).map(any());		
	}

	@Test
	void when_securedAggregateCreationCommand_and_Permit_then_accessGranted() {
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));
		assertThat(commandGateway.sendAndWait(new CreateAggregate("id1")), is("id1"));
	}
	
	@Test
	void when_securedAggregateCreationAndFollowUpCommand_and_Permit_then_accessGranted() {
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));
		commandGateway.sendAndWait(new CreateAggregate("id2"));
		commandGateway.sendAndWait(new ModifyAggregate("id2"));
//		assertThat(commandGateway.sendAndWait(new CreateAggregate("id1")), is("id1"));
//		assertThat(commandGateway.sendAndWait(new ModifyAggregate("id1")), is("id1"));
	}

	@Test
	void when_securedAggregateCreationCommand_and_Deny_then_accessDenies() {
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.DENY));
		assertThrows(AccessDeniedException.class, () -> commandGateway.sendAndWait(new CreateAggregate("id1")));
	}

	@Value
	static class CommandOne {
		final String value;
	}

	@Value
	static class CommandTwo {
		final String value;
	}

	@Service
	public static class CommandHandlingService {

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
	}

	static class OnDecisionProvider implements OnDecisionConstraintHandlerProvider {

		@Override
		public boolean isResponsible(JsonNode constraint) {
			return constraint.isTextual() && ON_DECISION_DO.equals(constraint.textValue());
		}

		@Override
		public Runnable getHandler(JsonNode constraint) {
			return this::run;
		}

		public void run() {
			// NOOP
		}

	}

	static class CommandMappingProvider implements CommandConstraintHandlerProvider {

		@Override
		public boolean isResponsible(JsonNode constraint) {
			return constraint.isTextual() && MODIFY_COMMAND.equals(constraint.textValue());
		}

		@Override
		public Object mapPayload(Object payload, Class<?> clazz) {
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

	@SpringBootApplication(scanBasePackages = { "io.sapl.axon.commandhandling.*" })
	public static class TestApplication {

		public static void main(String[] args) {
			SpringApplication.run(TestApplication.class, args);
		}
	}

	@DynamicPropertySource
	static void registerAxonProperties(DynamicPropertyRegistry registry) {
		registry.add("axon.axonserver.enabled", () -> "false");
	}

	@Configuration
	static class EmbeddedEventstoreConfiguration {
		@Bean
		public EmbeddedEventStore eventStore(EventStorageEngine storageEngine, AxonConfiguration configuration) {
			return EmbeddedEventStore.builder().storageEngine(storageEngine)
					.messageMonitor(configuration.messageMonitor(EventStore.class, "eventStore")).build();
		}

		@Bean
		public EventStorageEngine storageEngine() {
			return new InMemoryEventStorageEngine();
		}

	}

}
