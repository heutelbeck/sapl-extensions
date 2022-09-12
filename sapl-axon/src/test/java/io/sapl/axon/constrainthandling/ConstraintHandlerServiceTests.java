package io.sapl.axon.constrainthandling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.axonframework.messaging.annotation.DefaultParameterResolverFactory;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.axonframework.messaging.responsetypes.AbstractResponseType;
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
import io.sapl.axon.constrainthandling.api.CommandConstraintHandlerProvider;
import io.sapl.axon.constrainthandling.api.OnDecisionConstraintHandlerProvider;
import io.sapl.axon.constrainthandling.api.QueryConstraintHandlerProvider;
import io.sapl.axon.constrainthandling.api.ResultConstraintHandlerProvider;
import io.sapl.axon.constrainthandling.api.UpdateFilterConstraintHandlerProvider;
import io.sapl.spring.constraints.api.ErrorMappingConstraintHandlerProvider;
import io.sapl.spring.constraints.api.MappingConstraintHandlerProvider;

public class ConstraintHandlerServiceTests {

	private static class UnknownResponseType<R> extends AbstractResponseType<R> {

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

	private static ObjectMapper mapper;
	private static JsonNodeFactory factory;
	private static ParameterResolverFactory parameterResolverFactory;

	@BeforeAll
	static void beforeAll() {
		mapper = new ObjectMapper();
		factory = new JsonNodeFactory(true);
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
		firstOnDecisionConstraintHandlerProvider = mock(OnDecisionConstraintHandlerProvider.class);
		secondOnDecisionConstraintHandlerProvider = mock(OnDecisionConstraintHandlerProvider.class);

		firstCommandConstraintHandlerProvider = mock(CommandConstraintHandlerProvider.class);
		secondCommandConstraintHandlerProvider = mock(CommandConstraintHandlerProvider.class);

		firstQueryConstraintHandlerProvider = mock(QueryConstraintHandlerProvider.class);
		secondQueryConstraintHandlerProvider = mock(QueryConstraintHandlerProvider.class);

		firstErrorMappingConstraintHandlerProvider = mock(ErrorMappingConstraintHandlerProvider.class);
		secondErrorMappingConstraintHandlerProvider = mock(ErrorMappingConstraintHandlerProvider.class);

		firstMappingConstraintHandlerProvider = mock(MappingConstraintHandlerProvider.class);
		secondMappingConstraintHandlerProvider = mock(MappingConstraintHandlerProvider.class);

		firstUpdateFilterConstraintHandlerProvider = mock(UpdateFilterConstraintHandlerProvider.class);
		secondUpdateFilterConstraintHandlerProvider = mock(UpdateFilterConstraintHandlerProvider.class);

		firstResultConstraintHandlerProvider = mock(ResultConstraintHandlerProvider.class);
		secondResultConstraintHandlerProvider = mock(ResultConstraintHandlerProvider.class);
	}

//================================================================

	@Test
	void when_deserializeResource_with_stringRessource_then_returnString() {
		var service = buildServiceForTest(0, 0, 0, 0, 0, 0, 0);

		var resource = new TextNode("resourceString");
		var<String> type = ResponseTypes.instanceOf(String.class);

		var deserialized = service.deserializeResource(resource, type);

		assertEquals(deserialized, "resourceString");
	}

	@Test
	void when_deserializeResource_with_nonMatchingRessource_then_accessDenied() {
		var service = buildServiceForTest(0, 0, 0, 0, 0, 0, 0);

		var resource = new TextNode("resourceString");
		var<Integer> type = ResponseTypes.instanceOf(Integer.class);

		assertThrows(AccessDeniedException.class, () -> service.deserializeResource(resource, type));
	}

	@Test
	void when_deserializeResource_with_optionalStringRessource_and_stringExists_then_returnString() {
		var service = buildServiceForTest(0, 0, 0, 0, 0, 0, 0);

		var resource = new TextNode("resourceString");
		var type = ResponseTypes.optionalInstanceOf(String.class);

		var deserialized = service.deserializeResource(resource, type);

		assertEquals(deserialized, Optional.of("resourceString"));
	}

