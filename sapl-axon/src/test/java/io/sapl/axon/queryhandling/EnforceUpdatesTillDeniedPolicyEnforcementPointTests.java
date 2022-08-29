package io.sapl.axon.queryhandling;

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
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.axon.constrainthandling.ConstraintHandlerService;
import lombok.EqualsAndHashCode;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.Optional;

import static io.sapl.axon.TestUtils.*;
import static io.sapl.axon.queryhandling.EnforceUpdatesTillDeniedPolicyEnforcementPoint.of;
import static org.springframework.test.util.ReflectionTestUtils.*;

public class EnforceUpdatesTillDeniedPolicyEnforcementPointTests {
	
	private static final String MAPPER_FILED_NAME = "mapper";
	private static final Duration DEFAULT_TIMEOUT = Duration.ofMillis(500);
	private static final Duration DEFAULT_TIMESTEP = Duration.ofMillis(20);
	private static final SubscriptionQueryUpdateMessage<TestUpdateResponseType> DEFAULT_UPDATE_MESSAGE = new GenericSubscriptionQueryUpdateMessage<TestUpdateResponseType>(new TestUpdateResponseType());
	private static final JsonNode DEFAULT_OBLIGATION = JsonNodeFactory.instance.textNode("onDecisionDo");
	
	private static class TestQueryPayload { }
	private static class TestInitialResponse { }
	@EqualsAndHashCode
	@JsonIgnoreProperties("hibernateLazyInitializer")
	private static class TestUpdateResponseType { }
	
	private static ConstraintHandlerService constraintHandlerService;
	private static JsonNode defaultResource;
	
	@BeforeAll
	@SuppressWarnings("unchecked")
	static void beforeAll() {
		var mapper = new ObjectMapper();
		constraintHandlerService = mock(ConstraintHandlerService.class);
		setField(constraintHandlerService, MAPPER_FILED_NAME, mapper);
		when(constraintHandlerService.buildQueryPreHandlerBundle(any(AuthorizationDecision.class), any(ResponseType.class), any(Optional.class)))
				.thenCallRealMethod();
		when(constraintHandlerService.deserializeResource(any(JsonNode.class), any(ResponseType.class)))
				.thenCallRealMethod();
		defaultResource = mapper.valueToTree(new TestUpdateResponseType());
	}
	
	@Test
	void when_pep_empty_then_noEvent() {
		var resultResponseType = ResponseTypes.instanceOf(TestInitialResponse.class);
		var updateResponseType = ResponseTypes.multipleInstancesOf(TestUpdateResponseType.class);
		var query = new GenericSubscriptionQueryMessage<>(new TestQueryPayload(), resultResponseType, updateResponseType);
		Flux<AuthorizationDecision> decisions = Flux.just();
		Flux<SubscriptionQueryUpdateMessage<TestUpdateResponseType>> updateMessageFlux = Flux.just();
		
		var enforcedUpdateMessageFlux = of(query, decisions, updateMessageFlux, constraintHandlerService, resultResponseType, updateResponseType);
		StepVerifier.create(enforcedUpdateMessageFlux)
				.expectSubscription()
				.expectNoEvent(DEFAULT_TIMEOUT)
				.verifyTimeout(DEFAULT_TIMEOUT.multipliedBy(2));
	}
	
	@Test
	void when_pep_subscribedTwice_then_illegalState() {
		var resultResponseType = ResponseTypes.instanceOf(TestInitialResponse.class);
		var updateResponseType = ResponseTypes.multipleInstancesOf(TestUpdateResponseType.class);
		var query = new GenericSubscriptionQueryMessage<>(new TestQueryPayload(), resultResponseType, updateResponseType);
		Flux<AuthorizationDecision> decisions = Flux.just();
		Flux<SubscriptionQueryUpdateMessage<TestUpdateResponseType>> updateMessageFlux = Flux.just();
		
		var enforcedUpdateMessageFlux = of(query, decisions, updateMessageFlux, constraintHandlerService, resultResponseType, updateResponseType);
		StepVerifier.create(enforcedUpdateMessageFlux)
				.expectSubscription()
				.expectNoEvent(DEFAULT_TIMEOUT)
				.verifyTimeout(DEFAULT_TIMEOUT.multipliedBy(2));
		StepVerifier.create(enforcedUpdateMessageFlux)
				.expectError(IllegalStateException.class)
				.verify(DEFAULT_TIMEOUT.multipliedBy(3));
	}
	
