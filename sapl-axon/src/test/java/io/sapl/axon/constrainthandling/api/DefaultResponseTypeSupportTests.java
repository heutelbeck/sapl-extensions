package io.sapl.axon.constrainthandling.api;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class DefaultResponseTypeSupportTests {

	private ResponseTypeSupport responseTypeSupport;
	
	@BeforeEach
	void beforeEach() {
		responseTypeSupport = mock(ResponseTypeSupport.class);
		when(responseTypeSupport.supports(any(Class.class))).thenCallRealMethod();
		when(responseTypeSupport.supports(any(ResponseType.class))).thenCallRealMethod();
	}
	
	@Test
	void when_noSupportedType_then_alwasyFalse() {
		when(responseTypeSupport.getSupportedResponseTypes()).thenReturn(Set.of());
		
		assertFalse(responseTypeSupport.supports(Object.class));
		assertFalse(responseTypeSupport.supports(ResponseTypes.instanceOf(Object.class)));
	}
	
	@Test
	void when_instanceSupportedType_then_true() {
		when(responseTypeSupport.getSupportedResponseTypes()).thenReturn(Set.of(ResponseTypes.instanceOf(String.class)));
		
		assertTrue(responseTypeSupport.supports(String.class));
		assertTrue(responseTypeSupport.supports(ResponseTypes.instanceOf(String.class)));
	}
	
	@Test
	void when_subClassOfInstanceSupportedType_then_false() {
		when(responseTypeSupport.getSupportedResponseTypes()).thenReturn(Set.of(ResponseTypes.instanceOf(String.class)));
		
		assertFalse(responseTypeSupport.supports(Object.class));
		assertFalse(responseTypeSupport.supports(ResponseTypes.instanceOf(Object.class)));
	}
	
	@Test
	void when_superClassOfInstanceSupportedType_then_true() {
		when(responseTypeSupport.getSupportedResponseTypes()).thenReturn(Set.of(ResponseTypes.instanceOf(Object.class)));
		
		assertTrue(responseTypeSupport.supports(String.class));
		assertTrue(responseTypeSupport.supports(ResponseTypes.instanceOf(String.class)));
	}
	
	@Test
	void when_multipleInstancesResponseType_then_true() {
		when(responseTypeSupport.getSupportedResponseTypes()).thenReturn(Set.of(ResponseTypes.multipleInstancesOf(String.class)));
		
		assertTrue(responseTypeSupport.supports(String[].class));
		assertTrue(responseTypeSupport.supports(List.class));
		assertTrue(responseTypeSupport.supports(ResponseTypes.multipleInstancesOf(String.class)));
	}
	
	@Test
	void when_optionalResponseType_then_true() {
		when(responseTypeSupport.getSupportedResponseTypes()).thenReturn(Set.of(ResponseTypes.optionalInstanceOf(String.class)));
		
		assertTrue(responseTypeSupport.supports(Optional.class));
		assertTrue(responseTypeSupport.supports(ResponseTypes.optionalInstanceOf(String.class)));
	}
	
	@Test
	void when_differingResponseTypes_and_instanceResponseType_then_false() {
		when(responseTypeSupport.getSupportedResponseTypes()).thenReturn(Set.of(ResponseTypes.instanceOf(String.class)));
		
		assertFalse(responseTypeSupport.supports(String[].class));
		assertFalse(responseTypeSupport.supports(List.class));
		assertFalse(responseTypeSupport.supports(ResponseTypes.multipleInstancesOf(String.class)));
		assertFalse(responseTypeSupport.supports(Optional.class));
		assertFalse(responseTypeSupport.supports(ResponseTypes.optionalInstanceOf(String.class)));
	}
	
	@Test
	void when_differingResponseTypes_and_multipleInstancesResponseType_then_false() {
		when(responseTypeSupport.getSupportedResponseTypes()).thenReturn(Set.of(ResponseTypes.multipleInstancesOf(String.class)));
		
		assertFalse(responseTypeSupport.supports(String.class));
		assertFalse(responseTypeSupport.supports(ResponseTypes.instanceOf(String.class)));
		assertFalse(responseTypeSupport.supports(Optional.class));
		assertFalse(responseTypeSupport.supports(ResponseTypes.optionalInstanceOf(String.class)));
	}
	
	@Test
	void when_differingResponseTypes_and_optionalResponseType_then_false() {
		when(responseTypeSupport.getSupportedResponseTypes()).thenReturn(Set.of(ResponseTypes.optionalInstanceOf(String.class)));
		
		assertFalse(responseTypeSupport.supports(String.class));
		assertFalse(responseTypeSupport.supports(ResponseTypes.instanceOf(String.class)));
		assertFalse(responseTypeSupport.supports(String[].class));
		assertFalse(responseTypeSupport.supports(List.class));
		assertFalse(responseTypeSupport.supports(ResponseTypes.multipleInstancesOf(String.class)));
	}
}
