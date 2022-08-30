package io.sapl.axon.queryhandling;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import org.axonframework.common.Registration;
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
	private static final String DEFAULT_CAUSE = "defaultCause";
	private static final FlaggedUpdateResponseType DEFAULT_UPDATE_RESPONSE_TYPE = new FlaggedUpdateResponseType();
	
	private static ConstraintHandlerService constraintHandlerService;
	
	private static class FlaggedUpdateHandlerRegistration<U> extends UpdateHandlerRegistration<U> {
		public FlaggedUpdateHandlerRegistration(Registration registration, Flux<SubscriptionQueryUpdateMessage<U>> updates, Runnable completeHandler) {
			super(registration, updates, completeHandler);
		}
	}
	
	private static class FlaggedUpdateResponseType { }
	
	private static class FlaggedSubscriptionQueryMessage<Q, I, U> extends GenericSubscriptionQueryMessage<Q, I, U> {
		public FlaggedSubscriptionQueryMessage(Q payload, ResponseType<I> responseType, ResponseType<U> updateResponseType) {
			super(payload, responseType, updateResponseType);
		}
	}
	
	@BeforeAll
	static void setup() {
		constraintHandlerService = mock(ConstraintHandlerService.class);
	}
	
	final String TEXTSUBSCRIPTIONQUERYUPDATEMESSAGE = "some-text";
	
	private SaplQueryUpdateEmitter emitter;
	private SubscriptionQueryMessage<String, List<String>, String> subscriptionQueryMessage;
	
	@BeforeEach
    void setUp() {
        subscriptionQueryMessage = new GenericSubscriptionQueryMessage<>(
                "some-payload", "chatMessages", ResponseTypes.multipleInstancesOf(String.class),
                ResponseTypes.instanceOf(String.class));
        subscriptionQueryMessage = subscriptionQueryMessage.andMetaData(Map.of("updateResponseType",subscriptionQueryMessage.getUpdateResponseType().getExpectedResponseType().getSimpleName()));
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
	void when_queryUpdateHandlerRegistered_with_nonRegisteredSubscriptionQuery_then_false() {
		var query = new GenericSubscriptionQueryMessage<>(new Object(), ResponseTypes.instanceOf(Object.class), ResponseTypes.multipleInstancesOf(Object.class));
		
		var queryUpdateHandlerRegistered = emitter.queryUpdateHandlerRegistered(query);
		assertFalse(queryUpdateHandlerRegistered);
	}
	
	@Test
	void when_queryUpdateHandlerRegistered_with_registeredSubscriptionQuery_then_true() {
		var query = new GenericSubscriptionQueryMessage<>(new Object(), ResponseTypes.instanceOf(Object.class), ResponseTypes.multipleInstancesOf(Object.class));
		emitter.registerUpdateHandler(query, DEFAULT_UPDATE_BUFFER_SIZE);
		
		var queryUpdateHandlerRegistered = emitter.queryUpdateHandlerRegistered(query);
		assertTrue(queryUpdateHandlerRegistered);
	}
	
	@Test
	void when_authorizeUpdatesForSubscriptionQueryWithId_with_nonExistingMessageIdentifier_then_return() {
		emitter.authorizeUpdatesForSubscriptionQueryWithId(DEFAULT_MESSAGE_IDENTIFIER);
	}
	
	@Test
	void when_authorizeUpdatesForSubscriptionQueryWithId_with_existingMessageIdentifier_then_return() {
		var query = new GenericSubscriptionQueryMessage<>(new Object(), ResponseTypes.instanceOf(Object.class), ResponseTypes.multipleInstancesOf(Object.class));
		var messageIdentifier = query.getIdentifier();
		
		emitter.registerUpdateHandler(query, DEFAULT_UPDATE_BUFFER_SIZE);
		emitter.authorizeUpdatesForSubscriptionQueryWithId(messageIdentifier);
	}
	
	@Test
	void when_authorizeUpdatesForSubscriptionQueryWithId_with_nonExistingMessageIdentifier_and_decisionFlux_and_Annotation_then_return() {
		var decisions = Flux.<AuthorizationDecision>empty();
		var clazz = Annotation.class;
		
		emitter.authorizeUpdatesForSubscriptionQueryWithId(DEFAULT_MESSAGE_IDENTIFIER, decisions, clazz);
	}
	
	@Test
	void when_authorizeUpdatesForSubscriptionQueryWithId_with_existingMessageIdentifier_and_decisionFlux_and_Annotation_then_return() {
		var query = new GenericSubscriptionQueryMessage<>(new Object(), ResponseTypes.instanceOf(Object.class), ResponseTypes.multipleInstancesOf(Object.class));
		var decisions = Flux.<AuthorizationDecision>empty();
		var clazz = Annotation.class;
		
		emitter.registerUpdateHandler(query, DEFAULT_UPDATE_BUFFER_SIZE);
		emitter.authorizeUpdatesForSubscriptionQueryWithId(DEFAULT_MESSAGE_IDENTIFIER, decisions, clazz);
	}
	
	@Test
	void when_registerUpdateHandler_without_backpressure_then_UpdateHandlerRegistration() {
		var query = new GenericSubscriptionQueryMessage<>(new Object(), ResponseTypes.instanceOf(Object.class), ResponseTypes.multipleInstancesOf(Object.class));
		
		var updateHandlerRegistration = emitter.registerUpdateHandler(query, DEFAULT_UPDATE_BUFFER_SIZE);
		assertTrue(UpdateHandlerRegistration.class.isAssignableFrom(updateHandlerRegistration.getClass()));
	}
	
	@Test
	void when_emit_then_return() {
		Predicate<SubscriptionQueryMessage<?, ?, FlaggedUpdateResponseType>> filter = subscriptionQueryMessage -> false;
		var update = GenericSubscriptionQueryUpdateMessage.<FlaggedUpdateResponseType>asUpdateMessage(DEFAULT_UPDATE_RESPONSE_TYPE);
		
		emitter.emit(filter, update);
	}
	
	@Test
	void when_emit_with_closedFilter_and_existingSubscriptionQueries_then_return() {
		var queryA = new GenericSubscriptionQueryMessage<>(new Object(), ResponseTypes.instanceOf(Object.class), ResponseTypes.multipleInstancesOf(FlaggedUpdateResponseType.class));
		var queryB = new GenericSubscriptionQueryMessage<>(new Object(), ResponseTypes.instanceOf(Object.class), ResponseTypes.multipleInstancesOf(FlaggedUpdateResponseType.class));

		emitter.registerUpdateHandler(queryA, DEFAULT_UPDATE_BUFFER_SIZE);
		emitter.registerUpdateHandler(queryB, DEFAULT_UPDATE_BUFFER_SIZE);
		
		Predicate<SubscriptionQueryMessage<?, ?, FlaggedUpdateResponseType>> filter = subscriptionQueryMessage -> false;
		var update = GenericSubscriptionQueryUpdateMessage.<FlaggedUpdateResponseType>asUpdateMessage(DEFAULT_UPDATE_RESPONSE_TYPE);
		
		emitter.emit(filter, update);
	}
	
	@Test
	void when_emit_with_openFilter_and_existingSubscriptionQueries_then_return() {
		var queryA = new GenericSubscriptionQueryMessage<>(new Object(), ResponseTypes.instanceOf(Object.class), ResponseTypes.multipleInstancesOf(FlaggedUpdateResponseType.class));
		var queryB = new GenericSubscriptionQueryMessage<>(new Object(), ResponseTypes.instanceOf(Object.class), ResponseTypes.multipleInstancesOf(FlaggedUpdateResponseType.class));

		emitter.registerUpdateHandler(queryA, DEFAULT_UPDATE_BUFFER_SIZE);
		emitter.registerUpdateHandler(queryB, DEFAULT_UPDATE_BUFFER_SIZE);
		
		Predicate<SubscriptionQueryMessage<?, ?, FlaggedUpdateResponseType>> filter = subscriptionQueryMessage -> true;
		var update = GenericSubscriptionQueryUpdateMessage.<FlaggedUpdateResponseType>asUpdateMessage(DEFAULT_UPDATE_RESPONSE_TYPE);
		
		emitter.emit(filter, update);
	}
	
	@Test
	void when_emit_with_paritallyOpenFilter_and_existingSubscriptionQueries_then_return() {
		var queryA = new GenericSubscriptionQueryMessage<>(new Object(), ResponseTypes.instanceOf(Object.class), ResponseTypes.multipleInstancesOf(FlaggedUpdateResponseType.class));
		var queryB = new FlaggedSubscriptionQueryMessage<>(new Object(), ResponseTypes.instanceOf(Object.class), ResponseTypes.multipleInstancesOf(FlaggedUpdateResponseType.class));

		emitter.registerUpdateHandler(queryA, DEFAULT_UPDATE_BUFFER_SIZE);
		emitter.registerUpdateHandler(queryB, DEFAULT_UPDATE_BUFFER_SIZE);
		
		Predicate<SubscriptionQueryMessage<?, ?, FlaggedUpdateResponseType>> filter = subscriptionQueryMessage -> FlaggedSubscriptionQueryMessage.class.isAssignableFrom(subscriptionQueryMessage.getClass());
		var update = GenericSubscriptionQueryUpdateMessage.<FlaggedUpdateResponseType>asUpdateMessage(DEFAULT_UPDATE_RESPONSE_TYPE);
		
		emitter.emit(filter, update);
	}
	
	@Test
	void when_complete_then_return() {
		Predicate<SubscriptionQueryMessage<?, ?, ?>> filter = subscriptionQueryMessage -> false;
		
		emitter.complete(filter);
	}
	
	@Test
	void when_complete_with_closedFilter_and_existingSubscriptionQueries_then_return() {
		var queryA = new GenericSubscriptionQueryMessage<>(new Object(), ResponseTypes.instanceOf(Object.class), ResponseTypes.multipleInstancesOf(Object.class));
		var queryB = new GenericSubscriptionQueryMessage<>(new Object(), ResponseTypes.instanceOf(Object.class), ResponseTypes.multipleInstancesOf(Object.class));

		emitter.registerUpdateHandler(queryA, DEFAULT_UPDATE_BUFFER_SIZE);
		emitter.registerUpdateHandler(queryB, DEFAULT_UPDATE_BUFFER_SIZE);
		
		Predicate<SubscriptionQueryMessage<?, ?, ?>> filter = subscriptionQueryMessage -> false;
		
		emitter.complete(filter);
	}
	
	@Test
	void when_complete_with_openFilter_and_existingSubscriptionQueries_then_return() {
		var queryA = new GenericSubscriptionQueryMessage<>(new Object(), ResponseTypes.instanceOf(Object.class), ResponseTypes.multipleInstancesOf(Object.class));
		var queryB = new GenericSubscriptionQueryMessage<>(new Object(), ResponseTypes.instanceOf(Object.class), ResponseTypes.multipleInstancesOf(Object.class));

		emitter.registerUpdateHandler(queryA, DEFAULT_UPDATE_BUFFER_SIZE);
		emitter.registerUpdateHandler(queryB, DEFAULT_UPDATE_BUFFER_SIZE);
		
		Predicate<SubscriptionQueryMessage<?, ?, ?>> filter = subscriptionQueryMessage -> true;
		
		emitter.complete(filter);
	}
	
	@Test
	void when_complete_with_partiallyOpenFilter_and_existingSubscriptionQueries_then_return() {
		var queryA = new GenericSubscriptionQueryMessage<>(new Object(), ResponseTypes.instanceOf(Object.class), ResponseTypes.multipleInstancesOf(Object.class));
		var queryB = new FlaggedSubscriptionQueryMessage<>(new Object(), ResponseTypes.instanceOf(Object.class), ResponseTypes.multipleInstancesOf(Object.class));

		emitter.registerUpdateHandler(queryA, DEFAULT_UPDATE_BUFFER_SIZE);
		emitter.registerUpdateHandler(queryB, DEFAULT_UPDATE_BUFFER_SIZE);
		
		Predicate<SubscriptionQueryMessage<?, ?, ?>> filter = subscriptionQueryMessage ->
				FlaggedSubscriptionQueryMessage.class.isAssignableFrom(subscriptionQueryMessage.getClass());
		
		emitter.complete(filter);
	}
	
	@Test
	void when_completeExceptionally_then_return() {
		Predicate<SubscriptionQueryMessage<?, ?, ?>> filter = subscriptionQueryMessage -> false;
		var cause = new Throwable(DEFAULT_CAUSE);
		
		emitter.completeExceptionally(filter, cause);
	}
	
	@Test
	void when_completeExceptionally_with_closedFilter_and_existingSubscriptionQueries_then_return() {
		var queryA = new GenericSubscriptionQueryMessage<>(new Object(), ResponseTypes.instanceOf(Object.class), ResponseTypes.multipleInstancesOf(Object.class));
		var queryB = new GenericSubscriptionQueryMessage<>(new Object(), ResponseTypes.instanceOf(Object.class), ResponseTypes.multipleInstancesOf(Object.class));

		emitter.registerUpdateHandler(queryA, DEFAULT_UPDATE_BUFFER_SIZE);
		emitter.registerUpdateHandler(queryB, DEFAULT_UPDATE_BUFFER_SIZE);
		
		Predicate<SubscriptionQueryMessage<?, ?, ?>> filter = subscriptionQueryMessage -> false;
		var cause = new Throwable(DEFAULT_CAUSE);
		
		emitter.completeExceptionally(filter, cause);
	}
	
	@Test
	void when_completeExceptionally_with_openFilter_and_existingSubscriptionQueries_then_return() {
		var queryA = new GenericSubscriptionQueryMessage<>(new Object(), ResponseTypes.instanceOf(Object.class), ResponseTypes.multipleInstancesOf(Object.class));
		var queryB = new GenericSubscriptionQueryMessage<>(new Object(), ResponseTypes.instanceOf(Object.class), ResponseTypes.multipleInstancesOf(Object.class));

		emitter.registerUpdateHandler(queryA, DEFAULT_UPDATE_BUFFER_SIZE);
		emitter.registerUpdateHandler(queryB, DEFAULT_UPDATE_BUFFER_SIZE);
		
		Predicate<SubscriptionQueryMessage<?, ?, ?>> filter = subscriptionQueryMessage -> true;
		var cause = new Throwable(DEFAULT_CAUSE);
		
		emitter.completeExceptionally(filter, cause);
	}
	
	@Test
	void when_completeExceptionally_with_partiallyOpenFilter_and_existingSubscriptionQueries_then_return() {
		var queryA = new GenericSubscriptionQueryMessage<>(new Object(), ResponseTypes.instanceOf(Object.class), ResponseTypes.multipleInstancesOf(Object.class));
		var queryB = new FlaggedSubscriptionQueryMessage<>(new Object(), ResponseTypes.instanceOf(Object.class), ResponseTypes.multipleInstancesOf(Object.class));

		emitter.registerUpdateHandler(queryA, DEFAULT_UPDATE_BUFFER_SIZE);
		emitter.registerUpdateHandler(queryB, DEFAULT_UPDATE_BUFFER_SIZE);
		
		Predicate<SubscriptionQueryMessage<?, ?, ?>> filter = subscriptionQueryMessage -> FlaggedSubscriptionQueryMessage.class.isAssignableFrom(subscriptionQueryMessage.getClass());
		var cause = new Throwable(DEFAULT_CAUSE);
				
		emitter.completeExceptionally(filter, cause);
	}
	
	@Test
	void when_DispatchInterceptorRegistered_Then_NoException() {
        emitter.registerDispatchInterceptor(
                list -> (integer, updateMessage) -> GenericSubscriptionQueryUpdateMessage.asUpdateMessage(5));
    }

    @Test
    void when_UnitOfWorkIsStartedAndTaskIsAdded_Then_TaskIsQueued() {
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
    void when_UpdateHandlerRegistered_Then_ActiveSubscriptionsAreReturned() {
        emitter.registerUpdateHandler(subscriptionQueryMessage, 1024);

        assertTrue(emitter.activeSubscriptions().contains(subscriptionQueryMessage));
    }
}
