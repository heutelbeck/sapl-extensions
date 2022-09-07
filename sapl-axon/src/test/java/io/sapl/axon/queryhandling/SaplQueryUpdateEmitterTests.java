package io.sapl.axon.queryhandling;

import static io.sapl.axon.TestUtilities.alwaysTrue;
import static io.sapl.axon.TestUtilities.matchesIgnoringIdentifier;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.util.ReflectionTestUtils.setField;
import static reactor.test.StepVerifier.create;

import java.lang.annotation.Annotation;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import org.axonframework.common.Registration;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork;
import org.axonframework.monitoring.NoOpMessageMonitor;
import org.axonframework.queryhandling.GenericSubscriptionQueryMessage;
import org.axonframework.queryhandling.GenericSubscriptionQueryUpdateMessage;
import org.axonframework.queryhandling.SubscriptionQueryBackpressure;
import org.axonframework.queryhandling.SubscriptionQueryMessage;
import org.axonframework.queryhandling.SubscriptionQueryUpdateMessage;
import org.axonframework.queryhandling.UpdateHandlerRegistration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.axon.annotation.EnforceDropUpdatesWhileDenied;
import io.sapl.axon.annotation.EnforceRecoverableUpdatesIfDenied;
import io.sapl.axon.annotation.PreHandleEnforce;
import io.sapl.axon.constrainthandling.ConstraintHandlerService;
import lombok.Data;
import lombok.EqualsAndHashCode;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink.OverflowStrategy;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.Sinks.Many;

@SuppressWarnings("deprecation") // inherited from Axon
public class SaplQueryUpdateEmitterTests {

	private static final int DEFAULT_UPDATE_BUFFER_SIZE = 255;
	private static final String DEFAULT_MESSAGE_IDENTIFIER = "messageIdentifier";
	private static final String DEFAULT_CAUSE = "defaultCause";
	private static final FlaggedUpdateResponseType DEFAULT_UPDATE_RESPONSE_TYPE = new FlaggedUpdateResponseType();
	private static final Duration DEFAULT_TIMEOUT = Duration.ofMillis(500);
	private static final Duration DEFAULT_TIMESTEP = Duration.ofMillis(20);
	private static final String MAPPER_FILED_NAME = "mapper";
	private static final String DISPATCH_INTERCEPTORS_FILED_NAME = "dispatchInterceptors";

	private static ConstraintHandlerService constraintHandlerService;

	private static class FlaggedUpdateHandlerRegistration<U> extends UpdateHandlerRegistration<U> {
		public FlaggedUpdateHandlerRegistration(Registration registration,
				Flux<SubscriptionQueryUpdateMessage<U>> updates, Runnable completeHandler) {
			super(registration, updates, completeHandler);
		}
	}

	@Data
	@EqualsAndHashCode
	private static class FlaggedUpdateResponseType {
		private int someInt = 0;
	}

	private static class FlaggedSubscriptionQueryMessage<Q, I, U> extends GenericSubscriptionQueryMessage<Q, I, U> {
		public FlaggedSubscriptionQueryMessage(Q payload, ResponseType<I> responseType,
				ResponseType<U> updateResponseType) {
			super(payload, responseType, updateResponseType);
		}
	}

	@BeforeAll
	@SuppressWarnings("unchecked")
	static void setup() {
		var mapper = new ObjectMapper();
		constraintHandlerService = mock(ConstraintHandlerService.class);
		setField(constraintHandlerService, MAPPER_FILED_NAME, mapper);
		when(constraintHandlerService.buildQueryPreHandlerBundle(any(AuthorizationDecision.class),
				any(ResponseType.class), any(Optional.class))).thenCallRealMethod();
		when(constraintHandlerService.deserializeResource(any(JsonNode.class), any(ResponseType.class)))
				.thenCallRealMethod();
	}

	final String TEXTSUBSCRIPTIONQUERYUPDATEMESSAGE = "some-text";

	private SaplQueryUpdateEmitter emitter;
	private SubscriptionQueryMessage<String, List<String>, String> subscriptionQueryMessage;

	@BeforeEach
	void setUp() {
		subscriptionQueryMessage = new GenericSubscriptionQueryMessage<>("some-payload", "chatMessages",
				ResponseTypes.multipleInstancesOf(String.class), ResponseTypes.instanceOf(String.class));
		subscriptionQueryMessage = subscriptionQueryMessage.andMetaData(Map.of("updateResponseType",
				subscriptionQueryMessage.getUpdateResponseType().getExpectedResponseType().getSimpleName()));
		emitter = new SaplQueryUpdateEmitter(Optional.empty(), constraintHandlerService);
	}

	@Test
	void when_construct_without_updateMessageMonitor_then_return() {
		new SaplQueryUpdateEmitter(Optional.empty(), constraintHandlerService);
	}

	@Test
	void when_construct_with_updateMessageMonitor_then_return() {
		var updateMessageMonitor = NoOpMessageMonitor.INSTANCE;
		new SaplQueryUpdateEmitter(Optional.of(updateMessageMonitor), constraintHandlerService);
	}

	@Test
	void when_registerUpdateHandler_with_backpressure_then_ignoreBackpressure_and_callNonDeprecatedVariant() {
		var emitter = mock(SaplQueryUpdateEmitter.class);
		when(emitter.registerUpdateHandler(any(SubscriptionQueryMessage.class),
				any(SubscriptionQueryBackpressure.class), any(Integer.class))).thenCallRealMethod();
		when(emitter.registerUpdateHandler(any(SubscriptionQueryMessage.class), any(Integer.class)))
				.thenReturn(new FlaggedUpdateHandlerRegistration<Object>(() -> true, Flux.empty(), () -> {
				}));

		var query = new GenericSubscriptionQueryMessage<>(new Object(), ResponseTypes.instanceOf(Object.class),
				ResponseTypes.multipleInstancesOf(Object.class));
		var backpressure = new SubscriptionQueryBackpressure(OverflowStrategy.ERROR);

		var updateHandlerRegistration = emitter.registerUpdateHandler(query, backpressure, DEFAULT_UPDATE_BUFFER_SIZE);
		assertEquals(FlaggedUpdateHandlerRegistration.class, updateHandlerRegistration.getClass());
	}

	@Test
	void when_queryUpdateHandlerRegistered_with_nonRegisteredSubscriptionQuery_then_false() {
		var query = new GenericSubscriptionQueryMessage<>(new Object(), ResponseTypes.instanceOf(Object.class),
				ResponseTypes.multipleInstancesOf(Object.class));

		var queryUpdateHandlerRegistered = emitter.queryUpdateHandlerRegistered(query);
		assertFalse(queryUpdateHandlerRegistered);
	}

	@Test
	void when_queryUpdateHandlerRegistered_with_registeredSubscriptionQuery_then_true() {
		var query = new GenericSubscriptionQueryMessage<>(new Object(), ResponseTypes.instanceOf(Object.class),
				ResponseTypes.multipleInstancesOf(Object.class));
		emitter.registerUpdateHandler(query, DEFAULT_UPDATE_BUFFER_SIZE);

		var queryUpdateHandlerRegistered = emitter.queryUpdateHandlerRegistered(query);
		assertTrue(queryUpdateHandlerRegistered);
	}

	@Test
	void when_authorizeUpdatesForSubscriptionQueryWithId_with_nonExistingMessageIdentifier_then_noEvent() {
		var query = new GenericSubscriptionQueryMessage<>(new Object(), ResponseTypes.instanceOf(Object.class),
				ResponseTypes.multipleInstancesOf(Object.class));

		var registration = emitter.registerUpdateHandler(query, DEFAULT_UPDATE_BUFFER_SIZE);
		emitter.authorizeUpdatesForSubscriptionQueryWithId(DEFAULT_MESSAGE_IDENTIFIER);

		create(registration.getUpdates()).expectSubscription().verifyTimeout(DEFAULT_TIMEOUT);
	}

	@Test
	void when_authorizeUpdatesForSubscriptionQueryWithId_with_existingMessageIdentifier_then_noEvent() {
		var query = new GenericSubscriptionQueryMessage<>(new Object(), ResponseTypes.instanceOf(Object.class),
				ResponseTypes.multipleInstancesOf(Object.class));
		var messageIdentifier = query.getIdentifier();

		var registration = emitter.registerUpdateHandler(query, DEFAULT_UPDATE_BUFFER_SIZE);
		emitter.authorizeUpdatesForSubscriptionQueryWithId(messageIdentifier);

		create(registration.getUpdates()).expectSubscription().verifyTimeout(DEFAULT_TIMEOUT);
	}

	@Test
	void when_authorizeUpdatesForSubscriptionQueryWithId_with_nonExistingMessageIdentifier_and_noDecision_and_PreHandleEnforce_then_noEvent() {
		var query = new GenericSubscriptionQueryMessage<>(new Object(), ResponseTypes.instanceOf(Object.class),
				ResponseTypes.multipleInstancesOf(Object.class));
		var decisions = Flux.<AuthorizationDecision>empty();
		var clazz = PreHandleEnforce.class;

		var registration = emitter.registerUpdateHandler(query, DEFAULT_UPDATE_BUFFER_SIZE);
		emitter.authorizeUpdatesForSubscriptionQueryWithId(DEFAULT_MESSAGE_IDENTIFIER, decisions, clazz);

		create(registration.getUpdates()).expectSubscription().verifyTimeout(DEFAULT_TIMEOUT);
	}

