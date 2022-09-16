package io.sapl.axon.subscription;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.TargetAggregateIdentifier;
import org.axonframework.spring.stereotype.Aggregate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.expression.spel.SpelEvaluationException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.axon.annotation.PreHandleEnforce;
import lombok.AllArgsConstructor;
import lombok.Value;

public class AuthorizationSubscriptionBuilderServiceTests {
	private static final String ACTION_TYPE          = "actionType";
	private static final String AGGREGATE_IDENTIFIER = "aggregateIdentifier";
	private static final String AGGREGATE_TYPE       = "aggregateType";
	private static final String COMMAND              = "command";
	private static final String COMMAND_NAME         = "commandName";
	private static final String PAYLOAD              = "payload";
	private static final String PAYLOAD_TYPE         = "payloadType";

	private static final String TEST_AGGREGATE_IDENTIFIER = "testTargetAggregateIdentifier";
	private static final String TEST_ANONYMOUS            = "anonymous";
	private static final String TEST_SUBJECT              = "testSubject";
	private static final String TEST_ACTION               = "testAction";
	private static final String TEST_RESOURCE             = "testResource";
	private static final String TEST_ENVIRONMENT          = "testEnvironment";

	@Value
	private static class TestCommand {
		@TargetAggregateIdentifier
		Object targetAggregateIdentifier;
		Object someOtherField = "someOtherValue";
	}

	@Aggregate
	@AllArgsConstructor
	private static class TestAggregate {
		@AggregateIdentifier
		Object aggregateIdentifier;

		@CommandHandler
		@PreHandleEnforce
		public void handle1(TestCommand cmd) {
		}

		@CommandHandler
		@PreHandleEnforce(subject = "malformed")
		public void handle2(TestCommand cmd) {
		}

		@CommandHandler
		@PreHandleEnforce(subject = "'testSubject'")
		public void handle3(TestCommand cmd) {
		}

		@CommandHandler
		@PreHandleEnforce(action = "malformed")
		public void handle4(TestCommand cmd) {
		}

		@CommandHandler
		@PreHandleEnforce(action = "'testAction'")
		public void handle5(TestCommand cmd) {
		}

		@CommandHandler
		@PreHandleEnforce(resource = "malformed")
		public void handle6(TestCommand cmd) {
		}

		@CommandHandler
		@PreHandleEnforce(resource = "'testResource'")
		public void handle7(TestCommand cmd) {
		}

		@CommandHandler
		@PreHandleEnforce(environment = "malformed")
		public void handle8(TestCommand cmd) {
		}

		@CommandHandler
		@PreHandleEnforce(environment = "'testEnvironment'")
		public void handle9(TestCommand cmd) {
		}
	}

	private static class TestCommandHandlingObject {
		@CommandHandler
		@PreHandleEnforce
		public void handle1(TestCommand cmd) {
		}

		@CommandHandler
		@PreHandleEnforce(subject = "malformed")
		public void handle2(TestCommand cmd) {
		}

		@CommandHandler
		@PreHandleEnforce(subject = "'testSubject'")
		public void handle3(TestCommand cmd) {
		}

		@CommandHandler
		@PreHandleEnforce(action = "malformed")
		public void handle4(TestCommand cmd) {
		}

		@CommandHandler
		@PreHandleEnforce(action = "'testAction'")
		public void handle5(TestCommand cmd) {
		}

		@CommandHandler
		@PreHandleEnforce(resource = "malformed")
		public void handle6(TestCommand cmd) {
		}

		@CommandHandler
		@PreHandleEnforce(resource = "'testResource'")
		public void handle7(TestCommand cmd) {
		}

		@CommandHandler
		@PreHandleEnforce(environment = "malformed")
		public void handle8(TestCommand cmd) {
		}

		@CommandHandler
		@PreHandleEnforce(environment = "'testEnvironment'")
		public void handle9(TestCommand cmd) {
		}
	}