	@Test
	void when_pep_singleDeny_and_noUpdate_then_accessDenied() {
		var resultResponseType = ResponseTypes.instanceOf(TestInitialResponse.class);
		var updateResponseType = ResponseTypes.multipleInstancesOf(TestUpdateResponseType.class);
		var query = new GenericSubscriptionQueryMessage<>(new TestQueryPayload(), resultResponseType, updateResponseType);
		var decisions = Flux.just(new AuthorizationDecision(Decision.DENY));
		Flux<SubscriptionQueryUpdateMessage<TestUpdateResponseType>> updateMessageFlux = Flux.just();
		
		var enforcedUpdateMessageFlux = of(query, decisions, updateMessageFlux, constraintHandlerService, resultResponseType, updateResponseType);
		StepVerifier.create(enforcedUpdateMessageFlux)
				.expectError(AccessDeniedException.class)
				.verify(DEFAULT_TIMEOUT);
	}
	
	@Test
	void when_pep_singleIndeterminate_and_noUpdate_then_accessDenied() {
		var resultResponseType = ResponseTypes.instanceOf(TestInitialResponse.class);
		var updateResponseType = ResponseTypes.multipleInstancesOf(TestUpdateResponseType.class);
		var query = new GenericSubscriptionQueryMessage<>(new TestQueryPayload(), resultResponseType, updateResponseType);
		var decisions = Flux.just(new AuthorizationDecision(Decision.INDETERMINATE));
		Flux<SubscriptionQueryUpdateMessage<TestUpdateResponseType>> updateMessageFlux = Flux.just();
		
		var enforcedUpdateMessageFlux = of(query, decisions, updateMessageFlux, constraintHandlerService, resultResponseType, updateResponseType);
		StepVerifier.create(enforcedUpdateMessageFlux)
				.expectError(AccessDeniedException.class)
				.verify(DEFAULT_TIMEOUT);
	}
	
	@Test
	void when_pep_singleNotApplicable_and_noUpdate_then_accessDenied() {
		var resultResponseType = ResponseTypes.instanceOf(TestInitialResponse.class);
		var updateResponseType = ResponseTypes.multipleInstancesOf(TestUpdateResponseType.class);
		var query = new GenericSubscriptionQueryMessage<>(new TestQueryPayload(), resultResponseType, updateResponseType);
		var decisions = Flux.just(new AuthorizationDecision(Decision.NOT_APPLICABLE));
		Flux<SubscriptionQueryUpdateMessage<TestUpdateResponseType>> updateMessageFlux = Flux.just();
		
		var enforcedUpdateMessageFlux = of(query, decisions, updateMessageFlux, constraintHandlerService, resultResponseType, updateResponseType);
		StepVerifier.create(enforcedUpdateMessageFlux)
				.expectError(AccessDeniedException.class)
				.verify(DEFAULT_TIMEOUT);
	}
	
	@Test
	void when_pep_singlePermit_and_noUpdate_then_complete() {
		var resultResponseType = ResponseTypes.instanceOf(TestInitialResponse.class);
		var updateResponseType = ResponseTypes.multipleInstancesOf(TestUpdateResponseType.class);
		var query = new GenericSubscriptionQueryMessage<>(new TestQueryPayload(), resultResponseType, updateResponseType);
		var decisions = Flux.just(new AuthorizationDecision(Decision.PERMIT));
		Flux<SubscriptionQueryUpdateMessage<TestUpdateResponseType>> updateMessageFlux = Flux.just();
		
		var enforcedUpdateMessageFlux = of(query, decisions, updateMessageFlux, constraintHandlerService, resultResponseType, updateResponseType);
		StepVerifier.create(enforcedUpdateMessageFlux)
				.verifyComplete();
	}
	