	@Test
	void when_authorizeUpdatesForSubscriptionQueryWithId_with_existingMessageIdentifier_and_noDecision_and_PreHandleEnforce_then_noEvent() {
		var query = new GenericSubscriptionQueryMessage<>(new Object(), ResponseTypes.instanceOf(Object.class),
				ResponseTypes.multipleInstancesOf(Object.class));
		var decisions = Flux.<AuthorizationDecision>empty();
		var messageIdentifier = query.getIdentifier();
		var clazz = PreHandleEnforce.class;

		var registration = emitter.registerUpdateHandler(query, DEFAULT_UPDATE_BUFFER_SIZE);
		emitter.authorizeUpdatesForSubscriptionQueryWithId(messageIdentifier, decisions, clazz);

		create(registration.getUpdates()).expectSubscription().verifyTimeout(DEFAULT_TIMEOUT);
	}

	@Test
	void when_authorizeUpdatesForSubscriptionQueryWithId_with_existingMessageIdentifier_and_deny_and_Annotation_then_noEvent() {
		var decisions = Flux.just(AuthorizationDecision.DENY);
		var clazz = Annotation.class;

		assertThrows(IllegalArgumentException.class,
				() -> emitter.authorizeUpdatesForSubscriptionQueryWithId(DEFAULT_MESSAGE_IDENTIFIER, decisions, clazz));
	}
	
	@Test
	void when_authorizeUpdatesForSubscriptionQueryWithId_with_nonExistingMessageIdentifier_and_noDecision_and_EnforceDropUpdatesWhileDenied_then_noEvent() {
		var query = new GenericSubscriptionQueryMessage<>(new Object(), ResponseTypes.instanceOf(Object.class),
				ResponseTypes.multipleInstancesOf(Object.class));
		var decisions = Flux.<AuthorizationDecision>empty();
		var clazz = EnforceDropUpdatesWhileDenied.class;

		var registration = emitter.registerUpdateHandler(query, DEFAULT_UPDATE_BUFFER_SIZE);
		emitter.authorizeUpdatesForSubscriptionQueryWithId(DEFAULT_MESSAGE_IDENTIFIER, decisions, clazz);

		create(registration.getUpdates()).expectSubscription().verifyTimeout(DEFAULT_TIMEOUT);
	}
	
	@Test
	void when_authorizeUpdatesForSubscriptionQueryWithId_with_nonExistingMessageIdentifier_and_noDecision_and_EnforceRecoverableUpdatesIfDenied_then_noEvent() {
		var query = new GenericSubscriptionQueryMessage<>(new Object(), ResponseTypes.instanceOf(Object.class),
				ResponseTypes.multipleInstancesOf(Object.class));
		var decisions = Flux.<AuthorizationDecision>empty();
		var clazz = EnforceRecoverableUpdatesIfDenied.class;

		var registration = emitter.registerUpdateHandler(query, DEFAULT_UPDATE_BUFFER_SIZE);
		emitter.authorizeUpdatesForSubscriptionQueryWithId(DEFAULT_MESSAGE_IDENTIFIER, decisions, clazz);

		create(registration.getUpdates()).expectSubscription().verifyTimeout(DEFAULT_TIMEOUT);
	}

	@Test
	void when_authorizeUpdatesForSubscriptionQueryWithId_with_existingMessageIdentifier_and_deny_and_PreHandleEnforce_then_accessDenied() {
		var query = new GenericSubscriptionQueryMessage<>(new Object(), ResponseTypes.instanceOf(Object.class),
				ResponseTypes.multipleInstancesOf(Object.class));
		var decisions = Flux.just(AuthorizationDecision.DENY);
		var messageIdentifier = query.getIdentifier();
		var clazz = PreHandleEnforce.class;

		var registration = emitter.registerUpdateHandler(query, DEFAULT_UPDATE_BUFFER_SIZE);
		emitter.authorizeUpdatesForSubscriptionQueryWithId(messageIdentifier, decisions, clazz);

		create(registration.getUpdates()).expectError(AccessDeniedException.class).verify(DEFAULT_TIMEOUT);
	}

	@Test
	void when_authorizeUpdatesForSubscriptionQueryWithId_with_existingMessageIdentifier_and_indeterminate_and_PreHandleEnforce_then_accessDenied() {
		var query = new GenericSubscriptionQueryMessage<>(new Object(), ResponseTypes.instanceOf(Object.class),
				ResponseTypes.multipleInstancesOf(Object.class));
		var decisions = Flux.just(AuthorizationDecision.INDETERMINATE);
		var messageIdentifier = query.getIdentifier();
		var clazz = PreHandleEnforce.class;

		var registration = emitter.registerUpdateHandler(query, DEFAULT_UPDATE_BUFFER_SIZE);
		emitter.authorizeUpdatesForSubscriptionQueryWithId(messageIdentifier, decisions, clazz);

		create(registration.getUpdates()).expectError(AccessDeniedException.class).verify(DEFAULT_TIMEOUT);
	}

	@Test
	void when_authorizeUpdatesForSubscriptionQueryWithId_with_existingMessageIdentifier_and_notApplicable_and_PreHandleEnforce_then_accessDenied() {
		var query = new GenericSubscriptionQueryMessage<>(new Object(), ResponseTypes.instanceOf(Object.class),
				ResponseTypes.multipleInstancesOf(Object.class));
		var decisions = Flux.just(AuthorizationDecision.NOT_APPLICABLE);
		var messageIdentifier = query.getIdentifier();
		var clazz = PreHandleEnforce.class;

		var registration = emitter.registerUpdateHandler(query, DEFAULT_UPDATE_BUFFER_SIZE);
		emitter.authorizeUpdatesForSubscriptionQueryWithId(messageIdentifier, decisions, clazz);

		create(registration.getUpdates()).expectError(AccessDeniedException.class).verify(DEFAULT_TIMEOUT);
	}

	@Test
	void when_authorizeUpdatesForSubscriptionQueryWithId_with_existingMessageIdentifier_and_permit_and_PreHandleEnforce_then_noEvent() {
		var query = new GenericSubscriptionQueryMessage<>(new Object(), ResponseTypes.instanceOf(Object.class),
				ResponseTypes.multipleInstancesOf(Object.class));
		var decisions = Flux.just(AuthorizationDecision.PERMIT);
		var messageIdentifier = query.getIdentifier();
		var clazz = PreHandleEnforce.class;

		var registration = emitter.registerUpdateHandler(query, DEFAULT_UPDATE_BUFFER_SIZE);
		emitter.authorizeUpdatesForSubscriptionQueryWithId(messageIdentifier, decisions, clazz);

		create(registration.getUpdates()).expectSubscription().verifyTimeout(DEFAULT_TIMEOUT);
	}

	@Test
	void when_registerUpdateHandler_without_backpressure_then_UpdateHandlerRegistration() {
		var query = new GenericSubscriptionQueryMessage<>(new Object(), ResponseTypes.instanceOf(Object.class),
				ResponseTypes.multipleInstancesOf(Object.class));

		var updateHandlerRegistration = emitter.registerUpdateHandler(query, DEFAULT_UPDATE_BUFFER_SIZE);
		assertTrue(UpdateHandlerRegistration.class.isAssignableFrom(updateHandlerRegistration.getClass()));
	}

	@Test
	void when_emit_with_permit_and_closedFilter_then_noEvent() {
		Predicate<SubscriptionQueryMessage<?, ?, FlaggedUpdateResponseType>> filter = subscriptionQueryMessage -> false;
		var query = new GenericSubscriptionQueryMessage<>(new Object(), ResponseTypes.instanceOf(FlaggedUpdateResponseType.class),
				ResponseTypes.instanceOf(FlaggedUpdateResponseType.class));
		var decisions = Flux.just(AuthorizationDecision.PERMIT);
		var annotation = PreHandleEnforce.class;
		var update = GenericSubscriptionQueryUpdateMessage
				.<FlaggedUpdateResponseType>asUpdateMessage(DEFAULT_UPDATE_RESPONSE_TYPE);

		var registration = emitter.registerUpdateHandler(query, DEFAULT_UPDATE_BUFFER_SIZE);
		emitter.authorizeUpdatesForSubscriptionQueryWithId(query.getIdentifier(), decisions, annotation);
		
		emitter.emit(filter, update);
		
		create(registration.getUpdates())
				.verifyTimeout(DEFAULT_TIMEOUT);
	}
	
	@Test
	void when_emitMultipleInstancesAsList_with_permit_and_openFilter_then_update() {
		var query = new GenericSubscriptionQueryMessage<>(new Object(), ResponseTypes.instanceOf(FlaggedUpdateResponseType.class),
				ResponseTypes.multipleInstancesOf(FlaggedUpdateResponseType.class));
		var decisions = Flux.just(AuthorizationDecision.PERMIT);
		var annotation = PreHandleEnforce.class;
		var update = GenericSubscriptionQueryUpdateMessage
				.<FlaggedUpdateResponseType>asUpdateMessage(List.of(DEFAULT_UPDATE_RESPONSE_TYPE, DEFAULT_UPDATE_RESPONSE_TYPE));

		var registration = emitter.registerUpdateHandler(query, DEFAULT_UPDATE_BUFFER_SIZE);
		emitter.authorizeUpdatesForSubscriptionQueryWithId(query.getIdentifier(), decisions, annotation);
		emitter.emit(alwaysTrue(update), update);
		
		create(registration.getUpdates())
				.expectNextMatches(matchesIgnoringIdentifier(Object.class, update))
				.verifyTimeout(DEFAULT_TIMEOUT);
	}
	
