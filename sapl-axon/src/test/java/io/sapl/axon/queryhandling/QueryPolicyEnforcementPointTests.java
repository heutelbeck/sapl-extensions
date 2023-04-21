package io.sapl.axon.queryhandling;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.util.ReflectionTestUtils.setField;

import java.lang.annotation.Annotation;
import java.lang.reflect.Executable;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.axonframework.messaging.Message;
import org.axonframework.messaging.annotation.AnnotatedMessageHandlingMember;
import org.axonframework.messaging.annotation.DefaultParameterResolverFactory;
import org.axonframework.messaging.annotation.MessageHandlingMember;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.GenericQueryMessage;
import org.axonframework.queryhandling.GenericSubscriptionQueryMessage;
import org.axonframework.queryhandling.QueryHandler;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.queryhandling.SubscriptionQueryMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.axon.annotation.EnforceDropUpdatesWhileDenied;
import io.sapl.axon.annotation.EnforceRecoverableUpdatesIfDenied;
import io.sapl.axon.annotation.PostHandleEnforce;
import io.sapl.axon.annotation.PreHandleEnforce;
import io.sapl.axon.configuration.SaplAxonProperties;
import io.sapl.axon.constrainthandling.ConstraintHandlerService;
import io.sapl.axon.constrainthandling.QueryConstraintHandlerBundle;
import io.sapl.axon.subscription.AuthorizationSubscriptionBuilderService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;
import reactor.core.publisher.Flux;

class QueryPolicyEnforcementPointTests {

	private static final String                MAPPER_FILED_NAME = "mapper";
	private static final AccessDeniedException ACCESS_DENIED     = new AccessDeniedException("Access denied");
	private static final TestQueryPayload      DEFAULT_PAYLOAD   = new TestQueryPayload();
	private static final TestResponseType      DEFAULT_RESPONSE  = new TestResponseType();

	@SuppressWarnings("rawtypes")
	private static final QueryMessage DEFAULT_QUERY_MESSAGE = new GenericQueryMessage<TestQueryPayload, TestResponseType>(
			DEFAULT_PAYLOAD, ResponseTypes.instanceOf(TestResponseType.class));

	@SuppressWarnings("rawtypes")
	private static final SubscriptionQueryMessage DEFAULT_SUBSCRIPTION_QUERY_MESSAGE = new GenericSubscriptionQueryMessage<TestQueryPayload, TestResponseType, TestResponseType>(
			DEFAULT_PAYLOAD, ResponseTypes.instanceOf(TestResponseType.class),
			ResponseTypes.instanceOf(TestResponseType.class));

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	private static class TestQueryPayload {
		private Object someField = "some text";
	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	private static class TestResponseType {
		private Object someField = "some text";
	}

	@Setter
	@AllArgsConstructor
	private static class HandlingObject {

		private TestResponseType response;

		@QueryHandler
		@PreHandleEnforce
		public TestResponseType handle1(TestQueryPayload query) {
			return response;
		}

		@QueryHandler
		public TestResponseType handle2(TestQueryPayload query) {
			return response;
		}

		@QueryHandler
		@PreHandleEnforce
		@EnforceDropUpdatesWhileDenied
		public TestResponseType handle3(TestQueryPayload query) {
			return response;
		}

		@QueryHandler
		@PreHandleEnforce
		public CompletableFuture<TestResponseType> handle4(TestQueryPayload query) {
			return CompletableFuture.completedFuture(response);
		}

		@QueryHandler
		@EnforceDropUpdatesWhileDenied
		public TestResponseType handle5(TestQueryPayload query) {
			return response;
		}

		@QueryHandler
		@EnforceRecoverableUpdatesIfDenied
		public TestResponseType handle6(TestQueryPayload query) {
			return response;
		}

		@QueryHandler
		@PostHandleEnforce
		public TestResponseType handle7(TestQueryPayload query) {
			return response;
		}
	}

	private ObjectMapper             mapper;
	private ParameterResolverFactory factory;

	private MessageHandlingMember<HandlingObject>   delegate;
	private PolicyDecisionPoint                     pdp;
	private ConstraintHandlerService                axonConstraintEnforcementService;
	private SaplQueryUpdateEmitter                  emitter;
	private AuthorizationSubscriptionBuilderService subscriptionBuilder;
	private SaplAxonProperties                      properties;

	private QueryPolicyEnforcementPoint<HandlingObject> queryPEP;

	@SuppressWarnings("rawtypes")
	private QueryConstraintHandlerBundle queryConstraintHandlerBundle;
	private HandlingObject               handlingInstance;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void beforeEach() throws NoSuchMethodException, SecurityException {
		mapper = new ObjectMapper();
		var executable = HandlingObject.class.getDeclaredMethod("handle1", TestQueryPayload.class);
		factory = new DefaultParameterResolverFactory();

		delegate                         = spy(
				new AnnotatedMessageHandlingMember<>(executable, QueryMessage.class, TestQueryPayload.class, factory));
		pdp                              = mock(PolicyDecisionPoint.class);
		axonConstraintEnforcementService = mock(ConstraintHandlerService.class);
		emitter                          = mock(SaplQueryUpdateEmitter.class);
		subscriptionBuilder              = mock(AuthorizationSubscriptionBuilderService.class);
		properties                       = mock(SaplAxonProperties.class);
		queryConstraintHandlerBundle     = mock(QueryConstraintHandlerBundle.class);
		handlingInstance                 = spy(new HandlingObject(DEFAULT_RESPONSE));

		setField(axonConstraintEnforcementService, MAPPER_FILED_NAME, mapper);
		when(axonConstraintEnforcementService.deserializeResource(any(JsonNode.class), any(ResponseType.class)))
				.thenCallRealMethod();
		when(axonConstraintEnforcementService.buildQueryPreHandlerBundle(any(AuthorizationDecision.class),
				any(ResponseType.class), any(Optional.class))).thenReturn(queryConstraintHandlerBundle);
		when(axonConstraintEnforcementService.buildQueryPostHandlerBundle(any(AuthorizationDecision.class),
				any(ResponseType.class))).thenReturn(queryConstraintHandlerBundle);
		when(emitter.activeSubscriptions()).thenReturn(Set.of());
		when(subscriptionBuilder.constructAuthorizationSubscriptionForQuery(any(QueryMessage.class),
				any(Annotation.class), any(Executable.class), any(Optional.class)))
				.thenReturn(new AuthorizationSubscription());
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just());
		when(queryConstraintHandlerBundle.executePreHandlingHandlers(eq(DEFAULT_QUERY_MESSAGE)))
				.thenReturn(DEFAULT_QUERY_MESSAGE);
		when(queryConstraintHandlerBundle.executePreHandlingHandlers(eq(DEFAULT_SUBSCRIPTION_QUERY_MESSAGE)))
				.thenReturn(DEFAULT_SUBSCRIPTION_QUERY_MESSAGE);
		when(queryConstraintHandlerBundle.executePostHandlingHandlers(eq(DEFAULT_RESPONSE)))
				.thenReturn(DEFAULT_RESPONSE);

		queryPEP = new QueryPolicyEnforcementPoint<>(delegate, pdp, axonConstraintEnforcementService, emitter,
				subscriptionBuilder, properties);
	}

