package io.sapl.axon.constrainthandling;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;

import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.messaging.GenericResultMessage;
import org.axonframework.messaging.annotation.DefaultParameterResolverFactory;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.axonframework.messaging.responsetypes.AbstractResponseType;
import org.axonframework.messaging.responsetypes.InstanceResponseType;
import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.TextNode;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.axon.annotation.ConstraintHandler;
import io.sapl.axon.constrainthandling.api.CommandConstraintHandlerProvider;
import io.sapl.axon.constrainthandling.api.OnDecisionConstraintHandlerProvider;
import io.sapl.axon.constrainthandling.api.QueryConstraintHandlerProvider;
import io.sapl.axon.constrainthandling.api.ResultConstraintHandlerProvider;
import io.sapl.axon.constrainthandling.api.UpdateFilterConstraintHandlerProvider;
import io.sapl.spring.constraints.api.ErrorMappingConstraintHandlerProvider;
import io.sapl.spring.constraints.api.MappingConstraintHandlerProvider;

class ConstraintHandlerServiceTests {

	private static class UnknownResponseType<R> extends AbstractResponseType<R> {

		private static final long serialVersionUID = -9035650923297559796L;

		protected UnknownResponseType(Class<R> expectedResponseType) {
			super(expectedResponseType);
		}

		@Override
		public boolean matches(Type responseType) {
			return false;
		}

		@Override
		public Class<R> responseMessagePayloadType() {
			return null;
		}

	}

	private static class EmptyHandlerObject {

		public void noHandle() {
		}
	}

	private static class SingleHandlerObject extends EmptyHandlerObject {

		@ConstraintHandler
		public void handle1() {
		}
	}

	private static class MalformedHandlerObject extends SingleHandlerObject {

		@Override
		@ConstraintHandler("malformed")
		public void handle1() {
		}
	}

	private static class SpELHandlerObject extends SingleHandlerObject {

		@Override
		@ConstraintHandler("#constraint.textValue() == 'constraint'")
		public void handle1() {
		}
	}

	private static class NonBooleanSpELHandlerObject extends SpELHandlerObject {

		@Override
		@ConstraintHandler("#constraint.textValue()")
		public void handle1() {
		}
	}

	private static class MultipleHandlerObject extends SpELHandlerObject {

		@ConstraintHandler
		public void handle2() {
		}
	}

	private static class HandlerObjectWithParameters extends EmptyHandlerObject {

		@ConstraintHandler
		public void handle1(Object payload, JsonNode constraint, AuthorizationDecision decistion) {
		}
	}

	private static class HandlerObjectWithUnresolvedParameters extends EmptyHandlerObject {

		@ConstraintHandler
		public void handle1(JsonNode constraint, AuthorizationDecision decistion, Object contextParameter) {
		}
	}

	private static class ThrowingHandlerObject extends EmptyHandlerObject {

		@ConstraintHandler
		public void handle1() throws Exception {
			throw new Exception();
		}
	}

	private static ObjectMapper             mapper;
	private static JsonNodeFactory          factory;
	private static ParameterResolverFactory parameterResolverFactory;

	@BeforeAll
	static void beforeAll() {
		mapper                   = new ObjectMapper();
		factory                  = new JsonNodeFactory(true);
		parameterResolverFactory = new DefaultParameterResolverFactory();
	}

	private OnDecisionConstraintHandlerProvider firstOnDecisionConstraintHandlerProvider;
	private OnDecisionConstraintHandlerProvider secondOnDecisionConstraintHandlerProvider;

	private CommandConstraintHandlerProvider firstCommandConstraintHandlerProvider;
	private CommandConstraintHandlerProvider secondCommandConstraintHandlerProvider;

	private QueryConstraintHandlerProvider firstQueryConstraintHandlerProvider;
	private QueryConstraintHandlerProvider secondQueryConstraintHandlerProvider;

	private ErrorMappingConstraintHandlerProvider firstErrorMappingConstraintHandlerProvider;
	private ErrorMappingConstraintHandlerProvider secondErrorMappingConstraintHandlerProvider;

	private MappingConstraintHandlerProvider<Object> firstMappingConstraintHandlerProvider;
	private MappingConstraintHandlerProvider<Object> secondMappingConstraintHandlerProvider;

	private UpdateFilterConstraintHandlerProvider firstUpdateFilterConstraintHandlerProvider;
	private UpdateFilterConstraintHandlerProvider secondUpdateFilterConstraintHandlerProvider;

	private ResultConstraintHandlerProvider firstResultConstraintHandlerProvider;
	private ResultConstraintHandlerProvider secondResultConstraintHandlerProvider;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void beforeEach() {
		firstOnDecisionConstraintHandlerProvider  = mock(OnDecisionConstraintHandlerProvider.class);
		secondOnDecisionConstraintHandlerProvider = mock(OnDecisionConstraintHandlerProvider.class);

		firstCommandConstraintHandlerProvider  = mock(CommandConstraintHandlerProvider.class);
		secondCommandConstraintHandlerProvider = mock(CommandConstraintHandlerProvider.class);

		firstQueryConstraintHandlerProvider  = mock(QueryConstraintHandlerProvider.class);
		secondQueryConstraintHandlerProvider = mock(QueryConstraintHandlerProvider.class);

		firstErrorMappingConstraintHandlerProvider  = mock(ErrorMappingConstraintHandlerProvider.class);
		secondErrorMappingConstraintHandlerProvider = mock(ErrorMappingConstraintHandlerProvider.class);

		firstMappingConstraintHandlerProvider  = mock(MappingConstraintHandlerProvider.class);
		secondMappingConstraintHandlerProvider = mock(MappingConstraintHandlerProvider.class);

		firstUpdateFilterConstraintHandlerProvider  = mock(UpdateFilterConstraintHandlerProvider.class);
		secondUpdateFilterConstraintHandlerProvider = mock(UpdateFilterConstraintHandlerProvider.class);

		firstResultConstraintHandlerProvider  = mock(ResultConstraintHandlerProvider.class);
		secondResultConstraintHandlerProvider = mock(ResultConstraintHandlerProvider.class);
	}

//================================================================
//||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||
//================================================================

	@Test
	void when_deserializeResource_with_stringRessource_then_returnString() {
		var service = buildServiceForTest(0, 0, 0, 0, 0, 0, 0);

		var resource = new TextNode("resourceString");
		var type     = ResponseTypes.instanceOf(String.class);

		var deserialized = service.deserializeResource(resource, type);

		assertEquals("resourceString", deserialized);
	}

	@Test
	void when_deserializeResource_with_nonMatchingRessource_then_accessDenied() {
		var service = buildServiceForTest(0, 0, 0, 0, 0, 0, 0);

		var resource = new TextNode("resourceString");
		var type     = ResponseTypes.instanceOf(Integer.class);

		assertThrows(AccessDeniedException.class, () -> service.deserializeResource(resource, type));
	}

	@Test
	void when_deserializeResource_with_optionalStringRessource_and_stringExists_then_returnString() {
		var service = buildServiceForTest(0, 0, 0, 0, 0, 0, 0);

		var resource = new TextNode("resourceString");
		var type     = ResponseTypes.optionalInstanceOf(String.class);

		var deserialized = service.deserializeResource(resource, type);

		assertEquals(deserialized, Optional.of("resourceString"));
	}

	@Test
	void when_deserializeResource_with_optionalStringRessource_and_stringDoesNotExist_then_returnNull() {
		var service = buildServiceForTest(0, 0, 0, 0, 0, 0, 0);

		var resource = NullNode.getInstance();
		var type     = ResponseTypes.optionalInstanceOf(String.class);

		var deserialized = service.deserializeResource(resource, type);

		assertEquals(deserialized, Optional.empty());
	}

	@Test
	void when_deserializeResource_with_optionalStringRessource_and_nonMatchingRessource_then_accessDenied() {
		var service = buildServiceForTest(0, 0, 0, 0, 0, 0, 0);

		var resource = new TextNode("resourceString");
		var type     = ResponseTypes.optionalInstanceOf(Integer.class);

		assertThrows(AccessDeniedException.class, () -> service.deserializeResource(resource, type));
	}

	@Test
	void when_deserializeResource_with_multipleStringRessource_then_returnAllStrings() {
		var service = buildServiceForTest(0, 0, 0, 0, 0, 0, 0);

		var listOfStrings = List.of("resourceString", "otherResourceString");
		var resource      = factory.arrayNode().add("resourceString").add("otherResourceString");
		var type          = ResponseTypes.multipleInstancesOf(String.class);

		var deserialized = service.deserializeResource(resource, type);

		assertEquals(deserialized, listOfStrings);
	}

	@Test
	void when_deserializeResource_with_multipleStringRessource_and_onlyOneEntry_then_returnSingleString() {
		var service = buildServiceForTest(0, 0, 0, 0, 0, 0, 0);

		var listOfStrings = List.of("resourceString");
		var resource      = factory.arrayNode().add("resourceString");
		var type          = ResponseTypes.multipleInstancesOf(String.class);

		var deserialized = service.deserializeResource(resource, type);

		assertEquals(deserialized, listOfStrings);
	}

	@Test
	void when_deserializeResource_with_multipleStringRessource_and_noEntry_then_returnNoStrings() {
		var service = buildServiceForTest(0, 0, 0, 0, 0, 0, 0);

		var listOfStrings = List.<String>of();
		var resource      = factory.arrayNode();
		var type          = ResponseTypes.multipleInstancesOf(String.class);

		var deserialized = service.deserializeResource(resource, type);

		assertEquals(deserialized, listOfStrings);
	}

	@Test
	void when_deserializeResource_with_multipleStringRessource_and_nonMatchingRessource_then_accessDenied() {
		var service = buildServiceForTest(0, 0, 0, 0, 0, 0, 0);

		var resource = factory.arrayNode().add("resourceString").add("otherResourceString");
		var type     = ResponseTypes.multipleInstancesOf(Integer.class);

		assertThrows(AccessDeniedException.class, () -> service.deserializeResource(resource, type));
	}

	@Test
	void when_deserializeResource_with_multipleStringRessource_and_nonArrayNode_then_accessDenied() {
		var service = buildServiceForTest(0, 0, 0, 0, 0, 0, 0);

		var resource = new TextNode("resourceString");
		var type     = ResponseTypes.multipleInstancesOf(String.class);

		assertThrows(AccessDeniedException.class, () -> service.deserializeResource(resource, type));
	}

	@Test
	void when_deserializeResource_with_unknownResponseType_then_accessDenied() {
		var service = buildServiceForTest(0, 0, 0, 0, 0, 0, 0);

		var resource = new TextNode("resourceString");
		var type     = new UnknownResponseType<>(String.class);

		assertThrows(AccessDeniedException.class, () -> service.deserializeResource(resource, type));
	}

//================================================================
//||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||
//================================================================

	@Test
	void when_buildPreEnforceCommandConstraintHandlerBundle_with_resourceInDecision_then_accessDenied() {
		var service = buildServiceForTest(0, 0, 0, 0, 0, 0, 0);

		var resource = factory.objectNode();
		var decision = new AuthorizationDecision(Decision.PERMIT, Optional.of(resource), Optional.empty(),
				Optional.empty());

		assertThrows(AccessDeniedException.class,
				() -> service.buildPreEnforceCommandConstraintHandlerBundle(decision, null, null, null));
	}

//================================================================