	@Test
	void when_emitMultipleInstancesAsArray_with_permit_and_openFilter_then_update() {
		var query = new GenericSubscriptionQueryMessage<>(new Object(), ResponseTypes.instanceOf(FlaggedUpdateResponseType.class),
				ResponseTypes.multipleInstancesOf(FlaggedUpdateResponseType.class));
		var decisions = Flux.just(AuthorizationDecision.PERMIT);
		var annotation = PreHandleEnforce.class;
		var update = GenericSubscriptionQueryUpdateMessage
				.<FlaggedUpdateResponseType>asUpdateMessage(new FlaggedUpdateResponseType[] { DEFAULT_UPDATE_RESPONSE_TYPE, DEFAULT_UPDATE_RESPONSE_TYPE });

		var registration = emitter.registerUpdateHandler(query, DEFAULT_UPDATE_BUFFER_SIZE);
		emitter.authorizeUpdatesForSubscriptionQueryWithId(query.getIdentifier(), decisions, annotation);
		emitter.emit(alwaysTrue(update), update);
		
		create(registration.getUpdates())
				.expectNextMatches(matchesIgnoringIdentifier(Object.class, update))
				.verifyTimeout(DEFAULT_TIMEOUT);
	}
	
	@Test
	void when_emitOptionalInstance_with_permit_and_openFilter_then_update() {
		var query = new GenericSubscriptionQueryMessage<>(new Object(), ResponseTypes.instanceOf(FlaggedUpdateResponseType.class),
				ResponseTypes.optionalInstanceOf(FlaggedUpdateResponseType.class));
		var decisions = Flux.just(AuthorizationDecision.PERMIT);
		var annotation = PreHandleEnforce.class;
		var update = GenericSubscriptionQueryUpdateMessage
				.<FlaggedUpdateResponseType>asUpdateMessage(Optional.of(DEFAULT_UPDATE_RESPONSE_TYPE));

		var registration = emitter.registerUpdateHandler(query, DEFAULT_UPDATE_BUFFER_SIZE);
		emitter.authorizeUpdatesForSubscriptionQueryWithId(query.getIdentifier(), decisions, annotation);
		emitter.emit(alwaysTrue(update), update);
		
		create(registration.getUpdates())
				.expectNextMatches(matchesIgnoringIdentifier(Object.class, update))
				.verifyTimeout(DEFAULT_TIMEOUT);
	}
	
	@Test
	void when_emit_with_preHandleEnforce_deny_then_accessDenied() {
		Predicate<SubscriptionQueryMessage<?, ?, FlaggedUpdateResponseType>> filter = subscriptionQueryMessage -> true;
		var query = new GenericSubscriptionQueryMessage<>(new Object(), ResponseTypes.instanceOf(FlaggedUpdateResponseType.class),
				ResponseTypes.instanceOf(FlaggedUpdateResponseType.class));
		var decisions = Flux.just(AuthorizationDecision.DENY);
		var annotation = PreHandleEnforce.class;
		var update = GenericSubscriptionQueryUpdateMessage
				.<FlaggedUpdateResponseType>asUpdateMessage(DEFAULT_UPDATE_RESPONSE_TYPE);

		var registration = emitter.registerUpdateHandler(query, DEFAULT_UPDATE_BUFFER_SIZE);
		emitter.authorizeUpdatesForSubscriptionQueryWithId(query.getIdentifier(), decisions, annotation);
		
		emitter.emit(filter, update);
		
		create(registration.getUpdates())
				.expectError(AccessDeniedException.class)
				.verify(DEFAULT_TIMEOUT);
	}
	
	@Test
	void when_emit_with_preHandleEnforce_indeterminate_then_accessDenied() {
		Predicate<SubscriptionQueryMessage<?, ?, FlaggedUpdateResponseType>> filter = subscriptionQueryMessage -> true;
		var query = new GenericSubscriptionQueryMessage<>(new Object(), ResponseTypes.instanceOf(FlaggedUpdateResponseType.class),
				ResponseTypes.instanceOf(FlaggedUpdateResponseType.class));
		var decisions = Flux.just(AuthorizationDecision.INDETERMINATE);
		var annotation = PreHandleEnforce.class;
		var update = GenericSubscriptionQueryUpdateMessage
				.<FlaggedUpdateResponseType>asUpdateMessage(DEFAULT_UPDATE_RESPONSE_TYPE);

		var registration = emitter.registerUpdateHandler(query, DEFAULT_UPDATE_BUFFER_SIZE);
		emitter.authorizeUpdatesForSubscriptionQueryWithId(query.getIdentifier(), decisions, annotation);
		
		emitter.emit(filter, update);
		
		create(registration.getUpdates())
				.expectError(AccessDeniedException.class)
				.verify(DEFAULT_TIMEOUT);
	}
	
	@Test
	void when_emit_with_preHandleEnforce_notApplicable_then_accessDenied() {
		Predicate<SubscriptionQueryMessage<?, ?, FlaggedUpdateResponseType>> filter = subscriptionQueryMessage -> true;
		var query = new GenericSubscriptionQueryMessage<>(new Object(), ResponseTypes.instanceOf(FlaggedUpdateResponseType.class),
				ResponseTypes.instanceOf(FlaggedUpdateResponseType.class));
		var decisions = Flux.just(AuthorizationDecision.NOT_APPLICABLE);
		var annotation = PreHandleEnforce.class;
		var update = GenericSubscriptionQueryUpdateMessage
				.<FlaggedUpdateResponseType>asUpdateMessage(DEFAULT_UPDATE_RESPONSE_TYPE);

		var registration = emitter.registerUpdateHandler(query, DEFAULT_UPDATE_BUFFER_SIZE);
		emitter.authorizeUpdatesForSubscriptionQueryWithId(query.getIdentifier(), decisions, annotation);
		
		emitter.emit(filter, update);
		
		create(registration.getUpdates())
				.expectError(AccessDeniedException.class)
				.verify(DEFAULT_TIMEOUT);
	}
	
	@Test
	void when_emit_with_preHandleEnforce_permit_then_update() {
		Predicate<SubscriptionQueryMessage<?, ?, FlaggedUpdateResponseType>> filter = subscriptionQueryMessage -> true;
		var query = new GenericSubscriptionQueryMessage<>(new Object(), ResponseTypes.instanceOf(FlaggedUpdateResponseType.class),
				ResponseTypes.instanceOf(FlaggedUpdateResponseType.class));
		var decisions = Flux.just(AuthorizationDecision.PERMIT);
		var annotation = PreHandleEnforce.class;
		var update = GenericSubscriptionQueryUpdateMessage
				.<FlaggedUpdateResponseType>asUpdateMessage(DEFAULT_UPDATE_RESPONSE_TYPE);

		var registration = emitter.registerUpdateHandler(query, DEFAULT_UPDATE_BUFFER_SIZE);
		emitter.authorizeUpdatesForSubscriptionQueryWithId(query.getIdentifier(), decisions, annotation);
		
		emitter.emit(filter, update);
		
		create(registration.getUpdates())
				.expectNextMatches(matchesIgnoringIdentifier(Object.class, update))
				.verifyTimeout(DEFAULT_TIMEOUT);
	}
	
	@Test
	void when_emit_with_immediateDeny_then_accessDenied() {
		Predicate<SubscriptionQueryMessage<?, ?, FlaggedUpdateResponseType>> filter = subscriptionQueryMessage -> true;
		var query = new GenericSubscriptionQueryMessage<>(new Object(), ResponseTypes.instanceOf(FlaggedUpdateResponseType.class),
				ResponseTypes.instanceOf(FlaggedUpdateResponseType.class));
		var update = GenericSubscriptionQueryUpdateMessage
				.<FlaggedUpdateResponseType>asUpdateMessage(DEFAULT_UPDATE_RESPONSE_TYPE);

		var registration = emitter.registerUpdateHandler(query, DEFAULT_UPDATE_BUFFER_SIZE);
		emitter.immediatelyDenySubscriptionWithId(query.getIdentifier());
		
		emitter.emit(filter, update);
		
		create(registration.getUpdates())
				.expectNextMatches(matchesIgnoringIdentifier(Object.class, GenericSubscriptionQueryUpdateMessage.asUpdateMessage(FlaggedUpdateResponseType.class, new AccessDeniedException("Access Denied!"))))
				.verifyComplete();
	}
	
	@Test
	void when_multipleEmit_with_preHandleEnforce_deny_then_accessDenied() {
		Predicate<SubscriptionQueryMessage<?, ?, FlaggedUpdateResponseType>> filter = subscriptionQueryMessage -> true;
		var query = new GenericSubscriptionQueryMessage<>(new Object(), ResponseTypes.instanceOf(FlaggedUpdateResponseType.class),
				ResponseTypes.instanceOf(FlaggedUpdateResponseType.class));
		Many<AuthorizationDecision> decisionSink = Sinks.many().unicast().onBackpressureBuffer();
		var annotation = PreHandleEnforce.class;
		var update = GenericSubscriptionQueryUpdateMessage
				.<FlaggedUpdateResponseType>asUpdateMessage(DEFAULT_UPDATE_RESPONSE_TYPE);

		var registration = emitter.registerUpdateHandler(query, DEFAULT_UPDATE_BUFFER_SIZE);
		emitter.authorizeUpdatesForSubscriptionQueryWithId(query.getIdentifier(), decisionSink.asFlux(), annotation);
		
		create(registration.getUpdates())
				.then(() -> emitter.emit(filter, update))
				.then(() -> decisionSink.tryEmitNext(AuthorizationDecision.DENY))
				.then(() -> emitter.emit(filter, update))
				.then(() -> decisionSink.tryEmitNext(AuthorizationDecision.DENY))
				.then(() -> emitter.emit(filter, update))
				.expectError(AccessDeniedException.class)
				.verify(DEFAULT_TIMEOUT);
	}
	