	@Test
	void when_pep_noDecision_and_singleUpdate_then_noEvent() {
		var resultResponseType = ResponseTypes.instanceOf(TestInitialResponse.class);
		var updateResponseType = ResponseTypes.multipleInstancesOf(TestUpdateResponseType.class);
		var query = new GenericSubscriptionQueryMessage<>(new TestQueryPayload(), resultResponseType, updateResponseType);
		Flux<AuthorizationDecision> decisions = Flux.just();
		Flux<SubscriptionQueryUpdateMessage<TestUpdateResponseType>> updateMessageFlux = Flux.just(DEFAULT_UPDATE_MESSAGE);
		
		var enforcedUpdateMessageFlux = of(query, decisions, updateMessageFlux, constraintHandlerService, resultResponseType, updateResponseType);
		StepVerifier.create(enforcedUpdateMessageFlux)
				.expectSubscription()
				.expectNoEvent(DEFAULT_TIMEOUT)
				.verifyTimeout(DEFAULT_TIMEOUT.multipliedBy(2));
	}
	
	@Test
	void when_pep_singleDeny_and_singleUpdate_then_accessDenied() {
		var resultResponseType = ResponseTypes.instanceOf(TestInitialResponse.class);
		var updateResponseType = ResponseTypes.multipleInstancesOf(TestUpdateResponseType.class);
		var query = new GenericSubscriptionQueryMessage<>(new TestQueryPayload(), resultResponseType, updateResponseType);
		var decisions = Flux.just(new AuthorizationDecision(Decision.DENY));
		Flux<SubscriptionQueryUpdateMessage<TestUpdateResponseType>> updateMessageFlux = Flux.just(DEFAULT_UPDATE_MESSAGE);
		
		var enforcedUpdateMessageFlux = of(query, decisions, updateMessageFlux, constraintHandlerService, resultResponseType, updateResponseType);
		StepVerifier.create(enforcedUpdateMessageFlux)
				.expectError(AccessDeniedException.class)
				.verify(DEFAULT_TIMEOUT);
	}
	
	@Test
	void when_pep_singleIndeterminate_and_singleUpdate_then_accessDenied() {
		var resultResponseType = ResponseTypes.instanceOf(TestInitialResponse.class);
		var updateResponseType = ResponseTypes.multipleInstancesOf(TestUpdateResponseType.class);
		var query = new GenericSubscriptionQueryMessage<>(new TestQueryPayload(), resultResponseType, updateResponseType);
		var decisions = Flux.just(new AuthorizationDecision(Decision.INDETERMINATE));
		Flux<SubscriptionQueryUpdateMessage<TestUpdateResponseType>> updateMessageFlux = Flux.just(DEFAULT_UPDATE_MESSAGE);
		
		var enforcedUpdateMessageFlux = of(query, decisions, updateMessageFlux, constraintHandlerService, resultResponseType, updateResponseType);
		StepVerifier.create(enforcedUpdateMessageFlux)
				.expectError(AccessDeniedException.class)
				.verify(DEFAULT_TIMEOUT);
	}
	
	@Test
	void when_pep_singleNotApplicable_and_singleUpdate_then_accessDenied() {
		var resultResponseType = ResponseTypes.instanceOf(TestInitialResponse.class);
		var updateResponseType = ResponseTypes.multipleInstancesOf(TestUpdateResponseType.class);
		var query = new GenericSubscriptionQueryMessage<>(new TestQueryPayload(), resultResponseType, updateResponseType);
		var decisions = Flux.just(new AuthorizationDecision(Decision.NOT_APPLICABLE));
		Flux<SubscriptionQueryUpdateMessage<TestUpdateResponseType>> updateMessageFlux = Flux.just(DEFAULT_UPDATE_MESSAGE);
		
		var enforcedUpdateMessageFlux = of(query, decisions, updateMessageFlux, constraintHandlerService, resultResponseType, updateResponseType);
		StepVerifier.create(enforcedUpdateMessageFlux)
				.expectError(AccessDeniedException.class)
				.verify(DEFAULT_TIMEOUT);
	}
	