	private static ObjectMapper                            mapper;
	private static AuthorizationSubscriptionBuilderService service;

	@BeforeAll
	static void beforeAll() {
		mapper  = new ObjectMapper();
		service = new AuthorizationSubscriptionBuilderService(mapper);
	}

	@Test
	void when_constructAuthorizationSubscriptionForCommand_with_emptyAnnotation_then_anonymousSubscription()
			throws NoSuchMethodException, SecurityException {
		var payload = new TestCommand(TEST_AGGREGATE_IDENTIFIER);
		var command = new GenericCommandMessage<>(payload);

		var handlerObject1 = new TestAggregate(TEST_AGGREGATE_IDENTIFIER);
		var annotation1    = TestAggregate.class.getDeclaredMethod("handle1", TestCommand.class)
				.getAnnotation(PreHandleEnforce.class);
		var handlerObject2 = new TestCommandHandlingObject();
		var annotation2    = TestAggregate.class.getDeclaredMethod("handle1", TestCommand.class)
				.getAnnotation(PreHandleEnforce.class);

		var subscription1 = service.constructAuthorizationSubscriptionForCommand(command, handlerObject1, annotation1);
		var subscription2 = service.constructAuthorizationSubscriptionForCommand(command, handlerObject2, annotation2);

		assertEquals(TEST_ANONYMOUS, subscription1.getSubject().asText());
		assertEquals(TEST_ANONYMOUS, subscription2.getSubject().asText());
		assertEquals(COMMAND, subscription1.getAction().get(ACTION_TYPE).asText());
		assertEquals(COMMAND, subscription2.getAction().get(ACTION_TYPE).asText());
		assertEquals(TestCommand.class.getName(), subscription1.getAction().get(COMMAND_NAME).asText());
		assertEquals(TestCommand.class.getName(), subscription2.getAction().get(COMMAND_NAME).asText());
		assertEquals(asTree(payload), subscription1.getAction().get(PAYLOAD));
		assertEquals(asTree(payload), subscription2.getAction().get(PAYLOAD));
		assertEquals(TestCommand.class.getName(), subscription1.getAction().get(PAYLOAD_TYPE).asText());
		assertEquals(TestCommand.class.getName(), subscription2.getAction().get(PAYLOAD_TYPE).asText());
		assertEquals(aggregateInfo(), subscription1.getResource());
		assertEquals(handlerInfo(), subscription2.getResource());
		assertNull(subscription1.getEnvironment());
		assertNull(subscription2.getEnvironment());
	}

	@Test
	void when_constructAuthorizationSubscriptionForCommand_with_malformedSubject_then_exception()
			throws NoSuchMethodException, SecurityException {
		var payload = new TestCommand(TEST_AGGREGATE_IDENTIFIER);
		var command = new GenericCommandMessage<>(payload);

		var handlerObject1 = new TestAggregate(TEST_AGGREGATE_IDENTIFIER);
		var annotation1    = TestAggregate.class.getDeclaredMethod("handle2", TestCommand.class)
				.getAnnotation(PreHandleEnforce.class);
		var handlerObject2 = new TestCommandHandlingObject();
		var annotation2    = TestAggregate.class.getDeclaredMethod("handle2", TestCommand.class)
				.getAnnotation(PreHandleEnforce.class);

		assertThrows(SpelEvaluationException.class,
				() -> service.constructAuthorizationSubscriptionForCommand(command, handlerObject1, annotation1));
		assertThrows(SpelEvaluationException.class,
				() -> service.constructAuthorizationSubscriptionForCommand(command, handlerObject2, annotation2));
	}