	@Test
	void when_multipleEmit_with_preHandleEnforce_permitThenDeny_then_updateTwiceThenAccessDenied() {
		Predicate<SubscriptionQueryMessage<?, ?, FlaggedUpdateResponseType>> filter = subscriptionQueryMessage -> true;
		var query = new GenericSubscriptionQueryMessage<>(new Object(), ResponseTypes.instanceOf(FlaggedUpdateResponseType.class),
				ResponseTypes.instanceOf(FlaggedUpdateResponseType.class));
		Many<AuthorizationDecision> decisionSink = Sinks.many().unicast().onBackpressureBuffer();
		var annotation = PreHandleEnforce.class;
		var update = GenericSubscriptionQueryUpdateMessage
				.<FlaggedUpdateResponseType>asUpdateMessage(DEFAULT_UPDATE_RESPONSE_TYPE);

		var registration = emitter.registerUpdateHandler(query, DEFAULT_UPDATE_BUFFER_SIZE);
		emitter.authorizeUpdatesForSubscriptionQueryWithId(query.getIdentifier(), decisionSink.asFlux(), annotation);
		
		create(registration.getUpdates())
				.then(() -> emitter.emit(filter, update))
				.then(() -> decisionSink.tryEmitNext(AuthorizationDecision.PERMIT))
				.then(() -> emitter.emit(filter, update))
				.then(() -> decisionSink.tryEmitNext(AuthorizationDecision.DENY))
				.then(() -> emitter.emit(filter, update))
				.expectNextMatches(matchesIgnoringIdentifier(Object.class, update))
				.expectNextMatches(matchesIgnoringIdentifier(Object.class, update))
				.expectError(AccessDeniedException.class)
				.verify(DEFAULT_TIMEOUT);
	}
	
	@Test
	void when_multipleEmit_with_preHandleEnforce_denyThenPermit_then_accessDenied() {
		Predicate<SubscriptionQueryMessage<?, ?, FlaggedUpdateResponseType>> filter = subscriptionQueryMessage -> true;
		var query = new GenericSubscriptionQueryMessage<>(new Object(), ResponseTypes.instanceOf(FlaggedUpdateResponseType.class),
				ResponseTypes.instanceOf(FlaggedUpdateResponseType.class));
		Many<AuthorizationDecision> decisionSink = Sinks.many().unicast().onBackpressureBuffer();
		var annotation = PreHandleEnforce.class;
		var update = GenericSubscriptionQueryUpdateMessage
				.<FlaggedUpdateResponseType>asUpdateMessage(DEFAULT_UPDATE_RESPONSE_TYPE);

		var registration = emitter.registerUpdateHandler(query, DEFAULT_UPDATE_BUFFER_SIZE);
		emitter.authorizeUpdatesForSubscriptionQueryWithId(query.getIdentifier(), decisionSink.asFlux(), annotation);
		
		create(registration.getUpdates())
				.then(() -> emitter.emit(filter, update))
				.then(() -> decisionSink.tryEmitNext(AuthorizationDecision.DENY))
				.then(() -> emitter.emit(filter, update))
				.then(() -> decisionSink.tryEmitNext(AuthorizationDecision.PERMIT))
				.then(() -> emitter.emit(filter, update))
				.expectError(AccessDeniedException.class)
				.verify(DEFAULT_TIMEOUT);
	}
	
	@Test
	void when_multipleEmit_with_preHandleEnforce_permit_then_updateAll() {
		Predicate<SubscriptionQueryMessage<?, ?, FlaggedUpdateResponseType>> filter = subscriptionQueryMessage -> true;
		var query = new GenericSubscriptionQueryMessage<>(new Object(), ResponseTypes.instanceOf(FlaggedUpdateResponseType.class),
				ResponseTypes.instanceOf(FlaggedUpdateResponseType.class));
		Many<AuthorizationDecision> decisionSink = Sinks.many().unicast().onBackpressureBuffer();
		var annotation = PreHandleEnforce.class;
		var update = GenericSubscriptionQueryUpdateMessage
				.<FlaggedUpdateResponseType>asUpdateMessage(DEFAULT_UPDATE_RESPONSE_TYPE);

		var registration = emitter.registerUpdateHandler(query, DEFAULT_UPDATE_BUFFER_SIZE);
		emitter.authorizeUpdatesForSubscriptionQueryWithId(query.getIdentifier(), decisionSink.asFlux(), annotation);
		
		create(registration.getUpdates())
				.then(() -> emitter.emit(filter, update))
				.then(() -> decisionSink.tryEmitNext(AuthorizationDecision.PERMIT))
				.then(() -> emitter.emit(filter, update))
				.then(() -> decisionSink.tryEmitNext(AuthorizationDecision.PERMIT))
				.then(() -> emitter.emit(filter, update))
				.expectNextMatches(matchesIgnoringIdentifier(Object.class, update))
				.expectNextMatches(matchesIgnoringIdentifier(Object.class, update))
				.expectNextMatches(matchesIgnoringIdentifier(Object.class, update))
				.verifyTimeout(DEFAULT_TIMEOUT);
	}
	
	@Test
	void when_multipleEmit_with_enforceDropUpdatesWhileDenied_deny_then_noEvent() {
		Predicate<SubscriptionQueryMessage<?, ?, FlaggedUpdateResponseType>> filter = subscriptionQueryMessage -> true;
		var query = new GenericSubscriptionQueryMessage<>(new Object(), ResponseTypes.instanceOf(FlaggedUpdateResponseType.class),
				ResponseTypes.instanceOf(FlaggedUpdateResponseType.class));
		Many<AuthorizationDecision> decisionSink = Sinks.many().unicast().onBackpressureBuffer();
		var annotation = EnforceDropUpdatesWhileDenied.class;
		var update = GenericSubscriptionQueryUpdateMessage
				.<FlaggedUpdateResponseType>asUpdateMessage(DEFAULT_UPDATE_RESPONSE_TYPE);

		var registration = emitter.registerUpdateHandler(query, DEFAULT_UPDATE_BUFFER_SIZE);
		emitter.authorizeUpdatesForSubscriptionQueryWithId(query.getIdentifier(), decisionSink.asFlux(), annotation);
		
		create(registration.getUpdates())
				.then(() -> emitter.emit(filter, update))
				.then(() -> decisionSink.tryEmitNext(AuthorizationDecision.DENY))
				.then(() -> emitter.emit(filter, update))
				.then(() -> decisionSink.tryEmitNext(AuthorizationDecision.DENY))
				.then(() -> emitter.emit(filter, update))
				.verifyTimeout(DEFAULT_TIMEOUT);
	}
	
	@Test
	void when_multipleEmit_with_enforceDropUpdatesWhileDenied_permitThenDeny_then_updateTwice() {
		Predicate<SubscriptionQueryMessage<?, ?, FlaggedUpdateResponseType>> filter = subscriptionQueryMessage -> true;
		var query = new GenericSubscriptionQueryMessage<>(new Object(), ResponseTypes.instanceOf(FlaggedUpdateResponseType.class),
				ResponseTypes.instanceOf(FlaggedUpdateResponseType.class));
		Many<AuthorizationDecision> decisionSink = Sinks.many().unicast().onBackpressureBuffer();
		var annotation = EnforceDropUpdatesWhileDenied.class;
		var update = GenericSubscriptionQueryUpdateMessage
				.<FlaggedUpdateResponseType>asUpdateMessage(DEFAULT_UPDATE_RESPONSE_TYPE);

		var registration = emitter.registerUpdateHandler(query, DEFAULT_UPDATE_BUFFER_SIZE);
		emitter.authorizeUpdatesForSubscriptionQueryWithId(query.getIdentifier(), decisionSink.asFlux(), annotation);
		
		create(registration.getUpdates())
				.then(() -> emitter.emit(filter, update))
				.then(() -> decisionSink.tryEmitNext(AuthorizationDecision.PERMIT))
				.then(() -> emitter.emit(filter, update))
				.then(() -> decisionSink.tryEmitNext(AuthorizationDecision.DENY))
				.then(() -> emitter.emit(filter, update))
				.expectNextMatches(matchesIgnoringIdentifier(Object.class, update))
				.expectNextMatches(matchesIgnoringIdentifier(Object.class, update))
				.verifyTimeout(DEFAULT_TIMEOUT);
	}
	
	@Test
	void when_multipleEmit_with_enforceDropUpdatesWhileDenied_permit_then_updateAll() {
		Predicate<SubscriptionQueryMessage<?, ?, FlaggedUpdateResponseType>> filter = subscriptionQueryMessage -> true;
		var query = new GenericSubscriptionQueryMessage<>(new Object(), ResponseTypes.instanceOf(FlaggedUpdateResponseType.class),
				ResponseTypes.instanceOf(FlaggedUpdateResponseType.class));
		Many<AuthorizationDecision> decisionSink = Sinks.many().unicast().onBackpressureBuffer();
		var annotation = EnforceDropUpdatesWhileDenied.class;
		var update = GenericSubscriptionQueryUpdateMessage
				.<FlaggedUpdateResponseType>asUpdateMessage(DEFAULT_UPDATE_RESPONSE_TYPE);

		var registration = emitter.registerUpdateHandler(query, DEFAULT_UPDATE_BUFFER_SIZE);
		emitter.authorizeUpdatesForSubscriptionQueryWithId(query.getIdentifier(), decisionSink.asFlux(), annotation);
		
		create(registration.getUpdates())
				.then(() -> emitter.emit(filter, update))
				.then(() -> decisionSink.tryEmitNext(AuthorizationDecision.PERMIT))
				.then(() -> emitter.emit(filter, update))
				.then(() -> decisionSink.tryEmitNext(AuthorizationDecision.PERMIT))
				.then(() -> emitter.emit(filter, update))
				.expectNextMatches(matchesIgnoringIdentifier(Object.class, update))
				.expectNextMatches(matchesIgnoringIdentifier(Object.class, update))
				.expectNextMatches(matchesIgnoringIdentifier(Object.class, update))
				.verifyTimeout(DEFAULT_TIMEOUT);
	}
	