	@Test
	void when_deserializeResource_with_optionalStringRessource_and_stringDoesNotExist_then_returnNull() {
		var service = buildServiceForTest(0, 0, 0, 0, 0, 0, 0);

		var resource = NullNode.getInstance();
		var type = ResponseTypes.optionalInstanceOf(String.class);

		var deserialized = service.deserializeResource(resource, type);

		assertEquals(deserialized, Optional.empty());
	}

	@Test
	void when_deserializeResource_with_optionalStringRessource_and_nonMatchingRessource_then_accessDenied() {
		var service = buildServiceForTest(0, 0, 0, 0, 0, 0, 0);

		var resource = new TextNode("resourceString");
		var type = ResponseTypes.optionalInstanceOf(Integer.class);

		assertThrows(AccessDeniedException.class, () -> service.deserializeResource(resource, type));
	}

	@Test
	void when_deserializeResource_with_multipleStringRessource_then_returnAllStrings() {
		var service = buildServiceForTest(0, 0, 0, 0, 0, 0, 0);

		var<String> listOfStrings = List.of("resourceString", "otherResourceString");
		var resource = factory.arrayNode().add("resourceString").add("otherResourceString");
		var type = ResponseTypes.multipleInstancesOf(String.class);

		var deserialized = service.deserializeResource(resource, type);

		assertEquals(deserialized, listOfStrings);
	}

	@Test
	void when_deserializeResource_with_multipleStringRessource_and_onlyOneEntry_then_returnSingleString() {
		var service = buildServiceForTest(0, 0, 0, 0, 0, 0, 0);

		var<String> listOfStrings = List.of("resourceString");
		var resource = factory.arrayNode().add("resourceString");
		var type = ResponseTypes.multipleInstancesOf(String.class);

		var deserialized = service.deserializeResource(resource, type);

		assertEquals(deserialized, listOfStrings);
	}

	@Test
	void when_deserializeResource_with_multipleStringRessource_and_noEntry_then_returnNoStrings() {
		var service = buildServiceForTest(0, 0, 0, 0, 0, 0, 0);

		var listOfStrings = List.<String>of();
		var resource = factory.arrayNode();
		var type = ResponseTypes.multipleInstancesOf(String.class);

		var deserialized = service.deserializeResource(resource, type);

		assertEquals(deserialized, listOfStrings);
	}

	@Test
	void when_deserializeResource_with_multipleStringRessource_and_nonMatchingRessource_then_accessDenied() {
		var service = buildServiceForTest(0, 0, 0, 0, 0, 0, 0);

		var resource = factory.arrayNode().add("resourceString").add("otherResourceString");
		var type = ResponseTypes.multipleInstancesOf(Integer.class);

		assertThrows(AccessDeniedException.class, () -> service.deserializeResource(resource, type));
	}

	@Test
	void when_deserializeResource_with_multipleStringRessource_and_nonNonArrayNode_then_accessDenied() {
		var service = buildServiceForTest(0, 0, 0, 0, 0, 0, 0);

		var resource = new TextNode("resourceString");
		var type = ResponseTypes.multipleInstancesOf(String.class);

		assertThrows(AccessDeniedException.class, () -> service.deserializeResource(resource, type));
	}

