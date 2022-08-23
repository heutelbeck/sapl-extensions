package io.sapl.axon.commandhandling;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.axon.annotation.PreHandleEnforce;
import io.sapl.axon.commandhandling.model.TestAggregateAPI.CreateAggregate;
import io.sapl.axon.configuration.SaplAutoConfiguration;
import lombok.Value;
import reactor.core.publisher.Flux;

@SpringBootTest
public class CommandTests {
	private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

	@MockBean
	PolicyDecisionPoint pdp;

	@Autowired
	CommandGateway commandGateway;

	@Autowired
	CommandBus commandBus;

	@Autowired
	CommandHandlingService commandService;

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
	void when_securedAggregateCreationCommand_and_Permit_then_accessGranted() {
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));
		assertThat(commandGateway.sendAndWait(new CreateAggregate("id1")), is("id1"));
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

	@Service
	public static class CommandHandlingService {

		@CommandHandler
		@PreHandleEnforce
		public String handle(CommandOne command) {
			return "OK (" + command.getValue() + ")";
		}
	}

	@DynamicPropertySource
	static void registerAxonProperties(DynamicPropertyRegistry registry) {
		registry.add("axon.axonserver.enabled", () -> "false");
	}

	@Configuration
	@Import({ SaplAutoConfiguration.class })
	// @ComponentScan({ "io.sapl.axon.command" })
	static class ScenarioConfiguration {
		@Bean
		CommandHandlingService CommandHandlingService() {
			return new CommandHandlingService();
		}
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

	@SpringBootApplication(scanBasePackages = { "io.sapl.axon.command.*" })
	public static class TestApplication {

		public static void main(String[] args) {
			SpringApplication.run(TestApplication.class, args);
		}
	}

}