	@Test
	@SuppressWarnings("unchecked")
	void when_construct_and_delegateThrows_then_illegalStateException() {
		delegate = mock(MessageHandlingMember.class);
		when(delegate.unwrap(any(Class.class))).thenReturn(Optional.empty());
		assertThrows(IllegalStateException.class, () -> new QueryPolicyEnforcementPoint<>(delegate, pdp,
				axonConstraintEnforcementService, emitter, subscriptionBuilder, properties));
	}

	@Test
	void when_handle_with_noSaplAnnotations_then_handleWithoutAuthZ() throws NoSuchMethodException, SecurityException {
		var executable = HandlingObject.class.getDeclaredMethod("handle2", TestQueryPayload.class);
		delegate = spy(
				new AnnotatedMessageHandlingMember<>(executable, QueryMessage.class, TestQueryPayload.class, factory));
		queryPEP = new QueryPolicyEnforcementPoint<>(delegate, pdp, axonConstraintEnforcementService, emitter,
				subscriptionBuilder, properties);

		assertDoesNotThrow(() -> queryPEP.handle(DEFAULT_QUERY_MESSAGE, handlingInstance));

		assertDoesNotThrow(() -> verify(delegate, times(1)).handle(DEFAULT_QUERY_MESSAGE, handlingInstance));
		verify(pdp, times(0)).decide(any(AuthorizationSubscription.class));
	}

	@Test
	void when_handle_with_multiplePreEnforceAnnotations_then_accessDenied()
			throws NoSuchMethodException, SecurityException {
		var executable = HandlingObject.class.getDeclaredMethod("handle3", TestQueryPayload.class);
		delegate = spy(
				new AnnotatedMessageHandlingMember<>(executable, QueryMessage.class, TestQueryPayload.class, factory));
		queryPEP = new QueryPolicyEnforcementPoint<>(delegate, pdp, axonConstraintEnforcementService, emitter,
				subscriptionBuilder, properties);

		var response = assertDoesNotThrow(() -> queryPEP.handle(DEFAULT_QUERY_MESSAGE, handlingInstance));
		assertNotNull(response);
		assertInstanceOf(CompletableFuture.class, response);
		var future = (CompletableFuture<?>) response;
		assertTrue(future.isCompletedExceptionally());
		var exception = assertThrows(ExecutionException.class, () -> future.get());
		assertNotNull(exception.getCause());
		assertEquals(AccessDeniedException.class, exception.getCause().getClass());
	}

	@Test
	@SuppressWarnings("unchecked")
	void when_handle_with_preEnforce_and_buildQueryPreHandlerBundleThrowsAccessDenied_then_accessDenied()
			throws NoSuchMethodException, SecurityException {
		when(axonConstraintEnforcementService.buildQueryPreHandlerBundle(any(AuthorizationDecision.class),
				any(ResponseType.class), any(Optional.class))).thenThrow(ACCESS_DENIED);

		var response = assertDoesNotThrow(() -> queryPEP.handle(DEFAULT_QUERY_MESSAGE, handlingInstance));
		assertNotNull(response);
		assertInstanceOf(CompletableFuture.class, response);
		var future = (CompletableFuture<?>) response;
		assertTrue(future.isCompletedExceptionally());
		var exception = assertThrows(ExecutionException.class, () -> future.get());
		assertNotNull(exception.getCause());
		assertEquals(AccessDeniedException.class, exception.getCause().getClass());
	}