	@Test
	void when_multipleEmit_with_enforceRecoverableUpdatesIfDenied_deny_then_accessDenied() {
		Predicate<SubscriptionQueryMessage<?, ?, FlaggedUpdateResponseType>> filter = subscriptionQueryMessage -> true;
		var query = new GenericSubscriptionQueryMessage<>(new Object(), ResponseTypes.instanceOf(FlaggedUpdateResponseType.class),
				ResponseTypes.instanceOf(FlaggedUpdateResponseType.class));
		Many<AuthorizationDecision> decisionSink = Sinks.many().unicast().onBackpressureBuffer();
		var annotation = EnforceRecoverableUpdatesIfDenied.class;
		var update = GenericSubscriptionQueryUpdateMessage
				.<FlaggedUpdateResponseType>asUpdateMessage(DEFAULT_UPDATE_RESPONSE_TYPE);

		var registration = emitter.registerUpdateHandler(query, DEFAULT_UPDATE_BUFFER_SIZE);
		emitter.authorizeUpdatesForSubscriptionQueryWithId(query.getIdentifier(), decisionSink.asFlux(), annotation);
		
		create(registration.getUpdates())
				.then(() -> emitter.emit(filter, update))
				.then(() -> decisionSink.tryEmitNext(AuthorizationDecision.DENY))
				.then(() -> emitter.emit(filter, update))
				.then(() -> decisionSink.tryEmitNext(AuthorizationDecision.DENY))
				.then(() -> emitter.emit(filter, update))
				.expectError(AccessDeniedException.class)
				.verify(DEFAULT_TIMEOUT);
	}
	
	@Test
	void when_multipleEmit_with_enforceRecoverableUpdatesIfDenied_permitThenDeny_then_updateTwiceThenAccessDenied() {
		Predicate<SubscriptionQueryMessage<?, ?, FlaggedUpdateResponseType>> filter = subscriptionQueryMessage -> true;
		var query = new GenericSubscriptionQueryMessage<>(new Object(), ResponseTypes.instanceOf(FlaggedUpdateResponseType.class),
				ResponseTypes.instanceOf(FlaggedUpdateResponseType.class));
		Many<AuthorizationDecision> decisionSink = Sinks.many().unicast().onBackpressureBuffer();
		var annotation = EnforceRecoverableUpdatesIfDenied.class;
		var update = GenericSubscriptionQueryUpdateMessage
				.<FlaggedUpdateResponseType>asUpdateMessage(DEFAULT_UPDATE_RESPONSE_TYPE);

		var registration = emitter.registerUpdateHandler(query, DEFAULT_UPDATE_BUFFER_SIZE);
		emitter.authorizeUpdatesForSubscriptionQueryWithId(query.getIdentifier(), decisionSink.asFlux(), annotation);
		
		create(registration.getUpdates())
				.then(() -> emitter.emit(filter, update))
				.then(() -> decisionSink.tryEmitNext(AuthorizationDecision.PERMIT))
				.then(() -> emitter.emit(filter, update))
				.then(() -> decisionSink.tryEmitNext(AuthorizationDecision.DENY))
				.then(() -> emitter.emit(filter, update))
				.expectNextMatches(matchesIgnoringIdentifier(Object.class, update))
				.expectNextMatches(matchesIgnoringIdentifier(Object.class, update))
				.expectError(AccessDeniedException.class)
				.verify(DEFAULT_TIMEOUT);
	}
	
	@Test
	void when_multipleEmit_with_enforceRecoverableUpdatesIfDenied_denyThenPermit_then_accessDenied() {
		Predicate<SubscriptionQueryMessage<?, ?, FlaggedUpdateResponseType>> filter = subscriptionQueryMessage -> true;
		var query = new GenericSubscriptionQueryMessage<>(new Object(), ResponseTypes.instanceOf(FlaggedUpdateResponseType.class),
				ResponseTypes.instanceOf(FlaggedUpdateResponseType.class));
		Many<AuthorizationDecision> decisionSink = Sinks.many().unicast().onBackpressureBuffer();
		var annotation = EnforceRecoverableUpdatesIfDenied.class;
		var update = GenericSubscriptionQueryUpdateMessage
				.<FlaggedUpdateResponseType>asUpdateMessage(DEFAULT_UPDATE_RESPONSE_TYPE);

		var registration = emitter.registerUpdateHandler(query, DEFAULT_UPDATE_BUFFER_SIZE);
		emitter.authorizeUpdatesForSubscriptionQueryWithId(query.getIdentifier(), decisionSink.asFlux(), annotation);
		
		create(registration.getUpdates())
				.then(() -> emitter.emit(filter, update))
				.then(() -> decisionSink.tryEmitNext(AuthorizationDecision.DENY))
				.then(() -> emitter.emit(filter, update))
				.then(() -> decisionSink.tryEmitNext(AuthorizationDecision.PERMIT))
				.then(() -> emitter.emit(filter, update))
				.expectError(AccessDeniedException.class)
				.verify(DEFAULT_TIMEOUT);
	}
	
	@Test
	void when_multipleEmit_with_enforceRecoverableUpdatesIfDenied_permit_then_updateAll() {
		Predicate<SubscriptionQueryMessage<?, ?, FlaggedUpdateResponseType>> filter = subscriptionQueryMessage -> true;
		var query = new GenericSubscriptionQueryMessage<>(new Object(), ResponseTypes.instanceOf(FlaggedUpdateResponseType.class),
				ResponseTypes.instanceOf(FlaggedUpdateResponseType.class));
		Many<AuthorizationDecision> decisionSink = Sinks.many().unicast().onBackpressureBuffer();
		var annotation = EnforceRecoverableUpdatesIfDenied.class;
		var update = GenericSubscriptionQueryUpdateMessage
				.<FlaggedUpdateResponseType>asUpdateMessage(DEFAULT_UPDATE_RESPONSE_TYPE);

		var registration = emitter.registerUpdateHandler(query, DEFAULT_UPDATE_BUFFER_SIZE);
		emitter.authorizeUpdatesForSubscriptionQueryWithId(query.getIdentifier(), decisionSink.asFlux(), annotation);
		
		create(registration.getUpdates())
				.then(() -> emitter.emit(filter, update))
				.then(() -> decisionSink.tryEmitNext(AuthorizationDecision.PERMIT))
				.then(() -> emitter.emit(filter, update))
				.then(() -> decisionSink.tryEmitNext(AuthorizationDecision.PERMIT))
				.then(() -> emitter.emit(filter, update))
				.expectNextMatches(matchesIgnoringIdentifier(Object.class, update))
				.expectNextMatches(matchesIgnoringIdentifier(Object.class, update))
				.expectNextMatches(matchesIgnoringIdentifier(Object.class, update))
				.verifyTimeout(DEFAULT_TIMEOUT);
	}

	@Test
	void when_emit_with_closedFilter_and_existingSubscriptionQueries_then_noEvent() {
		var queryA = new GenericSubscriptionQueryMessage<>(new Object(), ResponseTypes.instanceOf(Object.class),
				ResponseTypes.instanceOf(FlaggedUpdateResponseType.class));
		var queryB = new GenericSubscriptionQueryMessage<>(new Object(), ResponseTypes.instanceOf(Object.class),
				ResponseTypes.instanceOf(FlaggedUpdateResponseType.class));
		
		var decisions = Flux.just(AuthorizationDecision.PERMIT);
		var annotation = PreHandleEnforce.class;

		var registrationA = emitter.registerUpdateHandler(queryA, DEFAULT_UPDATE_BUFFER_SIZE);
		var registrationB = emitter.registerUpdateHandler(queryB, DEFAULT_UPDATE_BUFFER_SIZE);

		emitter.authorizeUpdatesForSubscriptionQueryWithId(queryA.getIdentifier(), decisions, annotation);
		emitter.authorizeUpdatesForSubscriptionQueryWithId(queryB.getIdentifier(), decisions, annotation);

		Predicate<SubscriptionQueryMessage<?, ?, FlaggedUpdateResponseType>> filter = subscriptionQueryMessage -> false;
		var update = GenericSubscriptionQueryUpdateMessage
				.<FlaggedUpdateResponseType>asUpdateMessage(DEFAULT_UPDATE_RESPONSE_TYPE);

		emitter.emit(filter, update);
		
		create(registrationA.getUpdates())
				.verifyTimeout(DEFAULT_TIMEOUT);
		create(registrationB.getUpdates())
				.verifyTimeout(DEFAULT_TIMEOUT);
	}