	@Test
	void when_pep_singlePermit_and_singleUpdate_then_permit() {
		var resultResponseType = ResponseTypes.instanceOf(TestInitialResponse.class);
		var updateResponseType = ResponseTypes.multipleInstancesOf(TestUpdateResponseType.class);
		var query = new GenericSubscriptionQueryMessage<>(new TestQueryPayload(), resultResponseType, updateResponseType);
		var decisions = Flux.just(new AuthorizationDecision(Decision.PERMIT));
		Flux<SubscriptionQueryUpdateMessage<TestUpdateResponseType>> updateMessageFlux = Flux.just(DEFAULT_UPDATE_MESSAGE);
		
		var enforcedUpdateMessageFlux = of(query, decisions, updateMessageFlux, constraintHandlerService, resultResponseType, updateResponseType);
		StepVerifier.create(enforcedUpdateMessageFlux)
				.expectNext(DEFAULT_UPDATE_MESSAGE)
				.verifyComplete();
	}
	
	@Test
	void when_pep_singleDeny_and_singleUpdate_and_ressource_then_permit() {
		var resultResponseType = ResponseTypes.instanceOf(TestInitialResponse.class);
		var updateResponseType = ResponseTypes.multipleInstancesOf(TestUpdateResponseType.class);
		var query = new GenericSubscriptionQueryMessage<>(new TestQueryPayload(), resultResponseType, updateResponseType);
		var decisions = Flux.just(new AuthorizationDecision(Decision.DENY, Optional.of(defaultResource), Optional.empty(), Optional.empty()));
		Flux<SubscriptionQueryUpdateMessage<TestUpdateResponseType>> updateMessageFlux = Flux.just(DEFAULT_UPDATE_MESSAGE);
		
		var enforcedUpdateMessageFlux = of(query, decisions, updateMessageFlux, constraintHandlerService, resultResponseType, updateResponseType);
		StepVerifier.create(enforcedUpdateMessageFlux)
				.expectError(AccessDeniedException.class)
				.verify(DEFAULT_TIMEOUT);
	}
	
	@Test
	void when_pep_singleIndeterminate_and_singleUpdate_and_ressource_then_permit() {
		var resultResponseType = ResponseTypes.instanceOf(TestInitialResponse.class);
		var updateResponseType = ResponseTypes.multipleInstancesOf(TestUpdateResponseType.class);
		var query = new GenericSubscriptionQueryMessage<>(new TestQueryPayload(), resultResponseType, updateResponseType);
		var decisions = Flux.just(new AuthorizationDecision(Decision.INDETERMINATE, Optional.of(defaultResource), Optional.empty(), Optional.empty()));
		Flux<SubscriptionQueryUpdateMessage<TestUpdateResponseType>> updateMessageFlux = Flux.just(DEFAULT_UPDATE_MESSAGE);
		
		var enforcedUpdateMessageFlux = of(query, decisions, updateMessageFlux, constraintHandlerService, resultResponseType, updateResponseType);
		StepVerifier.create(enforcedUpdateMessageFlux)
				.expectError(AccessDeniedException.class)
				.verify(DEFAULT_TIMEOUT);
	}
	