	@Test
	void when_constructAuthorizationSubscriptionForCommand_with_subject_then_subscriptionWithSubject()
			throws NoSuchMethodException, SecurityException {
		var payload = new TestCommand(TEST_AGGREGATE_IDENTIFIER);
		var command = new GenericCommandMessage<>(payload);

		var handlerObject1 = new TestAggregate(TEST_AGGREGATE_IDENTIFIER);
		var annotation1    = TestAggregate.class.getDeclaredMethod("handle3", TestCommand.class)
				.getAnnotation(PreHandleEnforce.class);
		var handlerObject2 = new TestCommandHandlingObject();
		var annotation2    = TestAggregate.class.getDeclaredMethod("handle3", TestCommand.class)
				.getAnnotation(PreHandleEnforce.class);

		var subscription1 = service.constructAuthorizationSubscriptionForCommand(command, handlerObject1, annotation1);
		var subscription2 = service.constructAuthorizationSubscriptionForCommand(command, handlerObject2, annotation2);

		assertEquals(TEST_SUBJECT, subscription1.getSubject().asText());
		assertEquals(TEST_SUBJECT, subscription2.getSubject().asText());
		assertEquals(COMMAND, subscription1.getAction().get(ACTION_TYPE).asText());
		assertEquals(COMMAND, subscription2.getAction().get(ACTION_TYPE).asText());
		assertEquals(TestCommand.class.getName(), subscription1.getAction().get(COMMAND_NAME).asText());
		assertEquals(TestCommand.class.getName(), subscription2.getAction().get(COMMAND_NAME).asText());
		assertEquals(asTree(payload), subscription1.getAction().get(PAYLOAD));
		assertEquals(asTree(payload), subscription2.getAction().get(PAYLOAD));
		assertEquals(TestCommand.class.getName(), subscription1.getAction().get(PAYLOAD_TYPE).asText());
		assertEquals(TestCommand.class.getName(), subscription2.getAction().get(PAYLOAD_TYPE).asText());
		assertEquals(aggregateInfo(), subscription1.getResource());
		assertEquals(handlerInfo(), subscription2.getResource());
		assertNull(subscription1.getEnvironment());
		assertNull(subscription2.getEnvironment());
	}

	@Test
	void when_constructAuthorizationSubscriptionForCommand_with_malformedAction_then_exception()
			throws NoSuchMethodException, SecurityException {
		var payload = new TestCommand(TEST_AGGREGATE_IDENTIFIER);
		var command = new GenericCommandMessage<>(payload);

		var handlerObject1 = new TestAggregate(TEST_AGGREGATE_IDENTIFIER);
		var annotation1    = TestAggregate.class.getDeclaredMethod("handle4", TestCommand.class)
				.getAnnotation(PreHandleEnforce.class);
		var handlerObject2 = new TestCommandHandlingObject();
		var annotation2    = TestAggregate.class.getDeclaredMethod("handle4", TestCommand.class)
				.getAnnotation(PreHandleEnforce.class);

		assertThrows(SpelEvaluationException.class,
				() -> service.constructAuthorizationSubscriptionForCommand(command, handlerObject1, annotation1));
		assertThrows(SpelEvaluationException.class,
				() -> service.constructAuthorizationSubscriptionForCommand(command, handlerObject2, annotation2));
	}

	@Test
	void when_constructAuthorizationSubscriptionForCommand_with_action_then_subscriptionWithAction()
			throws NoSuchMethodException, SecurityException {
		var payload = new TestCommand(TEST_AGGREGATE_IDENTIFIER);
		var command = new GenericCommandMessage<>(payload);

		var handlerObject1 = new TestAggregate(TEST_AGGREGATE_IDENTIFIER);
		var annotation1    = TestAggregate.class.getDeclaredMethod("handle5", TestCommand.class)
				.getAnnotation(PreHandleEnforce.class);
		var handlerObject2 = new TestCommandHandlingObject();
		var annotation2    = TestAggregate.class.getDeclaredMethod("handle5", TestCommand.class)
				.getAnnotation(PreHandleEnforce.class);

		var subscription1 = service.constructAuthorizationSubscriptionForCommand(command, handlerObject1, annotation1);
		var subscription2 = service.constructAuthorizationSubscriptionForCommand(command, handlerObject2, annotation2);

		assertEquals(TEST_ANONYMOUS, subscription1.getSubject().asText());
		assertEquals(TEST_ANONYMOUS, subscription2.getSubject().asText());
		assertEquals(TEST_ACTION, subscription1.getAction().asText());
		assertEquals(TEST_ACTION, subscription2.getAction().asText());
		assertEquals(aggregateInfo(), subscription1.getResource());
		assertEquals(handlerInfo(), subscription2.getResource());
		assertNull(subscription1.getEnvironment());
		assertNull(subscription2.getEnvironment());
	}