	@Test
	void when_emit_with_openFilter_and_existingSubscriptionQueries_then_bothUpdate() {
		var queryA = new GenericSubscriptionQueryMessage<>(new Object(), ResponseTypes.instanceOf(Object.class),
				ResponseTypes.instanceOf(FlaggedUpdateResponseType.class));
		var queryB = new GenericSubscriptionQueryMessage<>(new Object(), ResponseTypes.instanceOf(Object.class),
				ResponseTypes.instanceOf(FlaggedUpdateResponseType.class));
		
		var decisions = Flux.just(AuthorizationDecision.PERMIT);
		var annotation = PreHandleEnforce.class;

		var registrationA = emitter.registerUpdateHandler(queryA, DEFAULT_UPDATE_BUFFER_SIZE);
		var registrationB = emitter.registerUpdateHandler(queryB, DEFAULT_UPDATE_BUFFER_SIZE);

		emitter.authorizeUpdatesForSubscriptionQueryWithId(queryA.getIdentifier(), decisions, annotation);
		emitter.authorizeUpdatesForSubscriptionQueryWithId(queryB.getIdentifier(), decisions, annotation);

		Predicate<SubscriptionQueryMessage<?, ?, FlaggedUpdateResponseType>> filter = subscriptionQueryMessage -> true;
		var update = GenericSubscriptionQueryUpdateMessage
				.<FlaggedUpdateResponseType>asUpdateMessage(DEFAULT_UPDATE_RESPONSE_TYPE);
		
		emitter.emit(filter, update);
		
		create(registrationA.getUpdates())
				.expectNextMatches(matchesIgnoringIdentifier(Object.class, update))
				.verifyTimeout(DEFAULT_TIMEOUT);
		create(registrationB.getUpdates())
				.expectNextMatches(matchesIgnoringIdentifier(Object.class, update))
				.verifyTimeout(DEFAULT_TIMEOUT);
	}

	@Test
	void when_emit_with_paritallyOpenFilter_and_existingSubscriptionQueries_then_oneUpdate() {
		var queryA = new GenericSubscriptionQueryMessage<>(new Object(), ResponseTypes.instanceOf(Object.class),
				ResponseTypes.instanceOf(FlaggedUpdateResponseType.class));
		var queryB = new FlaggedSubscriptionQueryMessage<>(new Object(), ResponseTypes.instanceOf(Object.class),
				ResponseTypes.instanceOf(FlaggedUpdateResponseType.class));
		
		var decisions = Flux.just(AuthorizationDecision.PERMIT);
		var annotation = PreHandleEnforce.class;

		var registrationA = emitter.registerUpdateHandler(queryA, DEFAULT_UPDATE_BUFFER_SIZE);
		var registrationB = emitter.registerUpdateHandler(queryB, DEFAULT_UPDATE_BUFFER_SIZE);

		emitter.authorizeUpdatesForSubscriptionQueryWithId(queryA.getIdentifier(), decisions, annotation);
		emitter.authorizeUpdatesForSubscriptionQueryWithId(queryB.getIdentifier(), decisions, annotation);

		Predicate<SubscriptionQueryMessage<?, ?, FlaggedUpdateResponseType>> filter = subscriptionQueryMessage -> FlaggedSubscriptionQueryMessage.class
				.isAssignableFrom(subscriptionQueryMessage.getClass());
		var update = GenericSubscriptionQueryUpdateMessage
				.<FlaggedUpdateResponseType>asUpdateMessage(DEFAULT_UPDATE_RESPONSE_TYPE);
		
		emitter.emit(filter, update);
		
		create(registrationA.getUpdates())
				.verifyTimeout(DEFAULT_TIMEOUT);
		create(registrationB.getUpdates())
				.expectNextMatches(matchesIgnoringIdentifier(Object.class, update))
				.verifyTimeout(DEFAULT_TIMEOUT);
	}
	
	@Test
	void when_emit_with_permit_and_openFilter_and_dispatchInterceptor_then_interceptUpdate() {
		Predicate<SubscriptionQueryMessage<?, ?, FlaggedUpdateResponseType>> filter = subscriptionQueryMessage -> true;
		var query = new GenericSubscriptionQueryMessage<>(new Object(), ResponseTypes.instanceOf(FlaggedUpdateResponseType.class),
				ResponseTypes.instanceOf(FlaggedUpdateResponseType.class));
		var decisions = Flux.just(AuthorizationDecision.PERMIT);
		var annotation = PreHandleEnforce.class;
		var update = GenericSubscriptionQueryUpdateMessage
				.<FlaggedUpdateResponseType>asUpdateMessage(DEFAULT_UPDATE_RESPONSE_TYPE);
		MessageDispatchInterceptor<? super SubscriptionQueryUpdateMessage<?>> interceptor = list -> (integer, updateMessage) -> {
			var newPayload = new FlaggedUpdateResponseType();
			newPayload.setSomeInt(1);
			return GenericSubscriptionQueryUpdateMessage.<FlaggedUpdateResponseType>asUpdateMessage(newPayload);
		};
		
		emitter.registerDispatchInterceptor(interceptor);

		var registration = emitter.registerUpdateHandler(query, DEFAULT_UPDATE_BUFFER_SIZE);
		emitter.authorizeUpdatesForSubscriptionQueryWithId(query.getIdentifier(), decisions, annotation);
		
		emitter.emit(filter, update);
		
		create(registration.getUpdates())
				.expectNextMatches(next -> ((FlaggedUpdateResponseType)next.getPayload()).getSomeInt() == 1)
				.verifyTimeout(DEFAULT_TIMEOUT);
	}
	
	@Test
	void when_complete_then_complete() {
		var query = new GenericSubscriptionQueryMessage<>(new Object(), ResponseTypes.instanceOf(Object.class),
				ResponseTypes.instanceOf(FlaggedUpdateResponseType.class));
		
		var decisions = Flux.just(AuthorizationDecision.PERMIT);
		var annotation = PreHandleEnforce.class;
		
		var registration = emitter.registerUpdateHandler(query, DEFAULT_UPDATE_BUFFER_SIZE);
		emitter.authorizeUpdatesForSubscriptionQueryWithId(query.getIdentifier(), decisions, annotation);
		
		Predicate<SubscriptionQueryMessage<?, ?, ?>> filter = subscriptionQueryMessage -> true;
		
		create(registration.getUpdates())
				.then(() -> emitter.complete(filter))
				.verifyComplete();
	}
	
	@Test
	void when_complete_and_unauthorized_then_noEvent() {
		var query = new GenericSubscriptionQueryMessage<>(new Object(), ResponseTypes.instanceOf(Object.class),
				ResponseTypes.instanceOf(FlaggedUpdateResponseType.class));
		
		var registration = emitter.registerUpdateHandler(query, DEFAULT_UPDATE_BUFFER_SIZE);
		
		Predicate<SubscriptionQueryMessage<?, ?, ?>> filter = subscriptionQueryMessage -> true;
		
		create(registration.getUpdates())
				.then(() -> emitter.complete(filter))
				.verifyTimeout(DEFAULT_TIMEOUT);
	}

	@Test
	void when_completeThenEmit_then_complete() {
		var query = new GenericSubscriptionQueryMessage<>(new Object(), ResponseTypes.instanceOf(Object.class),
				ResponseTypes.instanceOf(FlaggedUpdateResponseType.class));
		
		var decisions = Flux.just(AuthorizationDecision.PERMIT);
		var annotation = PreHandleEnforce.class;
		
		var registration = emitter.registerUpdateHandler(query, DEFAULT_UPDATE_BUFFER_SIZE);
		emitter.authorizeUpdatesForSubscriptionQueryWithId(query.getIdentifier(), decisions, annotation);
		
		Predicate<SubscriptionQueryMessage<?, ?, ?>> completionFilter = subscriptionQueryMessage -> true;
		Predicate<SubscriptionQueryMessage<?, ?, FlaggedUpdateResponseType>> emissionFilter = subscriptionQueryMessage -> true;
		var update = GenericSubscriptionQueryUpdateMessage
				.<FlaggedUpdateResponseType>asUpdateMessage(DEFAULT_UPDATE_RESPONSE_TYPE);
		
		create(registration.getUpdates())
				.then(() -> emitter.complete(completionFilter))
				.thenAwait(DEFAULT_TIMESTEP)
				.then(() -> emitter.emit(emissionFilter, update))
				.verifyComplete();
	}
	
	@Test
	void when_emitThenComplete_then_updateThenComplete() {
		var query = new GenericSubscriptionQueryMessage<>(new Object(), ResponseTypes.instanceOf(Object.class),
				ResponseTypes.instanceOf(FlaggedUpdateResponseType.class));
		
		var decisions = Flux.just(AuthorizationDecision.PERMIT);
		var annotation = PreHandleEnforce.class;
		
		var registration = emitter.registerUpdateHandler(query, DEFAULT_UPDATE_BUFFER_SIZE);
		emitter.authorizeUpdatesForSubscriptionQueryWithId(query.getIdentifier(), decisions, annotation);
		
		Predicate<SubscriptionQueryMessage<?, ?, ?>> completionFilter = subscriptionQueryMessage -> true;
		Predicate<SubscriptionQueryMessage<?, ?, FlaggedUpdateResponseType>> emissionFilter = subscriptionQueryMessage -> true;
		var update = GenericSubscriptionQueryUpdateMessage
				.<FlaggedUpdateResponseType>asUpdateMessage(DEFAULT_UPDATE_RESPONSE_TYPE);
		
		create(registration.getUpdates())
				.then(() -> emitter.emit(emissionFilter, update))
				.expectNextMatches(matchesIgnoringIdentifier(Object.class, update))
				.then(() -> emitter.complete(completionFilter))
				.verifyComplete();
	}