	@Test
	@SuppressWarnings("unchecked")
	void when_handle_with_preEnforce_and_executeOnDecisionHandlersThrowsAccessDenied_then_handledException()
			throws NoSuchMethodException, SecurityException {
		doThrow(ACCESS_DENIED).when(queryConstraintHandlerBundle)
				.executeOnDecisionHandlers(any(AuthorizationDecision.class), any(Message.class));
		when(queryConstraintHandlerBundle.executeOnErrorHandlers(any(Throwable.class)))
				.thenReturn(new RuntimeException("A very special message!"));

		var response = assertDoesNotThrow(() -> queryPEP.handle(DEFAULT_QUERY_MESSAGE, handlingInstance));
		assertNotNull(response);
		assertInstanceOf(CompletableFuture.class, response);
		var future = (CompletableFuture<?>) response;
		assertTrue(future.isCompletedExceptionally());
		var exception = assertThrows(ExecutionException.class, () -> future.get());
		assertNotNull(exception.getCause());
		assertEquals(RuntimeException.class, exception.getCause().getClass());
		assertEquals("A very special message!", exception.getCause().getLocalizedMessage());
	}

	@Test
	void when_handle_with_preEnforce_and_decisionNotPermit_then_handledException()
			throws NoSuchMethodException, SecurityException {
		when(queryConstraintHandlerBundle.executeOnErrorHandlers(any(Throwable.class)))
				.thenReturn(new RuntimeException("A very special message!"));

		var response = assertDoesNotThrow(() -> queryPEP.handle(DEFAULT_QUERY_MESSAGE, handlingInstance));
		assertNotNull(response);
		assertInstanceOf(CompletableFuture.class, response);
		var future = (CompletableFuture<?>) response;
		assertTrue(future.isCompletedExceptionally());
		var exception = assertThrows(ExecutionException.class, () -> future.get());
		assertNotNull(exception.getCause());
		assertEquals(RuntimeException.class, exception.getCause().getClass());
		assertEquals("A very special message!", exception.getCause().getLocalizedMessage());
	}

