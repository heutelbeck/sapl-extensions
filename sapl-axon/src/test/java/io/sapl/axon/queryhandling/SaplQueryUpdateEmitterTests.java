package io.sapl.axon.queryhandling;

import java.lang.annotation.Annotation;
import java.util.Optional;
import java.util.function.Predicate;

import org.axonframework.common.Registration;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.monitoring.NoOpMessageMonitor;
import org.axonframework.queryhandling.GenericSubscriptionQueryMessage;
import org.axonframework.queryhandling.GenericSubscriptionQueryUpdateMessage;
import org.axonframework.queryhandling.SubscriptionQueryBackpressure;
import org.axonframework.queryhandling.SubscriptionQueryMessage;
import org.axonframework.queryhandling.SubscriptionQueryUpdateMessage;
import org.axonframework.queryhandling.UpdateHandlerRegistration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.axon.constrainthandling.ConstraintHandlerService;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink.OverflowStrategy;

@SuppressWarnings("deprecation") // inherited from Axon
public class SaplQueryUpdateEmitterTests {

	private static final int DEFAULT_UPDATE_BUFFER_SIZE = 255;
	private static final String DEFAULT_MESSAGE_IDENTIFIER = "messageIdentifier";
	private static final FlaggedUpdateResponseType DEFAULT_UPDATE_RESPONSE_TYPE = new FlaggedUpdateResponseType();
	
	private static ConstraintHandlerService constraintHandlerService;
	
	private static class FlaggedUpdateHandlerRegistration<U> extends UpdateHandlerRegistration<U> {
		public FlaggedUpdateHandlerRegistration(Registration registration,
				Flux<SubscriptionQueryUpdateMessage<U>> updates, Runnable completeHandler) {
			super(registration, updates, completeHandler);
		}
	}
	
	private static class FlaggedUpdateResponseType { }
	
	@BeforeAll
	public static void setup() {
		constraintHandlerService = mock(ConstraintHandlerService.class);
	}
	
	@Test
	public void when_construct_without_updateMessageMonitor_then_return() {
		new SaplQueryUpdateEmitter(Optional.empty(), constraintHandlerService);
	}
	
	@Test
	public void when_construct_with_updateMessageMonitor_then_return() {
		var updateMessageMonitor = NoOpMessageMonitor.INSTANCE;
		new SaplQueryUpdateEmitter(Optional.of(updateMessageMonitor), constraintHandlerService);
	}
	
	@Test
	public void when_registerUpdateHandler_with_backpressure_then_ignoreBackpressure_and_callNonDeprecatedVariant() {
		var emitter = mock(SaplQueryUpdateEmitter.class);
		when(emitter.registerUpdateHandler(any(SubscriptionQueryMessage.class), any(SubscriptionQueryBackpressure.class), any(Integer.class)))
				.thenCallRealMethod();
		when(emitter.registerUpdateHandler(any(SubscriptionQueryMessage.class), any(Integer.class)))
				.thenReturn(new FlaggedUpdateHandlerRegistration<Object>(() -> true, Flux.empty(), () -> { }));
		
		var query = new GenericSubscriptionQueryMessage<>(new Object(), ResponseTypes.instanceOf(Object.class), ResponseTypes.multipleInstancesOf(Object.class));
		var backpressure = new SubscriptionQueryBackpressure(OverflowStrategy.ERROR);
		
		var updateHandlerRegistration = emitter.registerUpdateHandler(query, backpressure, DEFAULT_UPDATE_BUFFER_SIZE);
		assertEquals(FlaggedUpdateHandlerRegistration.class, updateHandlerRegistration.getClass());
	}
	
	@Test
	public void when_queryUpdateHandlerRegistered_with_nonRegisteredSubscriptionQuery_then_false() {
		var emitter = new SaplQueryUpdateEmitter(Optional.empty(), constraintHandlerService);
		
		var query = new GenericSubscriptionQueryMessage<>(new Object(), ResponseTypes.instanceOf(Object.class), ResponseTypes.multipleInstancesOf(Object.class));
		
		var queryUpdateHandlerRegistered = emitter.queryUpdateHandlerRegistered(query);
		assertFalse(queryUpdateHandlerRegistered);
	}
	
	@Test
	public void when_queryUpdateHandlerRegistered_with_registeredSubscriptionQuery_then_true() {
		var emitter = new SaplQueryUpdateEmitter(Optional.empty(), constraintHandlerService);
		
		var query = new GenericSubscriptionQueryMessage<>(new Object(), ResponseTypes.instanceOf(Object.class), ResponseTypes.multipleInstancesOf(Object.class));
		emitter.registerUpdateHandler(query, DEFAULT_UPDATE_BUFFER_SIZE);
		
		var queryUpdateHandlerRegistered = emitter.queryUpdateHandlerRegistered(query);
		assertTrue(queryUpdateHandlerRegistered);
	}
	