	@Test
	void when_buildPreEnforceCommandConstraintHandlerBundle_with_noGlobalRunnableProviders_then_noOnDecisionHandlers() {
		var service = buildServiceForTest(0, 0, 0, 0, 0, 0, 0);

		var decision = new AuthorizationDecision(Decision.PERMIT);

		var bundle = service.buildPreEnforceCommandConstraintHandlerBundle(decision, null, Optional.empty(), null);
		bundle.executeOnDecisionHandlers(null, null);

		verify(firstOnDecisionConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
		verify(secondOnDecisionConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildPreEnforceCommandConstraintHandlerBundle_with_presentGlobalRunnableProviders_and_noConstraints_then_noOnDecisionHandlers() {
		when(firstOnDecisionConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(secondOnDecisionConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		var service = buildServiceForTest(2, 0, 0, 0, 0, 0, 0);

		var decision = new AuthorizationDecision(Decision.PERMIT);

		var bundle = service.buildPreEnforceCommandConstraintHandlerBundle(decision, null, Optional.empty(), null);
		bundle.executeOnDecisionHandlers(null, null);

		verify(firstOnDecisionConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
		verify(secondOnDecisionConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildPreEnforceCommandConstraintHandlerBundle_with_presentGlobalRunnableProviders_and_obligation_and_noResponsible_then_accessDenied() {
		when(firstOnDecisionConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.FALSE);
		when(secondOnDecisionConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.FALSE);
		var service = buildServiceForTest(2, 0, 0, 0, 0, 0, 0);

		var obligations = factory.arrayNode().add("obligation");
		var decision = new AuthorizationDecision(Decision.PERMIT).withObligations(obligations);

		assertThrows(AccessDeniedException.class,
				() -> service.buildPreEnforceCommandConstraintHandlerBundle(decision, null, Optional.empty(), null));
	}

	@Test
	void when_buildPreEnforceCommandConstraintHandlerBundle_with_presentGlobalRunnableProviders_and_obligation_and_throwingHandler_then_accessDenied() {
		when(firstOnDecisionConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.FALSE);
		when(secondOnDecisionConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(secondOnDecisionConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn((__, ___) -> {
			throw new RuntimeException();
		});
		var service = buildServiceForTest(2, 0, 0, 0, 0, 0, 0);

		var obligations = factory.arrayNode().add("obligation");
		var decision = new AuthorizationDecision(Decision.PERMIT).withObligations(obligations);

		var bundle = service.buildPreEnforceCommandConstraintHandlerBundle(decision, null, Optional.empty(), null);
		assertThrows(AccessDeniedException.class, () -> bundle.executeOnDecisionHandlers(null, null));

		verify(firstOnDecisionConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
		verify(secondOnDecisionConstraintHandlerProvider, times(1)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildPreEnforceCommandConstraintHandlerBundle_with_presentGlobalRunnableProviders_and_obligation_and_oneResponsible_then_oneOnDecisionHandlers() {
		when(firstOnDecisionConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.FALSE);
		when(secondOnDecisionConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(secondOnDecisionConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn((__, ___) -> {
		});
		var service = buildServiceForTest(2, 0, 0, 0, 0, 0, 0);

		var obligations = factory.arrayNode().add("obligation");
		var decision = new AuthorizationDecision(Decision.PERMIT).withObligations(obligations);

		var bundle = service.buildPreEnforceCommandConstraintHandlerBundle(decision, null, Optional.empty(), null);
		bundle.executeOnDecisionHandlers(null, null);

		verify(firstOnDecisionConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
		verify(secondOnDecisionConstraintHandlerProvider, times(1)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildPreEnforceCommandConstraintHandlerBundle_with_presentGlobalRunnableProviders_and_obligation_and_allResponsible_then_allOnDecisionHandlers() {
		when(firstOnDecisionConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(secondOnDecisionConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(firstOnDecisionConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn((__, ___) -> {
		});
		when(secondOnDecisionConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn((__, ___) -> {
		});
		var service = buildServiceForTest(2, 0, 0, 0, 0, 0, 0);

		var obligations = factory.arrayNode().add("obligation");
		var decision = new AuthorizationDecision(Decision.PERMIT).withObligations(obligations);

		var bundle = service.buildPreEnforceCommandConstraintHandlerBundle(decision, null, Optional.empty(), null);
		bundle.executeOnDecisionHandlers(null, null);

		verify(firstOnDecisionConstraintHandlerProvider, times(1)).getHandler(any(JsonNode.class));
		verify(secondOnDecisionConstraintHandlerProvider, times(1)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildPreEnforceCommandConstraintHandlerBundle_with_presentGlobalRunnableProviders_and_advice_and_noResponsible_then_noOnDecisionHandlers() {
		when(firstOnDecisionConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.FALSE);
		when(secondOnDecisionConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.FALSE);
		var service = buildServiceForTest(2, 0, 0, 0, 0, 0, 0);

		var advices = factory.arrayNode().add("advice");
		var decision = new AuthorizationDecision(Decision.PERMIT).withAdvice(advices);

		var bundle = service.buildPreEnforceCommandConstraintHandlerBundle(decision, null, Optional.empty(), null);
		bundle.executeOnDecisionHandlers(null, null);

		verify(firstOnDecisionConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
		verify(secondOnDecisionConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildPreEnforceCommandConstraintHandlerBundle_with_presentGlobalRunnableProviders_and_advice_and_throwingHandler_then_oneOnDecisionHandlers() {
		when(firstOnDecisionConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.FALSE);
		when(secondOnDecisionConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(secondOnDecisionConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn((__, ___) -> {
			throw new RuntimeException();
		});
		var service = buildServiceForTest(2, 0, 0, 0, 0, 0, 0);

		var advices = factory.arrayNode().add("advice");
		var decision = new AuthorizationDecision(Decision.PERMIT).withAdvice(advices);

		var bundle = service.buildPreEnforceCommandConstraintHandlerBundle(decision, null, Optional.empty(), null);
		assertDoesNotThrow(() -> bundle.executeOnDecisionHandlers(null, null));

		verify(firstOnDecisionConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
		verify(secondOnDecisionConstraintHandlerProvider, times(1)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildPreEnforceCommandConstraintHandlerBundle_with_presentGlobalRunnableProviders_and_advice_and_oneResponsible_then_oneOnDecisionHandlers() {
		when(firstOnDecisionConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.FALSE);
		when(secondOnDecisionConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(secondOnDecisionConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn((__, ___) -> {
		});
		var service = buildServiceForTest(2, 0, 0, 0, 0, 0, 0);

		var advices = factory.arrayNode().add("advice");
		var decision = new AuthorizationDecision(Decision.PERMIT).withAdvice(advices);

		var bundle = service.buildPreEnforceCommandConstraintHandlerBundle(decision, null, Optional.empty(), null);
		bundle.executeOnDecisionHandlers(null, null);

		verify(firstOnDecisionConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
		verify(secondOnDecisionConstraintHandlerProvider, times(1)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildPreEnforceCommandConstraintHandlerBundle_with_presentGlobalRunnableProviders_and_advice_and_allResponsible_then_allOnDecisionHandlers() {
		when(firstOnDecisionConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(secondOnDecisionConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(firstOnDecisionConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn((__, ___) -> {
		});
		when(secondOnDecisionConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn((__, ___) -> {
		});
		var service = buildServiceForTest(2, 0, 0, 0, 0, 0, 0);

		var advices = factory.arrayNode().add("advice");
		var decision = new AuthorizationDecision(Decision.PERMIT).withAdvice(advices);

		var bundle = service.buildPreEnforceCommandConstraintHandlerBundle(decision, null, Optional.empty(), null);
		bundle.executeOnDecisionHandlers(null, null);

		verify(firstOnDecisionConstraintHandlerProvider, times(1)).getHandler(any(JsonNode.class));
		verify(secondOnDecisionConstraintHandlerProvider, times(1)).getHandler(any(JsonNode.class));
	}

//================================================================

	@Test
	void when_buildPreEnforceCommandConstraintHandlerBundle_with_noGlobalCommandMessageMappingProviders_then_noCommandMessageMappingHandlers() {
		var service = buildServiceForTest(0, 0, 0, 0, 0, 0, 0);

		var decision = new AuthorizationDecision(Decision.PERMIT);

		var bundle = service.buildPreEnforceCommandConstraintHandlerBundle(decision, null, Optional.empty(), null);
		bundle.executeCommandMappingHandlers(null);

		verify(firstCommandConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
		verify(secondCommandConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildPreEnforceCommandConstraintHandlerBundle_with_presentGlobalCommandMessageMappingProviders_and_noConstraints_then_noCommandMessageMappingHandlers() {
		when(firstCommandConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(secondCommandConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		var service = buildServiceForTest(0, 2, 0, 0, 0, 0, 0);

		var decision = new AuthorizationDecision(Decision.PERMIT);

		var bundle = service.buildPreEnforceCommandConstraintHandlerBundle(decision, null, Optional.empty(), null);
		bundle.executeCommandMappingHandlers(null);

		verify(firstCommandConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
		verify(secondCommandConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildPreEnforceCommandConstraintHandlerBundle_with_presentGlobalCommandMessageMappingProviders_and_obligation_and_throwingHandler_then_accessDenied() {
		when(firstCommandConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(secondCommandConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.FALSE);
		when(firstCommandConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn(__ -> {
			throw new RuntimeException();
		});
		var service = buildServiceForTest(0, 2, 0, 0, 0, 0, 0);

		var obligations = factory.arrayNode().add("obligation");
		var decision = new AuthorizationDecision(Decision.PERMIT).withObligations(obligations);

		var bundle = service.buildPreEnforceCommandConstraintHandlerBundle(decision, null, Optional.empty(), null);
		assertThrows(AccessDeniedException.class, () -> bundle.executeCommandMappingHandlers(null));
	}

	@Test
	void when_buildPreEnforceCommandConstraintHandlerBundle_with_presentGlobalCommandMessageMappingProviders_and_obligation_and_noResponsible_then_accessDenied() {
		when(firstCommandConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.FALSE);
		when(secondCommandConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.FALSE);
		var service = buildServiceForTest(0, 2, 0, 0, 0, 0, 0);

		var obligations = factory.arrayNode().add("obligation");
		var decision = new AuthorizationDecision(Decision.PERMIT).withObligations(obligations);

		assertThrows(AccessDeniedException.class,
				() -> service.buildPreEnforceCommandConstraintHandlerBundle(decision, null, Optional.empty(), null));
	}

	@Test
	void when_buildPreEnforceCommandConstraintHandlerBundle_with_presentGlobalCommandMessageMappingProviders_and_obligation_and_oneResponsible_then_oneCommandMessageMappingHandlers() {
		when(firstCommandConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.FALSE);
		when(secondCommandConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(secondCommandConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn(UnaryOperator.identity());
		var service = buildServiceForTest(0, 2, 0, 0, 0, 0, 0);

		var obligations = factory.arrayNode().add("obligation");
		var decision = new AuthorizationDecision(Decision.PERMIT).withObligations(obligations);

		var bundle = service.buildPreEnforceCommandConstraintHandlerBundle(decision, null, Optional.empty(), null);
		bundle.executeCommandMappingHandlers(null);

		verify(firstCommandConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
		verify(secondCommandConstraintHandlerProvider, times(1)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildPreEnforceCommandConstraintHandlerBundle_with_presentGlobalCommandMessageMappingProviders_and_obligation_and_allResponsible_then_allCommandMessageMappingHandlers() {
		when(firstCommandConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(secondCommandConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(firstCommandConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn(UnaryOperator.identity());
		when(secondCommandConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn(UnaryOperator.identity());
		var service = buildServiceForTest(0, 2, 0, 0, 0, 0, 0);

		var obligations = factory.arrayNode().add("obligation");
		var decision = new AuthorizationDecision(Decision.PERMIT).withObligations(obligations);

		var bundle = service.buildPreEnforceCommandConstraintHandlerBundle(decision, null, Optional.empty(), null);
		bundle.executeCommandMappingHandlers(null);

		verify(firstCommandConstraintHandlerProvider, times(1)).getHandler(any(JsonNode.class));
		verify(secondCommandConstraintHandlerProvider, times(1)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildPreEnforceCommandConstraintHandlerBundle_with_presentGlobalCommandMessageMappingProviders_and_advice_and_throwingHandler_then_ignoreHandler() {
		when(firstCommandConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(secondCommandConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.FALSE);
		when(firstCommandConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn(__ -> {
			throw new RuntimeException();
		});
		var service = buildServiceForTest(0, 2, 0, 0, 0, 0, 0);

		var advices = factory.arrayNode().add("advice");
		var decision = new AuthorizationDecision(Decision.PERMIT).withAdvice(advices);

		var bundle = service.buildPreEnforceCommandConstraintHandlerBundle(decision, null, Optional.empty(), null);
		bundle.executeCommandMappingHandlers(null);

		verify(firstCommandConstraintHandlerProvider, times(1)).getHandler(any(JsonNode.class));
		verify(secondCommandConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildPreEnforceCommandConstraintHandlerBundle_with_presentGlobalCommandMessageMappingProviders_and_advice_and_noResponsible_then_noCommandMessageMappingHandlers() {
		when(firstCommandConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.FALSE);
		when(secondCommandConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.FALSE);
		var service = buildServiceForTest(0, 2, 0, 0, 0, 0, 0);

		var advices = factory.arrayNode().add("advice");
		var decision = new AuthorizationDecision(Decision.PERMIT).withAdvice(advices);

		var bundle = service.buildPreEnforceCommandConstraintHandlerBundle(decision, null, Optional.empty(), null);
		bundle.executeCommandMappingHandlers(null);

		verify(firstCommandConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
		verify(secondCommandConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildPreEnforceCommandConstraintHandlerBundle_with_presentGlobalCommandMessageMappingProviders_and_advice_and_oneResponsible_then_oneCommandMessageMappingHandlers() {
		when(firstCommandConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.FALSE);
		when(secondCommandConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(secondCommandConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn(UnaryOperator.identity());
		var service = buildServiceForTest(0, 2, 0, 0, 0, 0, 0);

		var advices = factory.arrayNode().add("advice");
		var decision = new AuthorizationDecision(Decision.PERMIT).withAdvice(advices);

		var bundle = service.buildPreEnforceCommandConstraintHandlerBundle(decision, null, Optional.empty(), null);
		bundle.executeCommandMappingHandlers(null);

		verify(firstCommandConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
		verify(secondCommandConstraintHandlerProvider, times(1)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildPreEnforceCommandConstraintHandlerBundle_with_presentGlobalCommandMessageMappingProviders_and_advice_and_allResponsible_then_allCommandMessageMappingHandlers() {
		when(firstCommandConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(secondCommandConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(firstCommandConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn(UnaryOperator.identity());
		when(secondCommandConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn(UnaryOperator.identity());
		var service = buildServiceForTest(0, 2, 0, 0, 0, 0, 0);

		var advices = factory.arrayNode().add("advice");
		var decision = new AuthorizationDecision(Decision.PERMIT).withAdvice(advices);

		var bundle = service.buildPreEnforceCommandConstraintHandlerBundle(decision, null, Optional.empty(), null);
		bundle.executeCommandMappingHandlers(null);

		verify(firstCommandConstraintHandlerProvider, times(1)).getHandler(any(JsonNode.class));
		verify(secondCommandConstraintHandlerProvider, times(1)).getHandler(any(JsonNode.class));
	}

//================================================================

	@Test
	void when_buildPreEnforceCommandConstraintHandlerBundle_with_noGlobalErrorMappingHandlerProviders_then_noErrorMappingConstraintHandlers() {
		var service = buildServiceForTest(0, 0, 0, 0, 0, 0, 0);

		var decision = new AuthorizationDecision(Decision.PERMIT);

		var error  = new Exception("Error");
		var bundle = service.buildPreEnforceCommandConstraintHandlerBundle(decision, null, Optional.empty(), null);
		bundle.executeOnErrorHandlers(error);

		verify(firstErrorMappingConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
		verify(secondErrorMappingConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildPreEnforceCommandConstraintHandlerBundle_with_presentGlobalErrorMappingHandlerProviders_and_noConstraints_then_noErrorMappingConstraintHandlers() {
		when(firstErrorMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(secondErrorMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		var service = buildServiceForTest(0, 0, 0, 2, 0, 0, 0);

		var decision = new AuthorizationDecision(Decision.PERMIT);

		var error = new Exception("Error");
		var bundle = service.buildPreEnforceCommandConstraintHandlerBundle(decision, null, Optional.empty(), null);
		bundle.executeOnErrorHandlers(error);

		verify(firstErrorMappingConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
		verify(secondErrorMappingConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildPreEnforceCommandConstraintHandlerBundle_with_presentGlobalErrorMappingHandlerProviders_and_obligation_and_throwingHandler_then_accessDenied() {
		when(firstErrorMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(secondErrorMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.FALSE);
		when(firstErrorMappingConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn(__ -> {
			throw new RuntimeException();
		});
		var service = buildServiceForTest(0, 0, 0, 2, 0, 0, 0);

		var obligations = factory.arrayNode().add("obligation");
		var decision = new AuthorizationDecision(Decision.PERMIT).withObligations(obligations);

		var error = new Exception("Error");
		var bundle = service.buildPreEnforceCommandConstraintHandlerBundle(decision, null, Optional.empty(), null);
		assertThrows(AccessDeniedException.class, () -> bundle.executeOnErrorHandlers(error));
	}

	@Test
	void when_buildPreEnforceCommandConstraintHandlerBundle_with_presentGlobalErrorMappingHandlerProviders_and_obligation_and_noResponsible_then_accessDenied() {
		when(firstErrorMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.FALSE);
		when(secondErrorMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.FALSE);
		var service = buildServiceForTest(0, 0, 0, 2, 0, 0, 0);

		var obligations = factory.arrayNode().add("obligation");
		var decision = new AuthorizationDecision(Decision.PERMIT).withObligations(obligations);

		assertThrows(AccessDeniedException.class,
				() -> service.buildPreEnforceCommandConstraintHandlerBundle(decision, null, Optional.empty(), null));
	}

	@Test
	void when_buildPreEnforceCommandConstraintHandlerBundle_with_presentGlobalErrorMappingHandlerProviders_and_obligation_and_oneResponsible_then_oneErrorMappingConstraintHandlers() {
		when(firstErrorMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.FALSE);
		when(secondErrorMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(secondErrorMappingConstraintHandlerProvider.getHandler(any(JsonNode.class)))
				.thenReturn(UnaryOperator.identity());
		var service = buildServiceForTest(0, 0, 0, 2, 0, 0, 0);

		var obligations = factory.arrayNode().add("obligation");
		var decision = new AuthorizationDecision(Decision.PERMIT).withObligations(obligations);

		var error = new Exception("Error");
		var bundle = service.buildPreEnforceCommandConstraintHandlerBundle(decision, null, Optional.empty(), null);
		bundle.executeOnErrorHandlers(error);

		verify(firstErrorMappingConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
		verify(secondErrorMappingConstraintHandlerProvider, times(1)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildPreEnforceCommandConstraintHandlerBundle_with_presentGlobalErrorMappingHandlerProviders_and_obligation_and_allResponsible_then_allErrorMappingConstraintHandlers() {
		when(firstErrorMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(secondErrorMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(firstErrorMappingConstraintHandlerProvider.getHandler(any(JsonNode.class)))
				.thenReturn(UnaryOperator.identity());
		when(secondErrorMappingConstraintHandlerProvider.getHandler(any(JsonNode.class)))
				.thenReturn(UnaryOperator.identity());
		var service = buildServiceForTest(0, 0, 0, 2, 0, 0, 0);

		var obligations = factory.arrayNode().add("obligation");
		var decision = new AuthorizationDecision(Decision.PERMIT).withObligations(obligations);

		var error = new Exception("Error");
		var bundle = service.buildPreEnforceCommandConstraintHandlerBundle(decision, null, Optional.empty(), null);
		bundle.executeOnErrorHandlers(error);

		verify(firstErrorMappingConstraintHandlerProvider, times(1)).getHandler(any(JsonNode.class));
		verify(secondErrorMappingConstraintHandlerProvider, times(1)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildPreEnforceCommandConstraintHandlerBundle_with_presentGlobalErrorMappingHandlerProviders_and_advice_and_throwingHandler_then_ignoreHandler() {
		when(firstErrorMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(secondErrorMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.FALSE);
		when(firstErrorMappingConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn(__ -> {
			throw new RuntimeException();
		});
		var service = buildServiceForTest(0, 0, 0, 2, 0, 0, 0);

		var advices = factory.arrayNode().add("advice");
		var decision = new AuthorizationDecision(Decision.PERMIT).withAdvice(advices);

		var error = new Exception("Error");
		var bundle = service.buildPreEnforceCommandConstraintHandlerBundle(decision, null, Optional.empty(), null);
		bundle.executeOnErrorHandlers(error);

		verify(firstErrorMappingConstraintHandlerProvider, times(1)).getHandler(any(JsonNode.class));
		verify(secondErrorMappingConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildPreEnforceCommandConstraintHandlerBundle_with_presentGlobalErrorMappingHandlerProviders_and_advice_and_noResponsible_then_noErrorMappingConstraintHandlers() {
		when(firstErrorMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.FALSE);
		when(secondErrorMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.FALSE);
		var service = buildServiceForTest(0, 0, 0, 2, 0, 0, 0);

		var advices = factory.arrayNode().add("advice");
		var decision = new AuthorizationDecision(Decision.PERMIT).withAdvice(advices);

		var error = new Exception("Error");
		var bundle = service.buildPreEnforceCommandConstraintHandlerBundle(decision, null, Optional.empty(), null);
		bundle.executeOnErrorHandlers(error);

		verify(firstErrorMappingConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
		verify(secondErrorMappingConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildPreEnforceCommandConstraintHandlerBundle_with_presentGlobalErrorMappingHandlerProviders_and_advice_and_oneResponsible_then_oneErrorMappingConstraintHandlers() {
		when(firstErrorMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.FALSE);
		when(secondErrorMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(secondErrorMappingConstraintHandlerProvider.getHandler(any(JsonNode.class)))
				.thenReturn(UnaryOperator.identity());
		var service = buildServiceForTest(0, 0, 0, 2, 0, 0, 0);

		var advices = factory.arrayNode().add("advice");
		var decision = new AuthorizationDecision(Decision.PERMIT).withAdvice(advices);

		var error = new Exception("Error");
		var bundle = service.buildPreEnforceCommandConstraintHandlerBundle(decision, null, Optional.empty(), null);
		bundle.executeOnErrorHandlers(error);

		verify(firstErrorMappingConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
		verify(secondErrorMappingConstraintHandlerProvider, times(1)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildPreEnforceCommandConstraintHandlerBundle_with_presentGlobalErrorMappingHandlerProviders_and_advice_and_allResponsible_then_allErrorMappingConstraintHandlers() {
		when(firstErrorMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(secondErrorMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(firstErrorMappingConstraintHandlerProvider.getHandler(any(JsonNode.class)))
				.thenReturn(UnaryOperator.identity());
		when(secondErrorMappingConstraintHandlerProvider.getHandler(any(JsonNode.class)))
				.thenReturn(UnaryOperator.identity());
		var service = buildServiceForTest(0, 0, 0, 2, 0, 0, 0);

		var advices = factory.arrayNode().add("advice");
		var decision = new AuthorizationDecision(Decision.PERMIT).withAdvice(advices);

		var error = new Exception("Error");
		var bundle = service.buildPreEnforceCommandConstraintHandlerBundle(decision, null, Optional.empty(), null);
		bundle.executeOnErrorHandlers(error);

		verify(firstErrorMappingConstraintHandlerProvider, times(1)).getHandler(any(JsonNode.class));
		verify(secondErrorMappingConstraintHandlerProvider, times(1)).getHandler(any(JsonNode.class));
	}

//================================================================

	@Test
	void when_buildPreEnforceCommandConstraintHandlerBundle_with_noGlobalMappingProviders_then_noResultMappingHandlers() {
		var service = buildServiceForTest(0, 0, 0, 0, 0, 0, 0);

		var decision = new AuthorizationDecision(Decision.PERMIT);

		var bundle = service.buildPreEnforceCommandConstraintHandlerBundle(decision, null, Optional.empty(), null);
		bundle.executePostHandlingHandlers(null);

		verify(firstMappingConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
		verify(secondMappingConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildPreEnforceCommandConstraintHandlerBundle_with_presentGlobalMappingProviders_and_noConstraints_then_noResultMappingHandlers() {
		when(firstMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(secondMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		var service = buildServiceForTest(0, 0, 0, 0, 2, 0, 0);

		var decision = new AuthorizationDecision(Decision.PERMIT);

		var bundle = service.buildPreEnforceCommandConstraintHandlerBundle(decision, null, Optional.empty(), null);
		bundle.executePostHandlingHandlers(null);

		verify(firstMappingConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
		verify(secondMappingConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildPreEnforceCommandConstraintHandlerBundle_with_presentGlobalMappingProviders_and_obligation_and_throwingHandler_then_accessDenied() {
		when(firstMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(secondMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.FALSE);
		when(firstMappingConstraintHandlerProvider.getSupportedType()).thenReturn(Object.class);
		when(secondMappingConstraintHandlerProvider.getSupportedType()).thenReturn(Object.class);
		when(firstMappingConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn(__ -> {
			throw new RuntimeException();
		});
		var service = buildServiceForTest(0, 0, 0, 0, 2, 0, 0);

		var obligations = factory.arrayNode().add("obligation");
		var decision = new AuthorizationDecision(Decision.PERMIT).withObligations(obligations);

		var bundle = service.buildPreEnforceCommandConstraintHandlerBundle(decision, null, Optional.empty(), null);
		assertThrows(AccessDeniedException.class, () -> bundle.executePostHandlingHandlers(null));
	}

	@Test
	void when_buildPreEnforceCommandConstraintHandlerBundle_with_presentGlobalMappingProviders_and_obligation_and_noResponsible_then_accessDenied() {
		when(firstMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.FALSE);
		when(secondMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.FALSE);
		when(firstMappingConstraintHandlerProvider.getSupportedType()).thenReturn(Object.class);
		when(secondMappingConstraintHandlerProvider.getSupportedType()).thenReturn(Object.class);
		var service = buildServiceForTest(0, 0, 0, 0, 2, 0, 0);

		var obligations = factory.arrayNode().add("obligation");
		var decision = new AuthorizationDecision(Decision.PERMIT).withObligations(obligations);

		assertThrows(AccessDeniedException.class,
				() -> service.buildPreEnforceCommandConstraintHandlerBundle(decision, null, Optional.empty(), null));
	}

	@Test
	void when_buildPreEnforceCommandConstraintHandlerBundle_with_presentGlobalMappingProviders_and_obligation_and_oneResponsible_then_oneResultMappingHandlers() {
		when(firstMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.FALSE);
		when(secondMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(firstMappingConstraintHandlerProvider.getSupportedType()).thenReturn(Object.class);
		when(secondMappingConstraintHandlerProvider.getSupportedType()).thenReturn(Object.class);
		when(secondMappingConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn(UnaryOperator.identity());
		var service = buildServiceForTest(0, 0, 0, 0, 2, 0, 0);

		var obligations = factory.arrayNode().add("obligation");
		var decision = new AuthorizationDecision(Decision.PERMIT).withObligations(obligations);

		var bundle = service.buildPreEnforceCommandConstraintHandlerBundle(decision, null, Optional.empty(), null);
		bundle.executePostHandlingHandlers(null);

		verify(firstMappingConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
		verify(secondMappingConstraintHandlerProvider, times(1)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildPreEnforceCommandConstraintHandlerBundle_with_presentGlobalMappingProviders_and_obligation_and_oneSupported_then_oneResultMappingHandlers() {
		var alternativeMappingConstraintHandlerProvider = spy(new MappingConstraintHandlerProvider<String>() {

			@Override
			public Class<String> getSupportedType() {
				return String.class;
			}

			@Override
			public boolean isResponsible(JsonNode constraint) {
				return true;
			}

			@Override
			public UnaryOperator<String> getHandler(JsonNode constraint) {
				return UnaryOperator.identity();
			}
		});

		when(firstMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(firstMappingConstraintHandlerProvider.getSupportedType()).thenReturn(Object.class);
		when(firstMappingConstraintHandlerProvider.getHandler(any(JsonNode.class)))
				.thenReturn(UnaryOperator.identity());
		var service = new ConstraintHandlerService(mapper, parameterResolverFactory, List.of(), List.of(), List.of(),
				List.of(), List.of(firstMappingConstraintHandlerProvider, alternativeMappingConstraintHandlerProvider),
				List.of(), List.of());

		var obligations = factory.arrayNode().add("obligation");
		var decision    = new AuthorizationDecision(Decision.PERMIT).withObligations(obligations);

		var bundle = service.buildPreEnforceCommandConstraintHandlerBundle(decision, null, Optional.empty(), null);
		bundle.executePostHandlingHandlers(null);

		verify(firstMappingConstraintHandlerProvider, times(1)).getHandler(any(JsonNode.class));
		verify(alternativeMappingConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildPreEnforceCommandConstraintHandlerBundle_with_presentGlobalMappingProviders_and_obligation_and_allResponsible_then_allResultMappingHandlers() {
		when(firstMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(secondMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(firstMappingConstraintHandlerProvider.getSupportedType()).thenReturn(Object.class);
		when(secondMappingConstraintHandlerProvider.getSupportedType()).thenReturn(Object.class);
		when(firstMappingConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn(UnaryOperator.identity());
		when(secondMappingConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn(UnaryOperator.identity());
		var service = buildServiceForTest(0, 0, 0, 0, 2, 0, 0);

		var obligations = factory.arrayNode().add("obligation");
		var decision = new AuthorizationDecision(Decision.PERMIT).withObligations(obligations);

		var bundle = service.buildPreEnforceCommandConstraintHandlerBundle(decision, null, Optional.empty(), null);
		bundle.executePostHandlingHandlers(null);

		verify(firstMappingConstraintHandlerProvider, times(1)).getHandler(any(JsonNode.class));
		verify(secondMappingConstraintHandlerProvider, times(1)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildPreEnforceCommandConstraintHandlerBundle_with_presentGlobalMappingProviders_and_advice_and_throwingHandler_then_ignoreHandler() {
		when(firstMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(secondMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.FALSE);
		when(firstMappingConstraintHandlerProvider.getSupportedType()).thenReturn(Object.class);
		when(secondMappingConstraintHandlerProvider.getSupportedType()).thenReturn(Object.class);
		when(firstMappingConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn(__ -> {
			throw new RuntimeException();
		});
		var service = buildServiceForTest(0, 0, 0, 0, 2, 0, 0);

		var advices = factory.arrayNode().add("advice");
		var decision = new AuthorizationDecision(Decision.PERMIT).withAdvice(advices);

		var bundle = service.buildPreEnforceCommandConstraintHandlerBundle(decision, null, Optional.empty(), null);
		bundle.executePostHandlingHandlers(null);

		verify(firstMappingConstraintHandlerProvider, times(1)).getHandler(any(JsonNode.class));
		verify(secondMappingConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildPreEnforceCommandConstraintHandlerBundle_with_presentGlobalMappingProviders_and_advice_and_noResponsible_then_noResultMappingHandlers() {
		when(firstMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.FALSE);
		when(secondMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.FALSE);
		when(firstMappingConstraintHandlerProvider.getSupportedType()).thenReturn(Object.class);
		when(secondMappingConstraintHandlerProvider.getSupportedType()).thenReturn(Object.class);
		var service = buildServiceForTest(0, 0, 0, 0, 2, 0, 0);

		var advices = factory.arrayNode().add("advice");
		var decision = new AuthorizationDecision(Decision.PERMIT).withAdvice(advices);

		var bundle = service.buildPreEnforceCommandConstraintHandlerBundle(decision, null, Optional.empty(), null);
		bundle.executePostHandlingHandlers(null);

		verify(firstMappingConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
		verify(secondMappingConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildPreEnforceCommandConstraintHandlerBundle_with_presentGlobalMappingProviders_and_advice_and_oneResponsible_then_oneResultMappingHandlers() {
		when(firstMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.FALSE);
		when(secondMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(firstMappingConstraintHandlerProvider.getSupportedType()).thenReturn(Object.class);
		when(secondMappingConstraintHandlerProvider.getSupportedType()).thenReturn(Object.class);
		when(secondMappingConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn(UnaryOperator.identity());
		var service = buildServiceForTest(0, 0, 0, 0, 2, 0, 0);

		var advices = factory.arrayNode().add("advice");
		var decision = new AuthorizationDecision(Decision.PERMIT).withAdvice(advices);

		var bundle = service.buildPreEnforceCommandConstraintHandlerBundle(decision, null, Optional.empty(), null);
		bundle.executePostHandlingHandlers(null);

		verify(firstMappingConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
		verify(secondMappingConstraintHandlerProvider, times(1)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildPreEnforceCommandConstraintHandlerBundle_with_presentGlobalMappingProviders_and_advice_and_oneSupported_then_oneResultMappingHandlers() {
		var alternativeMappingConstraintHandlerProvider = spy(new MappingConstraintHandlerProvider<String>() {

			@Override
			public Class<String> getSupportedType() {
				return String.class;
			}

			@Override
			public boolean isResponsible(JsonNode constraint) {
				return true;
			}

			@Override
			public UnaryOperator<String> getHandler(JsonNode constraint) {
				return UnaryOperator.identity();
			}
		});

		when(firstMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(firstMappingConstraintHandlerProvider.getSupportedType()).thenReturn(Object.class);
		when(firstMappingConstraintHandlerProvider.getHandler(any(JsonNode.class)))
				.thenReturn(UnaryOperator.identity());
		var service = new ConstraintHandlerService(mapper, parameterResolverFactory, List.of(), List.of(), List.of(),
				List.of(), List.of(firstMappingConstraintHandlerProvider, alternativeMappingConstraintHandlerProvider),
				List.of(), List.of());

		var advices  = factory.arrayNode().add("advice");
		var decision = new AuthorizationDecision(Decision.PERMIT).withAdvice(advices);

		var bundle = service.buildPreEnforceCommandConstraintHandlerBundle(decision, null, Optional.empty(), null);
		bundle.executePostHandlingHandlers(null);

		verify(firstMappingConstraintHandlerProvider, times(1)).getHandler(any(JsonNode.class));
		verify(alternativeMappingConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildPreEnforceCommandConstraintHandlerBundle_with_presentGlobalMappingProviders_and_advice_and_allResponsible_then_allResultMappingHandlers() {
		when(firstMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(secondMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(firstMappingConstraintHandlerProvider.getSupportedType()).thenReturn(Object.class);
		when(secondMappingConstraintHandlerProvider.getSupportedType()).thenReturn(Object.class);
		when(firstMappingConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn(UnaryOperator.identity());
		when(secondMappingConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn(UnaryOperator.identity());
		var service = buildServiceForTest(0, 0, 0, 0, 2, 0, 0);

		var advices = factory.arrayNode().add("advice");
		var decision = new AuthorizationDecision(Decision.PERMIT).withAdvice(advices);

		var bundle = service.buildPreEnforceCommandConstraintHandlerBundle(decision, null, Optional.empty(), null);
		bundle.executePostHandlingHandlers(null);

		verify(firstMappingConstraintHandlerProvider, times(1)).getHandler(any(JsonNode.class));
		verify(secondMappingConstraintHandlerProvider, times(1)).getHandler(any(JsonNode.class));
	}

//================================================================

	@Test
	void when_buildPreEnforceCommandConstraintHandlerBundle_with_obligation_and_handlerObject_and_noHandlers_then_accessDenied() {
		var handlerObject = spy(new EmptyHandlerObject());
		var service       = buildServiceForTest(0, 0, 0, 0, 0, 0, 0);

		var obligations = factory.arrayNode().add("constraint");
		var decision    = new AuthorizationDecision(Decision.PERMIT).withObligations(obligations);

		assertThrows(AccessDeniedException.class, () -> service.buildPreEnforceCommandConstraintHandlerBundle(decision,
				handlerObject, Optional.empty(), null));

		verify(handlerObject, times(0)).noHandle();
	}

	@Test
	void when_buildPreEnforceCommandConstraintHandlerBundle_with_obligation_and_handlerObject_and_oneHandlers_then_oneConstraintHandlers() {
		var handlerObject = spy(new SingleHandlerObject());
		var service       = buildServiceForTest(0, 0, 0, 0, 0, 0, 0);

		var obligations = factory.arrayNode().add("constraint");
		var decision    = new AuthorizationDecision(Decision.PERMIT).withObligations(obligations);

		var bundle = service.buildPreEnforceCommandConstraintHandlerBundle(decision, handlerObject, Optional.empty(),
				null);
		bundle.executeAggregateConstraintHandlerMethods();

		verify(handlerObject, times(0)).noHandle();
		verify(handlerObject, times(1)).handle1();
	}

	@Test
	void when_buildPreEnforceCommandConstraintHandlerBundle_with_obligation_and_handlerObject_and_malformedHandlers_then_accessDenied() {
		var handlerObject = spy(new MalformedHandlerObject());
		var service       = buildServiceForTest(0, 0, 0, 0, 0, 0, 0);

		var obligations = factory.arrayNode().add("constraint");
		var decision    = new AuthorizationDecision(Decision.PERMIT).withObligations(obligations);

		assertThrows(AccessDeniedException.class, () -> service.buildPreEnforceCommandConstraintHandlerBundle(decision,
				handlerObject, Optional.empty(), null));

		verify(handlerObject, times(0)).noHandle();
		verify(handlerObject, times(0)).handle1();
	}

	@Test
	void when_buildPreEnforceCommandConstraintHandlerBundle_with_obligation_and_handlerObject_and_spelHandlers_then_oneConstraintHandlers() {
		var handlerObject = spy(new SpELHandlerObject());
		var service       = buildServiceForTest(0, 0, 0, 0, 0, 0, 0);

		var obligations = factory.arrayNode().add("constraint");
		var decision    = new AuthorizationDecision(Decision.PERMIT).withObligations(obligations);

		var bundle = service.buildPreEnforceCommandConstraintHandlerBundle(decision, handlerObject, Optional.empty(),
				null);
		bundle.executeAggregateConstraintHandlerMethods();

		verify(handlerObject, times(0)).noHandle();
		verify(handlerObject, times(1)).handle1();
	}

	@Test
	void when_buildPreEnforceCommandConstraintHandlerBundle_with_obligation_and_handlerObject_and_nonBooleanSpelHandlers_then_accessDenied() {
		var handlerObject = spy(new NonBooleanSpELHandlerObject());
		var service       = buildServiceForTest(0, 0, 0, 0, 0, 0, 0);

		var obligations = factory.arrayNode().add("constraint");
		var decision    = new AuthorizationDecision(Decision.PERMIT).withObligations(obligations);

		assertThrows(AccessDeniedException.class, () -> service.buildPreEnforceCommandConstraintHandlerBundle(decision,
				handlerObject, Optional.empty(), null));

		verify(handlerObject, times(0)).noHandle();
		verify(handlerObject, times(0)).handle1();
	}

	@Test
	void when_buildPreEnforceCommandConstraintHandlerBundle_with_obligation_and_handlerObject_and_multipleHandlers_then_allConstraintHandlers() {
		var handlerObject = spy(new MultipleHandlerObject());
		var service       = buildServiceForTest(0, 0, 0, 0, 0, 0, 0);

		var obligations = factory.arrayNode().add("constraint");
		var decision    = new AuthorizationDecision(Decision.PERMIT).withObligations(obligations);

		var bundle = service.buildPreEnforceCommandConstraintHandlerBundle(decision, handlerObject, Optional.empty(),
				null);
		bundle.executeAggregateConstraintHandlerMethods();

		verify(handlerObject, times(0)).noHandle();
		verify(handlerObject, times(1)).handle1();
		verify(handlerObject, times(1)).handle2();
	}

	@Test
	void when_buildPreEnforceCommandConstraintHandlerBundle_with_advice_and_handlerObject_and_noHandlers_then_noConstraintHandlers() {
		var handlerObject = spy(new EmptyHandlerObject());
		var service       = buildServiceForTest(0, 0, 0, 0, 0, 0, 0);

		var advices  = factory.arrayNode().add("constraint");
		var decision = new AuthorizationDecision(Decision.PERMIT).withAdvice(advices);

		var bundle = service.buildPreEnforceCommandConstraintHandlerBundle(decision, handlerObject, Optional.empty(),
				null);
		bundle.executeAggregateConstraintHandlerMethods();

		verify(handlerObject, times(0)).noHandle();
	}

	@Test
	void when_buildPreEnforceCommandConstraintHandlerBundle_with_advice_and_handlerObject_and_oneHandlers_then_oneConstraintHandlers() {
		var handlerObject = spy(new SingleHandlerObject());
		var service       = buildServiceForTest(0, 0, 0, 0, 0, 0, 0);

		var advices  = factory.arrayNode().add("constraint");
		var decision = new AuthorizationDecision(Decision.PERMIT).withAdvice(advices);

		var bundle = service.buildPreEnforceCommandConstraintHandlerBundle(decision, handlerObject, Optional.empty(),
				null);
		bundle.executeAggregateConstraintHandlerMethods();

		verify(handlerObject, times(0)).noHandle();
		verify(handlerObject, times(1)).handle1();
	}

	@Test
	void when_buildPreEnforceCommandConstraintHandlerBundle_with_advice_and_handlerObject_and_malformedHandlers_then_noConstraintHandlers() {
		var handlerObject = spy(new MalformedHandlerObject());
		var service       = buildServiceForTest(0, 0, 0, 0, 0, 0, 0);

		var advices  = factory.arrayNode().add("constraint");
		var decision = new AuthorizationDecision(Decision.PERMIT).withAdvice(advices);

		var bundle = service.buildPreEnforceCommandConstraintHandlerBundle(decision, handlerObject, Optional.empty(),
				null);
		bundle.executeAggregateConstraintHandlerMethods();

		verify(handlerObject, times(0)).noHandle();
		verify(handlerObject, times(0)).handle1();
	}

	@Test
	void when_buildPreEnforceCommandConstraintHandlerBundle_with_advice_and_handlerObject_and_spelHandlers_then_oneConstraintHandlers() {
		var handlerObject = spy(new SpELHandlerObject());
		var service       = buildServiceForTest(0, 0, 0, 0, 0, 0, 0);

		var advices  = factory.arrayNode().add("constraint");
		var decision = new AuthorizationDecision(Decision.PERMIT).withAdvice(advices);

		var bundle = service.buildPreEnforceCommandConstraintHandlerBundle(decision, handlerObject, Optional.empty(),
				null);
		bundle.executeAggregateConstraintHandlerMethods();

		verify(handlerObject, times(0)).noHandle();
		verify(handlerObject, times(1)).handle1();
	}

	@Test
	void when_buildPreEnforceCommandConstraintHandlerBundle_with_advice_and_handlerObject_and_nonBooleanSpelHandlers_then_noConstraintHandlers() {
		var handlerObject = spy(new NonBooleanSpELHandlerObject());
		var service       = buildServiceForTest(0, 0, 0, 0, 0, 0, 0);

		var advices  = factory.arrayNode().add("constraint");
		var decision = new AuthorizationDecision(Decision.PERMIT).withAdvice(advices);

		var bundle = service.buildPreEnforceCommandConstraintHandlerBundle(decision, handlerObject, Optional.empty(),
				null);
		bundle.executeAggregateConstraintHandlerMethods();

		verify(handlerObject, times(0)).noHandle();
		verify(handlerObject, times(0)).handle1();
	}

	@Test
	void when_buildPreEnforceCommandConstraintHandlerBundle_with_advice_and_handlerObject_and_multipleHandlers_then_allConstraintHandlers() {
		var handlerObject = spy(new MultipleHandlerObject());
		var service       = buildServiceForTest(0, 0, 0, 0, 0, 0, 0);

		var advices  = factory.arrayNode().add("constraint");
		var decision = new AuthorizationDecision(Decision.PERMIT).withAdvice(advices);

		var bundle = service.buildPreEnforceCommandConstraintHandlerBundle(decision, handlerObject, Optional.empty(),
				null);
		bundle.executeAggregateConstraintHandlerMethods();

		verify(handlerObject, times(0)).noHandle();
		verify(handlerObject, times(1)).handle1();
		verify(handlerObject, times(1)).handle2();
	}

	@Test
	void when_buildPreEnforceCommandConstraintHandlerBundle_with_someConstraint_and_handlerObject_and_parameterizedHandler_then_parameterizedConstraintHandlers() {
		var handlerObject = spy(new HandlerObjectWithParameters());
		var service       = buildServiceForTest(0, 0, 0, 0, 0, 0, 0);

		var advices  = factory.arrayNode().add("constraint");
		var decision = new AuthorizationDecision(Decision.PERMIT).withAdvice(advices);
		var payload  = new Object();

		var bundle = service.buildPreEnforceCommandConstraintHandlerBundle(decision, handlerObject, Optional.empty(),
				new GenericCommandMessage<>(payload));
		bundle.executeAggregateConstraintHandlerMethods();

		verify(handlerObject, times(0)).noHandle();
		verify(handlerObject, times(1)).handle1(payload, advices.get(0), decision);
	}

	@Test
	void when_buildPreEnforceCommandConstraintHandlerBundle_with_someConstraint_and_handlerObject_and_unresolvedParameterizedHandler_then_parameterizedConstraintHandlers() {
		var handlerObject = spy(new HandlerObjectWithUnresolvedParameters());
		var service       = buildServiceForTest(0, 0, 0, 0, 0, 0, 0);

		var advices  = factory.arrayNode().add("constraint");
		var decision = new AuthorizationDecision(Decision.PERMIT).withAdvice(advices);
		var payload  = "payload";

		var bundle = service.buildPreEnforceCommandConstraintHandlerBundle(decision, handlerObject, Optional.empty(),
				new GenericCommandMessage<>(payload));
		bundle.executeAggregateConstraintHandlerMethods();

		verify(handlerObject, times(0)).noHandle();
		verify(handlerObject, times(0)).handle1(eq(advices.get(0)), eq(decision), any(Object.class));
	}

	@Test
	void when_buildPreEnforceCommandConstraintHandlerBundle_with_someConstraint_and_handlerObject_and_throwingHandlers_then_sneakyThrows() {
		var handlerObject = spy(new ThrowingHandlerObject());
		var service       = buildServiceForTest(0, 0, 0, 0, 0, 0, 0);

		var advices  = factory.arrayNode().add("constraint");
		var decision = new AuthorizationDecision(Decision.PERMIT).withAdvice(advices);

		assertDoesNotThrow(() -> {
			var bundle = service.buildPreEnforceCommandConstraintHandlerBundle(decision, handlerObject,
					Optional.empty(), null);
			bundle.executeAggregateConstraintHandlerMethods();

			verify(handlerObject, times(0)).noHandle();
			verify(handlerObject, times(1)).handle1();
		});
	}

//================================================================
//||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||
//================================================================

	@Test
	void when_buildQueryPreHandlerBundle_with_noGlobalRunnableProviders_then_noOnDecisionConstraintHandlers() {
		var service = buildServiceForTest(0, 0, 0, 0, 0, 0, 0);

		var decision = new AuthorizationDecision(Decision.PERMIT);

		var bundle = service.buildQueryPreHandlerBundle(decision, null, Optional.empty());
		bundle.executeOnDecisionHandlers(null, null);

		verify(firstOnDecisionConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
		verify(secondOnDecisionConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildQueryPreHandlerBundle_with_presentGlobalRunnableProviders_and_noConstraints_then_noOnDecisionConstraintHandlers() {
		when(firstOnDecisionConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(secondOnDecisionConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		var service = buildServiceForTest(2, 0, 0, 0, 0, 0, 0);

		var decision = new AuthorizationDecision(Decision.PERMIT);

		var bundle = service.buildQueryPreHandlerBundle(decision, null, Optional.empty());
		bundle.executeOnDecisionHandlers(null, null);

		verify(firstOnDecisionConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
		verify(secondOnDecisionConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildQueryPreHandlerBundle_with_presentGlobalRunnableProviders_and_obligation_and_noResponsible_then_accessDenied() {
		when(firstOnDecisionConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.FALSE);
		when(secondOnDecisionConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.FALSE);
		var service = buildServiceForTest(2, 0, 0, 0, 0, 0, 0);

		var obligations = factory.arrayNode().add("obligation");
		var decision = new AuthorizationDecision(Decision.PERMIT).withObligations(obligations);

		assertThrows(AccessDeniedException.class,
				() -> service.buildQueryPreHandlerBundle(decision, null, Optional.empty()));
	}

	@Test
	void when_buildQueryPreHandlerBundle_with_presentGlobalRunnableProviders_and_obligation_and_throwingHandler_then_accessDenied() {
		when(firstOnDecisionConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.FALSE);
		when(secondOnDecisionConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(secondOnDecisionConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn((__, ___) -> {
			throw new RuntimeException();
		});
		var service = buildServiceForTest(2, 0, 0, 0, 0, 0, 0);

		var obligations = factory.arrayNode().add("obligation");
		var decision = new AuthorizationDecision(Decision.PERMIT).withObligations(obligations);

		var bundle = service.buildQueryPreHandlerBundle(decision, null, Optional.empty());
		assertThrows(AccessDeniedException.class, () -> bundle.executeOnDecisionHandlers(null, null));

		verify(firstOnDecisionConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
		verify(secondOnDecisionConstraintHandlerProvider, times(1)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildQueryPreHandlerBundle_with_presentGlobalRunnableProviders_and_obligation_and_oneResponsible_then_oneOnDecisionHandlers() {
		when(firstOnDecisionConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.FALSE);
		when(secondOnDecisionConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(secondOnDecisionConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn((__, ___) -> {
		});
		var service = buildServiceForTest(2, 0, 0, 0, 0, 0, 0);

		var obligations = factory.arrayNode().add("obligation");
		var decision = new AuthorizationDecision(Decision.PERMIT).withObligations(obligations);

		var bundle = service.buildQueryPreHandlerBundle(decision, null, Optional.empty());
		bundle.executeOnDecisionHandlers(null, null);

		verify(firstOnDecisionConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
		verify(secondOnDecisionConstraintHandlerProvider, times(1)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildQueryPreHandlerBundle_with_presentGlobalRunnableProviders_and_obligation_and_allResponsible_then_allOnDecisionHandlers() {
		when(firstOnDecisionConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(secondOnDecisionConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(firstOnDecisionConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn((__, ___) -> {
		});
		when(secondOnDecisionConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn((__, ___) -> {
		});
		var service = buildServiceForTest(2, 0, 0, 0, 0, 0, 0);

		var obligations = factory.arrayNode().add("obligation");
		var decision = new AuthorizationDecision(Decision.PERMIT).withObligations(obligations);

		var bundle = service.buildQueryPreHandlerBundle(decision, null, Optional.empty());
		bundle.executeOnDecisionHandlers(null, null);

		verify(firstOnDecisionConstraintHandlerProvider, times(1)).getHandler(any(JsonNode.class));
		verify(secondOnDecisionConstraintHandlerProvider, times(1)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildQueryPreHandlerBundle_with_presentGlobalRunnableProviders_and_advice_and_noResponsible_then_noOnDecisionHandlers() {
		when(firstOnDecisionConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.FALSE);
		when(secondOnDecisionConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.FALSE);
		var service = buildServiceForTest(2, 0, 0, 0, 0, 0, 0);

		var advices = factory.arrayNode().add("advice");
		var decision = new AuthorizationDecision(Decision.PERMIT).withAdvice(advices);

		var bundle = service.buildQueryPreHandlerBundle(decision, null, Optional.empty());
		bundle.executeOnDecisionHandlers(null, null);

		verify(firstOnDecisionConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
		verify(secondOnDecisionConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildQueryPreHandlerBundle_with_presentGlobalRunnableProviders_and_advice_and_throwingHandler_then_oneOnDecisionHandlers() {
		when(firstOnDecisionConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.FALSE);
		when(secondOnDecisionConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(secondOnDecisionConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn((__, ___) -> {
			throw new RuntimeException();
		});
		var service = buildServiceForTest(2, 0, 0, 0, 0, 0, 0);

		var advices = factory.arrayNode().add("advice");
		var decision = new AuthorizationDecision(Decision.PERMIT).withAdvice(advices);

		var bundle = service.buildQueryPreHandlerBundle(decision, null, Optional.empty());
		assertDoesNotThrow(() -> bundle.executeOnDecisionHandlers(null, null));

		verify(firstOnDecisionConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
		verify(secondOnDecisionConstraintHandlerProvider, times(1)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildQueryPreHandlerBundle_with_presentGlobalRunnableProviders_and_advice_and_oneResponsible_then_oneOnDecisionHandlers() {
		when(firstOnDecisionConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.FALSE);
		when(secondOnDecisionConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(secondOnDecisionConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn((__, ___) -> {
		});
		var service = buildServiceForTest(2, 0, 0, 0, 0, 0, 0);

		var advices = factory.arrayNode().add("advice");
		var decision = new AuthorizationDecision(Decision.PERMIT).withAdvice(advices);

		var bundle = service.buildQueryPreHandlerBundle(decision, null, Optional.empty());
		bundle.executeOnDecisionHandlers(null, null);

		verify(firstOnDecisionConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
		verify(secondOnDecisionConstraintHandlerProvider, times(1)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildQueryPreHandlerBundle_with_presentGlobalRunnableProviders_and_advice_and_allResponsible_then_allOnDecisionHandlers() {
		when(firstOnDecisionConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(secondOnDecisionConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(firstOnDecisionConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn((__, ___) -> {
		});
		when(secondOnDecisionConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn((__, ___) -> {
		});
		var service = buildServiceForTest(2, 0, 0, 0, 0, 0, 0);

		var advices = factory.arrayNode().add("advice");
		var decision = new AuthorizationDecision(Decision.PERMIT).withAdvice(advices);

		var bundle = service.buildQueryPreHandlerBundle(decision, null, Optional.empty());
		bundle.executeOnDecisionHandlers(null, null);

		verify(firstOnDecisionConstraintHandlerProvider, times(1)).getHandler(any(JsonNode.class));
		verify(secondOnDecisionConstraintHandlerProvider, times(1)).getHandler(any(JsonNode.class));
	}

//================================================================

	@Test
	void when_buildQueryPreHandlerBundle_with_noGlobalQueryMessageMappingProviders_then_noQueryConstraintHandlers() {
		var service = buildServiceForTest(0, 0, 0, 0, 0, 0, 0);

		var decision = new AuthorizationDecision(Decision.PERMIT);

		var bundle = service.buildQueryPreHandlerBundle(decision, null, Optional.empty());
		bundle.executePreHandlingHandlers(null);

		verify(firstQueryConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
		verify(secondQueryConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildQueryPreHandlerBundle_with_presentGlobalQueryMessageMappingProviders_and_noConstraints_then_noQueryConstraintHandlers() {
		when(firstQueryConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(secondQueryConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		var service = buildServiceForTest(0, 0, 2, 0, 0, 0, 0);

		var decision = new AuthorizationDecision(Decision.PERMIT);

		var bundle = service.buildQueryPreHandlerBundle(decision, null, Optional.empty());
		bundle.executePreHandlingHandlers(null);

		verify(firstQueryConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
		verify(secondQueryConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildQueryPreHandlerBundle_with_presentGlobalQueryMessageMappingProviders_and_obligation_and_throwingHandler_then_accessDenied() {
		when(firstQueryConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(secondQueryConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.FALSE);
		when(firstQueryConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn(__ -> {
			throw new RuntimeException();
		});
		var service = buildServiceForTest(0, 0, 2, 0, 0, 0, 0);

		var obligations = factory.arrayNode().add("obligation");
		var decision = new AuthorizationDecision(Decision.PERMIT).withObligations(obligations);

		var bundle = service.buildQueryPreHandlerBundle(decision, null, Optional.empty());
		assertThrows(AccessDeniedException.class, () -> bundle.executePreHandlingHandlers(null));
	}

	@Test
	void when_buildQueryPreHandlerBundle_with_presentGlobalQueryMessageMappingProviders_and_obligation_and_noResponsible_then_accessDenied() {
		when(firstQueryConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.FALSE);
		when(secondQueryConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.FALSE);
		var service = buildServiceForTest(0, 0, 2, 0, 0, 0, 0);

		var obligations = factory.arrayNode().add("obligation");
		var decision = new AuthorizationDecision(Decision.PERMIT).withObligations(obligations);

		assertThrows(AccessDeniedException.class,
				() -> service.buildQueryPreHandlerBundle(decision, null, Optional.empty()));
	}

	@Test
	void when_buildQueryPreHandlerBundle_with_presentGlobalQueryMessageMappingProviders_and_obligation_and_oneResponsible_then_oneQueryConstraintHandlers() {
		when(firstQueryConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.FALSE);
		when(secondQueryConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(secondQueryConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn(UnaryOperator.identity());
		var service = buildServiceForTest(0, 0, 2, 0, 0, 0, 0);

		var obligations = factory.arrayNode().add("obligation");
		var decision = new AuthorizationDecision(Decision.PERMIT).withObligations(obligations);

		var bundle = service.buildQueryPreHandlerBundle(decision, null, Optional.empty());
		bundle.executePreHandlingHandlers(null);

		verify(firstQueryConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
		verify(secondQueryConstraintHandlerProvider, times(1)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildQueryPreHandlerBundle_with_presentGlobalQueryMessageMappingProviders_and_obligation_and_allResponsible_then_allQueryConstraintHandlers() {
		when(firstQueryConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(secondQueryConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(firstQueryConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn(UnaryOperator.identity());
		when(secondQueryConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn(UnaryOperator.identity());
		var service = buildServiceForTest(0, 0, 2, 0, 0, 0, 0);

		var obligations = factory.arrayNode().add("obligation");
		var decision = new AuthorizationDecision(Decision.PERMIT).withObligations(obligations);

		var bundle = service.buildQueryPreHandlerBundle(decision, null, Optional.empty());
		bundle.executePreHandlingHandlers(null);

		verify(firstQueryConstraintHandlerProvider, times(1)).getHandler(any(JsonNode.class));
		verify(secondQueryConstraintHandlerProvider, times(1)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildQueryPreHandlerBundle_with_presentGlobalQueryMessageMappingProviders_and_advice_and_throwingHandler_then_ignoreHandler() {
		when(firstQueryConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(secondQueryConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.FALSE);
		when(firstQueryConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn(__ -> {
			throw new RuntimeException();
		});
		var service = buildServiceForTest(0, 0, 2, 0, 0, 0, 0);

		var advices = factory.arrayNode().add("advice");
		var decision = new AuthorizationDecision(Decision.PERMIT).withAdvice(advices);

		var bundle = service.buildQueryPreHandlerBundle(decision, null, Optional.empty());
		bundle.executePreHandlingHandlers(null);

		verify(firstQueryConstraintHandlerProvider, times(1)).getHandler(any(JsonNode.class));
		verify(secondQueryConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildQueryPreHandlerBundle_with_presentGlobalQueryMessageMappingProviders_and_advice_and_noResponsible_then_noQueryConstraintHandlers() {
		when(firstQueryConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.FALSE);
		when(secondQueryConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.FALSE);
		var service = buildServiceForTest(0, 0, 2, 0, 0, 0, 0);

		var advices = factory.arrayNode().add("advice");
		var decision = new AuthorizationDecision(Decision.PERMIT).withAdvice(advices);

		var bundle = service.buildQueryPreHandlerBundle(decision, null, Optional.empty());
		bundle.executePreHandlingHandlers(null);

		verify(firstQueryConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
		verify(secondQueryConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildQueryPreHandlerBundle_with_presentGlobalQueryMessageMappingProviders_and_advice_and_oneResponsible_then_oneQueryConstraintHandlers() {
		when(firstQueryConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.FALSE);
		when(secondQueryConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(secondQueryConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn(UnaryOperator.identity());
		var service = buildServiceForTest(0, 0, 2, 0, 0, 0, 0);

		var advices = factory.arrayNode().add("advice");
		var decision = new AuthorizationDecision(Decision.PERMIT).withAdvice(advices);

		var bundle = service.buildQueryPreHandlerBundle(decision, null, Optional.empty());
		bundle.executePreHandlingHandlers(null);

		verify(firstQueryConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
		verify(secondQueryConstraintHandlerProvider, times(1)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildQueryPreHandlerBundle_with_presentGlobalQueryMessageMappingProviders_and_advice_and_allResponsible_then_allQueryConstraintHandlers() {
		when(firstQueryConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(secondQueryConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(firstQueryConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn(UnaryOperator.identity());
		when(secondQueryConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn(UnaryOperator.identity());
		var service = buildServiceForTest(0, 0, 2, 0, 0, 0, 0);

		var advices = factory.arrayNode().add("advice");
		var decision = new AuthorizationDecision(Decision.PERMIT).withAdvice(advices);

		var bundle = service.buildQueryPreHandlerBundle(decision, null, Optional.empty());
		bundle.executePreHandlingHandlers(null);

		verify(firstQueryConstraintHandlerProvider, times(1)).getHandler(any(JsonNode.class));
		verify(secondQueryConstraintHandlerProvider, times(1)).getHandler(any(JsonNode.class));
	}

//================================================================

	@Test
	void when_buildQueryPreHandlerBundle_with_noGlobalErrorMappingHandlerProviders_then_noErrorMappingConstraintHandlers() {
		var service = buildServiceForTest(0, 0, 0, 0, 0, 0, 0);

		var decision = new AuthorizationDecision(Decision.PERMIT);

		var error  = new Exception("Error");
		var bundle = service.buildQueryPreHandlerBundle(decision, null, Optional.empty());
		bundle.executeOnErrorHandlers(error);

		verify(firstErrorMappingConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
		verify(secondErrorMappingConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildQueryPreHandlerBundle_with_presentGlobalErrorMappingHandlerProviders_and_noConstraints_then_noErrorMappingConstraintHandlers() {
		when(firstErrorMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(secondErrorMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		var service = buildServiceForTest(0, 0, 0, 2, 0, 0, 0);

		var decision = new AuthorizationDecision(Decision.PERMIT);

		var error = new Exception("Error");
		var bundle = service.buildQueryPreHandlerBundle(decision, null, Optional.empty());
		bundle.executeOnErrorHandlers(error);

		verify(firstErrorMappingConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
		verify(secondErrorMappingConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildQueryPreHandlerBundle_with_presentGlobalErrorMappingHandlerProviders_and_obligation_and_throwingHandler_then_accessDenied() {
		when(firstErrorMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(secondErrorMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.FALSE);
		when(firstErrorMappingConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn(__ -> {
			throw new RuntimeException();
		});
		var service = buildServiceForTest(0, 0, 0, 2, 0, 0, 0);

		var obligations = factory.arrayNode().add("obligation");
		var decision = new AuthorizationDecision(Decision.PERMIT).withObligations(obligations);

		var error = new Exception("Error");
		var bundle = service.buildQueryPreHandlerBundle(decision, null, Optional.empty());
		assertThrows(AccessDeniedException.class, () -> bundle.executeOnErrorHandlers(error));
	}

	@Test
	void when_buildQueryPreHandlerBundle_with_presentGlobalErrorMappingHandlerProviders_and_obligation_and_noResponsible_then_accessDenied() {
		when(firstErrorMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.FALSE);
		when(secondErrorMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.FALSE);
		var service = buildServiceForTest(0, 0, 0, 2, 0, 0, 0);

		var obligations = factory.arrayNode().add("obligation");
		var decision = new AuthorizationDecision(Decision.PERMIT).withObligations(obligations);

		assertThrows(AccessDeniedException.class,
				() -> service.buildQueryPreHandlerBundle(decision, null, Optional.empty()));
	}

	@Test
	void when_buildQueryPreHandlerBundle_with_presentGlobalErrorMappingHandlerProviders_and_obligation_and_oneResponsible_then_oneErrorMappingConstraintHandlers() {
		when(firstErrorMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.FALSE);
		when(secondErrorMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(secondErrorMappingConstraintHandlerProvider.getHandler(any(JsonNode.class)))
				.thenReturn(UnaryOperator.identity());
		var service = buildServiceForTest(0, 0, 0, 2, 0, 0, 0);

		var obligations = factory.arrayNode().add("obligation");
		var decision = new AuthorizationDecision(Decision.PERMIT).withObligations(obligations);

		var error = new Exception("Error");
		var bundle = service.buildQueryPreHandlerBundle(decision, null, Optional.empty());
		bundle.executeOnErrorHandlers(error);

		verify(firstErrorMappingConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
		verify(secondErrorMappingConstraintHandlerProvider, times(1)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildQueryPreHandlerBundle_with_presentGlobalErrorMappingHandlerProviders_and_obligation_and_allResponsible_then_allErrorMappingConstraintHandlers() {
		when(firstErrorMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(secondErrorMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(firstErrorMappingConstraintHandlerProvider.getHandler(any(JsonNode.class)))
				.thenReturn(UnaryOperator.identity());
		when(secondErrorMappingConstraintHandlerProvider.getHandler(any(JsonNode.class)))
				.thenReturn(UnaryOperator.identity());
		var service = buildServiceForTest(0, 0, 0, 2, 0, 0, 0);

		var obligations = factory.arrayNode().add("obligation");
		var decision = new AuthorizationDecision(Decision.PERMIT).withObligations(obligations);

		var error = new Exception("Error");
		var bundle = service.buildQueryPreHandlerBundle(decision, null, Optional.empty());
		bundle.executeOnErrorHandlers(error);

		verify(firstErrorMappingConstraintHandlerProvider, times(1)).getHandler(any(JsonNode.class));
		verify(secondErrorMappingConstraintHandlerProvider, times(1)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildQueryPreHandlerBundle_with_presentGlobalErrorMappingHandlerProviders_and_advice_and_throwingHandler_then_ignoreHandler() {
		when(firstErrorMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(secondErrorMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.FALSE);
		when(firstErrorMappingConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn(__ -> {
			throw new RuntimeException();
		});
		var service = buildServiceForTest(0, 0, 0, 2, 0, 0, 0);

		var advices = factory.arrayNode().add("advice");
		var decision = new AuthorizationDecision(Decision.PERMIT).withAdvice(advices);

		var error = new Exception("Error");
		var bundle = service.buildQueryPreHandlerBundle(decision, null, Optional.empty());
		bundle.executeOnErrorHandlers(error);

		verify(firstErrorMappingConstraintHandlerProvider, times(1)).getHandler(any(JsonNode.class));
		verify(secondErrorMappingConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildQueryPreHandlerBundle_with_presentGlobalErrorMappingHandlerProviders_and_advice_and_noResponsible_then_noErrorMappingConstraintHandlers() {
		when(firstErrorMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.FALSE);
		when(secondErrorMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.FALSE);
		var service = buildServiceForTest(0, 0, 0, 2, 0, 0, 0);

		var advices = factory.arrayNode().add("advice");
		var decision = new AuthorizationDecision(Decision.PERMIT).withAdvice(advices);

		var error = new Exception("Error");
		var bundle = service.buildQueryPreHandlerBundle(decision, null, Optional.empty());
		bundle.executeOnErrorHandlers(error);

		verify(firstErrorMappingConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
		verify(secondErrorMappingConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildQueryPreHandlerBundle_with_presentGlobalErrorMappingHandlerProviders_and_advice_and_oneResponsible_then_oneErrorMappingConstraintHandlers() {
		when(firstErrorMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.FALSE);
		when(secondErrorMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(secondErrorMappingConstraintHandlerProvider.getHandler(any(JsonNode.class)))
				.thenReturn(UnaryOperator.identity());
		var service = buildServiceForTest(0, 0, 0, 2, 0, 0, 0);

		var advices = factory.arrayNode().add("advice");
		var decision = new AuthorizationDecision(Decision.PERMIT).withAdvice(advices);

		var error = new Exception("Error");
		var bundle = service.buildQueryPreHandlerBundle(decision, null, Optional.empty());
		bundle.executeOnErrorHandlers(error);

		verify(firstErrorMappingConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
		verify(secondErrorMappingConstraintHandlerProvider, times(1)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildQueryPreHandlerBundle_with_presentGlobalErrorMappingHandlerProviders_and_advice_and_allResponsible_then_allErrorMappingConstraintHandlers() {
		when(firstErrorMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(secondErrorMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(firstErrorMappingConstraintHandlerProvider.getHandler(any(JsonNode.class)))
				.thenReturn(UnaryOperator.identity());
		when(secondErrorMappingConstraintHandlerProvider.getHandler(any(JsonNode.class)))
				.thenReturn(UnaryOperator.identity());
		var service = buildServiceForTest(0, 0, 0, 2, 0, 0, 0);

		var advices = factory.arrayNode().add("advice");
		var decision = new AuthorizationDecision(Decision.PERMIT).withAdvice(advices);

		var error = new Exception("Error");
		var bundle = service.buildQueryPreHandlerBundle(decision, null, Optional.empty());
		bundle.executeOnErrorHandlers(error);

		verify(firstErrorMappingConstraintHandlerProvider, times(1)).getHandler(any(JsonNode.class));
		verify(secondErrorMappingConstraintHandlerProvider, times(1)).getHandler(any(JsonNode.class));
	}

//================================================================

	@Test
	void when_buildQueryPreHandlerBundle_with_noInitialMappingProviders_then_noResultConstraintHandlers() {
		var service = buildServiceForTest(0, 0, 0, 0, 0, 0, 0);

		var decision     = new AuthorizationDecision(Decision.PERMIT);
		var responseType = ResponseTypes.instanceOf(Object.class);

		var bundle = service.buildQueryPreHandlerBundle(decision, responseType, Optional.empty());
		bundle.executePostHandlingHandlers(null);

		verify(firstResultConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
		verify(secondResultConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildQueryPreHandlerBundle_with_presentInitialMappingProviders_and_noConstraints_then_noResultConstraintHandlers() {
		when(firstResultConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(secondResultConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		var service = buildServiceForTest(0, 0, 0, 0, 0, 0, 2);

		var decision = new AuthorizationDecision(Decision.PERMIT);
		var responseType = ResponseTypes.instanceOf(Object.class);

		var bundle = service.buildQueryPreHandlerBundle(decision, responseType, Optional.empty());
		bundle.executePostHandlingHandlers(null);

		verify(firstResultConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
		verify(secondResultConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildQueryPreHandlerBundle_with_presentInitialMappingProviders_and_obligation_and_throwingHandler_then_accessDenied() {
		when(firstOnDecisionConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(firstResultConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(secondResultConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.FALSE);
		when(firstResultConstraintHandlerProvider.supports(any(ResponseType.class))).thenReturn(Boolean.TRUE);
		when(secondResultConstraintHandlerProvider.supports(any(ResponseType.class))).thenReturn(Boolean.TRUE);
		when(firstResultConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn(__ -> {
			throw new RuntimeException();
		});
		var service = buildServiceForTest(1, 0, 0, 0, 0, 0, 2);

		var obligations = factory.arrayNode().add("obligation");
		var decision = new AuthorizationDecision(Decision.PERMIT).withObligations(obligations);
		var responseType = ResponseTypes.instanceOf(Object.class);

		var bundle = service.buildQueryPreHandlerBundle(decision, responseType, Optional.empty());
		assertThrows(AccessDeniedException.class, () -> bundle.executePostHandlingHandlers(null));
	}

	@Test
	void when_buildQueryPreHandlerBundle_with_presentInitialMappingProviders_and_obligation_and_noResponsible_then_accessDenied() {
		when(firstResultConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.FALSE);
		when(secondResultConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.FALSE);
		when(firstResultConstraintHandlerProvider.supports(any(ResponseType.class))).thenReturn(Boolean.TRUE);
		when(secondResultConstraintHandlerProvider.supports(any(ResponseType.class))).thenReturn(Boolean.TRUE);
		var service = buildServiceForTest(0, 0, 0, 0, 0, 0, 2);

		var obligations = factory.arrayNode().add("obligation");
		var decision = new AuthorizationDecision(Decision.PERMIT).withObligations(obligations);
		var responseType = ResponseTypes.instanceOf(Object.class);

		assertThrows(AccessDeniedException.class,
				() -> service.buildQueryPreHandlerBundle(decision, responseType, Optional.empty()));
	}

	@Test
	void when_buildQueryPreHandlerBundle_with_presentInitialMappingProviders_and_obligation_and_oneResponsible_then_oneResultConstraintHandlers() {
		when(firstOnDecisionConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(firstResultConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.FALSE);
		when(secondResultConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(firstResultConstraintHandlerProvider.supports(any(ResponseType.class))).thenReturn(Boolean.TRUE);
		when(secondResultConstraintHandlerProvider.supports(any(ResponseType.class))).thenReturn(Boolean.TRUE);
		when(secondResultConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn(UnaryOperator.identity());
		var service = buildServiceForTest(1, 0, 0, 0, 0, 0, 2);

		var obligations = factory.arrayNode().add("obligation");
		var decision = new AuthorizationDecision(Decision.PERMIT).withObligations(obligations);
		var responseType = ResponseTypes.instanceOf(Object.class);

		var bundle = service.buildQueryPreHandlerBundle(decision, responseType, Optional.empty());
		bundle.executePostHandlingHandlers(null);

		verify(firstResultConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
		verify(secondResultConstraintHandlerProvider, times(1)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildQueryPreHandlerBundle_with_presentInitialMappingProviders_and_obligation_and_oneSupported_then_oneResultConstraintHandlers() {
		when(firstOnDecisionConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(firstResultConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(secondResultConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(firstResultConstraintHandlerProvider.supports(any(ResponseType.class))).thenReturn(Boolean.FALSE);
		when(secondResultConstraintHandlerProvider.supports(any(ResponseType.class))).thenReturn(Boolean.TRUE);
		when(secondResultConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn(UnaryOperator.identity());
		var service = buildServiceForTest(1, 0, 0, 0, 0, 0, 2);

		var obligations = factory.arrayNode().add("obligation");
		var decision = new AuthorizationDecision(Decision.PERMIT).withObligations(obligations);
		var responseType = ResponseTypes.instanceOf(Object.class);

		var bundle = service.buildQueryPreHandlerBundle(decision, responseType, Optional.empty());
		bundle.executePostHandlingHandlers(null);

		verify(firstResultConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
		verify(secondResultConstraintHandlerProvider, times(1)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildQueryPreHandlerBundle_with_presentInitialMappingProviders_and_obligation_and_allResponsible_then_allResultConstraintHandlers() {
		when(firstOnDecisionConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(firstResultConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(secondResultConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(firstResultConstraintHandlerProvider.supports(any(ResponseType.class))).thenReturn(Boolean.TRUE);
		when(secondResultConstraintHandlerProvider.supports(any(ResponseType.class))).thenReturn(Boolean.TRUE);
		when(firstResultConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn(UnaryOperator.identity());
		when(secondResultConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn(UnaryOperator.identity());
		var service = buildServiceForTest(1, 0, 0, 0, 0, 0, 2);

		var obligations = factory.arrayNode().add("obligation");
		var decision = new AuthorizationDecision(Decision.PERMIT).withObligations(obligations);
		var responseType = ResponseTypes.instanceOf(Object.class);

		var bundle = service.buildQueryPreHandlerBundle(decision, responseType, Optional.empty());
		bundle.executePostHandlingHandlers(null);

		verify(firstResultConstraintHandlerProvider, times(1)).getHandler(any(JsonNode.class));
		verify(secondResultConstraintHandlerProvider, times(1)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildQueryPreHandlerBundle_with_presentInitialMappingProviders_and_advice_and_throwingHandler_then_ignoreHandler() {
		when(firstResultConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(secondResultConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.FALSE);
		when(firstResultConstraintHandlerProvider.supports(any(ResponseType.class))).thenReturn(Boolean.TRUE);
		when(secondResultConstraintHandlerProvider.supports(any(ResponseType.class))).thenReturn(Boolean.TRUE);
		when(firstResultConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn(__ -> {
			throw new RuntimeException();
		});
		var service = buildServiceForTest(0, 0, 0, 0, 0, 0, 2);

		var advices = factory.arrayNode().add("advice");
		var decision = new AuthorizationDecision(Decision.PERMIT).withAdvice(advices);
		var responseType = ResponseTypes.instanceOf(Object.class);

		var bundle = service.buildQueryPreHandlerBundle(decision, responseType, Optional.empty());
		bundle.executePostHandlingHandlers(null);

		verify(firstResultConstraintHandlerProvider, times(1)).getHandler(any(JsonNode.class));
		verify(secondResultConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildQueryPreHandlerBundle_with_presentInitialMappingProviders_and_advice_and_noResponsible_then_noResultConstraintHandlers() {
		when(firstResultConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.FALSE);
		when(secondResultConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.FALSE);
		when(firstResultConstraintHandlerProvider.supports(any(ResponseType.class))).thenReturn(Boolean.TRUE);
		when(secondResultConstraintHandlerProvider.supports(any(ResponseType.class))).thenReturn(Boolean.TRUE);
		var service = buildServiceForTest(0, 0, 0, 0, 0, 0, 2);

		var advices = factory.arrayNode().add("advice");
		var decision = new AuthorizationDecision(Decision.PERMIT).withAdvice(advices);
		var responseType = ResponseTypes.instanceOf(Object.class);

		var bundle = service.buildQueryPreHandlerBundle(decision, responseType, Optional.empty());
		bundle.executePostHandlingHandlers(null);

		verify(firstResultConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
		verify(secondResultConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildQueryPreHandlerBundle_with_presentInitialMappingProviders_and_advice_and_oneResponsible_then_oneResultConstraintHandlers() {
		when(firstResultConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.FALSE);
		when(secondResultConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(firstResultConstraintHandlerProvider.supports(any(ResponseType.class))).thenReturn(Boolean.TRUE);
		when(secondResultConstraintHandlerProvider.supports(any(ResponseType.class))).thenReturn(Boolean.TRUE);
		when(secondResultConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn(UnaryOperator.identity());
		var service = buildServiceForTest(0, 0, 0, 0, 0, 0, 2);

		var advices = factory.arrayNode().add("advice");
		var decision = new AuthorizationDecision(Decision.PERMIT).withAdvice(advices);
		var responseType = ResponseTypes.instanceOf(Object.class);

		var bundle = service.buildQueryPreHandlerBundle(decision, responseType, Optional.empty());
		bundle.executePostHandlingHandlers(null);

		verify(firstResultConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
		verify(secondResultConstraintHandlerProvider, times(1)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildQueryPreHandlerBundle_with_presentInitialMappingProviders_and_advice_and_oneSupported_then_oneResultConstraintHandlers() {
		when(firstResultConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(secondResultConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(firstResultConstraintHandlerProvider.supports(any(ResponseType.class))).thenReturn(Boolean.FALSE);
		when(secondResultConstraintHandlerProvider.supports(any(ResponseType.class))).thenReturn(Boolean.TRUE);
		when(secondResultConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn(UnaryOperator.identity());
		var service = buildServiceForTest(0, 0, 0, 0, 0, 0, 2);

		var advices = factory.arrayNode().add("advice");
		var decision = new AuthorizationDecision(Decision.PERMIT).withAdvice(advices);
		var responseType = ResponseTypes.instanceOf(Object.class);

		var bundle = service.buildQueryPreHandlerBundle(decision, responseType, Optional.empty());
		bundle.executePostHandlingHandlers(null);

		verify(firstResultConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
		verify(secondResultConstraintHandlerProvider, times(1)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildQueryPreHandlerBundle_with_presentInitialMappingProviders_and_advice_and_allResponsible_then_allResultConstraintHandlers() {
		when(firstResultConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(secondResultConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(firstResultConstraintHandlerProvider.supports(any(ResponseType.class))).thenReturn(Boolean.TRUE);
		when(secondResultConstraintHandlerProvider.supports(any(ResponseType.class))).thenReturn(Boolean.TRUE);
		when(firstResultConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn(UnaryOperator.identity());
		when(secondResultConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn(UnaryOperator.identity());
		var service = buildServiceForTest(0, 0, 0, 0, 0, 0, 2);

		var advices = factory.arrayNode().add("advice");
		var decision = new AuthorizationDecision(Decision.PERMIT).withAdvice(advices);
		var responseType = ResponseTypes.instanceOf(Object.class);

		var bundle = service.buildQueryPreHandlerBundle(decision, responseType, Optional.empty());
		bundle.executePostHandlingHandlers(null);

		verify(firstResultConstraintHandlerProvider, times(1)).getHandler(any(JsonNode.class));
		verify(secondResultConstraintHandlerProvider, times(1)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildQueryPreHandlerBundle_with_noUpdateMappingProviders_then_noResultConstraintHandlers() {
		var service = buildServiceForTest(0, 0, 0, 0, 0, 0, 0);

		var decision     = new AuthorizationDecision(Decision.PERMIT);
		var responseType = ResponseTypes.instanceOf(Object.class);
		var updateType   = ResponseTypes.instanceOf(Object.class);

		var bundle = service.buildQueryPreHandlerBundle(decision, responseType, Optional.of(updateType));
		bundle.executeOnNextHandlers(null);

		verify(firstResultConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
		verify(secondResultConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildQueryPreHandlerBundle_with_presentUpdateMappingProviders_and_noConstraints_then_noResultConstraintHandlers() {
		when(firstResultConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(secondResultConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		var service = buildServiceForTest(0, 0, 0, 0, 0, 0, 2);

		var decision = new AuthorizationDecision(Decision.PERMIT);
		var responseType = ResponseTypes.instanceOf(Object.class);
		var updateType = ResponseTypes.instanceOf(Object.class);

		var bundle = service.buildQueryPreHandlerBundle(decision, responseType, Optional.of(updateType));
		bundle.executeOnNextHandlers(null);

		verify(firstResultConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
		verify(secondResultConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildQueryPreHandlerBundle_with_presentUpdateMappingProviders_and_obligation_and_throwingHandler_then_accessDenied() {
		when(firstOnDecisionConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(firstResultConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(secondResultConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.FALSE);
		when(firstResultConstraintHandlerProvider.supports(any(ResponseType.class))).thenReturn(Boolean.TRUE);
		when(secondResultConstraintHandlerProvider.supports(any(ResponseType.class))).thenReturn(Boolean.TRUE);
		when(firstResultConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn(__ -> {
			throw new RuntimeException();
		});
		var service = buildServiceForTest(1, 0, 0, 0, 0, 0, 2);

		var obligations = factory.arrayNode().add("obligation");
		var decision = new AuthorizationDecision(Decision.PERMIT).withObligations(obligations);
		var responseType = ResponseTypes.instanceOf(Object.class);
		var updateType = ResponseTypes.instanceOf(Object.class);

		var bundle = service.buildQueryPreHandlerBundle(decision, responseType, Optional.of(updateType));
		assertThrows(AccessDeniedException.class, () -> bundle.executeOnNextHandlers(null));
	}

	@Test
	void when_buildQueryPreHandlerBundle_with_presentUpdateMappingProviders_and_obligation_and_noResponsible_then_accessDenied() {
		when(firstResultConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.FALSE);
		when(secondResultConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.FALSE);
		when(firstResultConstraintHandlerProvider.supports(any(ResponseType.class))).thenReturn(Boolean.TRUE);
		when(secondResultConstraintHandlerProvider.supports(any(ResponseType.class))).thenReturn(Boolean.TRUE);
		var service = buildServiceForTest(0, 0, 0, 0, 0, 0, 2);

		var obligations = factory.arrayNode().add("obligation");
		var decision = new AuthorizationDecision(Decision.PERMIT).withObligations(obligations);
		var responseType = ResponseTypes.instanceOf(Object.class);
		var updateType = ResponseTypes.instanceOf(Object.class);

		assertThrows(AccessDeniedException.class,
				() -> service.buildQueryPreHandlerBundle(decision, responseType, Optional.of(updateType)));
	}

	@Test
	void when_buildQueryPreHandlerBundle_with_presentUpdateMappingProviders_and_obligation_and_oneResponsible_then_oneResultConstraintHandlers() {
		when(firstOnDecisionConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(firstResultConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.FALSE);
		when(secondResultConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(firstResultConstraintHandlerProvider.supports(any(InstanceResponseType.class))).thenReturn(Boolean.TRUE);
		when(secondResultConstraintHandlerProvider.supports(any(InstanceResponseType.class))).thenReturn(Boolean.TRUE);
		when(secondResultConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn(UnaryOperator.identity());
		var service = buildServiceForTest(1, 0, 0, 0, 0, 0, 2);

		var obligations = factory.arrayNode().add("obligation");
		var decision = new AuthorizationDecision(Decision.PERMIT).withObligations(obligations);
		var responseType = ResponseTypes.optionalInstanceOf(Object.class);
		var updateType = ResponseTypes.instanceOf(Object.class);

		var bundle = service.buildQueryPreHandlerBundle(decision, responseType, Optional.of(updateType));
		bundle.executeOnNextHandlers(null);

		verify(firstResultConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
		verify(secondResultConstraintHandlerProvider, times(1)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildQueryPreHandlerBundle_with_presentUpdateMappingProviders_and_obligation_and_oneSupported_then_oneResultConstraintHandlers() {
		when(firstOnDecisionConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(firstResultConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(secondResultConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(firstResultConstraintHandlerProvider.supports(any(InstanceResponseType.class))).thenReturn(Boolean.FALSE);
		when(secondResultConstraintHandlerProvider.supports(any(InstanceResponseType.class))).thenReturn(Boolean.TRUE);
		when(secondResultConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn(UnaryOperator.identity());
		var service = buildServiceForTest(1, 0, 0, 0, 0, 0, 2);

		var obligations = factory.arrayNode().add("obligation");
		var decision = new AuthorizationDecision(Decision.PERMIT).withObligations(obligations);
		var responseType = ResponseTypes.optionalInstanceOf(Object.class);
		var updateType = ResponseTypes.instanceOf(Object.class);

		var bundle = service.buildQueryPreHandlerBundle(decision, responseType, Optional.of(updateType));
		bundle.executeOnNextHandlers(null);

		verify(firstResultConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
		verify(secondResultConstraintHandlerProvider, times(1)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildQueryPreHandlerBundle_with_presentUpdateMappingProviders_and_obligation_and_allResponsible_then_allResultConstraintHandlers() {
		when(firstOnDecisionConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(firstResultConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(secondResultConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(firstResultConstraintHandlerProvider.supports(any(InstanceResponseType.class))).thenReturn(Boolean.TRUE);
		when(secondResultConstraintHandlerProvider.supports(any(InstanceResponseType.class))).thenReturn(Boolean.TRUE);
		when(firstResultConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn(UnaryOperator.identity());
		when(secondResultConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn(UnaryOperator.identity());
		var service = buildServiceForTest(1, 0, 0, 0, 0, 0, 2);

		var obligations = factory.arrayNode().add("obligation");
		var decision = new AuthorizationDecision(Decision.PERMIT).withObligations(obligations);
		var responseType = ResponseTypes.optionalInstanceOf(Object.class);
		var updateType = ResponseTypes.instanceOf(Object.class);

		var bundle = service.buildQueryPreHandlerBundle(decision, responseType, Optional.of(updateType));
		bundle.executeOnNextHandlers(null);

		verify(firstResultConstraintHandlerProvider, times(1)).getHandler(any(JsonNode.class));
		verify(secondResultConstraintHandlerProvider, times(1)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildQueryPreHandlerBundle_with_presentUpdateMappingProviders_and_advice_and_throwingHandler_then_ignoreHandler() {
		when(firstResultConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(secondResultConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.FALSE);
		when(firstResultConstraintHandlerProvider.supports(any(InstanceResponseType.class))).thenReturn(Boolean.TRUE);
		when(secondResultConstraintHandlerProvider.supports(any(InstanceResponseType.class))).thenReturn(Boolean.TRUE);
		when(firstResultConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn(__ -> {
			throw new RuntimeException();
		});
		var service = buildServiceForTest(0, 0, 0, 0, 0, 0, 2);

		var advices = factory.arrayNode().add("advice");
		var decision = new AuthorizationDecision(Decision.PERMIT).withAdvice(advices);
		var responseType = ResponseTypes.optionalInstanceOf(Object.class);
		var updateType = ResponseTypes.instanceOf(Object.class);

		var bundle = service.buildQueryPreHandlerBundle(decision, responseType, Optional.of(updateType));
		bundle.executeOnNextHandlers(null);

		verify(firstResultConstraintHandlerProvider, times(1)).getHandler(any(JsonNode.class));
		verify(secondResultConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildQueryPreHandlerBundle_with_presentUpdateMappingProviders_and_advice_and_noResponsible_then_noResultConstraintHandlers() {
		when(firstResultConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.FALSE);
		when(secondResultConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.FALSE);
		when(firstResultConstraintHandlerProvider.supports(any(ResponseType.class))).thenReturn(Boolean.TRUE);
		when(secondResultConstraintHandlerProvider.supports(any(ResponseType.class))).thenReturn(Boolean.TRUE);
		var service = buildServiceForTest(0, 0, 0, 0, 0, 0, 2);

		var advices = factory.arrayNode().add("advice");
		var decision = new AuthorizationDecision(Decision.PERMIT).withAdvice(advices);
		var responseType = ResponseTypes.instanceOf(Object.class);
		var updateType = ResponseTypes.instanceOf(Object.class);

		var bundle = service.buildQueryPreHandlerBundle(decision, responseType, Optional.of(updateType));
		bundle.executeOnNextHandlers(null);

		verify(firstResultConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
		verify(secondResultConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildQueryPreHandlerBundle_with_presentUpdateMappingProviders_and_advice_and_oneResponsible_then_oneResultConstraintHandlers() {
		when(firstResultConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.FALSE);
		when(secondResultConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(firstResultConstraintHandlerProvider.supports(any(InstanceResponseType.class))).thenReturn(Boolean.TRUE);
		when(secondResultConstraintHandlerProvider.supports(any(InstanceResponseType.class))).thenReturn(Boolean.TRUE);
		when(secondResultConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn(UnaryOperator.identity());
		var service = buildServiceForTest(0, 0, 0, 0, 0, 0, 2);

		var advices = factory.arrayNode().add("advice");
		var decision = new AuthorizationDecision(Decision.PERMIT).withAdvice(advices);
		var responseType = ResponseTypes.optionalInstanceOf(Object.class);
		var updateType = ResponseTypes.instanceOf(Object.class);

		var bundle = service.buildQueryPreHandlerBundle(decision, responseType, Optional.of(updateType));
		bundle.executeOnNextHandlers(null);

		verify(firstResultConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
		verify(secondResultConstraintHandlerProvider, times(1)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildQueryPreHandlerBundle_with_presentUpdateMappingProviders_and_advice_and_oneSupported_then_oneResultConstraintHandlers() {
		when(firstResultConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(secondResultConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(firstResultConstraintHandlerProvider.supports(any(InstanceResponseType.class))).thenReturn(Boolean.FALSE);
		when(secondResultConstraintHandlerProvider.supports(any(InstanceResponseType.class))).thenReturn(Boolean.TRUE);
		when(secondResultConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn(UnaryOperator.identity());
		var service = buildServiceForTest(0, 0, 0, 0, 0, 0, 2);

		var advices = factory.arrayNode().add("advice");
		var decision = new AuthorizationDecision(Decision.PERMIT).withAdvice(advices);
		var responseType = ResponseTypes.optionalInstanceOf(Object.class);
		var updateType = ResponseTypes.instanceOf(Object.class);

		var bundle = service.buildQueryPreHandlerBundle(decision, responseType, Optional.of(updateType));
		bundle.executeOnNextHandlers(null);

		verify(firstResultConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
		verify(secondResultConstraintHandlerProvider, times(1)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildQueryPreHandlerBundle_with_presentUpdateMappingProviders_and_advice_and_allResponsible_then_allResultConstraintHandlers() {
		when(firstResultConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(secondResultConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(firstResultConstraintHandlerProvider.supports(any(InstanceResponseType.class))).thenReturn(Boolean.TRUE);
		when(secondResultConstraintHandlerProvider.supports(any(InstanceResponseType.class))).thenReturn(Boolean.TRUE);
		when(firstResultConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn(UnaryOperator.identity());
		when(secondResultConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn(UnaryOperator.identity());
		var service = buildServiceForTest(0, 0, 0, 0, 0, 0, 2);

		var advices = factory.arrayNode().add("advice");
		var decision = new AuthorizationDecision(Decision.PERMIT).withAdvice(advices);
		var responseType = ResponseTypes.optionalInstanceOf(Object.class);
		var updateType = ResponseTypes.instanceOf(Object.class);

		var bundle = service.buildQueryPreHandlerBundle(decision, responseType, Optional.of(updateType));
		bundle.executeOnNextHandlers(null);

		verify(firstResultConstraintHandlerProvider, times(1)).getHandler(any(JsonNode.class));
		verify(secondResultConstraintHandlerProvider, times(1)).getHandler(any(JsonNode.class));
	}

//================================================================

	@Test
	void when_buildQueryPreHandlerBundle_with_noUpdatePredicateProviders_then_noFilters() {
		var service = buildServiceForTest(0, 0, 0, 0, 0, 0, 0);

		var decision     = new AuthorizationDecision(Decision.PERMIT);
		var responseType = ResponseTypes.instanceOf(Object.class);
		var updateType   = ResponseTypes.instanceOf(Object.class);

		var bundle = service.buildQueryPreHandlerBundle(decision, responseType, Optional.of(updateType));
		bundle.executeOnNextHandlers(null);

		verify(firstUpdateFilterConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
		verify(secondUpdateFilterConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildQueryPreHandlerBundle_with_presentUpdatePredicateProviders_and_noConstraints_then_noFilters() {
		when(firstUpdateFilterConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(secondUpdateFilterConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		var service = buildServiceForTest(0, 0, 0, 0, 0, 0, 0);

		var decision = new AuthorizationDecision(Decision.PERMIT);
		var responseType = ResponseTypes.instanceOf(Object.class);
		var updateType = ResponseTypes.instanceOf(Object.class);

		var bundle = service.buildQueryPreHandlerBundle(decision, responseType, Optional.of(updateType));
		bundle.executeOnNextHandlers(null);

		verify(firstUpdateFilterConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
		verify(secondUpdateFilterConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildQueryPreHandlerBundle_with_presentUpdatePredicateProviders_and_obligation_and_throwingHandler_then_fallbackFalse() {
		when(firstOnDecisionConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(firstUpdateFilterConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(secondUpdateFilterConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.FALSE);
		when(firstUpdateFilterConstraintHandlerProvider.supports(any(ResponseType.class))).thenReturn(Boolean.TRUE);
		when(secondUpdateFilterConstraintHandlerProvider.supports(any(ResponseType.class))).thenReturn(Boolean.TRUE);
		when(firstUpdateFilterConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn(__ -> {
			throw new RuntimeException();
		});
		var service = buildServiceForTest(1, 0, 0, 0, 0, 2, 0);

		var obligations = factory.arrayNode().add("obligation");
		var decision = new AuthorizationDecision(Decision.PERMIT).withObligations(obligations);
		var responseType = ResponseTypes.instanceOf(Object.class);
		var updateType = ResponseTypes.instanceOf(Object.class);

		var bundle = service.buildQueryPreHandlerBundle(decision, responseType, Optional.of(updateType));
		assertDoesNotThrow(() -> bundle.executeOnNextHandlers(null));

		verify(firstUpdateFilterConstraintHandlerProvider, times(1)).getHandler(any(JsonNode.class));
		verify(secondUpdateFilterConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildQueryPreHandlerBundle_with_presentUpdatePredicateProviders_and_obligation_and_noResponsible_then_accessDenied() {
		when(firstUpdateFilterConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.FALSE);
		when(secondUpdateFilterConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.FALSE);
		when(firstUpdateFilterConstraintHandlerProvider.supports(any(ResponseType.class))).thenReturn(Boolean.TRUE);
		when(secondUpdateFilterConstraintHandlerProvider.supports(any(ResponseType.class))).thenReturn(Boolean.TRUE);
		var service = buildServiceForTest(0, 0, 0, 0, 0, 2, 0);

		var obligations = factory.arrayNode().add("obligation");
		var decision = new AuthorizationDecision(Decision.PERMIT).withObligations(obligations);
		var responseType = ResponseTypes.instanceOf(Object.class);
		var updateType = ResponseTypes.instanceOf(Object.class);

		assertThrows(AccessDeniedException.class,
				() -> service.buildQueryPreHandlerBundle(decision, responseType, Optional.of(updateType)));
	}

	@Test
	void when_buildQueryPreHandlerBundle_with_presentUpdatePredicateProviders_and_obligation_and_oneResponsible_then_oneUpdatePredicates() {
		when(firstOnDecisionConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(firstUpdateFilterConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.FALSE);
		when(secondUpdateFilterConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(firstUpdateFilterConstraintHandlerProvider.supports(any(ResponseType.class))).thenReturn(Boolean.TRUE);
		when(secondUpdateFilterConstraintHandlerProvider.supports(any(ResponseType.class))).thenReturn(Boolean.TRUE);
		when(secondUpdateFilterConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn(__ -> true);
		var service = buildServiceForTest(1, 0, 0, 0, 0, 2, 0);

		var obligations = factory.arrayNode().add("obligation");
		var decision = new AuthorizationDecision(Decision.PERMIT).withObligations(obligations);
		var responseType = ResponseTypes.instanceOf(Object.class);
		var updateType = ResponseTypes.instanceOf(Object.class);

		var bundle = service.buildQueryPreHandlerBundle(decision, responseType, Optional.of(updateType));
		bundle.executeOnNextHandlers(null);

		verify(firstUpdateFilterConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
		verify(secondUpdateFilterConstraintHandlerProvider, times(1)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildQueryPreHandlerBundle_with_presentUpdatePredicateProviders_and_obligation_and_oneSupported_then_oneUpdatePredicates() {
		when(firstOnDecisionConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(firstUpdateFilterConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(secondUpdateFilterConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(firstUpdateFilterConstraintHandlerProvider.supports(any(ResponseType.class))).thenReturn(Boolean.FALSE);
		when(secondUpdateFilterConstraintHandlerProvider.supports(any(ResponseType.class))).thenReturn(Boolean.TRUE);
		when(secondUpdateFilterConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn(__ -> true);
		var service = buildServiceForTest(1, 0, 0, 0, 0, 2, 0);

		var obligations = factory.arrayNode().add("obligation");
		var decision = new AuthorizationDecision(Decision.PERMIT).withObligations(obligations);
		var responseType = ResponseTypes.instanceOf(Object.class);
		var updateType = ResponseTypes.instanceOf(Object.class);

		var bundle = service.buildQueryPreHandlerBundle(decision, responseType, Optional.of(updateType));
		bundle.executeOnNextHandlers(null);

		verify(firstUpdateFilterConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
		verify(secondUpdateFilterConstraintHandlerProvider, times(1)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildQueryPreHandlerBundle_with_presentUpdatePredicateProviders_and_obligation_and_allResponsible_then_allUpdatePredicates() {
		when(firstOnDecisionConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(firstUpdateFilterConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(secondUpdateFilterConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(firstUpdateFilterConstraintHandlerProvider.supports(any(ResponseType.class))).thenReturn(Boolean.TRUE);
		when(secondUpdateFilterConstraintHandlerProvider.supports(any(ResponseType.class))).thenReturn(Boolean.TRUE);
		when(firstUpdateFilterConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn(__ -> true);
		when(secondUpdateFilterConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn(__ -> true);
		var service = buildServiceForTest(1, 0, 0, 0, 0, 2, 0);

		var obligations = factory.arrayNode().add("obligation");
		var decision = new AuthorizationDecision(Decision.PERMIT).withObligations(obligations);
		var responseType = ResponseTypes.instanceOf(Object.class);
		var updateType = ResponseTypes.instanceOf(Object.class);

		var bundle = service.buildQueryPreHandlerBundle(decision, responseType, Optional.of(updateType));
		bundle.executeOnNextHandlers(null);

		verify(firstUpdateFilterConstraintHandlerProvider, times(1)).getHandler(any(JsonNode.class));
		verify(secondUpdateFilterConstraintHandlerProvider, times(1)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildQueryPreHandlerBundle_with_presentUpdatePredicateProviders_and_advice_and_throwingHandler_then_fallbackTrue() {
		when(firstUpdateFilterConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(secondUpdateFilterConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.FALSE);
		when(firstUpdateFilterConstraintHandlerProvider.supports(any(ResponseType.class))).thenReturn(Boolean.TRUE);
		when(secondUpdateFilterConstraintHandlerProvider.supports(any(ResponseType.class))).thenReturn(Boolean.TRUE);
		when(firstUpdateFilterConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn(__ -> {
			throw new RuntimeException();
		});
		var service = buildServiceForTest(0, 0, 0, 0, 0, 2, 0);

		var advices = factory.arrayNode().add("advice");
		var decision = new AuthorizationDecision(Decision.PERMIT).withAdvice(advices);
		var responseType = ResponseTypes.instanceOf(Object.class);
		var updateType = ResponseTypes.instanceOf(Object.class);

		var bundle = service.buildQueryPreHandlerBundle(decision, responseType, Optional.of(updateType));
		assertDoesNotThrow(() -> bundle.executeOnNextHandlers(null));

		verify(firstUpdateFilterConstraintHandlerProvider, times(1)).getHandler(any(JsonNode.class));
		verify(secondUpdateFilterConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildQueryPreHandlerBundle_with_presentUpdatePredicateProviders_and_advice_and_noResponsible_then_noUpdatePredicates() {
		when(firstUpdateFilterConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.FALSE);
		when(secondUpdateFilterConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.FALSE);
		when(firstUpdateFilterConstraintHandlerProvider.supports(any(ResponseType.class))).thenReturn(Boolean.TRUE);
		when(secondUpdateFilterConstraintHandlerProvider.supports(any(ResponseType.class))).thenReturn(Boolean.TRUE);
		var service = buildServiceForTest(0, 0, 0, 0, 0, 2, 0);

		var advices = factory.arrayNode().add("advice");
		var decision = new AuthorizationDecision(Decision.PERMIT).withAdvice(advices);
		var responseType = ResponseTypes.instanceOf(Object.class);
		var updateType = ResponseTypes.instanceOf(Object.class);

		var bundle = service.buildQueryPreHandlerBundle(decision, responseType, Optional.of(updateType));
		bundle.executeOnNextHandlers(null);

		verify(firstUpdateFilterConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
		verify(secondUpdateFilterConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildQueryPreHandlerBundle_with_presentUpdatePredicateProviders_and_advice_and_oneResponsible_then_oneUpdatePredicates() {
		when(firstUpdateFilterConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.FALSE);
		when(secondUpdateFilterConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(firstUpdateFilterConstraintHandlerProvider.supports(any(ResponseType.class))).thenReturn(Boolean.TRUE);
		when(secondUpdateFilterConstraintHandlerProvider.supports(any(ResponseType.class))).thenReturn(Boolean.TRUE);
		when(secondUpdateFilterConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn(__ -> true);
		var service = buildServiceForTest(0, 0, 0, 0, 0, 2, 0);

		var advices = factory.arrayNode().add("advice");
		var decision = new AuthorizationDecision(Decision.PERMIT).withAdvice(advices);
		var responseType = ResponseTypes.instanceOf(Object.class);
		var updateType = ResponseTypes.instanceOf(Object.class);

		var bundle = service.buildQueryPreHandlerBundle(decision, responseType, Optional.of(updateType));
		bundle.executeOnNextHandlers(null);

		verify(firstUpdateFilterConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
		verify(secondUpdateFilterConstraintHandlerProvider, times(1)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildQueryPreHandlerBundle_with_presentUpdatePredicateProviders_and_advice_and_oneSupported_then_oneUpdatePredicates() {
		when(firstUpdateFilterConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(secondUpdateFilterConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(firstUpdateFilterConstraintHandlerProvider.supports(any(ResponseType.class))).thenReturn(Boolean.FALSE);
		when(secondUpdateFilterConstraintHandlerProvider.supports(any(ResponseType.class))).thenReturn(Boolean.TRUE);
		when(secondUpdateFilterConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn(__ -> true);
		var service = buildServiceForTest(0, 0, 0, 0, 0, 2, 0);

		var advices = factory.arrayNode().add("advice");
		var decision = new AuthorizationDecision(Decision.PERMIT).withAdvice(advices);
		var responseType = ResponseTypes.instanceOf(Object.class);
		var updateType = ResponseTypes.instanceOf(Object.class);

		var bundle = service.buildQueryPreHandlerBundle(decision, responseType, Optional.of(updateType));
		bundle.executeOnNextHandlers(null);

		verify(firstUpdateFilterConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
		verify(secondUpdateFilterConstraintHandlerProvider, times(1)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildQueryPreHandlerBundle_with_presentUpdatePredicateProviders_and_advice_and_allResponsible_then_allUpdatePredicates() {
		when(firstUpdateFilterConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(secondUpdateFilterConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(firstUpdateFilterConstraintHandlerProvider.supports(any(ResponseType.class))).thenReturn(Boolean.TRUE);
		when(secondUpdateFilterConstraintHandlerProvider.supports(any(ResponseType.class))).thenReturn(Boolean.TRUE);
		when(firstUpdateFilterConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn(__ -> true);
		when(secondUpdateFilterConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn(__ -> true);
		var service = buildServiceForTest(0, 0, 0, 0, 0, 2, 0);

		var advices = factory.arrayNode().add("advice");
		var decision = new AuthorizationDecision(Decision.PERMIT).withAdvice(advices);
		var responseType = ResponseTypes.instanceOf(Object.class);
		var updateType = ResponseTypes.instanceOf(Object.class);

		var bundle = service.buildQueryPreHandlerBundle(decision, responseType, Optional.of(updateType));
		bundle.executeOnNextHandlers(null);

		verify(firstUpdateFilterConstraintHandlerProvider, times(1)).getHandler(any(JsonNode.class));
		verify(secondUpdateFilterConstraintHandlerProvider, times(1)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildQueryPreHandlerBundle_with_presentUpdatePredicateProviders_and_someConstraint_and_bothFiltersClosed_then_andAllFalse() {
		when(firstUpdateFilterConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(secondUpdateFilterConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(firstUpdateFilterConstraintHandlerProvider.supports(any(ResponseType.class))).thenReturn(Boolean.TRUE);
		when(secondUpdateFilterConstraintHandlerProvider.supports(any(ResponseType.class))).thenReturn(Boolean.TRUE);
		when(firstUpdateFilterConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn(__ -> false);
		when(secondUpdateFilterConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn(__ -> false);
		var service = buildServiceForTest(0, 0, 0, 0, 0, 2, 0);

		var constraints = factory.arrayNode().add("constraint");
		var decision = new AuthorizationDecision(Decision.PERMIT).withAdvice(constraints);
		var responseType = ResponseTypes.instanceOf(Object.class);
		var updateType = ResponseTypes.instanceOf(Object.class);

		var bundle = service.buildQueryPreHandlerBundle(decision, responseType, Optional.of(updateType));
		var filteredResult = bundle.executeOnNextHandlers(new GenericResultMessage<>(new Object()));

		assertTrue(filteredResult.isEmpty());
	}

	@Test
	void when_buildQueryPreHandlerBundle_with_presentUpdatePredicateProviders_and_someConstraint_and_oneFilterClosed_then_andAllFalse() {
		when(firstUpdateFilterConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(secondUpdateFilterConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(firstUpdateFilterConstraintHandlerProvider.supports(any(ResponseType.class))).thenReturn(Boolean.TRUE);
		when(secondUpdateFilterConstraintHandlerProvider.supports(any(ResponseType.class))).thenReturn(Boolean.TRUE);
		when(firstUpdateFilterConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn(__ -> true);
		when(secondUpdateFilterConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn(__ -> false);
		var service = buildServiceForTest(0, 0, 0, 0, 0, 2, 0);

		var constraints = factory.arrayNode().add("constraint");
		var decision = new AuthorizationDecision(Decision.PERMIT).withAdvice(constraints);
		var responseType = ResponseTypes.instanceOf(Object.class);
		var updateType = ResponseTypes.instanceOf(Object.class);

		var bundle = service.buildQueryPreHandlerBundle(decision, responseType, Optional.of(updateType));
		var filteredResult = bundle.executeOnNextHandlers(new GenericResultMessage<>(new Object()));

		assertTrue(filteredResult.isEmpty());
	}

	@Test
	void when_buildQueryPreHandlerBundle_with_presentUpdatePredicateProviders_and_someConstraint_and_bothFiltersOpen_then_andAllTrue() {
		when(firstUpdateFilterConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(secondUpdateFilterConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(firstUpdateFilterConstraintHandlerProvider.supports(any(ResponseType.class))).thenReturn(Boolean.TRUE);
		when(secondUpdateFilterConstraintHandlerProvider.supports(any(ResponseType.class))).thenReturn(Boolean.TRUE);
		when(firstUpdateFilterConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn(__ -> true);
		when(secondUpdateFilterConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn(__ -> true);
		var service = buildServiceForTest(0, 0, 0, 0, 0, 2, 0);

		var constraints = factory.arrayNode().add("constraint");
		var decision = new AuthorizationDecision(Decision.PERMIT).withAdvice(constraints);
		var responseType = ResponseTypes.instanceOf(Object.class);
		var updateType = ResponseTypes.instanceOf(Object.class);

		var bundle = service.buildQueryPreHandlerBundle(decision, responseType, Optional.of(updateType));
		var filteredResult = bundle.executeOnNextHandlers(new GenericResultMessage<>(new Object()));

		assertTrue(filteredResult.isPresent());
	}

//================================================================
//||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||
//================================================================

	@Test
	void when_buildQueryPostHandlerBundle_with_noGlobalRunnableProviders_then_noOnDecisionConstraintHandlers() {
		var service = buildServiceForTest(0, 0, 0, 0, 0, 0, 0);

		var decision = new AuthorizationDecision(Decision.PERMIT);

		var bundle = service.buildQueryPostHandlerBundle(decision, null);
		bundle.executeOnDecisionHandlers(null, null);

		verify(firstOnDecisionConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
		verify(secondOnDecisionConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildQueryPostHandlerBundle_with_presentGlobalRunnableProviders_and_noConstraints_then_noOnDecisionConstraintHandlers() {
		when(firstOnDecisionConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		when(secondOnDecisionConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);
		var service = buildServiceForTest(2, 0, 0, 0, 0, 0, 0);

		var decision = new AuthorizationDecision(Decision.PERMIT);

		var bundle = service.buildQueryPostHandlerBundle(decision, null);
		bundle.executeOnDecisionHandlers(null, null);

		verify(firstOnDecisionConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
		verify(secondOnDecisionConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildQueryPostHandlerBundle_with_presentGlobalRunnableProviders_and_obligation_and_noResponsible_then_accessDenied() {
		when(firstOnDecisionConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.FALSE);when(secondOnDecisionConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.FALSE);var service=buildServiceForTest(2,0,0,0,0,0,0);

		var obligations=factory.arrayNode().add("obligation");var decision=new AuthorizationDecision(Decision.PERMIT).withObligations(obligations);

		assertThrows(AccessDeniedException.class,()->service.buildQueryPostHandlerBundle(decision,null));
	}

	@Test
	void when_buildQueryPostHandlerBundle_with_presentGlobalRunnableProviders_and_obligation_and_throwingHandler_then_accessDenied() {
		when(firstOnDecisionConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.FALSE);when(secondOnDecisionConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);when(secondOnDecisionConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn((__,___)->{throw new RuntimeException();});var service=buildServiceForTest(2,0,0,0,0,0,0);

		var obligations=factory.arrayNode().add("obligation");var decision=new AuthorizationDecision(Decision.PERMIT).withObligations(obligations);

		var bundle=service.buildQueryPostHandlerBundle(decision,null);assertThrows(AccessDeniedException.class,()->bundle.executeOnDecisionHandlers(null,null));

		verify(firstOnDecisionConstraintHandlerProvider,times(0)).getHandler(any(JsonNode.class));verify(secondOnDecisionConstraintHandlerProvider,times(1)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildQueryPostHandlerBundle_with_presentGlobalRunnableProviders_and_obligation_and_oneResponsible_then_oneOnDecisionHandlers() {
		when(firstOnDecisionConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.FALSE);when(secondOnDecisionConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);when(secondOnDecisionConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn((__,___)->{});var service=buildServiceForTest(2,0,0,0,0,0,0);

		var obligations=factory.arrayNode().add("obligation");var decision=new AuthorizationDecision(Decision.PERMIT).withObligations(obligations);

		var bundle=service.buildQueryPostHandlerBundle(decision,null);bundle.executeOnDecisionHandlers(null,null);

		verify(firstOnDecisionConstraintHandlerProvider,times(0)).getHandler(any(JsonNode.class));verify(secondOnDecisionConstraintHandlerProvider,times(1)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildQueryPostHandlerBundle_with_presentGlobalRunnableProviders_and_obligation_and_allResponsible_then_allOnDecisionHandlers() {
		when(firstOnDecisionConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);when(secondOnDecisionConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);when(firstOnDecisionConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn((__,___)->{});when(secondOnDecisionConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn((__,___)->{});var service=buildServiceForTest(2,0,0,0,0,0,0);

		var obligations=factory.arrayNode().add("obligation");var decision=new AuthorizationDecision(Decision.PERMIT).withObligations(obligations);

		var bundle=service.buildQueryPostHandlerBundle(decision,null);bundle.executeOnDecisionHandlers(null,null);

		verify(firstOnDecisionConstraintHandlerProvider,times(1)).getHandler(any(JsonNode.class));verify(secondOnDecisionConstraintHandlerProvider,times(1)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildQueryPostHandlerBundle_with_presentGlobalRunnableProviders_and_advice_and_noResponsible_then_noOnDecisionHandlers() {
		when(firstOnDecisionConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.FALSE);when(secondOnDecisionConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.FALSE);var service=buildServiceForTest(2,0,0,0,0,0,0);

		var advices=factory.arrayNode().add("advice");var decision=new AuthorizationDecision(Decision.PERMIT).withAdvice(advices);

		var bundle=service.buildQueryPostHandlerBundle(decision,null);bundle.executeOnDecisionHandlers(null,null);

		verify(firstOnDecisionConstraintHandlerProvider,times(0)).getHandler(any(JsonNode.class));verify(secondOnDecisionConstraintHandlerProvider,times(0)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildQueryPostHandlerBundle_with_presentGlobalRunnableProviders_and_advice_and_throwingHandler_then_oneOnDecisionHandlers() {
		when(firstOnDecisionConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.FALSE);when(secondOnDecisionConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);when(secondOnDecisionConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn((__,___)->{throw new RuntimeException();});var service=buildServiceForTest(2,0,0,0,0,0,0);

		var advices=factory.arrayNode().add("advice");var decision=new AuthorizationDecision(Decision.PERMIT).withAdvice(advices);

		var bundle=service.buildQueryPostHandlerBundle(decision,null);assertDoesNotThrow(()->bundle.executeOnDecisionHandlers(null,null));

		verify(firstOnDecisionConstraintHandlerProvider,times(0)).getHandler(any(JsonNode.class));verify(secondOnDecisionConstraintHandlerProvider,times(1)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildQueryPostHandlerBundle_with_presentGlobalRunnableProviders_and_advice_and_oneResponsible_then_oneOnDecisionHandlers() {
		when(firstOnDecisionConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.FALSE);when(secondOnDecisionConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);when(secondOnDecisionConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn((__,___)->{});var service=buildServiceForTest(2,0,0,0,0,0,0);

		var advices=factory.arrayNode().add("advice");var decision=new AuthorizationDecision(Decision.PERMIT).withAdvice(advices);

		var bundle=service.buildQueryPostHandlerBundle(decision,null);bundle.executeOnDecisionHandlers(null,null);

		verify(firstOnDecisionConstraintHandlerProvider,times(0)).getHandler(any(JsonNode.class));verify(secondOnDecisionConstraintHandlerProvider,times(1)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildQueryPostHandlerBundle_with_presentGlobalRunnableProviders_and_advice_and_allResponsible_then_allOnDecisionHandlers() {
		when(firstOnDecisionConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);when(secondOnDecisionConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);when(firstOnDecisionConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn((__,___)->{});when(secondOnDecisionConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn((__,___)->{});var service=buildServiceForTest(2,0,0,0,0,0,0);

		var advices=factory.arrayNode().add("advice");var decision=new AuthorizationDecision(Decision.PERMIT).withAdvice(advices);

		var bundle=service.buildQueryPostHandlerBundle(decision,null);bundle.executeOnDecisionHandlers(null,null);

		verify(firstOnDecisionConstraintHandlerProvider,times(1)).getHandler(any(JsonNode.class));verify(secondOnDecisionConstraintHandlerProvider,times(1)).getHandler(any(JsonNode.class));
	}

//================================================================

	@Test
	void when_buildQueryPostHandlerBundle_with_noGlobalErrorMappingHandlerProviders_then_noErrorMappingConstraintHandlers() {
		var service = buildServiceForTest(0, 0, 0, 0, 0, 0, 0);

		var decision = new AuthorizationDecision(Decision.PERMIT);

		var error  = new Exception("Error");
		var bundle = service.buildQueryPostHandlerBundle(decision, null);
		bundle.executeOnErrorHandlers(error);

		verify(firstErrorMappingConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
		verify(secondErrorMappingConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildQueryPostHandlerBundle_with_presentGlobalErrorMappingHandlerProviders_and_noConstraints_then_noErrorMappingConstraintHandlers() {
		when(firstErrorMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);when(secondErrorMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);var service=buildServiceForTest(0,0,0,2,0,0,0);

		var decision=new AuthorizationDecision(Decision.PERMIT);

		var error=new Exception("Error");var bundle=service.buildQueryPostHandlerBundle(decision,null);bundle.executeOnErrorHandlers(error);

		verify(firstErrorMappingConstraintHandlerProvider,times(0)).getHandler(any(JsonNode.class));verify(secondErrorMappingConstraintHandlerProvider,times(0)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildQueryPostHandlerBundle_with_presentGlobalErrorMappingHandlerProviders_and_obligation_and_throwingHandler_then_accessDenied() {
		when(firstErrorMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);when(secondErrorMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.FALSE);when(firstErrorMappingConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn(__->{throw new RuntimeException();});var service=buildServiceForTest(0,0,0,2,0,0,0);

		var obligations=factory.arrayNode().add("obligation");var decision=new AuthorizationDecision(Decision.PERMIT).withObligations(obligations);

		var error=new Exception("Error");var bundle=service.buildQueryPostHandlerBundle(decision,null);assertThrows(AccessDeniedException.class,()->bundle.executeOnErrorHandlers(error));
	}

	@Test
	void when_buildQueryPostHandlerBundle_with_presentGlobalErrorMappingHandlerProviders_and_obligation_and_noResponsible_then_accessDenied() {
		when(firstErrorMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.FALSE);when(secondErrorMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.FALSE);var service=buildServiceForTest(0,0,0,2,0,0,0);

		var obligations=factory.arrayNode().add("obligation");var decision=new AuthorizationDecision(Decision.PERMIT).withObligations(obligations);

		assertThrows(AccessDeniedException.class,()->service.buildQueryPostHandlerBundle(decision,null));
	}

	@Test
	void when_buildQueryPostHandlerBundle_with_presentGlobalErrorMappingHandlerProviders_and_obligation_and_oneResponsible_then_oneErrorMappingConstraintHandlers() {
		when(firstErrorMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.FALSE);when(secondErrorMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);when(secondErrorMappingConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn(UnaryOperator.identity());var service=buildServiceForTest(0,0,0,2,0,0,0);

		var obligations=factory.arrayNode().add("obligation");var decision=new AuthorizationDecision(Decision.PERMIT).withObligations(obligations);

		var error=new Exception("Error");var bundle=service.buildQueryPostHandlerBundle(decision,null);bundle.executeOnErrorHandlers(error);

		verify(firstErrorMappingConstraintHandlerProvider,times(0)).getHandler(any(JsonNode.class));verify(secondErrorMappingConstraintHandlerProvider,times(1)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildQueryPostHandlerBundle_with_presentGlobalErrorMappingHandlerProviders_and_obligation_and_allResponsible_then_allErrorMappingConstraintHandlers() {
		when(firstErrorMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);when(secondErrorMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);when(firstErrorMappingConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn(UnaryOperator.identity());when(secondErrorMappingConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn(UnaryOperator.identity());var service=buildServiceForTest(0,0,0,2,0,0,0);

		var obligations=factory.arrayNode().add("obligation");var decision=new AuthorizationDecision(Decision.PERMIT).withObligations(obligations);

		var error=new Exception("Error");var bundle=service.buildQueryPostHandlerBundle(decision,null);bundle.executeOnErrorHandlers(error);

		verify(firstErrorMappingConstraintHandlerProvider,times(1)).getHandler(any(JsonNode.class));verify(secondErrorMappingConstraintHandlerProvider,times(1)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildQueryPostHandlerBundle_with_presentGlobalErrorMappingHandlerProviders_and_advice_and_throwingHandler_then_ignoreHandler() {
		when(firstErrorMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);when(secondErrorMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.FALSE);when(firstErrorMappingConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn(__->{throw new RuntimeException();});var service=buildServiceForTest(0,0,0,2,0,0,0);

		var advices=factory.arrayNode().add("advice");var decision=new AuthorizationDecision(Decision.PERMIT).withAdvice(advices);

		var error=new Exception("Error");var bundle=service.buildQueryPostHandlerBundle(decision,null);bundle.executeOnErrorHandlers(error);

		verify(firstErrorMappingConstraintHandlerProvider,times(1)).getHandler(any(JsonNode.class));verify(secondErrorMappingConstraintHandlerProvider,times(0)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildQueryPostHandlerBundle_with_presentGlobalErrorMappingHandlerProviders_and_advice_and_noResponsible_then_noErrorMappingConstraintHandlers() {
		when(firstErrorMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.FALSE);when(secondErrorMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.FALSE);var service=buildServiceForTest(0,0,0,2,0,0,0);

		var advices=factory.arrayNode().add("advice");var decision=new AuthorizationDecision(Decision.PERMIT).withAdvice(advices);

		var error=new Exception("Error");var bundle=service.buildQueryPostHandlerBundle(decision,null);bundle.executeOnErrorHandlers(error);

		verify(firstErrorMappingConstraintHandlerProvider,times(0)).getHandler(any(JsonNode.class));verify(secondErrorMappingConstraintHandlerProvider,times(0)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildQueryPostHandlerBundle_with_presentGlobalErrorMappingHandlerProviders_and_advice_and_oneResponsible_then_oneErrorMappingConstraintHandlers() {
		when(firstErrorMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.FALSE);when(secondErrorMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);when(secondErrorMappingConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn(UnaryOperator.identity());var service=buildServiceForTest(0,0,0,2,0,0,0);

		var advices=factory.arrayNode().add("advice");var decision=new AuthorizationDecision(Decision.PERMIT).withAdvice(advices);

		var error=new Exception("Error");var bundle=service.buildQueryPostHandlerBundle(decision,null);bundle.executeOnErrorHandlers(error);

		verify(firstErrorMappingConstraintHandlerProvider,times(0)).getHandler(any(JsonNode.class));verify(secondErrorMappingConstraintHandlerProvider,times(1)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildQueryPostHandlerBundle_with_presentGlobalErrorMappingHandlerProviders_and_advice_and_allResponsible_then_allErrorMappingConstraintHandlers() {
		when(firstErrorMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);when(secondErrorMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);when(firstErrorMappingConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn(UnaryOperator.identity());when(secondErrorMappingConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn(UnaryOperator.identity());var service=buildServiceForTest(0,0,0,2,0,0,0);

		var advices=factory.arrayNode().add("advice");var decision=new AuthorizationDecision(Decision.PERMIT).withAdvice(advices);

		var error=new Exception("Error");var bundle=service.buildQueryPostHandlerBundle(decision,null);bundle.executeOnErrorHandlers(error);

		verify(firstErrorMappingConstraintHandlerProvider,times(1)).getHandler(any(JsonNode.class));verify(secondErrorMappingConstraintHandlerProvider,times(1)).getHandler(any(JsonNode.class));
	}

//================================================================

	@Test
	void when_buildQueryPostHandlerBundle_with_noInitialMappingProviders_then_noResultConstraintHandlers() {
		var service = buildServiceForTest(0, 0, 0, 0, 0, 0, 0);

		var decision     = new AuthorizationDecision(Decision.PERMIT);
		var responseType = ResponseTypes.instanceOf(Object.class);

		var bundle = service.buildQueryPostHandlerBundle(decision, responseType);
		bundle.executePostHandlingHandlers(null);

		verify(firstResultConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
		verify(secondResultConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildQueryPostHandlerBundle_with_presentInitialMappingProviders_and_noConstraints_then_noResultConstraintHandlers() {
		when(firstResultConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);when(secondResultConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);var service=buildServiceForTest(0,0,0,0,0,0,2);

		var decision=new AuthorizationDecision(Decision.PERMIT);var responseType=ResponseTypes.instanceOf(Object.class);

		var bundle=service.buildQueryPostHandlerBundle(decision,responseType);bundle.executePostHandlingHandlers(null);

		verify(firstResultConstraintHandlerProvider,times(0)).getHandler(any(JsonNode.class));verify(secondResultConstraintHandlerProvider,times(0)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildQueryPostHandlerBundle_with_presentInitialMappingProviders_and_obligation_and_throwingHandler_then_accessDenied() {
		when(firstOnDecisionConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);when(firstResultConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);when(secondResultConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.FALSE);when(firstResultConstraintHandlerProvider.supports(any(ResponseType.class))).thenReturn(Boolean.TRUE);when(secondResultConstraintHandlerProvider.supports(any(ResponseType.class))).thenReturn(Boolean.TRUE);when(firstResultConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn(__->{throw new RuntimeException();});var service=buildServiceForTest(1,0,0,0,0,0,2);

		var obligations=factory.arrayNode().add("obligation");var decision=new AuthorizationDecision(Decision.PERMIT).withObligations(obligations);var responseType=ResponseTypes.instanceOf(Object.class);

		var bundle=service.buildQueryPostHandlerBundle(decision,responseType);assertThrows(AccessDeniedException.class,()->bundle.executePostHandlingHandlers(null));
	}

	@Test
	void when_buildQueryPostHandlerBundle_with_presentInitialMappingProviders_and_obligation_and_noResponsible_then_accessDenied() {
		when(firstResultConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.FALSE);when(secondResultConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.FALSE);when(firstResultConstraintHandlerProvider.supports(any(ResponseType.class))).thenReturn(Boolean.TRUE);when(secondResultConstraintHandlerProvider.supports(any(ResponseType.class))).thenReturn(Boolean.TRUE);var service=buildServiceForTest(0,0,0,0,0,0,2);

		var obligations=factory.arrayNode().add("obligation");var decision=new AuthorizationDecision(Decision.PERMIT).withObligations(obligations);var responseType=ResponseTypes.instanceOf(Object.class);

		assertThrows(AccessDeniedException.class,()->service.buildQueryPostHandlerBundle(decision,responseType));
	}

	@Test
	void when_buildQueryPostHandlerBundle_with_presentInitialMappingProviders_and_obligation_and_oneResponsible_then_oneResultConstraintHandlers() {
		when(firstOnDecisionConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);when(firstResultConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.FALSE);when(secondResultConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);when(firstResultConstraintHandlerProvider.supports(any(ResponseType.class))).thenReturn(Boolean.TRUE);when(secondResultConstraintHandlerProvider.supports(any(ResponseType.class))).thenReturn(Boolean.TRUE);when(secondResultConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn(UnaryOperator.identity());var service=buildServiceForTest(1,0,0,0,0,0,2);

		var obligations=factory.arrayNode().add("obligation");var decision=new AuthorizationDecision(Decision.PERMIT).withObligations(obligations);var responseType=ResponseTypes.instanceOf(Object.class);

		var bundle=service.buildQueryPostHandlerBundle(decision,responseType);bundle.executePostHandlingHandlers(null);

		verify(firstResultConstraintHandlerProvider,times(0)).getHandler(any(JsonNode.class));verify(secondResultConstraintHandlerProvider,times(1)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildQueryPostHandlerBundle_with_presentInitialMappingProviders_and_obligation_and_oneSupported_then_oneResultConstraintHandlers() {
		when(firstOnDecisionConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);when(firstResultConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);when(secondResultConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);when(firstResultConstraintHandlerProvider.supports(any(ResponseType.class))).thenReturn(Boolean.FALSE);when(secondResultConstraintHandlerProvider.supports(any(ResponseType.class))).thenReturn(Boolean.TRUE);when(secondResultConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn(UnaryOperator.identity());var service=buildServiceForTest(1,0,0,0,0,0,2);

		var obligations=factory.arrayNode().add("obligation");var decision=new AuthorizationDecision(Decision.PERMIT).withObligations(obligations);var responseType=ResponseTypes.instanceOf(Object.class);

		var bundle=service.buildQueryPostHandlerBundle(decision,responseType);bundle.executePostHandlingHandlers(null);

		verify(firstResultConstraintHandlerProvider,times(0)).getHandler(any(JsonNode.class));verify(secondResultConstraintHandlerProvider,times(1)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildQueryPostHandlerBundle_with_presentInitialMappingProviders_and_obligation_and_allResponsible_then_allResultConstraintHandlers() {
		when(firstOnDecisionConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);when(firstResultConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);when(secondResultConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);when(firstResultConstraintHandlerProvider.supports(any(ResponseType.class))).thenReturn(Boolean.TRUE);when(secondResultConstraintHandlerProvider.supports(any(ResponseType.class))).thenReturn(Boolean.TRUE);when(firstResultConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn(UnaryOperator.identity());when(secondResultConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn(UnaryOperator.identity());var service=buildServiceForTest(1,0,0,0,0,0,2);

		var obligations=factory.arrayNode().add("obligation");var decision=new AuthorizationDecision(Decision.PERMIT).withObligations(obligations);var responseType=ResponseTypes.instanceOf(Object.class);

		var bundle=service.buildQueryPostHandlerBundle(decision,responseType);bundle.executePostHandlingHandlers(null);

		verify(firstResultConstraintHandlerProvider,times(1)).getHandler(any(JsonNode.class));verify(secondResultConstraintHandlerProvider,times(1)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildQueryPostHandlerBundle_with_presentInitialMappingProviders_and_advice_and_throwingHandler_then_ignoreHandler() {
		when(firstResultConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);when(secondResultConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.FALSE);when(firstResultConstraintHandlerProvider.supports(any(ResponseType.class))).thenReturn(Boolean.TRUE);when(secondResultConstraintHandlerProvider.supports(any(ResponseType.class))).thenReturn(Boolean.TRUE);when(firstResultConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn(__->{throw new RuntimeException();});var service=buildServiceForTest(0,0,0,0,0,0,2);

		var advices=factory.arrayNode().add("advice");var decision=new AuthorizationDecision(Decision.PERMIT).withAdvice(advices);var responseType=ResponseTypes.instanceOf(Object.class);

		var bundle=service.buildQueryPostHandlerBundle(decision,responseType);bundle.executePostHandlingHandlers(null);

		verify(firstResultConstraintHandlerProvider,times(1)).getHandler(any(JsonNode.class));verify(secondResultConstraintHandlerProvider,times(0)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildQueryPostHandlerBundle_with_presentInitialMappingProviders_and_advice_and_noResponsible_then_noResultConstraintHandlers() {
		when(firstResultConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.FALSE);when(secondResultConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.FALSE);when(firstResultConstraintHandlerProvider.supports(any(ResponseType.class))).thenReturn(Boolean.TRUE);when(secondResultConstraintHandlerProvider.supports(any(ResponseType.class))).thenReturn(Boolean.TRUE);var service=buildServiceForTest(0,0,0,0,0,0,2);

		var advices=factory.arrayNode().add("advice");var decision=new AuthorizationDecision(Decision.PERMIT).withAdvice(advices);var responseType=ResponseTypes.instanceOf(Object.class);

		var bundle=service.buildQueryPostHandlerBundle(decision,responseType);bundle.executePostHandlingHandlers(null);

		verify(firstResultConstraintHandlerProvider,times(0)).getHandler(any(JsonNode.class));verify(secondResultConstraintHandlerProvider,times(0)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildQueryPostHandlerBundle_with_presentInitialMappingProviders_and_advice_and_oneResponsible_then_oneResultConstraintHandlers() {
		when(firstResultConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.FALSE);when(secondResultConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);when(firstResultConstraintHandlerProvider.supports(any(ResponseType.class))).thenReturn(Boolean.TRUE);when(secondResultConstraintHandlerProvider.supports(any(ResponseType.class))).thenReturn(Boolean.TRUE);when(secondResultConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn(UnaryOperator.identity());var service=buildServiceForTest(0,0,0,0,0,0,2);

		var advices=factory.arrayNode().add("advice");var decision=new AuthorizationDecision(Decision.PERMIT).withAdvice(advices);var responseType=ResponseTypes.instanceOf(Object.class);

		var bundle=service.buildQueryPostHandlerBundle(decision,responseType);bundle.executePostHandlingHandlers(null);

		verify(firstResultConstraintHandlerProvider,times(0)).getHandler(any(JsonNode.class));verify(secondResultConstraintHandlerProvider,times(1)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildQueryPostHandlerBundle_with_presentInitialMappingProviders_and_advice_and_oneSupported_then_oneResultConstraintHandlers() {
		when(firstResultConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);when(secondResultConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);when(firstResultConstraintHandlerProvider.supports(any(ResponseType.class))).thenReturn(Boolean.FALSE);when(secondResultConstraintHandlerProvider.supports(any(ResponseType.class))).thenReturn(Boolean.TRUE);when(secondResultConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn(UnaryOperator.identity());var service=buildServiceForTest(0,0,0,0,0,0,2);

		var advices=factory.arrayNode().add("advice");var decision=new AuthorizationDecision(Decision.PERMIT).withAdvice(advices);var responseType=ResponseTypes.instanceOf(Object.class);

		var bundle=service.buildQueryPostHandlerBundle(decision,responseType);bundle.executePostHandlingHandlers(null);

		verify(firstResultConstraintHandlerProvider,times(0)).getHandler(any(JsonNode.class));verify(secondResultConstraintHandlerProvider,times(1)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildQueryPostHandlerBundle_with_presentInitialMappingProviders_and_advice_and_allResponsible_then_allResultConstraintHandlers() {
		when(firstResultConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);when(secondResultConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(Boolean.TRUE);when(firstResultConstraintHandlerProvider.supports(any(ResponseType.class))).thenReturn(Boolean.TRUE);when(secondResultConstraintHandlerProvider.supports(any(ResponseType.class))).thenReturn(Boolean.TRUE);when(firstResultConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn(UnaryOperator.identity());when(secondResultConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn(UnaryOperator.identity());var service=buildServiceForTest(0,0,0,0,0,0,2);

		var advices=factory.arrayNode().add("advice");var decision=new AuthorizationDecision(Decision.PERMIT).withAdvice(advices);var responseType=ResponseTypes.instanceOf(Object.class);

		var bundle=service.buildQueryPostHandlerBundle(decision,responseType);bundle.executePostHandlingHandlers(null);

		verify(firstResultConstraintHandlerProvider,times(1)).getHandler(any(JsonNode.class));verify(secondResultConstraintHandlerProvider,times(1)).getHandler(any(JsonNode.class));
	}

//================================================================
//||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||
//================================================================

	private ConstraintHandlerService buildServiceForTest(int numOfglobalRunnableProviders,
			int numOfglobalCommandMessageMappingProviders, int numOfglobalQueryMessageMappingProviders,
			int numOfglobalErrorMappingHandlerProviders, int numOfglobalMappingProviders,
			int numOfupdatePredicateProviders, int numOfupdateMappingProviders) {

		var globalRunnableProviders = new ArrayList<OnDecisionConstraintHandlerProvider>();
		if (numOfglobalRunnableProviders > 0)
			globalRunnableProviders.add(firstOnDecisionConstraintHandlerProvider);
		if (numOfglobalRunnableProviders > 1)
			globalRunnableProviders.add(secondOnDecisionConstraintHandlerProvider);

		var globalCommandMessageMappingProviders = new ArrayList<CommandConstraintHandlerProvider>();
		if (numOfglobalCommandMessageMappingProviders > 0)
			globalCommandMessageMappingProviders.add(firstCommandConstraintHandlerProvider);
		if (numOfglobalCommandMessageMappingProviders > 1)
			globalCommandMessageMappingProviders.add(secondCommandConstraintHandlerProvider);

		var globalQueryMessageMappingProviders = new ArrayList<QueryConstraintHandlerProvider>();
		if (numOfglobalQueryMessageMappingProviders > 0)
			globalQueryMessageMappingProviders.add(firstQueryConstraintHandlerProvider);
		if (numOfglobalQueryMessageMappingProviders > 1)
			globalQueryMessageMappingProviders.add(secondQueryConstraintHandlerProvider);

		var globalErrorMappingHandlerProviders = new ArrayList<ErrorMappingConstraintHandlerProvider>();
		if (numOfglobalErrorMappingHandlerProviders > 0)
			globalErrorMappingHandlerProviders.add(firstErrorMappingConstraintHandlerProvider);
		if (numOfglobalErrorMappingHandlerProviders > 1)
			globalErrorMappingHandlerProviders.add(secondErrorMappingConstraintHandlerProvider);

		var globalMappingProviders = new ArrayList<MappingConstraintHandlerProvider<?>>();
		if (numOfglobalMappingProviders > 0)
			globalMappingProviders.add(firstMappingConstraintHandlerProvider);
		if (numOfglobalMappingProviders > 1)
			globalMappingProviders.add(secondMappingConstraintHandlerProvider);

		var updatePredicateProviders = new ArrayList<UpdateFilterConstraintHandlerProvider>();
		if (numOfupdatePredicateProviders > 0)
			updatePredicateProviders.add(firstUpdateFilterConstraintHandlerProvider);
		if (numOfupdatePredicateProviders > 1)
			updatePredicateProviders.add(secondUpdateFilterConstraintHandlerProvider);

		var updateMappingProviders = new ArrayList<ResultConstraintHandlerProvider>();
		if (numOfupdateMappingProviders > 0)
			updateMappingProviders.add(firstResultConstraintHandlerProvider);
		if (numOfupdateMappingProviders > 1)
			updateMappingProviders.add(secondResultConstraintHandlerProvider);

		return new ConstraintHandlerService(mapper, parameterResolverFactory, globalRunnableProviders,
				globalCommandMessageMappingProviders, globalQueryMessageMappingProviders,
				globalErrorMappingHandlerProviders, globalMappingProviders, updatePredicateProviders,
				updateMappingProviders);
	}
}