	@Test
	void when_deserializeResource_with_unknownResponseType_then_accessDenied() {
		var service = buildServiceForTest(0, 0, 0, 0, 0, 0, 0);

		var resource = new TextNode("resourceString");
		var<String> type = new UnknownResponseType<>(String.class);

		assertThrows(AccessDeniedException.class, () -> service.deserializeResource(resource, type));
	}

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
		when(firstOnDecisionConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(true);
		when(secondOnDecisionConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(true);
		var service = buildServiceForTest(2, 0, 0, 0, 0, 0, 0);

		var decision = new AuthorizationDecision(Decision.PERMIT);

		var bundle = service.buildPreEnforceCommandConstraintHandlerBundle(decision, null, Optional.empty(), null);
		bundle.executeOnDecisionHandlers(null, null);

		verify(firstOnDecisionConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
		verify(secondOnDecisionConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildPreEnforceCommandConstraintHandlerBundle_with_presentGlobalRunnableProviders_and_obligation_and_noResponsible_then_accessDenied() {
		when(firstOnDecisionConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(false);
		when(secondOnDecisionConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(false);
		var service = buildServiceForTest(2, 0, 0, 0, 0, 0, 0);

		var obligations = factory.arrayNode().add("obligation");
		var decision = new AuthorizationDecision(Decision.PERMIT).withObligations(obligations);

		assertThrows(AccessDeniedException.class,
				() -> service.buildPreEnforceCommandConstraintHandlerBundle(decision, null, Optional.empty(), null));
	}

	@Test
	void when_buildPreEnforceCommandConstraintHandlerBundle_with_presentGlobalRunnableProviders_and_obligation_and_oneResponsible_then_oneOnDecisionHandlers() {
		when(firstOnDecisionConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(false);
		when(secondOnDecisionConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(true);
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
		when(firstOnDecisionConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(true);
		when(secondOnDecisionConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(true);
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
		when(firstOnDecisionConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(false);
		when(secondOnDecisionConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(false);
		var service = buildServiceForTest(2, 0, 0, 0, 0, 0, 0);

		var advices = factory.arrayNode().add("advice");
		var decision = new AuthorizationDecision(Decision.PERMIT).withAdvice(advices);

		var bundle = service.buildPreEnforceCommandConstraintHandlerBundle(decision, null, Optional.empty(), null);
		bundle.executeOnDecisionHandlers(null, null);

		verify(firstOnDecisionConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
		verify(secondOnDecisionConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildPreEnforceCommandConstraintHandlerBundle_with_presentGlobalRunnableProviders_and_advice_and_oneResponsible_then_oneOnDecisionHandlers() {
		when(firstOnDecisionConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(false);
		when(secondOnDecisionConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(true);
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
		when(firstOnDecisionConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(true);
		when(secondOnDecisionConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(true);
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
		when(firstCommandConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(true);
		when(secondCommandConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(true);
		var service = buildServiceForTest(0, 2, 0, 0, 0, 0, 0);

		var decision = new AuthorizationDecision(Decision.PERMIT);

		var bundle = service.buildPreEnforceCommandConstraintHandlerBundle(decision, null, Optional.empty(), null);
		bundle.executeCommandMappingHandlers(null);

		verify(firstCommandConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
		verify(secondCommandConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildPreEnforceCommandConstraintHandlerBundle_with_presentGlobalCommandMessageMappingProviders_and_obligation_and_throwingHandler_then_accessDenied() {
		when(firstCommandConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(true);
		when(secondCommandConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(false);
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
		when(firstCommandConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(false);
		when(secondCommandConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(false);
		var service = buildServiceForTest(0, 2, 0, 0, 0, 0, 0);

		var obligations = factory.arrayNode().add("obligation");
		var decision = new AuthorizationDecision(Decision.PERMIT).withObligations(obligations);

		assertThrows(AccessDeniedException.class,
				() -> service.buildPreEnforceCommandConstraintHandlerBundle(decision, null, Optional.empty(), null));
	}

	@Test
	void when_buildPreEnforceCommandConstraintHandlerBundle_with_presentGlobalCommandMessageMappingProviders_and_obligation_and_oneResponsible_then_oneCommandMessageMappingHandlers() {
		when(firstCommandConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(false);
		when(secondCommandConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(true);
		when(secondCommandConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn(Function.identity());
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
		when(firstCommandConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(true);
		when(secondCommandConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(true);
		when(firstCommandConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn(Function.identity());
		when(secondCommandConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn(Function.identity());
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
		when(firstCommandConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(true);
		when(secondCommandConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(false);
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
		when(firstCommandConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(false);
		when(secondCommandConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(false);
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
		when(firstCommandConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(false);
		when(secondCommandConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(true);
		when(secondCommandConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn(Function.identity());
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
		when(firstCommandConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(true);
		when(secondCommandConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(true);
		when(firstCommandConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn(Function.identity());
		when(secondCommandConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn(Function.identity());
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

		var error = new Exception("Error");
		var bundle = service.buildPreEnforceCommandConstraintHandlerBundle(decision, null, Optional.empty(), null);
		bundle.executeOnErrorHandlers(error);

		verify(firstErrorMappingConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
		verify(secondErrorMappingConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildPreEnforceCommandConstraintHandlerBundle_with_presentGlobalErrorMappingHandlerProviders_and_noConstraints_then_noErrorMappingConstraintHandlers() {
		when(firstErrorMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(true);
		when(secondErrorMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(true);
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
		when(firstErrorMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(true);
		when(secondErrorMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(false);
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
		when(firstErrorMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(false);
		when(secondErrorMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(false);
		var service = buildServiceForTest(0, 0, 0, 2, 0, 0, 0);

		var obligations = factory.arrayNode().add("obligation");
		var decision = new AuthorizationDecision(Decision.PERMIT).withObligations(obligations);

		assertThrows(AccessDeniedException.class,
				() -> service.buildPreEnforceCommandConstraintHandlerBundle(decision, null, Optional.empty(), null));
	}

	@Test
	void when_buildPreEnforceCommandConstraintHandlerBundle_with_presentGlobalErrorMappingHandlerProviders_and_obligation_and_oneResponsible_then_oneErrorMappingConstraintHandlers() {
		when(firstErrorMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(false);
		when(secondErrorMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(true);
		when(secondErrorMappingConstraintHandlerProvider.getHandler(any(JsonNode.class)))
				.thenReturn(Function.identity());
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
		when(firstErrorMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(true);
		when(secondErrorMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(true);
		when(firstErrorMappingConstraintHandlerProvider.getHandler(any(JsonNode.class)))
				.thenReturn(Function.identity());
		when(secondErrorMappingConstraintHandlerProvider.getHandler(any(JsonNode.class)))
				.thenReturn(Function.identity());
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
		when(firstErrorMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(true);
		when(secondErrorMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(false);
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
		when(firstErrorMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(false);
		when(secondErrorMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(false);
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
		when(firstErrorMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(false);
		when(secondErrorMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(true);
		when(secondErrorMappingConstraintHandlerProvider.getHandler(any(JsonNode.class)))
				.thenReturn(Function.identity());
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
		when(firstErrorMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(true);
		when(secondErrorMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(true);
		when(firstErrorMappingConstraintHandlerProvider.getHandler(any(JsonNode.class)))
				.thenReturn(Function.identity());
		when(secondErrorMappingConstraintHandlerProvider.getHandler(any(JsonNode.class)))
				.thenReturn(Function.identity());
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
		when(firstMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(true);
		when(secondMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(true);
		var service = buildServiceForTest(0, 0, 0, 0, 2, 0, 0);

		var decision = new AuthorizationDecision(Decision.PERMIT);

		var bundle = service.buildPreEnforceCommandConstraintHandlerBundle(decision, null, Optional.empty(), null);
		bundle.executePostHandlingHandlers(null);

		verify(firstMappingConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
		verify(secondMappingConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildPreEnforceCommandConstraintHandlerBundle_with_presentGlobalMappingProviders_and_obligation_and_throwingHandler_then_accessDenied() {
		when(firstMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(true);
		when(secondMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(false);
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
		when(firstMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(false);
		when(secondMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(false);
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
		when(firstMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(false);
		when(secondMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(true);
		when(firstMappingConstraintHandlerProvider.getSupportedType()).thenReturn(Object.class);
		when(secondMappingConstraintHandlerProvider.getSupportedType()).thenReturn(Object.class);
		when(secondMappingConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn(Function.identity());
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
			public Function<String, String> getHandler(JsonNode constraint) {
				return Function.identity();
			}
		});

		when(firstMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(true);
		when(firstMappingConstraintHandlerProvider.getSupportedType()).thenReturn(Object.class);
		when(firstMappingConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn(Function.identity());
		var service = new ConstraintHandlerService(mapper, parameterResolverFactory, List.of(), List.of(), List.of(),
				List.of(), List.of(firstMappingConstraintHandlerProvider, alternativeMappingConstraintHandlerProvider),
				List.of(), List.of());

		var obligations = factory.arrayNode().add("obligation");
		var decision = new AuthorizationDecision(Decision.PERMIT).withObligations(obligations);

		var bundle = service.buildPreEnforceCommandConstraintHandlerBundle(decision, null, Optional.empty(), null);
		bundle.executePostHandlingHandlers(null);

		verify(firstMappingConstraintHandlerProvider, times(1)).getHandler(any(JsonNode.class));
		verify(alternativeMappingConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildPreEnforceCommandConstraintHandlerBundle_with_presentGlobalMappingProviders_and_obligation_and_allResponsible_then_allResultMappingHandlers() {
		when(firstMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(true);
		when(secondMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(true);
		when(firstMappingConstraintHandlerProvider.getSupportedType()).thenReturn(Object.class);
		when(secondMappingConstraintHandlerProvider.getSupportedType()).thenReturn(Object.class);
		when(firstMappingConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn(Function.identity());
		when(secondMappingConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn(Function.identity());
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
		when(firstMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(true);
		when(secondMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(false);
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
		when(firstMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(false);
		when(secondMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(false);
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
		when(firstMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(false);
		when(secondMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(true);
		when(firstMappingConstraintHandlerProvider.getSupportedType()).thenReturn(Object.class);
		when(secondMappingConstraintHandlerProvider.getSupportedType()).thenReturn(Object.class);
		when(secondMappingConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn(Function.identity());
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
			public Function<String, String> getHandler(JsonNode constraint) {
				return Function.identity();
			}
		});

		when(firstMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(true);
		when(firstMappingConstraintHandlerProvider.getSupportedType()).thenReturn(Object.class);
		when(firstMappingConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn(Function.identity());
		var service = new ConstraintHandlerService(mapper, parameterResolverFactory, List.of(), List.of(), List.of(),
				List.of(), List.of(firstMappingConstraintHandlerProvider, alternativeMappingConstraintHandlerProvider),
				List.of(), List.of());

		var advices = factory.arrayNode().add("advice");
		var decision = new AuthorizationDecision(Decision.PERMIT).withAdvice(advices);

		var bundle = service.buildPreEnforceCommandConstraintHandlerBundle(decision, null, Optional.empty(), null);
		bundle.executePostHandlingHandlers(null);

		verify(firstMappingConstraintHandlerProvider, times(1)).getHandler(any(JsonNode.class));
		verify(alternativeMappingConstraintHandlerProvider, times(0)).getHandler(any(JsonNode.class));
	}

	@Test
	void when_buildPreEnforceCommandConstraintHandlerBundle_with_presentGlobalMappingProviders_and_advice_and_allResponsible_then_allResultMappingHandlers() {
		when(firstMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(true);
		when(secondMappingConstraintHandlerProvider.isResponsible(any(JsonNode.class))).thenReturn(true);
		when(firstMappingConstraintHandlerProvider.getSupportedType()).thenReturn(Object.class);
		when(secondMappingConstraintHandlerProvider.getSupportedType()).thenReturn(Object.class);
		when(firstMappingConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn(Function.identity());
		when(secondMappingConstraintHandlerProvider.getHandler(any(JsonNode.class))).thenReturn(Function.identity());
		var service = buildServiceForTest(0, 0, 0, 0, 2, 0, 0);

		var advices = factory.arrayNode().add("advice");
		var decision = new AuthorizationDecision(Decision.PERMIT).withAdvice(advices);

		var bundle = service.buildPreEnforceCommandConstraintHandlerBundle(decision, null, Optional.empty(), null);
		bundle.executePostHandlingHandlers(null);

		verify(firstMappingConstraintHandlerProvider, times(1)).getHandler(any(JsonNode.class));
		verify(secondMappingConstraintHandlerProvider, times(1)).getHandler(any(JsonNode.class));
	}

//================================================================

	private ConstraintHandlerService buildServiceForTest(int numOfglobalRunnableProviders,
			int numOfglobalCommandMessageMappingProviders, int numOfglobalQueryMessageMappingProviders,
			int numOfglobalErrorMappingHandlerProviders, int numOfglobalMappingProviders,
			int numOfupdatePredicateProviders, int numOfupdateMappingProviders) {

		var<OnDecisionConstraintHandlerProvider> globalRunnableProviders = new ArrayList<OnDecisionConstraintHandlerProvider>();
		if (numOfglobalRunnableProviders > 0)
			globalRunnableProviders.add(firstOnDecisionConstraintHandlerProvider);
		if (numOfglobalRunnableProviders > 1)
			globalRunnableProviders.add(secondOnDecisionConstraintHandlerProvider);

		var<CommandConstraintHandlerProvider> globalCommandMessageMappingProviders = new ArrayList<CommandConstraintHandlerProvider>();
		if (numOfglobalCommandMessageMappingProviders > 0)
			globalCommandMessageMappingProviders.add(firstCommandConstraintHandlerProvider);
		if (numOfglobalCommandMessageMappingProviders > 1)
			globalCommandMessageMappingProviders.add(secondCommandConstraintHandlerProvider);

		var<QueryConstraintHandlerProvider> globalQueryMessageMappingProviders = new ArrayList<QueryConstraintHandlerProvider>();
		if (numOfglobalQueryMessageMappingProviders > 0)
			globalQueryMessageMappingProviders.add(firstQueryConstraintHandlerProvider);
		if (numOfglobalQueryMessageMappingProviders > 1)
			globalQueryMessageMappingProviders.add(secondQueryConstraintHandlerProvider);

		var<ErrorMappingConstraintHandlerProvider> globalErrorMappingHandlerProviders = new ArrayList<ErrorMappingConstraintHandlerProvider>();
		if (numOfglobalErrorMappingHandlerProviders > 0)
			globalErrorMappingHandlerProviders.add(firstErrorMappingConstraintHandlerProvider);
		if (numOfglobalErrorMappingHandlerProviders > 1)
			globalErrorMappingHandlerProviders.add(secondErrorMappingConstraintHandlerProvider);

		var<MappingConstraintHandlerProvider> globalMappingProviders = new ArrayList<MappingConstraintHandlerProvider<?>>();
		if (numOfglobalMappingProviders > 0)
			globalMappingProviders.add(firstMappingConstraintHandlerProvider);
		if (numOfglobalMappingProviders > 1)
			globalMappingProviders.add(secondMappingConstraintHandlerProvider);

		var<UpdateFilterConstraintHandlerProvider> updatePredicateProviders = new ArrayList<UpdateFilterConstraintHandlerProvider>();
		if (numOfupdatePredicateProviders > 0)
			updatePredicateProviders.add(firstUpdateFilterConstraintHandlerProvider);
		if (numOfupdatePredicateProviders > 1)
			updatePredicateProviders.add(secondUpdateFilterConstraintHandlerProvider);

		var<ResultConstraintHandlerProvider> updateMappingProviders = new ArrayList<ResultConstraintHandlerProvider>();
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
