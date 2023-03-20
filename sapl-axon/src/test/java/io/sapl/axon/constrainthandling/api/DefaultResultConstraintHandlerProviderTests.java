package io.sapl.axon.constrainthandling.api;

import static io.sapl.axon.TestUtilities.matches;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.axonframework.commandhandling.GenericCommandResultMessage;
import org.axonframework.messaging.GenericResultMessage;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.ResultMessage;
import org.axonframework.queryhandling.GenericQueryResponseMessage;
import org.axonframework.queryhandling.GenericSubscriptionQueryUpdateMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

class DefaultResultConstraintHandlerProviderTests {

	private static final JsonNode         DEFAULT_CONSTRAINT          = JsonNodeFactory.instance
			.textNode("default test constraint");
	private static final String           DEFAULT_RESULT              = "default test result";
	private static final Throwable        DEFAULT_THROWABLE           = new Throwable("default test throwable");
	private static final ResultMessage<?> DEFAULT_RESULT_MESSAGE      = new GenericResultMessage<String>(
			DEFAULT_RESULT);
	private static final ResultMessage<?> DEFAULT_EXCEPTIONAL_MESSAGE = new GenericResultMessage<String>(
			DEFAULT_THROWABLE);

	private ResultConstraintHandlerProvider defaultProvider;

	@BeforeEach
	void beforeEach() {
		defaultProvider = mock(ResultConstraintHandlerProvider.class);
		when(defaultProvider.getHandler(any(JsonNode.class))).thenCallRealMethod();
		when(defaultProvider.getResultMessageHandler(any(ResultMessage.class), any(JsonNode.class)))
				.thenCallRealMethod();
		when(defaultProvider.mapThrowable(any(Throwable.class), any(JsonNode.class))).thenCallRealMethod();
		when(defaultProvider.mapPayloadType(any(Class.class), any(JsonNode.class))).thenCallRealMethod();
		when(defaultProvider.mapPayload(any(Object.class), any(Class.class), any(JsonNode.class))).thenCallRealMethod();
		when(defaultProvider.mapMetadata(any(MetaData.class), any(JsonNode.class))).thenCallRealMethod();
	}

	@Test
	void when_default_then_identity() {
		var handler       = defaultProvider.getHandler(DEFAULT_CONSTRAINT);
		var handledResult = handler.apply(DEFAULT_RESULT_MESSAGE);

		assertTrue(matches(DEFAULT_RESULT_MESSAGE, handledResult));
		verify(defaultProvider, times(1)).accept(DEFAULT_RESULT_MESSAGE, DEFAULT_CONSTRAINT);
	}

	@Test
	void when_default_with_mappedMetadata_then_newMetaData() {
		var metaData = new MetaData(Map.of("key1", "value1", "key2", "value2"));
		when(defaultProvider.mapMetadata(any(MetaData.class), any(JsonNode.class))).thenReturn(metaData);

		var handler       = defaultProvider.getHandler(DEFAULT_CONSTRAINT);
		var handledResult = handler.apply(DEFAULT_RESULT_MESSAGE);

		var resultMessage = assertDoesNotThrow(() -> (ResultMessage<?>) handledResult);
		assertEquals(DEFAULT_RESULT_MESSAGE.getIdentifier(), resultMessage.getIdentifier());
		assertEquals(DEFAULT_RESULT_MESSAGE.getPayloadType(), resultMessage.getPayloadType());
		assertEquals(DEFAULT_RESULT_MESSAGE.isExceptional(), resultMessage.isExceptional());
		assertEquals(DEFAULT_RESULT_MESSAGE.getPayload(), resultMessage.getPayload());
		assertEquals(metaData, resultMessage.getMetaData());
		verify(defaultProvider, times(1)).accept(DEFAULT_RESULT_MESSAGE, DEFAULT_CONSTRAINT);
	}

	@Test
	void when_default_with_exceptionalResult_then_identity() {
		var handler       = defaultProvider.getHandler(DEFAULT_CONSTRAINT);
		var handledResult = handler.apply(DEFAULT_EXCEPTIONAL_MESSAGE);

		assertTrue(matches(DEFAULT_EXCEPTIONAL_MESSAGE, handledResult));
		verify(defaultProvider, times(1)).accept(DEFAULT_EXCEPTIONAL_MESSAGE, DEFAULT_CONSTRAINT);
	}

	@Test
	void when_default_with_exceptionalResult_and_mappedThrowable_then_newThrowable() {
		var throwable = new Throwable("message1");
		when(defaultProvider.mapThrowable(any(Throwable.class), any(JsonNode.class))).thenReturn(throwable);

		var handler       = defaultProvider.getHandler(DEFAULT_CONSTRAINT);
		var handledResult = handler.apply(DEFAULT_EXCEPTIONAL_MESSAGE);

		var resultMessage = assertDoesNotThrow(() -> (ResultMessage<?>) handledResult);
		assertEquals(DEFAULT_EXCEPTIONAL_MESSAGE.getIdentifier(), resultMessage.getIdentifier());
		assertEquals(DEFAULT_EXCEPTIONAL_MESSAGE.getPayloadType(), resultMessage.getPayloadType());
		assertEquals(DEFAULT_EXCEPTIONAL_MESSAGE.isExceptional(), resultMessage.isExceptional());
		assertEquals(throwable.getClass(), resultMessage.exceptionResult().getClass());
		assertEquals(throwable.getLocalizedMessage(), resultMessage.exceptionResult().getLocalizedMessage());
		verify(defaultProvider, times(1)).accept(DEFAULT_EXCEPTIONAL_MESSAGE, DEFAULT_CONSTRAINT);
	}

	@Test
	void when_default_with_subscriptionQueryUpdateMessage_then_identity() {
		var result = new GenericSubscriptionQueryUpdateMessage<>(DEFAULT_RESULT);

		var handler       = defaultProvider.getHandler(DEFAULT_CONSTRAINT);
		var handledResult = handler.apply(result);

		assertTrue(matches(result, handledResult));
		verify(defaultProvider, times(1)).accept(result, DEFAULT_CONSTRAINT);
	}

	@Test
	void when_default_with_queryResponseMessage_then_identity() {
		var result = new GenericQueryResponseMessage<>(DEFAULT_RESULT);

		var handler       = defaultProvider.getHandler(DEFAULT_CONSTRAINT);
		var handledResult = handler.apply(result);

		assertTrue(matches(result, handledResult));
		verify(defaultProvider, times(1)).accept(result, DEFAULT_CONSTRAINT);
	}

	@Test
	void when_default_with_commandResultMessage_then_identity() {
		var result = new GenericCommandResultMessage<>(DEFAULT_RESULT);

		var handler       = defaultProvider.getHandler(DEFAULT_CONSTRAINT);
		var handledResult = handler.apply(result);

		assertTrue(matches(result, handledResult));
		verify(defaultProvider, times(1)).accept(result, DEFAULT_CONSTRAINT);
	}

	@Test
	void when_default_with_nonResultMessage_then_mapPayload() {
		var handler       = defaultProvider.getHandler(DEFAULT_CONSTRAINT);
		var handledResult = handler.apply("not a response message!");

		assertEquals("not a response message!", handledResult);
		verify(defaultProvider, times(1)).mapPayload("not a response message!", Object.class, DEFAULT_CONSTRAINT);
	}
}