	@Test
	void when_pep_singleNotApplicable_and_singleUpdate_and_ressource_then_permit() {
		var resultResponseType = ResponseTypes.instanceOf(TestInitialResponse.class);
		var updateResponseType = ResponseTypes.multipleInstancesOf(TestUpdateResponseType.class);
		var query = new GenericSubscriptionQueryMessage<>(new TestQueryPayload(), resultResponseType, updateResponseType);
		var decisions = Flux.just(new AuthorizationDecision(Decision.NOT_APPLICABLE, Optional.of(defaultResource), Optional.empty(), Optional.empty()));
		Flux<SubscriptionQueryUpdateMessage<TestUpdateResponseType>> updateMessageFlux = Flux.just(DEFAULT_UPDATE_MESSAGE);
		
		var enforcedUpdateMessageFlux = of(query, decisions, updateMessageFlux, constraintHandlerService, resultResponseType, updateResponseType);
		StepVerifier.create(enforcedUpdateMessageFlux)
				.expectError(AccessDeniedException.class)
				.verify(DEFAULT_TIMEOUT);
	}
	
	@Test
	void when_pep_singlePermit_and_singleUpdate_and_ressource_then_permit() {
		var resultResponseType = ResponseTypes.instanceOf(TestInitialResponse.class);
		var updateResponseType = ResponseTypes.multipleInstancesOf(TestUpdateResponseType.class);
		var query = new GenericSubscriptionQueryMessage<>(new TestQueryPayload(), resultResponseType, updateResponseType);
		var decisions = Flux.just(new AuthorizationDecision(Decision.PERMIT, Optional.of(defaultResource), Optional.empty(), Optional.empty()));
		Flux<SubscriptionQueryUpdateMessage<TestUpdateResponseType>> updateMessageFlux = Flux.just(DEFAULT_UPDATE_MESSAGE);
		
		var enforcedUpdateMessageFlux = of(query, decisions, updateMessageFlux, constraintHandlerService, resultResponseType, updateResponseType);
		StepVerifier.create(enforcedUpdateMessageFlux)
				.expectNextMatches(matchesIgnoringIdentifier(DEFAULT_UPDATE_MESSAGE))
				.verifyComplete();
	}
	
	/*@Test
	void when_pep_singlePermit_and_singleUpdate_and_obligation_then_() {
		var resultResponseType = ResponseTypes.instanceOf(TestInitialResponse.class);
		var updateResponseType = ResponseTypes.multipleInstancesOf(TestUpdateResponseType.class);
		var query = new GenericSubscriptionQueryMessage<>(new TestQueryPayload(), resultResponseType, updateResponseType);
		var decisions = Flux.just(new AuthorizationDecision(Decision.PERMIT, Optional.empty(), Optional.of(DEFAULT_OBLIGATION), Optional.empty()));
		Flux<SubscriptionQueryUpdateMessage<TestUpdateResponseType>> updateMessageFlux = Flux.just(DEFAULT_UPDATE_MESSAGE);
		
		var enforcedUpdateMessageFlux = of(query, decisions, updateMessageFlux, constraintHandlerService, resultResponseType, updateResponseType);
		StepVerifier.create(enforcedUpdateMessageFlux)
				.expectNextMatches(matchesIgnoringIdentifier(DEFAULT_UPDATE_MESSAGE))
				.verifyComplete();
	}*/
	
	@Test
	void when_pep_permitThenDeny_and_singleUpdate_then_permit() {
		var resultResponseType = ResponseTypes.instanceOf(TestInitialResponse.class);
		var updateResponseType = ResponseTypes.multipleInstancesOf(TestUpdateResponseType.class);
		var query = new GenericSubscriptionQueryMessage<>(new TestQueryPayload(), resultResponseType, updateResponseType);
		var decisions = Flux.just(new AuthorizationDecision(Decision.PERMIT), new AuthorizationDecision(Decision.DENY));
		Flux<SubscriptionQueryUpdateMessage<TestUpdateResponseType>> updateMessageFlux = Flux.just(DEFAULT_UPDATE_MESSAGE);
		
		var enforcedUpdateMessageFlux = of(query, decisions, updateMessageFlux, constraintHandlerService, resultResponseType, updateResponseType);
		StepVerifier.create(enforcedUpdateMessageFlux)
				.expectNextMatches(matchesIgnoringIdentifier(DEFAULT_UPDATE_MESSAGE))
				.verifyComplete();
	}
	