	@Test
	void when_constructAuthorizationSubscriptionForCommand_with_malformedResource_then_exception()
			throws NoSuchMethodException, SecurityException {
		var payload = new TestCommand(TEST_AGGREGATE_IDENTIFIER);
		var command = new GenericCommandMessage<>(payload);

		var handlerObject1 = new TestAggregate(TEST_AGGREGATE_IDENTIFIER);
		var annotation1    = TestAggregate.class.getDeclaredMethod("handle6", TestCommand.class)
				.getAnnotation(PreHandleEnforce.class);
		var handlerObject2 = new TestCommandHandlingObject();
		var annotation2    = TestAggregate.class.getDeclaredMethod("handle6", TestCommand.class)
				.getAnnotation(PreHandleEnforce.class);

		assertThrows(SpelEvaluationException.class,
				() -> service.constructAuthorizationSubscriptionForCommand(command, handlerObject1, annotation1));
		assertThrows(SpelEvaluationException.class,
				() -> service.constructAuthorizationSubscriptionForCommand(command, handlerObject2, annotation2));
	}

	@Test
	void when_constructAuthorizationSubscriptionForCommand_with_resource_then_subscriptionWithResource()
			throws NoSuchMethodException, SecurityException {
		var payload = new TestCommand(TEST_AGGREGATE_IDENTIFIER);
		var command = new GenericCommandMessage<>(payload);

		var handlerObject1 = new TestAggregate(TEST_AGGREGATE_IDENTIFIER);
		var annotation1    = TestAggregate.class.getDeclaredMethod("handle7", TestCommand.class)
				.getAnnotation(PreHandleEnforce.class);
		var handlerObject2 = new TestCommandHandlingObject();
		var annotation2    = TestAggregate.class.getDeclaredMethod("handle7", TestCommand.class)
				.getAnnotation(PreHandleEnforce.class);

		var subscription1 = service.constructAuthorizationSubscriptionForCommand(command, handlerObject1, annotation1);
		var subscription2 = service.constructAuthorizationSubscriptionForCommand(command, handlerObject2, annotation2);

		assertEquals(TEST_ANONYMOUS, subscription1.getSubject().asText());
		assertEquals(TEST_ANONYMOUS, subscription2.getSubject().asText());
		assertEquals(COMMAND, subscription1.getAction().get(ACTION_TYPE).asText());
		assertEquals(COMMAND, subscription2.getAction().get(ACTION_TYPE).asText());
		assertEquals(TestCommand.class.getName(), subscription1.getAction().get(COMMAND_NAME).asText());
		assertEquals(TestCommand.class.getName(), subscription2.getAction().get(COMMAND_NAME).asText());
		assertEquals(asTree(payload), subscription1.getAction().get(PAYLOAD));
		assertEquals(asTree(payload), subscription2.getAction().get(PAYLOAD));
		assertEquals(TestCommand.class.getName(), subscription1.getAction().get(PAYLOAD_TYPE).asText());
		assertEquals(TestCommand.class.getName(), subscription2.getAction().get(PAYLOAD_TYPE).asText());
		assertEquals(TEST_RESOURCE, subscription1.getResource().asText());
		assertEquals(TEST_RESOURCE, subscription2.getResource().asText());
		assertNull(subscription1.getEnvironment());
		assertNull(subscription2.getEnvironment());
	}