	@Test
	void when_complete_with_closedFilter_and_existingSubscriptionQueries_then_noEvent() {
		var queryA = new GenericSubscriptionQueryMessage<>(new Object(), ResponseTypes.instanceOf(Object.class),
				ResponseTypes.instanceOf(Object.class));
		var queryB = new GenericSubscriptionQueryMessage<>(new Object(), ResponseTypes.instanceOf(Object.class),
				ResponseTypes.instanceOf(Object.class));
		
		var decisions = Flux.just(AuthorizationDecision.PERMIT);
		var annotation = PreHandleEnforce.class;

		var registrationA = emitter.registerUpdateHandler(queryA, DEFAULT_UPDATE_BUFFER_SIZE);
		var registrationB = emitter.registerUpdateHandler(queryB, DEFAULT_UPDATE_BUFFER_SIZE);
		
		emitter.authorizeUpdatesForSubscriptionQueryWithId(queryA.getIdentifier(), decisions, annotation);
		emitter.authorizeUpdatesForSubscriptionQueryWithId(queryB.getIdentifier(), decisions, annotation);

		Predicate<SubscriptionQueryMessage<?, ?, ?>> filter = subscriptionQueryMessage -> false;
		
		create(registrationA.getUpdates())
				.then(() -> {
					create(registrationB.getUpdates())
							.then(() -> emitter.complete(filter))
							.verifyTimeout(DEFAULT_TIMEOUT);
				})
				.verifyTimeout(DEFAULT_TIMEOUT);
	}

	@Test
	void when_complete_with_openFilter_and_existingSubscriptionQueries_then_allComplete() {
		var queryA = new GenericSubscriptionQueryMessage<>(new Object(), ResponseTypes.instanceOf(Object.class),
				ResponseTypes.instanceOf(Object.class));
		var queryB = new GenericSubscriptionQueryMessage<>(new Object(), ResponseTypes.instanceOf(Object.class),
				ResponseTypes.instanceOf(Object.class));

		var decisions = Flux.just(AuthorizationDecision.PERMIT);
		var annotation = PreHandleEnforce.class;

		var registrationA = emitter.registerUpdateHandler(queryA, DEFAULT_UPDATE_BUFFER_SIZE);
		var registrationB = emitter.registerUpdateHandler(queryB, DEFAULT_UPDATE_BUFFER_SIZE);
		
		emitter.authorizeUpdatesForSubscriptionQueryWithId(queryA.getIdentifier(), decisions, annotation);
		emitter.authorizeUpdatesForSubscriptionQueryWithId(queryB.getIdentifier(), decisions, annotation);

		Predicate<SubscriptionQueryMessage<?, ?, ?>> filter = subscriptionQueryMessage -> true;
		
		create(registrationA.getUpdates())
				.then(() -> {
					create(registrationB.getUpdates())
							.then(() -> emitter.complete(filter))
							.verifyComplete();
				})
				.verifyComplete();
	}

	@Test
	void when_complete_with_partiallyOpenFilter_and_existingSubscriptionQueries_then_return() {
		var queryA = new GenericSubscriptionQueryMessage<>(new Object(), ResponseTypes.instanceOf(Object.class),
				ResponseTypes.multipleInstancesOf(Object.class));
		var queryB = new FlaggedSubscriptionQueryMessage<>(new Object(), ResponseTypes.instanceOf(Object.class),
				ResponseTypes.multipleInstancesOf(Object.class));

		var decisions = Flux.just(AuthorizationDecision.PERMIT);
		var annotation = PreHandleEnforce.class;

		var registrationA = emitter.registerUpdateHandler(queryA, DEFAULT_UPDATE_BUFFER_SIZE);
		var registrationB = emitter.registerUpdateHandler(queryB, DEFAULT_UPDATE_BUFFER_SIZE);
		
		emitter.authorizeUpdatesForSubscriptionQueryWithId(queryA.getIdentifier(), decisions, annotation);
		emitter.authorizeUpdatesForSubscriptionQueryWithId(queryB.getIdentifier(), decisions, annotation);

		Predicate<SubscriptionQueryMessage<?, ?, ?>> filter = subscriptionQueryMessage -> FlaggedSubscriptionQueryMessage.class
				.isAssignableFrom(subscriptionQueryMessage.getClass());
		
		create(registrationA.getUpdates())
				.then(() -> {
					create(registrationB.getUpdates())
							.then(() -> emitter.complete(filter))
							.verifyComplete();
				})
				.verifyTimeout(DEFAULT_TIMEOUT);
	}

	@Test
	void when_completeExceptionally_then_error() {
		var query = new GenericSubscriptionQueryMessage<>(new Object(), ResponseTypes.instanceOf(Object.class),
				ResponseTypes.instanceOf(FlaggedUpdateResponseType.class));
		
		var decisions = Flux.just(AuthorizationDecision.PERMIT);
		var annotation = PreHandleEnforce.class;
		
		var registration = emitter.registerUpdateHandler(query, DEFAULT_UPDATE_BUFFER_SIZE);
		emitter.authorizeUpdatesForSubscriptionQueryWithId(query.getIdentifier(), decisions, annotation);
		
		Predicate<SubscriptionQueryMessage<?, ?, ?>> filter = subscriptionQueryMessage -> true;
		var cause = new Throwable(DEFAULT_CAUSE);
		
		create(registration.getUpdates())
				.then(() -> emitter.completeExceptionally(filter, cause))
				.verifyErrorMessage(DEFAULT_CAUSE);
	}
	
	@Test
	@Disabled
	void when_completeExceptionally_and_unauthorized_then_complete() {
		var query = new GenericSubscriptionQueryMessage<>(new Object(), ResponseTypes.instanceOf(Object.class),
				ResponseTypes.instanceOf(FlaggedUpdateResponseType.class));
		
		var registration = emitter.registerUpdateHandler(query, DEFAULT_UPDATE_BUFFER_SIZE);
		
		Predicate<SubscriptionQueryMessage<?, ?, ?>> filter = subscriptionQueryMessage -> true;
		var cause = new Throwable(DEFAULT_CAUSE);
		
		create(registration.getUpdates())
				.then(() -> emitter.completeExceptionally(filter, cause))
				.verifyComplete();
	}

	@Test
	void when_completeExceptionally_with_closedFilter_and_existingSubscriptionQueries_then_noEvent() {
		var queryA = new GenericSubscriptionQueryMessage<>(new Object(), ResponseTypes.instanceOf(Object.class),
				ResponseTypes.instanceOf(Object.class));
		var queryB = new GenericSubscriptionQueryMessage<>(new Object(), ResponseTypes.instanceOf(Object.class),
				ResponseTypes.instanceOf(Object.class));
		
		var decisions = Flux.just(AuthorizationDecision.PERMIT);
		var annotation = PreHandleEnforce.class;

		var registrationA = emitter.registerUpdateHandler(queryA, DEFAULT_UPDATE_BUFFER_SIZE);
		var registrationB = emitter.registerUpdateHandler(queryB, DEFAULT_UPDATE_BUFFER_SIZE);
		
		emitter.authorizeUpdatesForSubscriptionQueryWithId(queryA.getIdentifier(), decisions, annotation);
		emitter.authorizeUpdatesForSubscriptionQueryWithId(queryB.getIdentifier(), decisions, annotation);

		Predicate<SubscriptionQueryMessage<?, ?, ?>> filter = subscriptionQueryMessage -> false;
		var cause = new Throwable(DEFAULT_CAUSE);
		
		create(registrationA.getUpdates())
				.then(() -> {
					create(registrationB.getUpdates())
							.then(() -> emitter.completeExceptionally(filter, cause))
							.verifyTimeout(DEFAULT_TIMEOUT);
				})
				.verifyTimeout(DEFAULT_TIMEOUT);
	}

	@Test
	void when_completeExceptionally_with_openFilter_and_existingSubscriptionQueries_then_return() {
		var queryA = new GenericSubscriptionQueryMessage<>(new Object(), ResponseTypes.instanceOf(Object.class),
				ResponseTypes.instanceOf(Object.class));
		var queryB = new GenericSubscriptionQueryMessage<>(new Object(), ResponseTypes.instanceOf(Object.class),
				ResponseTypes.instanceOf(Object.class));
		
		var decisions = Flux.just(AuthorizationDecision.PERMIT);
		var annotation = PreHandleEnforce.class;

		var registrationA = emitter.registerUpdateHandler(queryA, DEFAULT_UPDATE_BUFFER_SIZE);
		var registrationB = emitter.registerUpdateHandler(queryB, DEFAULT_UPDATE_BUFFER_SIZE);
		
		emitter.authorizeUpdatesForSubscriptionQueryWithId(queryA.getIdentifier(), decisions, annotation);
		emitter.authorizeUpdatesForSubscriptionQueryWithId(queryB.getIdentifier(), decisions, annotation);

		Predicate<SubscriptionQueryMessage<?, ?, ?>> filter = subscriptionQueryMessage -> true;
		var cause = new Throwable(DEFAULT_CAUSE);
		
		create(registrationA.getUpdates())
				.then(() -> {
					create(registrationB.getUpdates())
							.then(() -> emitter.completeExceptionally(filter, cause))
							.verifyErrorMessage(DEFAULT_CAUSE);
				})
				.verifyErrorMessage(DEFAULT_CAUSE);
	}