	@Test
	void when_pep_denyThenPermit_and_singleUpdate_then_permit() {
		var resultResponseType = ResponseTypes.instanceOf(TestInitialResponse.class);
		var updateResponseType = ResponseTypes.multipleInstancesOf(TestUpdateResponseType.class);
		var query = new GenericSubscriptionQueryMessage<>(new TestQueryPayload(), resultResponseType, updateResponseType);
		var decisions = Flux.just(new AuthorizationDecision(Decision.DENY), new AuthorizationDecision(Decision.PERMIT));
		Flux<SubscriptionQueryUpdateMessage<TestUpdateResponseType>> updateMessageFlux = Flux.just(DEFAULT_UPDATE_MESSAGE);
		
		var enforcedUpdateMessageFlux = of(query, decisions, updateMessageFlux, constraintHandlerService, resultResponseType, updateResponseType);
		StepVerifier.create(enforcedUpdateMessageFlux)
				.expectError(AccessDeniedException.class)
				.verify(DEFAULT_TIMEOUT);
	}
	
	@Test
	void when_pep_multiplePermit_and_multipleUpdates_then_permitAll() {
		var resultResponseType = ResponseTypes.instanceOf(TestInitialResponse.class);
		var updateResponseType = ResponseTypes.multipleInstancesOf(TestUpdateResponseType.class);
		var query = new GenericSubscriptionQueryMessage<>(new TestQueryPayload(), resultResponseType, updateResponseType);
		var decisions = Flux.concat(
				Mono.delay(DEFAULT_TIMESTEP.multipliedBy(0)).thenReturn(new AuthorizationDecision(Decision.PERMIT)),
				Mono.delay(DEFAULT_TIMESTEP.multipliedBy(1)).thenReturn(new AuthorizationDecision(Decision.PERMIT))
				);
		Flux<SubscriptionQueryUpdateMessage<TestUpdateResponseType>> updateMessageFlux = Flux.concat(
				Mono.delay(DEFAULT_TIMESTEP.multipliedBy(0)).thenReturn(DEFAULT_UPDATE_MESSAGE),
				Mono.delay(DEFAULT_TIMESTEP.multipliedBy(2)).thenReturn(DEFAULT_UPDATE_MESSAGE)
				);
		
		var enforcedUpdateMessageFlux = of(query, decisions, updateMessageFlux, constraintHandlerService, resultResponseType, updateResponseType);
		StepVerifier.create(enforcedUpdateMessageFlux)
				.expectNextMatches(matchesIgnoringIdentifier(DEFAULT_UPDATE_MESSAGE))
				.expectNextMatches(matchesIgnoringIdentifier(DEFAULT_UPDATE_MESSAGE))
				.verifyComplete();
	}
	
	@Test
	void when_pep_permitThenDeny_and_multipleUpdates_then_permitThenAccessDenied() {
		var resultResponseType = ResponseTypes.instanceOf(TestInitialResponse.class);
		var updateResponseType = ResponseTypes.multipleInstancesOf(TestUpdateResponseType.class);
		var query = new GenericSubscriptionQueryMessage<>(new TestQueryPayload(), resultResponseType, updateResponseType);
		var decisions = Flux.concat(
				Mono.delay(DEFAULT_TIMESTEP.multipliedBy(0)).thenReturn(new AuthorizationDecision(Decision.PERMIT)),
				Mono.delay(DEFAULT_TIMESTEP.multipliedBy(1)).thenReturn(new AuthorizationDecision(Decision.DENY))
				);
		Flux<SubscriptionQueryUpdateMessage<TestUpdateResponseType>> updateMessageFlux = Flux.concat(
				Mono.delay(DEFAULT_TIMESTEP.multipliedBy(0)).thenReturn(DEFAULT_UPDATE_MESSAGE),
				Mono.delay(DEFAULT_TIMESTEP.multipliedBy(2)).thenReturn(DEFAULT_UPDATE_MESSAGE)
				);
		
		var enforcedUpdateMessageFlux = of(query, decisions, updateMessageFlux, constraintHandlerService, resultResponseType, updateResponseType);
		StepVerifier.create(enforcedUpdateMessageFlux)
				.expectNextMatches(matchesIgnoringIdentifier(DEFAULT_UPDATE_MESSAGE))
				.expectError(AccessDeniedException.class)
				.verify(DEFAULT_TIMEOUT);
	}
	