	@Test
	@SuppressWarnings("unchecked")
	void when_handle_with_preEnforce_and_executePreHandlingHandlersMapQuery_then_callHandlerWithManipulatedQuery()
			throws NoSuchMethodException, SecurityException {
		var          specialPayload      = new TestQueryPayload("some special text");
		@SuppressWarnings("rawtypes")
		QueryMessage specialQueryMessage = new GenericQueryMessage<>(specialPayload,
				ResponseTypes.instanceOf(TestResponseType.class));
		when(queryConstraintHandlerBundle.executePreHandlingHandlers(eq(DEFAULT_QUERY_MESSAGE)))
				.thenReturn(specialQueryMessage);
		when(queryConstraintHandlerBundle.executeOnErrorHandlers(any(Throwable.class)))
				.thenReturn(new RuntimeException("A very special message!"));
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));

		var response = assertDoesNotThrow(() -> queryPEP.handle(DEFAULT_QUERY_MESSAGE, handlingInstance));
		assertNotNull(response);
		assertInstanceOf(CompletableFuture.class, response);
		var future          = (CompletableFuture<?>) response;
		var completedResult = assertDoesNotThrow(() -> future.get());
		assertEquals(DEFAULT_RESPONSE, completedResult);
		verify(handlingInstance, times(1)).handle1(eq(specialPayload));
	}

	@Test
	@SuppressWarnings("unchecked")
	void when_handle_with_preEnforce_and_executePreHandlingHandlersThrowsAccessDenied_then_handledException()
			throws NoSuchMethodException, SecurityException {
		when(queryConstraintHandlerBundle.executePreHandlingHandlers(any(QueryMessage.class))).thenThrow(ACCESS_DENIED);
		when(queryConstraintHandlerBundle.executeOnErrorHandlers(any(Throwable.class)))
				.thenReturn(new RuntimeException("A very special message!"));
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));

		var response = assertDoesNotThrow(() -> queryPEP.handle(DEFAULT_QUERY_MESSAGE, handlingInstance));
		assertNotNull(response);
		assertInstanceOf(CompletableFuture.class, response);
		var future = (CompletableFuture<?>) response;
		assertTrue(future.isCompletedExceptionally());
		var exception = assertThrows(ExecutionException.class, () -> future.get());
		assertNotNull(exception.getCause());
		assertEquals(RuntimeException.class, exception.getCause().getClass());
		assertEquals("A very special message!", exception.getCause().getLocalizedMessage());
	}

	@Test
	void when_handle_with_preEnforce_and_delegateReturnsNull_then_empty()
			throws NoSuchMethodException, SecurityException {
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));
		handlingInstance.setResponse(null);

		var response = assertDoesNotThrow(() -> queryPEP.handle(DEFAULT_QUERY_MESSAGE, handlingInstance));
		assertNotNull(response);
		assertInstanceOf(CompletableFuture.class, response);
		var future = (CompletableFuture<?>) response;
		var completedResult = assertDoesNotThrow(() -> future.get());
		assertNull(completedResult);
	}

	@Test
	void when_handle_with_preEnforce_and_delegateThrows_then_handledException()
			throws NoSuchMethodException, SecurityException {
		when(queryConstraintHandlerBundle.executeOnErrorHandlers(any(Throwable.class)))
				.thenReturn(new RuntimeException("An even more special message!"));
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));
		when(handlingInstance.handle1(any(TestQueryPayload.class)))
				.thenThrow(new RuntimeException("A very special message!"));

		var response = assertDoesNotThrow(() -> queryPEP.handle(DEFAULT_QUERY_MESSAGE, handlingInstance));
		assertNotNull(response);
		assertInstanceOf(CompletableFuture.class, response);
		var future = (CompletableFuture<?>) response;
		assertTrue(future.isCompletedExceptionally());
		var exception = assertThrows(ExecutionException.class, () -> future.get());
		assertNotNull(exception.getCause());
		assertEquals(RuntimeException.class, exception.getCause().getClass());
		assertEquals("An even more special message!", exception.getCause().getLocalizedMessage());
	}

	@Test
	void when_handle_with_preEnforce_and_delegateReturnsCompletableFuture_then_deferResponse()
			throws NoSuchMethodException, SecurityException {
		var executable = HandlingObject.class.getDeclaredMethod("handle4", TestQueryPayload.class);
		delegate = spy(
				new AnnotatedMessageHandlingMember<>(executable, QueryMessage.class, TestQueryPayload.class, factory));
		queryPEP = new QueryPolicyEnforcementPoint<>(delegate, pdp, axonConstraintEnforcementService, emitter,
				subscriptionBuilder, properties);

		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));

		var response = assertDoesNotThrow(() -> queryPEP.handle(DEFAULT_QUERY_MESSAGE, handlingInstance));
		assertNotNull(response);
		assertInstanceOf(CompletableFuture.class, response);
		var future          = (CompletableFuture<?>) response;
		var completedResult = assertDoesNotThrow(() -> future.get());
		assertEquals(DEFAULT_RESPONSE, completedResult);
	}

	@Test
	void when_handle_with_preEnforce_and_resourceInDecision_then_replaceResult()
			throws NoSuchMethodException, SecurityException {
		var resource = new TestResponseType("some special text");
		var decision = AuthorizationDecision.PERMIT.withResource(asTree(resource));
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(decision));
		when(queryConstraintHandlerBundle.executePostHandlingHandlers(eq(resource))).thenReturn(resource);

		var response = assertDoesNotThrow(() -> queryPEP.handle(DEFAULT_QUERY_MESSAGE, handlingInstance));
		assertNotNull(response);
		assertInstanceOf(CompletableFuture.class, response);
		var future          = (CompletableFuture<?>) response;
		var completedResult = assertDoesNotThrow(() -> future.get());
		assertEquals(resource, completedResult);
	}

	@Test
	void when_handle_with_preEnforce_and_executePostHandlingHandlersMapResponse_then_handledException()
			throws NoSuchMethodException, SecurityException {
		when(queryConstraintHandlerBundle.executePostHandlingHandlers(eq(DEFAULT_RESPONSE)))
				.thenReturn("special response");
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));

		var response = assertDoesNotThrow(() -> queryPEP.handle(DEFAULT_QUERY_MESSAGE, handlingInstance));
		assertNotNull(response);
		assertInstanceOf(CompletableFuture.class, response);
		var future = (CompletableFuture<?>) response;
		var completedResult = assertDoesNotThrow(() -> future.get());
		assertEquals("special response", completedResult);
	}

	@Test
	void when_handle_with_enforceDropUpdatesWhileDenied_then_preEnforceInitialResult()
			throws NoSuchMethodException, SecurityException {
		var executable = HandlingObject.class.getDeclaredMethod("handle5", TestQueryPayload.class);
		delegate = spy(
				new AnnotatedMessageHandlingMember<>(executable, QueryMessage.class, TestQueryPayload.class, factory));
		queryPEP = new QueryPolicyEnforcementPoint<>(delegate, pdp, axonConstraintEnforcementService, emitter,
				subscriptionBuilder, properties);

		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));

		var response = assertDoesNotThrow(() -> queryPEP.handle(DEFAULT_QUERY_MESSAGE, handlingInstance));
		assertNotNull(response);
		assertInstanceOf(CompletableFuture.class, response);
		var future          = (CompletableFuture<?>) response;
		var completedResult = assertDoesNotThrow(() -> future.get());
		assertEquals(DEFAULT_RESPONSE, completedResult);
	}

	@Test
	void when_handle_with_enforceRecoverableUpdatesIfDenied_then_preEnforceInitialResult()
			throws NoSuchMethodException, SecurityException {
		var executable = HandlingObject.class.getDeclaredMethod("handle6", TestQueryPayload.class);
		delegate = spy(
				new AnnotatedMessageHandlingMember<>(executable, QueryMessage.class, TestQueryPayload.class, factory));
		queryPEP = new QueryPolicyEnforcementPoint<>(delegate, pdp, axonConstraintEnforcementService, emitter,
				subscriptionBuilder, properties);

		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));

		var response = assertDoesNotThrow(() -> queryPEP.handle(DEFAULT_QUERY_MESSAGE, handlingInstance));
		assertNotNull(response);
		assertInstanceOf(CompletableFuture.class, response);
		var future          = (CompletableFuture<?>) response;
		var completedResult = assertDoesNotThrow(() -> future.get());
		assertEquals(DEFAULT_RESPONSE, completedResult);
	}

	@Test
	void when_handle_with_postEnforce_and_handlerThrows_buildQueryPostHandlerBundleThrowsAccessDenied_then_accessDenied()
			throws NoSuchMethodException, SecurityException {
		var executable = HandlingObject.class.getDeclaredMethod("handle7", TestQueryPayload.class);
		delegate = spy(
				new AnnotatedMessageHandlingMember<>(executable, QueryMessage.class, TestQueryPayload.class, factory));
		queryPEP = new QueryPolicyEnforcementPoint<>(delegate, pdp, axonConstraintEnforcementService, emitter,
				subscriptionBuilder, properties);

		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));
		when(handlingInstance.handle7(any(TestQueryPayload.class))).thenThrow(new RuntimeException("some Exception"));
		when(axonConstraintEnforcementService.buildQueryPostHandlerBundle(any(AuthorizationDecision.class),
				any(ResponseType.class))).thenThrow(ACCESS_DENIED);

		var response = assertDoesNotThrow(() -> queryPEP.handle(DEFAULT_QUERY_MESSAGE, handlingInstance));
		assertNotNull(response);
		assertInstanceOf(CompletableFuture.class, response);
		var future = (CompletableFuture<?>) response;
		assertTrue(future.isCompletedExceptionally());
		var exception = assertThrows(ExecutionException.class, () -> future.get());
		assertNotNull(exception.getCause());
		assertEquals(AccessDeniedException.class, exception.getCause().getClass());
	}

	@Test
	@SuppressWarnings("unchecked")
	void when_handle_with_postEnforce_and_handlerThrows_and_executeOnDecisionHandlersThrowsAccessDenied_then_handledThrowable()
			throws NoSuchMethodException, SecurityException {
		var executable = HandlingObject.class.getDeclaredMethod("handle7", TestQueryPayload.class);
		delegate = spy(
				new AnnotatedMessageHandlingMember<>(executable, QueryMessage.class, TestQueryPayload.class, factory));
		queryPEP = new QueryPolicyEnforcementPoint<>(delegate, pdp, axonConstraintEnforcementService, emitter,
				subscriptionBuilder, properties);

		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));
		when(handlingInstance.handle7(any(TestQueryPayload.class))).thenThrow(new RuntimeException("some Exception"));
		doThrow(ACCESS_DENIED).when(queryConstraintHandlerBundle)
				.executeOnDecisionHandlers(any(AuthorizationDecision.class), any(Message.class));
		when(queryConstraintHandlerBundle.executeOnErrorHandlers(any(AccessDeniedException.class)))
				.thenReturn(new RuntimeException("some special throwable message"));

		var response = assertDoesNotThrow(() -> queryPEP.handle(DEFAULT_QUERY_MESSAGE, handlingInstance));
		assertNotNull(response);
		assertInstanceOf(CompletableFuture.class, response);
		var future = (CompletableFuture<?>) response;
		assertTrue(future.isCompletedExceptionally());
		var exception = assertThrows(ExecutionException.class, () -> future.get());
		assertNotNull(exception.getCause());
		assertEquals(RuntimeException.class, exception.getCause().getClass());
		assertEquals("some special throwable message", exception.getCause().getLocalizedMessage());
	}

	@Test
	void when_handle_with_postEnforce_and_handlerThrows_and_pdpDeny_then_handledThrowable()
			throws NoSuchMethodException, SecurityException {
		var executable = HandlingObject.class.getDeclaredMethod("handle7", TestQueryPayload.class);
		delegate = spy(
				new AnnotatedMessageHandlingMember<>(executable, QueryMessage.class, TestQueryPayload.class, factory));
		queryPEP = new QueryPolicyEnforcementPoint<>(delegate, pdp, axonConstraintEnforcementService, emitter,
				subscriptionBuilder, properties);

		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.DENY));
		when(handlingInstance.handle7(any(TestQueryPayload.class))).thenThrow(new RuntimeException("some Exception"));
		when(queryConstraintHandlerBundle.executeOnErrorHandlers(any(AccessDeniedException.class)))
				.thenReturn(new RuntimeException("some special throwable message"));

		var response = assertDoesNotThrow(() -> queryPEP.handle(DEFAULT_QUERY_MESSAGE, handlingInstance));
		assertNotNull(response);
		assertInstanceOf(CompletableFuture.class, response);
		var future = (CompletableFuture<?>) response;
		assertTrue(future.isCompletedExceptionally());
		var exception = assertThrows(ExecutionException.class, () -> future.get());
		assertNotNull(exception.getCause());
		assertEquals(RuntimeException.class, exception.getCause().getClass());
		assertEquals("some special throwable message", exception.getCause().getLocalizedMessage());
	}

	@Test
	void when_handle_with_postEnforce_and_handlerThrows_and_pdpPermit_then_handledThrowable()
			throws NoSuchMethodException, SecurityException {
		var executable = HandlingObject.class.getDeclaredMethod("handle7", TestQueryPayload.class);
		delegate = spy(
				new AnnotatedMessageHandlingMember<>(executable, QueryMessage.class, TestQueryPayload.class, factory));
		queryPEP = new QueryPolicyEnforcementPoint<>(delegate, pdp, axonConstraintEnforcementService, emitter,
				subscriptionBuilder, properties);

		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));
		when(handlingInstance.handle7(any(TestQueryPayload.class))).thenThrow(new RuntimeException("some Exception"));
		when(queryConstraintHandlerBundle.executeOnErrorHandlers(any(RuntimeException.class)))
				.thenReturn(new RuntimeException("some special throwable message"));

		var response = assertDoesNotThrow(() -> queryPEP.handle(DEFAULT_QUERY_MESSAGE, handlingInstance));
		assertNotNull(response);
		assertInstanceOf(CompletableFuture.class, response);
		var future = (CompletableFuture<?>) response;
		assertTrue(future.isCompletedExceptionally());
		var exception = assertThrows(ExecutionException.class, () -> future.get());
		assertNotNull(exception.getCause());
		assertEquals(RuntimeException.class, exception.getCause().getClass());
		assertEquals("some special throwable message", exception.getCause().getLocalizedMessage());
	}

	@Test
	void when_handle_with_postEnforce_and_buildQueryPostHandlerBundleThrowsAccessDenied_pdpPermit_then_accessDenied()
			throws NoSuchMethodException, SecurityException {
		var executable = HandlingObject.class.getDeclaredMethod("handle7", TestQueryPayload.class);
		delegate = spy(
				new AnnotatedMessageHandlingMember<>(executable, QueryMessage.class, TestQueryPayload.class, factory));
		queryPEP = new QueryPolicyEnforcementPoint<>(delegate, pdp, axonConstraintEnforcementService, emitter,
				subscriptionBuilder, properties);

		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));
		when(axonConstraintEnforcementService.buildQueryPostHandlerBundle(any(AuthorizationDecision.class),
				any(ResponseType.class))).thenThrow(ACCESS_DENIED);

		var response = assertDoesNotThrow(() -> queryPEP.handle(DEFAULT_QUERY_MESSAGE, handlingInstance));
		assertNotNull(response);
		assertInstanceOf(CompletableFuture.class, response);
		var future = (CompletableFuture<?>) response;
		assertTrue(future.isCompletedExceptionally());
		var exception = assertThrows(ExecutionException.class, () -> future.get());
		assertNotNull(exception.getCause());
		assertEquals(AccessDeniedException.class, exception.getCause().getClass());
	}

	@Test
	@SuppressWarnings("unchecked")
	void when_handle_with_postEnforce_and_executeOnDecisionHandlersThrowsAccessDenied_pdpPermit_then_handledThrowable()
			throws NoSuchMethodException, SecurityException {
		var executable = HandlingObject.class.getDeclaredMethod("handle7", TestQueryPayload.class);
		delegate = spy(
				new AnnotatedMessageHandlingMember<>(executable, QueryMessage.class, TestQueryPayload.class, factory));
		queryPEP = new QueryPolicyEnforcementPoint<>(delegate, pdp, axonConstraintEnforcementService, emitter,
				subscriptionBuilder, properties);

		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));
		doThrow(ACCESS_DENIED).when(queryConstraintHandlerBundle)
				.executeOnDecisionHandlers(any(AuthorizationDecision.class), any(Message.class));
		when(queryConstraintHandlerBundle.executeOnErrorHandlers(any(RuntimeException.class)))
				.thenReturn(new RuntimeException("some special throwable message"));

		var response = assertDoesNotThrow(() -> queryPEP.handle(DEFAULT_QUERY_MESSAGE, handlingInstance));
		assertNotNull(response);
		assertInstanceOf(CompletableFuture.class, response);
		var future = (CompletableFuture<?>) response;
		assertTrue(future.isCompletedExceptionally());
		var exception = assertThrows(ExecutionException.class, () -> future.get());
		assertNotNull(exception.getCause());
		assertEquals(RuntimeException.class, exception.getCause().getClass());
		assertEquals("some special throwable message", exception.getCause().getLocalizedMessage());
	}

	@Test
	void when_handle_with_postEnforce_and_pdpDeny_then_handledThrowable()
			throws NoSuchMethodException, SecurityException {
		var executable = HandlingObject.class.getDeclaredMethod("handle7", TestQueryPayload.class);
		delegate = spy(
				new AnnotatedMessageHandlingMember<>(executable, QueryMessage.class, TestQueryPayload.class, factory));
		queryPEP = new QueryPolicyEnforcementPoint<>(delegate, pdp, axonConstraintEnforcementService, emitter,
				subscriptionBuilder, properties);

		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.DENY));
		when(queryConstraintHandlerBundle.executeOnErrorHandlers(any(RuntimeException.class)))
				.thenReturn(new RuntimeException("some special throwable message"));

		var response = assertDoesNotThrow(() -> queryPEP.handle(DEFAULT_QUERY_MESSAGE, handlingInstance));
		assertNotNull(response);
		assertInstanceOf(CompletableFuture.class, response);
		var future = (CompletableFuture<?>) response;
		assertTrue(future.isCompletedExceptionally());
		var exception = assertThrows(ExecutionException.class, () -> future.get());
		assertNotNull(exception.getCause());
		assertEquals(RuntimeException.class, exception.getCause().getClass());
		assertEquals("some special throwable message", exception.getCause().getLocalizedMessage());
	}

	@Test
	void when_handle_with_postEnforce_and_resourceInDecision_then_resourceOfDecision()
			throws NoSuchMethodException, SecurityException {
		var executable = HandlingObject.class.getDeclaredMethod("handle7", TestQueryPayload.class);
		delegate = spy(
				new AnnotatedMessageHandlingMember<>(executable, QueryMessage.class, TestQueryPayload.class, factory));
		queryPEP = new QueryPolicyEnforcementPoint<>(delegate, pdp, axonConstraintEnforcementService, emitter,
				subscriptionBuilder, properties);

		var resourceResponse = new TestResponseType("ResourceText");
		var decision         = new AuthorizationDecision(Decision.PERMIT).withResource(asTree(resourceResponse));
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(decision));
		when(queryConstraintHandlerBundle.executePostHandlingHandlers(eq(resourceResponse)))
				.thenReturn(resourceResponse);

		var response = assertDoesNotThrow(() -> queryPEP.handle(DEFAULT_QUERY_MESSAGE, handlingInstance));
		assertNotNull(response);
		assertInstanceOf(CompletableFuture.class, response);
		var future = (CompletableFuture<?>) response;
		assertFalse(future.isCompletedExceptionally());
		var resource = assertDoesNotThrow(() -> future.get());
		assertNotNull(resource);
		assertEquals(resourceResponse, resource);
	}

	@Test
	@SuppressWarnings("unchecked")
	void when_handle_with_postEnforce_and_resourceInDecision_and_deserializeResourceThrows_then_handledThrowable()
			throws NoSuchMethodException, SecurityException {
		var executable = HandlingObject.class.getDeclaredMethod("handle7", TestQueryPayload.class);
		delegate = spy(
				new AnnotatedMessageHandlingMember<>(executable, QueryMessage.class, TestQueryPayload.class, factory));
		queryPEP = new QueryPolicyEnforcementPoint<>(delegate, pdp, axonConstraintEnforcementService, emitter,
				subscriptionBuilder, properties);

		var resourceResponse = new TestResponseType("ResourceText");
		var decision         = new AuthorizationDecision(Decision.PERMIT).withResource(asTree(resourceResponse));
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(decision));
		when(axonConstraintEnforcementService.deserializeResource(any(JsonNode.class), any(ResponseType.class)))
				.thenThrow(ACCESS_DENIED);
		when(queryConstraintHandlerBundle.executeOnErrorHandlers(any(RuntimeException.class)))
				.thenReturn(new RuntimeException("some special throwable message"));

		var response = assertDoesNotThrow(() -> queryPEP.handle(DEFAULT_QUERY_MESSAGE, handlingInstance));
		assertNotNull(response);
		assertInstanceOf(CompletableFuture.class, response);
		var future = (CompletableFuture<?>) response;
		assertTrue(future.isCompletedExceptionally());
		var exception = assertThrows(ExecutionException.class, () -> future.get());
		assertNotNull(exception.getCause());
		assertEquals(RuntimeException.class, exception.getCause().getClass());
		assertEquals("some special throwable message", exception.getCause().getLocalizedMessage());
	}

	@Test
	void when_handle_with_postEnforce_then_handledResponse() throws NoSuchMethodException, SecurityException {
		var executable = HandlingObject.class.getDeclaredMethod("handle7", TestQueryPayload.class);
		delegate = spy(
				new AnnotatedMessageHandlingMember<>(executable, QueryMessage.class, TestQueryPayload.class, factory));
		queryPEP = new QueryPolicyEnforcementPoint<>(delegate, pdp, axonConstraintEnforcementService, emitter,
				subscriptionBuilder, properties);

		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));
		when(queryConstraintHandlerBundle.executePostHandlingHandlers(any(TestResponseType.class)))
				.thenReturn("SomeOtherResponse");

		var response = assertDoesNotThrow(() -> queryPEP.handle(DEFAULT_QUERY_MESSAGE, handlingInstance));
		assertNotNull(response);
		assertInstanceOf(CompletableFuture.class, response);
		var future = (CompletableFuture<?>) response;
		assertFalse(future.isCompletedExceptionally());
		var resource = assertDoesNotThrow(() -> future.get());
		assertNotNull(resource);
		assertEquals("SomeOtherResponse", resource);
	}

	@Test
	void when_handle_with_postEnforce_and_executePostHandlingHandlersThrowsAccessDenied_then_handledResponse()
			throws NoSuchMethodException, SecurityException {
		var executable = HandlingObject.class.getDeclaredMethod("handle7", TestQueryPayload.class);
		delegate = spy(
				new AnnotatedMessageHandlingMember<>(executable, QueryMessage.class, TestQueryPayload.class, factory));
		queryPEP = new QueryPolicyEnforcementPoint<>(delegate, pdp, axonConstraintEnforcementService, emitter,
				subscriptionBuilder, properties);

		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));
		when(queryConstraintHandlerBundle.executePostHandlingHandlers(any(TestResponseType.class)))
				.thenThrow(ACCESS_DENIED);
		when(queryConstraintHandlerBundle.executeOnErrorHandlers(any(RuntimeException.class)))
				.thenReturn(new RuntimeException("some special throwable message"));

		var response = assertDoesNotThrow(() -> queryPEP.handle(DEFAULT_QUERY_MESSAGE, handlingInstance));
		assertNotNull(response);
		assertInstanceOf(CompletableFuture.class, response);
		var future = (CompletableFuture<?>) response;
		assertTrue(future.isCompletedExceptionally());
		var exception = assertThrows(ExecutionException.class, () -> future.get());
		assertNotNull(exception.getCause());
		assertEquals(RuntimeException.class, exception.getCause().getClass());
		assertEquals("some special throwable message", exception.getCause().getLocalizedMessage());
	}

	@Test
	void when_handleSubscriptionQuery_with_noAnnotation_then_handleWithoutAuthZ()
			throws NoSuchMethodException, SecurityException {
		var executable = HandlingObject.class.getDeclaredMethod("handle2", TestQueryPayload.class);
		delegate = spy(
				new AnnotatedMessageHandlingMember<>(executable, QueryMessage.class, TestQueryPayload.class, factory));
		queryPEP = new QueryPolicyEnforcementPoint<>(delegate, pdp, axonConstraintEnforcementService, emitter,
				subscriptionBuilder, properties);

		when(emitter.activeSubscriptions()).thenReturn(Set.of(DEFAULT_SUBSCRIPTION_QUERY_MESSAGE));

		assertDoesNotThrow(() -> queryPEP.handle(DEFAULT_SUBSCRIPTION_QUERY_MESSAGE, handlingInstance));

		assertDoesNotThrow(
				() -> verify(delegate, times(1)).handle(DEFAULT_SUBSCRIPTION_QUERY_MESSAGE, handlingInstance));
		verify(emitter, times(1))
				.authorizeUpdatesForSubscriptionQueryWithId(eq(DEFAULT_SUBSCRIPTION_QUERY_MESSAGE.getIdentifier()));
		verify(pdp, times(0)).decide(any(AuthorizationSubscription.class));
	}

	@Test
	void when_handleSubscriptionQuery_with_postEnforce_then_accessDenied()
			throws NoSuchMethodException, SecurityException {
		var executable = HandlingObject.class.getDeclaredMethod("handle7", TestQueryPayload.class);
		delegate = spy(
				new AnnotatedMessageHandlingMember<>(executable, QueryMessage.class, TestQueryPayload.class, factory));
		queryPEP = new QueryPolicyEnforcementPoint<>(delegate, pdp, axonConstraintEnforcementService, emitter,
				subscriptionBuilder, properties);

		when(emitter.activeSubscriptions()).thenReturn(Set.of(DEFAULT_SUBSCRIPTION_QUERY_MESSAGE));

		assertThrows(AccessDeniedException.class,
				() -> queryPEP.handle(DEFAULT_SUBSCRIPTION_QUERY_MESSAGE, handlingInstance));
		verify(emitter, times(1))
				.immediatelyDenySubscriptionWithId(eq(DEFAULT_SUBSCRIPTION_QUERY_MESSAGE.getIdentifier()));
	}

	@Test
	void when_handleSubscriptionQuery_with_multipleAnnotations_then_illegalState()
			throws NoSuchMethodException, SecurityException {
		var executable = HandlingObject.class.getDeclaredMethod("handle3", TestQueryPayload.class);
		delegate = spy(
				new AnnotatedMessageHandlingMember<>(executable, QueryMessage.class, TestQueryPayload.class, factory));
		queryPEP = new QueryPolicyEnforcementPoint<>(delegate, pdp, axonConstraintEnforcementService, emitter,
				subscriptionBuilder, properties);

		when(emitter.activeSubscriptions()).thenReturn(Set.of(DEFAULT_SUBSCRIPTION_QUERY_MESSAGE));

		assertThrows(IllegalStateException.class,
				() -> queryPEP.handle(DEFAULT_SUBSCRIPTION_QUERY_MESSAGE, handlingInstance));
		verify(emitter, times(0)).authorizeUpdatesForSubscriptionQueryWithId(any(String.class));
	}

	@Test
	void when_handleSubscriptionQuery_with_enforceDropUpdatesWhileDenied_then_preEnforceInitialResult()
			throws NoSuchMethodException, SecurityException {
		var executable = HandlingObject.class.getDeclaredMethod("handle5", TestQueryPayload.class);
		delegate = spy(
				new AnnotatedMessageHandlingMember<>(executable, QueryMessage.class, TestQueryPayload.class, factory));
		queryPEP = new QueryPolicyEnforcementPoint<>(delegate, pdp, axonConstraintEnforcementService, emitter,
				subscriptionBuilder, properties);

		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));
		when(emitter.activeSubscriptions()).thenReturn(Set.of(DEFAULT_SUBSCRIPTION_QUERY_MESSAGE));

		var response = assertDoesNotThrow(() -> queryPEP.handle(DEFAULT_SUBSCRIPTION_QUERY_MESSAGE, handlingInstance));
		assertNotNull(response);
		assertInstanceOf(CompletableFuture.class, response);
		var future          = (CompletableFuture<?>) response;
		var completedResult = assertDoesNotThrow(() -> future.get());
		assertEquals(DEFAULT_RESPONSE, completedResult);
	}

	@Test
	void when_handleSubscriptionQuery_with_enforceRecoverableUpdatesIfDenied_then_preEnforceInitialResult()
			throws NoSuchMethodException, SecurityException {
		var executable = HandlingObject.class.getDeclaredMethod("handle6", TestQueryPayload.class);
		delegate = spy(
				new AnnotatedMessageHandlingMember<>(executable, QueryMessage.class, TestQueryPayload.class, factory));
		queryPEP = new QueryPolicyEnforcementPoint<>(delegate, pdp, axonConstraintEnforcementService, emitter,
				subscriptionBuilder, properties);

		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));
		when(emitter.activeSubscriptions()).thenReturn(Set.of(DEFAULT_SUBSCRIPTION_QUERY_MESSAGE));

		var response = assertDoesNotThrow(() -> queryPEP.handle(DEFAULT_SUBSCRIPTION_QUERY_MESSAGE, handlingInstance));
		assertNotNull(response);
		assertInstanceOf(CompletableFuture.class, response);
		var future          = (CompletableFuture<?>) response;
		var completedResult = assertDoesNotThrow(() -> future.get());
		assertEquals(DEFAULT_RESPONSE, completedResult);
	}

	private JsonNode asTree(Object obj) {
		return mapper.valueToTree(obj);
	}

}