	@Test
	public void when_authorizeUpdatesForSubscriptionQueryWithId_with_nonExistingMessageIdentifier_then_return() {
		var emitter = new SaplQueryUpdateEmitter(Optional.empty(), constraintHandlerService);
		
		emitter.authorizeUpdatesForSubscriptionQueryWithId(DEFAULT_MESSAGE_IDENTIFIER);
	}
	
	@Test
	public void when_authorizeUpdatesForSubscriptionQueryWithId_with_existingMessageIdentifier_then_return() {
		var emitter = new SaplQueryUpdateEmitter(Optional.empty(), constraintHandlerService);
		
		var query = new GenericSubscriptionQueryMessage<>(new Object(), ResponseTypes.instanceOf(Object.class), ResponseTypes.multipleInstancesOf(Object.class));
		var messageIdentifier = query.getIdentifier();
		
		emitter.registerUpdateHandler(query, DEFAULT_UPDATE_BUFFER_SIZE);
		emitter.authorizeUpdatesForSubscriptionQueryWithId(messageIdentifier);
	}
	
	@Test
	public void when_authorizeUpdatesForSubscriptionQueryWithId_with_nonExistingMessageIdentifier_and_decisionFlux_and_Annotation_then_return() {
		var emitter = new SaplQueryUpdateEmitter(Optional.empty(), constraintHandlerService);
		
		var decisions = Flux.<AuthorizationDecision>empty();
		var clazz = Annotation.class;
		
		emitter.authorizeUpdatesForSubscriptionQueryWithId(DEFAULT_MESSAGE_IDENTIFIER, decisions, clazz);
	}
	
	@Test
	public void when_authorizeUpdatesForSubscriptionQueryWithId_with_existingMessageIdentifier_and_decisionFlux_and_Annotation_then_return() {
		var emitter = new SaplQueryUpdateEmitter(Optional.empty(), constraintHandlerService);
		
		var query = new GenericSubscriptionQueryMessage<>(new Object(), ResponseTypes.instanceOf(Object.class), ResponseTypes.multipleInstancesOf(Object.class));
		var decisions = Flux.<AuthorizationDecision>empty();
		var clazz = Annotation.class;
		
		emitter.registerUpdateHandler(query, DEFAULT_UPDATE_BUFFER_SIZE);
		emitter.authorizeUpdatesForSubscriptionQueryWithId(DEFAULT_MESSAGE_IDENTIFIER, decisions, clazz);
	}
	
	@Test
	public void when_registerUpdateHandler_without_backpressure_then_UpdateHandlerRegistration() {
		var emitter = new SaplQueryUpdateEmitter(Optional.empty(), constraintHandlerService);
		
		var query = new GenericSubscriptionQueryMessage<>(new Object(), ResponseTypes.instanceOf(Object.class), ResponseTypes.multipleInstancesOf(Object.class));
		
		var updateHandlerRegistration = emitter.registerUpdateHandler(query, DEFAULT_UPDATE_BUFFER_SIZE);
		assertTrue(UpdateHandlerRegistration.class.isAssignableFrom(updateHandlerRegistration.getClass()));
	}
	
	@Test
	public void when_emit_with_closedFilter() {
		var emitter = new SaplQueryUpdateEmitter(Optional.empty(), constraintHandlerService);
		
		Predicate<SubscriptionQueryMessage<?, ?, FlaggedUpdateResponseType>> filter = subscriptionQueryMessage -> false;
		var update = GenericSubscriptionQueryUpdateMessage.<FlaggedUpdateResponseType>asUpdateMessage(DEFAULT_UPDATE_RESPONSE_TYPE);
		
		emitter.emit(filter, update);
	}
	
	@Test
	public void when_emit_with_closedFilter_and_existingSubscriptionQueries() {
		var emitter = new SaplQueryUpdateEmitter(Optional.empty(), constraintHandlerService);
		
		Predicate<SubscriptionQueryMessage<?, ?, FlaggedUpdateResponseType>> filter = subscriptionQueryMessage -> false;
		var update = GenericSubscriptionQueryUpdateMessage.<FlaggedUpdateResponseType>asUpdateMessage(DEFAULT_UPDATE_RESPONSE_TYPE);
		
		emitter.emit(filter, update);
	}
}