	@Test
	void when_constructAuthorizationSubscriptionForCommand_with_malformedEnvironment_then_exception()
			throws NoSuchMethodException, SecurityException {
		var payload = new TestCommand(TEST_AGGREGATE_IDENTIFIER);
		var command = new GenericCommandMessage<>(payload);

		var handlerObject1 = new TestAggregate(TEST_AGGREGATE_IDENTIFIER);
		var annotation1    = TestAggregate.class.getDeclaredMethod("handle8", TestCommand.class)
				.getAnnotation(PreHandleEnforce.class);
		var handlerObject2 = new TestCommandHandlingObject();
		var annotation2    = TestAggregate.class.getDeclaredMethod("handle8", TestCommand.class)
				.getAnnotation(PreHandleEnforce.class);

		assertThrows(SpelEvaluationException.class,
				() -> service.constructAuthorizationSubscriptionForCommand(command, handlerObject1, annotation1));
		assertThrows(SpelEvaluationException.class,
				() -> service.constructAuthorizationSubscriptionForCommand(command, handlerObject2, annotation2));
	}

	@Test
	void when_constructAuthorizationSubscriptionForCommand_with_environment_then_subscriptionWithEnvironment()
			throws NoSuchMethodException, SecurityException {
		var payload = new TestCommand(TEST_AGGREGATE_IDENTIFIER);
		var command = new GenericCommandMessage<>(payload);

		var handlerObject1 = new TestAggregate(TEST_AGGREGATE_IDENTIFIER);
		var annotation1    = TestAggregate.class.getDeclaredMethod("handle9", TestCommand.class)
				.getAnnotation(PreHandleEnforce.class);
		var handlerObject2 = new TestCommandHandlingObject();
		var annotation2    = TestAggregate.class.getDeclaredMethod("handle9", TestCommand.class)
				.getAnnotation(PreHandleEnforce.class);

		var subscription1 = service.constructAuthorizationSubscriptionForCommand(command, handlerObject1, annotation1);
		var subscription2 = service.constructAuthorizationSubscriptionForCommand(command, handlerObject2, annotation2);

		assertEquals(TEST_ANONYMOUS, subscription1.getSubject().asText());
		assertEquals(TEST_ANONYMOUS, subscription2.getSubject().asText());
		assertEquals(COMMAND, subscription1.getAction().get(ACTION_TYPE).asText());
		assertEquals(COMMAND, subscription2.getAction().get(ACTION_TYPE).asText());
		assertEquals(TestCommand.class.getName(), subscription1.getAction().get(COMMAND_NAME).asText());
		assertEquals(TestCommand.class.getName(), subscription2.getAction().get(COMMAND_NAME).asText());
		assertEquals(asTree(payload), subscription1.getAction().get(PAYLOAD));
		assertEquals(asTree(payload), subscription2.getAction().get(PAYLOAD));
		assertEquals(TestCommand.class.getName(), subscription1.getAction().get(PAYLOAD_TYPE).asText());
		assertEquals(TestCommand.class.getName(), subscription2.getAction().get(PAYLOAD_TYPE).asText());
		assertEquals(aggregateInfo(), subscription1.getResource());
		assertEquals(handlerInfo(), subscription2.getResource());
		assertEquals(TEST_ENVIRONMENT, subscription1.getEnvironment().asText());
		assertEquals(TEST_ENVIRONMENT, subscription2.getEnvironment().asText());
	}

	private static JsonNode aggregateInfo() {
		var node = JsonNodeFactory.instance.objectNode();
		node.put(AGGREGATE_TYPE, TestAggregate.class.getSimpleName());
		node.put(AGGREGATE_IDENTIFIER, TEST_AGGREGATE_IDENTIFIER);
		return node;
	}

	private static JsonNode handlerInfo() {
		var node = JsonNodeFactory.instance.objectNode();
		node.put(AGGREGATE_IDENTIFIER, TEST_AGGREGATE_IDENTIFIER);
		return node;
	}

	private static JsonNode asTree(Object obj) {
		return mapper.valueToTree(obj);
	}
}