	@Test
	void when_pep_denyThenPermit_and_multipleUpdates_then_permitThenAccessDenied() {
		var resultResponseType = ResponseTypes.instanceOf(TestInitialResponse.class);
		var updateResponseType = ResponseTypes.multipleInstancesOf(TestUpdateResponseType.class);
		var query = new GenericSubscriptionQueryMessage<>(new TestQueryPayload(), resultResponseType, updateResponseType);
		var decisions = Flux.concat(
				Mono.delay(DEFAULT_TIMESTEP.multipliedBy(0)).thenReturn(new AuthorizationDecision(Decision.DENY)),
				Mono.delay(DEFAULT_TIMESTEP.multipliedBy(1)).thenReturn(new AuthorizationDecision(Decision.PERMIT))
				);
		Flux<SubscriptionQueryUpdateMessage<TestUpdateResponseType>> updateMessageFlux = Flux.concat(
				Mono.delay(DEFAULT_TIMESTEP.multipliedBy(0)).thenReturn(DEFAULT_UPDATE_MESSAGE),
				Mono.delay(DEFAULT_TIMESTEP.multipliedBy(2)).thenReturn(DEFAULT_UPDATE_MESSAGE)
				);
		
		var enforcedUpdateMessageFlux = of(query, decisions, updateMessageFlux, constraintHandlerService, resultResponseType, updateResponseType);
		StepVerifier.create(enforcedUpdateMessageFlux)
				.expectError(AccessDeniedException.class)
				.verify(DEFAULT_TIMEOUT);
	}
	
	@Test
	void when_pep_permitThenDenyThenPermit_and_multipleUpdates_then_permitThenAccessDenied() {
		var resultResponseType = ResponseTypes.instanceOf(TestInitialResponse.class);
		var updateResponseType = ResponseTypes.multipleInstancesOf(TestUpdateResponseType.class);
		var query = new GenericSubscriptionQueryMessage<>(new TestQueryPayload(), resultResponseType, updateResponseType);
		var decisions = Flux.concat(
				Mono.delay(DEFAULT_TIMESTEP.multipliedBy(0)).thenReturn(new AuthorizationDecision(Decision.PERMIT)),
				Mono.delay(DEFAULT_TIMESTEP.multipliedBy(1)).thenReturn(new AuthorizationDecision(Decision.DENY)),
				Mono.delay(DEFAULT_TIMESTEP.multipliedBy(3)).thenReturn(new AuthorizationDecision(Decision.PERMIT))
				);
		Flux<SubscriptionQueryUpdateMessage<TestUpdateResponseType>> updateMessageFlux = Flux.concat(
				Mono.delay(DEFAULT_TIMESTEP.multipliedBy(0)).thenReturn(DEFAULT_UPDATE_MESSAGE),
				Mono.delay(DEFAULT_TIMESTEP.multipliedBy(2)).thenReturn(DEFAULT_UPDATE_MESSAGE),
				Mono.delay(DEFAULT_TIMESTEP.multipliedBy(4)).thenReturn(DEFAULT_UPDATE_MESSAGE)
				);
		
		var enforcedUpdateMessageFlux = of(query, decisions, updateMessageFlux, constraintHandlerService, resultResponseType, updateResponseType);
		StepVerifier.create(enforcedUpdateMessageFlux)
				.expectNextMatches(matchesIgnoringIdentifier(DEFAULT_UPDATE_MESSAGE))
				.expectError(AccessDeniedException.class)
				.verify(DEFAULT_TIMEOUT);
	}
}