	@Test
	void when_completeExceptionally_with_partiallyOpenFilter_and_existingSubscriptionQueries_then_oneNoEventOneError() {
		var queryA = new GenericSubscriptionQueryMessage<>(new Object(), ResponseTypes.instanceOf(Object.class),
				ResponseTypes.multipleInstancesOf(Object.class));
		var queryB = new FlaggedSubscriptionQueryMessage<>(new Object(), ResponseTypes.instanceOf(Object.class),
				ResponseTypes.multipleInstancesOf(Object.class));
		
		var decisions = Flux.just(AuthorizationDecision.PERMIT);
		var annotation = PreHandleEnforce.class;

		var registrationA = emitter.registerUpdateHandler(queryA, DEFAULT_UPDATE_BUFFER_SIZE);
		var registrationB = emitter.registerUpdateHandler(queryB, DEFAULT_UPDATE_BUFFER_SIZE);
		
		emitter.authorizeUpdatesForSubscriptionQueryWithId(queryA.getIdentifier(), decisions, annotation);
		emitter.authorizeUpdatesForSubscriptionQueryWithId(queryB.getIdentifier(), decisions, annotation);

		Predicate<SubscriptionQueryMessage<?, ?, ?>> filter = subscriptionQueryMessage -> FlaggedSubscriptionQueryMessage.class
				.isAssignableFrom(subscriptionQueryMessage.getClass());
		var cause = new Throwable(DEFAULT_CAUSE);
		
		create(registrationA.getUpdates())
				.then(() -> {
					create(registrationB.getUpdates())
							.then(() -> emitter.completeExceptionally(filter, cause))
							.verifyErrorMessage(DEFAULT_CAUSE);
				})
				.verifyTimeout(DEFAULT_TIMEOUT);
	}

	@Test
	@SuppressWarnings("rawtypes")
	void when_dispatchInterceptorRegistered_then_contains() {
		MessageDispatchInterceptor<? super SubscriptionQueryUpdateMessage<?>> interceptor = list -> (integer, updateMessage) -> GenericSubscriptionQueryUpdateMessage.asUpdateMessage(5);
		assertFalse(((List)ReflectionTestUtils.getField(emitter, DISPATCH_INTERCEPTORS_FILED_NAME)).contains(interceptor));
		var registration = emitter.registerDispatchInterceptor(interceptor);
		assertTrue(((List)ReflectionTestUtils.getField(emitter, DISPATCH_INTERCEPTORS_FILED_NAME)).contains(interceptor));
		registration.cancel();
		assertFalse(((List)ReflectionTestUtils.getField(emitter, DISPATCH_INTERCEPTORS_FILED_NAME)).contains(interceptor));
	}

	@Test
	void when_unitOfWorkIsStartedAndTaskIsAdded_then_taskIsQueued() {
		SubscriptionQueryMessage<String, List<String>, String> subscriptionQueryMessage = new GenericSubscriptionQueryMessage<>(
				"some-payload", "chatMessages", ResponseTypes.multipleInstancesOf(String.class),
				ResponseTypes.instanceOf(String.class));
		var uow = DefaultUnitOfWork.startAndGet(subscriptionQueryMessage);

		var unitOfWorkTasks = uow.resources();
		assertEquals(0, unitOfWorkTasks.size());
		emitter.complete(q -> true);
		assertEquals(1, unitOfWorkTasks.size());
		uow.commit();// Cleanup to not interfere with other tests

	}

	@Test
	void when_updateHandlerRegistered_then_contains() {
		assertFalse(emitter.activeSubscriptions().contains(subscriptionQueryMessage));
		var registration = emitter.registerUpdateHandler(subscriptionQueryMessage, 1024);
		assertTrue(emitter.activeSubscriptions().contains(subscriptionQueryMessage));
		registration.getRegistration().cancel();
		assertFalse(emitter.activeSubscriptions().contains(subscriptionQueryMessage));
	}
	
	@Test
    void differentUpdateAreDisambiguatedAndWrongTypesAreFilteredBasedOnQueryTypes() {
        SubscriptionQueryMessage<String, List<String>, Integer> query = new GenericSubscriptionQueryMessage<>(
                "some-payload",
                "chatMessages",
                ResponseTypes.multipleInstancesOf(String.class),
                ResponseTypes.instanceOf(Integer.class)
        );

        UpdateHandlerRegistration<Object> result = emitter.registerUpdateHandler(
        		query,
                1024
        );
        
        var decisions = Flux.just(AuthorizationDecision.PERMIT);
		var annotation = PreHandleEnforce.class;
		
		emitter.authorizeUpdatesForSubscriptionQueryWithId(query.getIdentifier(), decisions, annotation);

        result.getUpdates().subscribe();
        emitter.emit(any -> true, "some-awesome-text");
        emitter.emit(any -> true, 1234);
        result.complete();

        create(result.getUpdates().map(Message::getPayload))
                .expectNext(1234)
                .verifyComplete();
    }
	
	@Test
    void updateResponseTypeFilteringWorksForMultipleInstanceOfWithArrayAndList() {
        SubscriptionQueryMessage<String, List<String>, List<String>> query = new GenericSubscriptionQueryMessage<>(
                "some-payload",
                "chatMessages",
                ResponseTypes.multipleInstancesOf(String.class),
                ResponseTypes.multipleInstancesOf(String.class)
        );

        UpdateHandlerRegistration<Object> result = emitter.registerUpdateHandler(
        		query,
                1024
        );
        
        var decisions = Flux.just(AuthorizationDecision.PERMIT);
		var annotation = PreHandleEnforce.class;
		
		emitter.authorizeUpdatesForSubscriptionQueryWithId(query.getIdentifier(), decisions, annotation);

        result.getUpdates().subscribe();
        emitter.emit(any -> true, "some-awesome-text");
        emitter.emit(any -> true, 1234);
        emitter.emit(any -> true, Optional.of("optional-payload"));
        emitter.emit(any -> true, Optional.empty());
        emitter.emit(any -> true, new String[] { "array-item-1", "array-item-2" });
        emitter.emit(any -> true, Arrays.asList("list-item-1", "list-item-2"));
        emitter.emit(any -> true, Flux.just("flux-item-1", "flux-item-2"));
        emitter.emit(any -> true, Mono.just("mono-item"));
        result.complete();

        create(result.getUpdates().map(Message::getPayload))
    			.expectNextMatches(actual -> equalTo(new String[] { "array-item-1", "array-item-2" }).matches(actual) )
    			.expectNextMatches(actual -> equalTo(Arrays.asList("list-item-1", "list-item-2")).matches(actual) )
                .verifyComplete();
    }
	
	@Test
    void updateResponseTypeFilteringWorksForOptionaInstanceOf() {
        SubscriptionQueryMessage<String, List<String>, Optional<String>> query = new GenericSubscriptionQueryMessage<>(
                "some-payload",
                "chatMessages",
                ResponseTypes.multipleInstancesOf(String.class),
                ResponseTypes.optionalInstanceOf(String.class)
        );

        UpdateHandlerRegistration<Object> result = emitter.registerUpdateHandler(
        		query,
                1024
        );
        
        var decisions = Flux.just(AuthorizationDecision.PERMIT);
		var annotation = PreHandleEnforce.class;
		
		emitter.authorizeUpdatesForSubscriptionQueryWithId(query.getIdentifier(), decisions, annotation);

        result.getUpdates().subscribe();
        emitter.emit(any -> true, "some-awesome-text");
        emitter.emit(any -> true, 1234);
        emitter.emit(any -> true, Optional.of("optional-payload"));
        emitter.emit(any -> true, Optional.empty());
        emitter.emit(any -> true, new String[] { "array-item-1", "array-item-2" });
        emitter.emit(any -> true, Arrays.asList("list-item-1", "list-item-2"));
        emitter.emit(any -> true, Flux.just("flux-item-1", "flux-item-2"));
        emitter.emit(any -> true, Mono.just("mono-item"));
        result.complete();

        create(result.getUpdates().map(Message::getPayload))
    			.expectNext(Optional.of("optional-payload"), Optional.empty() )
                .verifyComplete();
    }
	
	@Test
    void multipleInstanceUpdatesAreDelivered() {
        SubscriptionQueryMessage<String, List<String>, List<String>> query = new GenericSubscriptionQueryMessage<>(
                "some-payload",
                "chatMessages",
                ResponseTypes.multipleInstancesOf(String.class),
                ResponseTypes.multipleInstancesOf(String.class)
        );

        UpdateHandlerRegistration<Object> result = emitter.registerUpdateHandler(
        		query,
                1024
        );
        
        var decisions = Flux.just(AuthorizationDecision.PERMIT);
		var annotation = PreHandleEnforce.class;
		
		emitter.authorizeUpdatesForSubscriptionQueryWithId(query.getIdentifier(), decisions, annotation);

        result.getUpdates().subscribe();
        emitter.emit(any -> true, Arrays.asList("text1","text2"));
        emitter.emit(any -> true, Arrays.asList("text3","text4"));
        result.complete();

        create(result.getUpdates().map(Message::getPayload))
                .expectNext(Arrays.asList("text1","text2"), Arrays.asList("text3","text4"))
                .verifyComplete();
    }
	
	@Test
    void optionalUpdatesAreDelivered() {
        SubscriptionQueryMessage<String, Optional<String>, Optional<String>> query = new GenericSubscriptionQueryMessage<>(
                "some-payload",
                "chatMessages",
                ResponseTypes.optionalInstanceOf(String.class),
                ResponseTypes.optionalInstanceOf(String.class)
        );

        UpdateHandlerRegistration<Object> result = emitter.registerUpdateHandler(
        		query,
                1024
        );
        
        var decisions = Flux.just(AuthorizationDecision.PERMIT);
		var annotation = PreHandleEnforce.class;
		
		emitter.authorizeUpdatesForSubscriptionQueryWithId(query.getIdentifier(), decisions, annotation);

        result.getUpdates().subscribe();
        emitter.emit(any -> true, Optional.of("text1"));
        emitter.emit(any -> true, Optional.of("text2"));
        result.complete();

        create(result.getUpdates().map(Message::getPayload))
                .expectNext(Optional.of("text1"),Optional.of("text2"))
                .verifyComplete();
    }
}
