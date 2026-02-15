/*
 * Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.sapl.axon.subscription;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import java.util.Optional;

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.TargetAggregateIdentifier;
import org.axonframework.queryhandling.GenericQueryMessage;
import org.axonframework.queryhandling.GenericSubscriptionQueryMessage;
import org.axonframework.queryhandling.QueryHandler;
import org.axonframework.spring.stereotype.Aggregate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.expression.spel.SpelEvaluationException;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import io.sapl.api.model.ValueJsonMarshaller;
import io.sapl.axon.annotation.PostHandleEnforce;
import io.sapl.axon.annotation.PreHandleEnforce;
import lombok.AllArgsConstructor;

class AuthorizationSubscriptionBuilderServiceTests {
    private static final String ACTION_TYPE          = "actionType";
    private static final String AGGREGATE_IDENTIFIER = "aggregateIdentifier";
    private static final String AGGREGATE_TYPE       = "aggregateType";
    private static final String CLASS_NAME           = "className";
    private static final String COMMAND              = "command";
    private static final String COMMAND_NAME         = "commandName";
    private static final String METADATA             = "metadata";
    private static final String METHOD_NAME          = "methodName";
    private static final String PAYLOAD              = "payload";
    private static final String PAYLOAD_TYPE         = "payloadType";
    private static final String PROJECTION_CLASS     = "projectionClass";
    private static final String QUERY                = "query";
    private static final String QUERY_NAME           = "queryName";
    private static final String QUERY_RESULT         = "queryResult";
    private static final String RESPONSE_TYPE        = "responseType";
    private static final String UPDATE_RESPONSE_TYPE = "updateResponseType";

    private static final String TEST_AGGREGATE_IDENTIFIER = "testAggregateIdentifier";
    private static final String TEST_DOCUMENT_IDENTIFIER  = "testDocumentIdentifier";
    private static final String TEST_ANONYMOUS            = "anonymous";
    private static final String TEST_SUBJECT              = "testSubject";
    private static final String TEST_ACTION               = "testAction";
    private static final String TEST_RESOURCE             = "testResource";
    private static final String TEST_AGGREGATE_TYPE       = "testAggregateType";
    private static final String TEST_ENVIRONMENT          = "testEnvironment";

    @lombok.Value
    private static class TestCommand {
        @TargetAggregateIdentifier
        Object targetAggregateIdentifier;
        Object someOtherField = "someOtherValue";
    }

    @lombok.Value
    private static class NonAnnotatedTestCommand {
        Object nonAnnotatedTargetAggregateIdentifier;
        Object someOtherField = "someOtherValue";
    }

    @lombok.Value
    private static class TestQuery {
        Object targetDocumentIdentifier;
        Object someOtherField = "someOtherValue";
    }

    @lombok.Value
    private static class TestQueryResult {
        Object documentIdentifier;
        Object someOtherField = "someOtherField";
    }

    @lombok.Value
    private static class TestQueryUpdate {
        Object documentIdentifier;
        Object someOtherField = "someOtherField";
    }

    @Aggregate
    @AllArgsConstructor
    private static class TestAggregate {
        @AggregateIdentifier
        Object aggregateIdentifier;

        @CommandHandler
        @PreHandleEnforce
        public void handle1(TestCommand cmd) {
            // NOOP test dummy
        }

        @CommandHandler
        @PreHandleEnforce(subject = "malformed")
        public void handle2(TestCommand cmd) {
            // NOOP test dummy
        }

        @CommandHandler
        @PreHandleEnforce(subject = "'testSubject'")
        public void handle3(TestCommand cmd) {
            // NOOP test dummy
        }

        @CommandHandler
        @PreHandleEnforce(action = "malformed")
        public void handle4(TestCommand cmd) {
            // NOOP test dummy
        }

        @CommandHandler
        @PreHandleEnforce(action = "'testAction'")
        public void handle5(TestCommand cmd) {
            // NOOP test dummy
        }

        @CommandHandler
        @PreHandleEnforce(resource = "malformed")
        public void handle6(TestCommand cmd) {
            // NOOP test dummy
        }

        @CommandHandler
        @PreHandleEnforce(resource = "'testResource'")
        public void handle7(TestCommand cmd) {
            // NOOP test dummy
        }

        @CommandHandler
        @PreHandleEnforce(environment = "malformed")
        public void handle8(TestCommand cmd) {
            // NOOP test dummy
        }

        @CommandHandler
        @PreHandleEnforce(environment = "'testEnvironment'")
        public void handle9(TestCommand cmd) {
            // NOOP test dummy
        }

        @CommandHandler
        @PreHandleEnforce
        public void handle10(NonAnnotatedTestCommand cmd) {
            // NOOP test dummy
        }
    }

    private static class TestCommandHandlingObject {
        @CommandHandler
        @PreHandleEnforce
        public void handle1(TestCommand cmd) {
            // NOOP test dummy
        }

        @CommandHandler
        @PreHandleEnforce(subject = "malformed")
        public void handle2(TestCommand cmd) {
            // NOOP test dummy
        }

        @CommandHandler
        @PreHandleEnforce(subject = "'testSubject'")
        public void handle3(TestCommand cmd) {
            // NOOP test dummy
        }

        @CommandHandler
        @PreHandleEnforce(action = "malformed")
        public void handle4(TestCommand cmd) {
            // NOOP test dummy
        }

        @CommandHandler
        @PreHandleEnforce(action = "'testAction'")
        public void handle5(TestCommand cmd) {
            // NOOP test dummy
        }

        @CommandHandler
        @PreHandleEnforce(resource = "malformed")
        public void handle6(TestCommand cmd) {
            // NOOP test dummy
        }

        @CommandHandler
        @PreHandleEnforce(resource = "'testResource'")
        public void handle7(TestCommand cmd) {
            // NOOP test dummy
        }

        @CommandHandler
        @PreHandleEnforce(environment = "malformed")
        public void handle8(TestCommand cmd) {
            // NOOP test dummy
        }

        @CommandHandler
        @PreHandleEnforce(environment = "'testEnvironment'")
        public void handle9(TestCommand cmd) {
            // NOOP test dummy
        }

        @CommandHandler
        @PreHandleEnforce
        public void handle10(NonAnnotatedTestCommand cmd) {
            // NOOP test dummy
        }
    }

    private static class TestProjection {
        @QueryHandler
        @PreHandleEnforce
        public TestQueryResult handle1(TestQuery query) {
            return new TestQueryResult(query.getTargetDocumentIdentifier());
        }

        @QueryHandler
        @PreHandleEnforce(subject = "malformed")
        public TestQueryResult handle2(TestQuery query) {
            return new TestQueryResult(query.getTargetDocumentIdentifier());
        }

        @QueryHandler
        @PreHandleEnforce(subject = "'testSubject'")
        public TestQueryResult handle3(TestQuery query) {
            return new TestQueryResult(query.getTargetDocumentIdentifier());
        }

        @QueryHandler
        @PreHandleEnforce(action = "malformed")
        public TestQueryResult handle4(TestQuery query) {
            return new TestQueryResult(query.getTargetDocumentIdentifier());
        }

        @QueryHandler
        @PreHandleEnforce(action = "'testAction'")
        public TestQueryResult handle5(TestQuery query) {
            return new TestQueryResult(query.getTargetDocumentIdentifier());
        }

        @QueryHandler
        @PreHandleEnforce(resource = "malformed")
        public TestQueryResult handle6(TestQuery query) {
            return new TestQueryResult(query.getTargetDocumentIdentifier());
        }

        @QueryHandler
        @PreHandleEnforce(resource = "'testResource'")
        public TestQueryResult handle7(TestQuery query) {
            return new TestQueryResult(query.getTargetDocumentIdentifier());
        }

        @QueryHandler
        @PostHandleEnforce(resource = "{ 'testResource':#queryResult }")
        public TestQueryResult handle8(TestQuery query) {
            return new TestQueryResult(query.getTargetDocumentIdentifier());
        }

        @QueryHandler
        @PostHandleEnforce
        public TestQueryResult handle9(TestQuery query) {
            return new TestQueryResult(query.getTargetDocumentIdentifier());
        }

        @QueryHandler
        @PreHandleEnforce
        public TestQueryResult handle10(NonEnclosedTestQuery query) {
            return new TestQueryResult(query.getTargetDocumentIdentifier());
        }

        @QueryHandler
        @PreHandleEnforce(environment = "malformed")
        public TestQueryResult handle11(TestQuery query) {
            return new TestQueryResult(query.getTargetDocumentIdentifier());
        }

        @QueryHandler
        @PreHandleEnforce(environment = "'testEnvironment'")
        public TestQueryResult handle12(TestQuery query) {
            return new TestQueryResult(query.getTargetDocumentIdentifier());
        }
    }

    private static JsonMapper                              mapper;
    private static AuthorizationSubscriptionBuilderService service;

    @BeforeAll
    static void beforeAll() {
        mapper  = JsonMapper.builder().build();
        service = new AuthorizationSubscriptionBuilderService(mapper);
    }

    @Test
    void when_constructAuthorizationSubscriptionForCommand_with_emptyAnnotation_then_anonymousSubscription()
            throws NoSuchMethodException {
        var payload = new TestCommand(TEST_AGGREGATE_IDENTIFIER);
        var command = new GenericCommandMessage<>(payload);

        var handlerObject1 = new TestAggregate(TEST_AGGREGATE_IDENTIFIER);
        var annotation1    = TestAggregate.class.getDeclaredMethod("handle1", TestCommand.class)
                .getAnnotation(PreHandleEnforce.class);
        var handlerObject2 = new TestCommandHandlingObject();
        var annotation2    = TestCommandHandlingObject.class.getDeclaredMethod("handle1", TestCommand.class)
                .getAnnotation(PreHandleEnforce.class);

        var subscription1 = service.constructAuthorizationSubscriptionForCommand(command, handlerObject1, annotation1);
        var subscription2 = service.constructAuthorizationSubscriptionForCommand(command, handlerObject2, annotation2);

        assertEquals(Value.of(TEST_ANONYMOUS), subscription1.subject());
        assertEquals(Value.of(TEST_ANONYMOUS), subscription2.subject());
        var action1 = (ObjectValue) subscription1.action();
        var action2 = (ObjectValue) subscription2.action();
        assertEquals(Value.of(COMMAND), action1.get(ACTION_TYPE));
        assertEquals(Value.of(COMMAND), action2.get(ACTION_TYPE));
        assertEquals(Value.of(TestCommand.class.getName()), action1.get(COMMAND_NAME));
        assertEquals(Value.of(TestCommand.class.getName()), action2.get(COMMAND_NAME));
        assertEquals(asValue(payload), action1.get(PAYLOAD));
        assertEquals(asValue(payload), action2.get(PAYLOAD));
        assertEquals(Value.of(TestCommand.class.getName()), action1.get(PAYLOAD_TYPE));
        assertEquals(Value.of(TestCommand.class.getName()), action2.get(PAYLOAD_TYPE));
        assertEquals(aggregateInfo(), subscription1.resource());
        assertEquals(handlerInfo(), subscription2.resource());
        assertEquals(Value.UNDEFINED, subscription1.environment());
        assertEquals(Value.UNDEFINED, subscription2.environment());
    }

    @Test
    void when_constructAuthorizationSubscriptionForCommand_with_malformedSubject_then_exception()
            throws NoSuchMethodException {
        var payload = new TestCommand(TEST_AGGREGATE_IDENTIFIER);
        var command = new GenericCommandMessage<>(payload);

        var handlerObject1 = new TestAggregate(TEST_AGGREGATE_IDENTIFIER);
        var annotation1    = TestAggregate.class.getDeclaredMethod("handle2", TestCommand.class)
                .getAnnotation(PreHandleEnforce.class);
        var handlerObject2 = new TestCommandHandlingObject();
        var annotation2    = TestCommandHandlingObject.class.getDeclaredMethod("handle2", TestCommand.class)
                .getAnnotation(PreHandleEnforce.class);

        assertThrows(SpelEvaluationException.class,
                () -> service.constructAuthorizationSubscriptionForCommand(command, handlerObject1, annotation1));
        assertThrows(SpelEvaluationException.class,
                () -> service.constructAuthorizationSubscriptionForCommand(command, handlerObject2, annotation2));
    }

    @Test
    void when_constructAuthorizationSubscriptionForCommand_with_subject_then_subscriptionWithSubject()
            throws NoSuchMethodException {
        var payload = new TestCommand(TEST_AGGREGATE_IDENTIFIER);
        var command = new GenericCommandMessage<>(payload);

        var handlerObject1 = new TestAggregate(TEST_AGGREGATE_IDENTIFIER);
        var annotation1    = TestAggregate.class.getDeclaredMethod("handle3", TestCommand.class)
                .getAnnotation(PreHandleEnforce.class);
        var handlerObject2 = new TestCommandHandlingObject();
        var annotation2    = TestCommandHandlingObject.class.getDeclaredMethod("handle3", TestCommand.class)
                .getAnnotation(PreHandleEnforce.class);

        var subscription1 = service.constructAuthorizationSubscriptionForCommand(command, handlerObject1, annotation1);
        var subscription2 = service.constructAuthorizationSubscriptionForCommand(command, handlerObject2, annotation2);

        assertEquals(Value.of(TEST_SUBJECT), subscription1.subject());
        assertEquals(Value.of(TEST_SUBJECT), subscription2.subject());
        var action1 = (ObjectValue) subscription1.action();
        var action2 = (ObjectValue) subscription2.action();
        assertEquals(Value.of(COMMAND), action1.get(ACTION_TYPE));
        assertEquals(Value.of(COMMAND), action2.get(ACTION_TYPE));
        assertEquals(Value.of(TestCommand.class.getName()), action1.get(COMMAND_NAME));
        assertEquals(Value.of(TestCommand.class.getName()), action2.get(COMMAND_NAME));
        assertEquals(asValue(payload), action1.get(PAYLOAD));
        assertEquals(asValue(payload), action2.get(PAYLOAD));
        assertEquals(Value.of(TestCommand.class.getName()), action1.get(PAYLOAD_TYPE));
        assertEquals(Value.of(TestCommand.class.getName()), action2.get(PAYLOAD_TYPE));
        assertEquals(aggregateInfo(), subscription1.resource());
        assertEquals(handlerInfo(), subscription2.resource());
        assertEquals(Value.UNDEFINED, subscription1.environment());
        assertEquals(Value.UNDEFINED, subscription2.environment());
    }

    @Test
    void when_constructAuthorizationSubscriptionForCommand_with_malformedAction_then_exception()
            throws NoSuchMethodException {
        var payload = new TestCommand(TEST_AGGREGATE_IDENTIFIER);
        var command = new GenericCommandMessage<>(payload);

        var handlerObject1 = new TestAggregate(TEST_AGGREGATE_IDENTIFIER);
        var annotation1    = TestAggregate.class.getDeclaredMethod("handle4", TestCommand.class)
                .getAnnotation(PreHandleEnforce.class);
        var handlerObject2 = new TestCommandHandlingObject();
        var annotation2    = TestCommandHandlingObject.class.getDeclaredMethod("handle4", TestCommand.class)
                .getAnnotation(PreHandleEnforce.class);

        assertThrows(SpelEvaluationException.class,
                () -> service.constructAuthorizationSubscriptionForCommand(command, handlerObject1, annotation1));
        assertThrows(SpelEvaluationException.class,
                () -> service.constructAuthorizationSubscriptionForCommand(command, handlerObject2, annotation2));
    }

    @Test
    void when_constructAuthorizationSubscriptionForCommand_with_action_then_subscriptionWithAction()
            throws NoSuchMethodException {
        var payload = new TestCommand(TEST_AGGREGATE_IDENTIFIER);
        var command = new GenericCommandMessage<>(payload);

        var handlerObject1 = new TestAggregate(TEST_AGGREGATE_IDENTIFIER);
        var annotation1    = TestAggregate.class.getDeclaredMethod("handle5", TestCommand.class)
                .getAnnotation(PreHandleEnforce.class);
        var handlerObject2 = new TestCommandHandlingObject();
        var annotation2    = TestCommandHandlingObject.class.getDeclaredMethod("handle5", TestCommand.class)
                .getAnnotation(PreHandleEnforce.class);

        var subscription1 = service.constructAuthorizationSubscriptionForCommand(command, handlerObject1, annotation1);
        var subscription2 = service.constructAuthorizationSubscriptionForCommand(command, handlerObject2, annotation2);

        assertEquals(Value.of(TEST_ANONYMOUS), subscription1.subject());
        assertEquals(Value.of(TEST_ANONYMOUS), subscription2.subject());
        assertEquals(Value.of(TEST_ACTION), subscription1.action());
        assertEquals(Value.of(TEST_ACTION), subscription2.action());
        assertEquals(aggregateInfo(), subscription1.resource());
        assertEquals(handlerInfo(), subscription2.resource());
        assertEquals(Value.UNDEFINED, subscription1.environment());
        assertEquals(Value.UNDEFINED, subscription2.environment());
    }

    @Test
    void when_constructAuthorizationSubscriptionForCommand_with_malformedResource_then_exception()
            throws NoSuchMethodException {
        var payload = new TestCommand(TEST_AGGREGATE_IDENTIFIER);
        var command = new GenericCommandMessage<>(payload);

        var handlerObject1 = new TestAggregate(TEST_AGGREGATE_IDENTIFIER);
        var annotation1    = TestAggregate.class.getDeclaredMethod("handle6", TestCommand.class)
                .getAnnotation(PreHandleEnforce.class);
        var handlerObject2 = new TestCommandHandlingObject();
        var annotation2    = TestCommandHandlingObject.class.getDeclaredMethod("handle6", TestCommand.class)
                .getAnnotation(PreHandleEnforce.class);

        assertThrows(SpelEvaluationException.class,
                () -> service.constructAuthorizationSubscriptionForCommand(command, handlerObject1, annotation1));
        assertThrows(SpelEvaluationException.class,
                () -> service.constructAuthorizationSubscriptionForCommand(command, handlerObject2, annotation2));
    }

    @Test
    void when_constructAuthorizationSubscriptionForCommand_with_resource_then_subscriptionWithResource()
            throws NoSuchMethodException {
        var payload = new TestCommand(TEST_AGGREGATE_IDENTIFIER);
        var command = new GenericCommandMessage<>(payload);

        var handlerObject1 = new TestAggregate(TEST_AGGREGATE_IDENTIFIER);
        var annotation1    = TestAggregate.class.getDeclaredMethod("handle7", TestCommand.class)
                .getAnnotation(PreHandleEnforce.class);
        var handlerObject2 = new TestCommandHandlingObject();
        var annotation2    = TestCommandHandlingObject.class.getDeclaredMethod("handle7", TestCommand.class)
                .getAnnotation(PreHandleEnforce.class);

        var subscription1 = service.constructAuthorizationSubscriptionForCommand(command, handlerObject1, annotation1);
        var subscription2 = service.constructAuthorizationSubscriptionForCommand(command, handlerObject2, annotation2);

        assertEquals(Value.of(TEST_ANONYMOUS), subscription1.subject());
        assertEquals(Value.of(TEST_ANONYMOUS), subscription2.subject());
        var action1 = (ObjectValue) subscription1.action();
        var action2 = (ObjectValue) subscription2.action();
        assertEquals(Value.of(COMMAND), action1.get(ACTION_TYPE));
        assertEquals(Value.of(COMMAND), action2.get(ACTION_TYPE));
        assertEquals(Value.of(TestCommand.class.getName()), action1.get(COMMAND_NAME));
        assertEquals(Value.of(TestCommand.class.getName()), action2.get(COMMAND_NAME));
        assertEquals(asValue(payload), action1.get(PAYLOAD));
        assertEquals(asValue(payload), action2.get(PAYLOAD));
        assertEquals(Value.of(TestCommand.class.getName()), action1.get(PAYLOAD_TYPE));
        assertEquals(Value.of(TestCommand.class.getName()), action2.get(PAYLOAD_TYPE));
        assertEquals(Value.of(TEST_RESOURCE), subscription1.resource());
        assertEquals(Value.of(TEST_RESOURCE), subscription2.resource());
        assertEquals(Value.UNDEFINED, subscription1.environment());
        assertEquals(Value.UNDEFINED, subscription2.environment());
    }

    @Test
    void when_constructAuthorizationSubscriptionForCommand_with_noHandlerObject_then_noHandlerObjectInResource()
            throws NoSuchMethodException {
        var payload = new TestCommand(TEST_AGGREGATE_IDENTIFIER);
        var command = new GenericCommandMessage<>(payload);

        var annotation1 = TestAggregate.class.getDeclaredMethod("handle1", TestCommand.class)
                .getAnnotation(PreHandleEnforce.class);
        var annotation2 = TestCommandHandlingObject.class.getDeclaredMethod("handle1", TestCommand.class)
                .getAnnotation(PreHandleEnforce.class);

        var subscription1 = service.constructAuthorizationSubscriptionForCommand(command, null, annotation1);
        var subscription2 = service.constructAuthorizationSubscriptionForCommand(command, null, annotation2);

        assertEquals(Value.of(TEST_ANONYMOUS), subscription1.subject());
        assertEquals(Value.of(TEST_ANONYMOUS), subscription2.subject());
        var action1 = (ObjectValue) subscription1.action();
        var action2 = (ObjectValue) subscription2.action();
        assertEquals(Value.of(COMMAND), action1.get(ACTION_TYPE));
        assertEquals(Value.of(COMMAND), action2.get(ACTION_TYPE));
        assertEquals(Value.of(TestCommand.class.getName()), action1.get(COMMAND_NAME));
        assertEquals(Value.of(TestCommand.class.getName()), action2.get(COMMAND_NAME));
        assertEquals(asValue(payload), action1.get(PAYLOAD));
        assertEquals(asValue(payload), action2.get(PAYLOAD));
        assertEquals(Value.of(TestCommand.class.getName()), action1.get(PAYLOAD_TYPE));
        assertEquals(Value.of(TestCommand.class.getName()), action2.get(PAYLOAD_TYPE));
        assertEquals(handlerInfo(), subscription1.resource());
        assertEquals(handlerInfo(), subscription2.resource());
        assertEquals(Value.UNDEFINED, subscription1.environment());
        assertEquals(Value.UNDEFINED, subscription2.environment());
    }

    @Test
    void when_constructAuthorizationSubscriptionForCommand_with_providedAggregateType_then_subscriptionWithProvidedAggregateType()
            throws NoSuchMethodException {
        var payload = new TestCommand(TEST_AGGREGATE_IDENTIFIER);
        var command = new GenericCommandMessage<>(payload).withMetaData(Map.of(AGGREGATE_TYPE, TEST_AGGREGATE_TYPE));

        var handlerObject1 = new TestAggregate(TEST_AGGREGATE_IDENTIFIER);
        var annotation1    = TestAggregate.class.getDeclaredMethod("handle1", TestCommand.class)
                .getAnnotation(PreHandleEnforce.class);
        var handlerObject2 = new TestCommandHandlingObject();
        var annotation2    = TestCommandHandlingObject.class.getDeclaredMethod("handle1", TestCommand.class)
                .getAnnotation(PreHandleEnforce.class);

        var subscription1 = service.constructAuthorizationSubscriptionForCommand(command, handlerObject1, annotation1);
        var subscription2 = service.constructAuthorizationSubscriptionForCommand(command, handlerObject2, annotation2);

        assertEquals(Value.of(TEST_ANONYMOUS), subscription1.subject());
        assertEquals(Value.of(TEST_ANONYMOUS), subscription2.subject());
        var action1 = (ObjectValue) subscription1.action();
        var action2 = (ObjectValue) subscription2.action();
        assertEquals(Value.of(COMMAND), action1.get(ACTION_TYPE));
        assertEquals(Value.of(COMMAND), action2.get(ACTION_TYPE));
        assertEquals(Value.of(TestCommand.class.getName()), action1.get(COMMAND_NAME));
        assertEquals(Value.of(TestCommand.class.getName()), action2.get(COMMAND_NAME));
        assertEquals(asValue(payload), action1.get(PAYLOAD));
        assertEquals(asValue(payload), action2.get(PAYLOAD));
        assertEquals(Value.of(TestCommand.class.getName()), action1.get(PAYLOAD_TYPE));
        assertEquals(Value.of(TestCommand.class.getName()), action2.get(PAYLOAD_TYPE));
        assertEquals(info(TEST_AGGREGATE_TYPE), subscription1.resource());
        assertEquals(info(TEST_AGGREGATE_TYPE), subscription2.resource());
        assertEquals(Value.UNDEFINED, subscription1.environment());
        assertEquals(Value.UNDEFINED, subscription2.environment());
    }

    @Test
    void when_constructAuthorizationSubscriptionForCommand_with_commandWithoutTargetIdentifier_then_noIdentifierInResource()
            throws NoSuchMethodException {
        var payload = new NonAnnotatedTestCommand(TEST_AGGREGATE_IDENTIFIER);
        var command = new GenericCommandMessage<>(payload);

        var handlerObject1 = new TestAggregate(TEST_AGGREGATE_IDENTIFIER);
        var annotation1    = TestAggregate.class.getDeclaredMethod("handle10", NonAnnotatedTestCommand.class)
                .getAnnotation(PreHandleEnforce.class);
        var handlerObject2 = new TestCommandHandlingObject();
        var annotation2    = TestCommandHandlingObject.class
                .getDeclaredMethod("handle10", NonAnnotatedTestCommand.class).getAnnotation(PreHandleEnforce.class);

        var subscription1 = service.constructAuthorizationSubscriptionForCommand(command, handlerObject1, annotation1);
        var subscription2 = service.constructAuthorizationSubscriptionForCommand(command, handlerObject2, annotation2);

        assertEquals(Value.of(TEST_ANONYMOUS), subscription1.subject());
        assertEquals(Value.of(TEST_ANONYMOUS), subscription2.subject());
        var action1 = (ObjectValue) subscription1.action();
        var action2 = (ObjectValue) subscription2.action();
        assertEquals(Value.of(COMMAND), action1.get(ACTION_TYPE));
        assertEquals(Value.of(COMMAND), action2.get(ACTION_TYPE));
        assertEquals(Value.of(NonAnnotatedTestCommand.class.getName()), action1.get(COMMAND_NAME));
        assertEquals(Value.of(NonAnnotatedTestCommand.class.getName()), action2.get(COMMAND_NAME));
        assertEquals(asValue(payload), action1.get(PAYLOAD));
        assertEquals(asValue(payload), action2.get(PAYLOAD));
        assertEquals(Value.of(NonAnnotatedTestCommand.class.getName()), action1.get(PAYLOAD_TYPE));
        assertEquals(Value.of(NonAnnotatedTestCommand.class.getName()), action2.get(PAYLOAD_TYPE));
        assertEquals(aggregateInfoWithoutIdentifier(), subscription1.resource());
        assertEquals(handlerInfoWithoutIdentifier(), subscription2.resource());
        assertEquals(Value.UNDEFINED, subscription1.environment());
        assertEquals(Value.UNDEFINED, subscription2.environment());
    }

    @Test
    void when_constructAuthorizationSubscriptionForCommand_with_malformedEnvironment_then_exception()
            throws NoSuchMethodException {
        var payload = new TestCommand(TEST_AGGREGATE_IDENTIFIER);
        var command = new GenericCommandMessage<>(payload);

        var handlerObject1 = new TestAggregate(TEST_AGGREGATE_IDENTIFIER);
        var annotation1    = TestAggregate.class.getDeclaredMethod("handle8", TestCommand.class)
                .getAnnotation(PreHandleEnforce.class);
        var handlerObject2 = new TestCommandHandlingObject();
        var annotation2    = TestCommandHandlingObject.class.getDeclaredMethod("handle8", TestCommand.class)
                .getAnnotation(PreHandleEnforce.class);

        assertThrows(SpelEvaluationException.class,
                () -> service.constructAuthorizationSubscriptionForCommand(command, handlerObject1, annotation1));
        assertThrows(SpelEvaluationException.class,
                () -> service.constructAuthorizationSubscriptionForCommand(command, handlerObject2, annotation2));
    }

    @Test
    void when_constructAuthorizationSubscriptionForCommand_with_environment_then_subscriptionWithEnvironment()
            throws NoSuchMethodException {
        var payload = new TestCommand(TEST_AGGREGATE_IDENTIFIER);
        var command = new GenericCommandMessage<>(payload);

        var handlerObject1 = new TestAggregate(TEST_AGGREGATE_IDENTIFIER);
        var annotation1    = TestAggregate.class.getDeclaredMethod("handle9", TestCommand.class)
                .getAnnotation(PreHandleEnforce.class);
        var handlerObject2 = new TestCommandHandlingObject();
        var annotation2    = TestCommandHandlingObject.class.getDeclaredMethod("handle9", TestCommand.class)
                .getAnnotation(PreHandleEnforce.class);

        var subscription1 = service.constructAuthorizationSubscriptionForCommand(command, handlerObject1, annotation1);
        var subscription2 = service.constructAuthorizationSubscriptionForCommand(command, handlerObject2, annotation2);

        assertEquals(Value.of(TEST_ANONYMOUS), subscription1.subject());
        assertEquals(Value.of(TEST_ANONYMOUS), subscription2.subject());
        var action1 = (ObjectValue) subscription1.action();
        var action2 = (ObjectValue) subscription2.action();
        assertEquals(Value.of(COMMAND), action1.get(ACTION_TYPE));
        assertEquals(Value.of(COMMAND), action2.get(ACTION_TYPE));
        assertEquals(Value.of(TestCommand.class.getName()), action1.get(COMMAND_NAME));
        assertEquals(Value.of(TestCommand.class.getName()), action2.get(COMMAND_NAME));
        assertEquals(asValue(payload), action1.get(PAYLOAD));
        assertEquals(asValue(payload), action2.get(PAYLOAD));
        assertEquals(Value.of(TestCommand.class.getName()), action1.get(PAYLOAD_TYPE));
        assertEquals(Value.of(TestCommand.class.getName()), action2.get(PAYLOAD_TYPE));
        assertEquals(aggregateInfo(), subscription1.resource());
        assertEquals(handlerInfo(), subscription2.resource());
        assertEquals(Value.of(TEST_ENVIRONMENT), subscription1.environment());
        assertEquals(Value.of(TEST_ENVIRONMENT), subscription2.environment());
    }

    @Test
    void when_constructAuthorizationSubscriptionForCommand_with_metaData_then_subscriptionWithMetaData()
            throws NoSuchMethodException {
        var payload  = new TestCommand(TEST_AGGREGATE_IDENTIFIER);
        var metaData = Map.of("metaDataKey1", "metaDataValue1", "metaDataKey2", "metaDataValue2");
        var command  = new GenericCommandMessage<>(payload).andMetaData(metaData);

        var handlerObject1 = new TestAggregate(TEST_AGGREGATE_IDENTIFIER);
        var annotation1    = TestAggregate.class.getDeclaredMethod("handle1", TestCommand.class)
                .getAnnotation(PreHandleEnforce.class);
        var handlerObject2 = new TestCommandHandlingObject();
        var annotation2    = TestCommandHandlingObject.class.getDeclaredMethod("handle1", TestCommand.class)
                .getAnnotation(PreHandleEnforce.class);

        var subscription1 = service.constructAuthorizationSubscriptionForCommand(command, handlerObject1, annotation1);
        var subscription2 = service.constructAuthorizationSubscriptionForCommand(command, handlerObject2, annotation2);

        assertEquals(Value.of(TEST_ANONYMOUS), subscription1.subject());
        assertEquals(Value.of(TEST_ANONYMOUS), subscription2.subject());
        var action1 = (ObjectValue) subscription1.action();
        var action2 = (ObjectValue) subscription2.action();
        assertEquals(Value.of(COMMAND), action1.get(ACTION_TYPE));
        assertEquals(Value.of(COMMAND), action2.get(ACTION_TYPE));
        assertEquals(Value.of(TestCommand.class.getName()), action1.get(COMMAND_NAME));
        assertEquals(Value.of(TestCommand.class.getName()), action2.get(COMMAND_NAME));
        assertEquals(asValue(payload), action1.get(PAYLOAD));
        assertEquals(asValue(payload), action2.get(PAYLOAD));
        assertEquals(Value.of(TestCommand.class.getName()), action1.get(PAYLOAD_TYPE));
        assertEquals(Value.of(TestCommand.class.getName()), action2.get(PAYLOAD_TYPE));
        assertEquals(asValue(metaData), action1.get(METADATA));
        assertEquals(asValue(metaData), action2.get(METADATA));
        assertEquals(aggregateInfo(), subscription1.resource());
        assertEquals(handlerInfo(), subscription2.resource());
        assertEquals(Value.UNDEFINED, subscription1.environment());
        assertEquals(Value.UNDEFINED, subscription2.environment());
    }

    @Test
    void when_constructAuthorizationSubscriptionForQuery_with_emptyAnnotation_then_anonymousSubscription()
            throws NoSuchMethodException {
        var payload = new TestQuery(TEST_DOCUMENT_IDENTIFIER);
        var query   = new GenericQueryMessage<>(payload, ResponseTypes.instanceOf(TestQueryResult.class));

        var handlerMethod = TestProjection.class.getDeclaredMethod("handle1", TestQuery.class);
        var annotation    = handlerMethod.getAnnotation(PreHandleEnforce.class);
        var queryResult   = Optional.empty();

        var subscription = service.constructAuthorizationSubscriptionForQuery(query, annotation, handlerMethod,
                queryResult);

        assertEquals(Value.of(TEST_ANONYMOUS), subscription.subject());
        var action = (ObjectValue) subscription.action();
        assertEquals(Value.of(QUERY), action.get(ACTION_TYPE));
        assertEquals(Value.of(TestQuery.class.getName()), action.get(QUERY_NAME));
        assertEquals(asValue(payload), action.get(PAYLOAD));
        assertEquals(Value.of(TestQuery.class.getName()), action.get(PAYLOAD_TYPE));
        assertEquals(projectionInfo("handle1"), subscription.resource());
        assertEquals(Value.UNDEFINED, subscription.environment());
    }

    @Test
    void when_constructAuthorizationSubscriptionForQuery_with_malformedSubject_then_exception()
            throws NoSuchMethodException {
        var payload = new TestQuery(TEST_DOCUMENT_IDENTIFIER);
        var query   = new GenericQueryMessage<>(payload, ResponseTypes.instanceOf(TestQueryResult.class));

        var handlerMethod = TestProjection.class.getDeclaredMethod("handle2", TestQuery.class);
        var annotation    = handlerMethod.getAnnotation(PreHandleEnforce.class);
        var queryResult   = Optional.empty();

        assertThrows(SpelEvaluationException.class, () -> service.constructAuthorizationSubscriptionForQuery(query,
                annotation, handlerMethod, queryResult));
    }

    @Test
    void when_constructAuthorizationSubscriptionForQuery_with_subject_then_subscriptionWithSubject()
            throws NoSuchMethodException {
        var payload = new TestQuery(TEST_DOCUMENT_IDENTIFIER);
        var query   = new GenericQueryMessage<>(payload, ResponseTypes.instanceOf(TestQueryResult.class));

        var handlerMethod = TestProjection.class.getDeclaredMethod("handle3", TestQuery.class);
        var annotation    = handlerMethod.getAnnotation(PreHandleEnforce.class);
        var queryResult   = Optional.empty();

        var subscription = service.constructAuthorizationSubscriptionForQuery(query, annotation, handlerMethod,
                queryResult);

        assertEquals(Value.of(TEST_SUBJECT), subscription.subject());
        var action = (ObjectValue) subscription.action();
        assertEquals(Value.of(QUERY), action.get(ACTION_TYPE));
        assertEquals(Value.of(TestQuery.class.getName()), action.get(QUERY_NAME));
        assertEquals(asValue(payload), action.get(PAYLOAD));
        assertEquals(Value.of(TestQuery.class.getName()), action.get(PAYLOAD_TYPE));
        assertEquals(projectionInfo("handle3"), subscription.resource());
        assertEquals(Value.UNDEFINED, subscription.environment());
    }

    @Test
    void when_constructAuthorizationSubscriptionForQuery_with_malformedAction_then_exception()
            throws NoSuchMethodException {
        var payload = new TestQuery(TEST_DOCUMENT_IDENTIFIER);
        var query   = new GenericQueryMessage<>(payload, ResponseTypes.instanceOf(TestQueryResult.class));

        var handlerMethod = TestProjection.class.getDeclaredMethod("handle4", TestQuery.class);
        var annotation    = handlerMethod.getAnnotation(PreHandleEnforce.class);
        var queryResult   = Optional.empty();

        assertThrows(SpelEvaluationException.class, () -> service.constructAuthorizationSubscriptionForQuery(query,
                annotation, handlerMethod, queryResult));
    }

    @Test
    void when_constructAuthorizationSubscriptionForQuery_with_action_then_subscriptionWithAction()
            throws NoSuchMethodException {
        var payload = new TestQuery(TEST_DOCUMENT_IDENTIFIER);
        var query   = new GenericQueryMessage<>(payload, ResponseTypes.instanceOf(TestQueryResult.class));

        var handlerMethod = TestProjection.class.getDeclaredMethod("handle5", TestQuery.class);
        var annotation    = handlerMethod.getAnnotation(PreHandleEnforce.class);
        var queryResult   = Optional.empty();

        var subscription = service.constructAuthorizationSubscriptionForQuery(query, annotation, handlerMethod,
                queryResult);

        assertEquals(Value.of(TEST_ANONYMOUS), subscription.subject());
        assertEquals(Value.of(TEST_ACTION), subscription.action());
        assertEquals(projectionInfo("handle5"), subscription.resource());
        assertEquals(Value.UNDEFINED, subscription.environment());
    }

    @Test
    void when_constructAuthorizationSubscriptionForQuery_with_malformedResource_then_exception()
            throws NoSuchMethodException {
        var payload = new TestQuery(TEST_DOCUMENT_IDENTIFIER);
        var query   = new GenericQueryMessage<>(payload, ResponseTypes.instanceOf(TestQueryResult.class));

        var handlerMethod = TestProjection.class.getDeclaredMethod("handle6", TestQuery.class);
        var annotation    = handlerMethod.getAnnotation(PreHandleEnforce.class);
        var queryResult   = Optional.empty();

        assertThrows(SpelEvaluationException.class, () -> service.constructAuthorizationSubscriptionForQuery(query,
                annotation, handlerMethod, queryResult));
    }

    @Test
    void when_constructAuthorizationSubscriptionForQuery_with_resource_then_subscriptionWithResource()
            throws NoSuchMethodException {
        var payload = new TestQuery(TEST_DOCUMENT_IDENTIFIER);
        var query   = new GenericQueryMessage<>(payload, ResponseTypes.instanceOf(TestQueryResult.class));

        var handlerMethod = TestProjection.class.getDeclaredMethod("handle7", TestQuery.class);
        var annotation    = handlerMethod.getAnnotation(PreHandleEnforce.class);
        var queryResult   = Optional.empty();

        var subscription = service.constructAuthorizationSubscriptionForQuery(query, annotation, handlerMethod,
                queryResult);

        assertEquals(Value.of(TEST_ANONYMOUS), subscription.subject());
        var action = (ObjectValue) subscription.action();
        assertEquals(Value.of(QUERY), action.get(ACTION_TYPE));
        assertEquals(Value.of(TestQuery.class.getName()), action.get(QUERY_NAME));
        assertEquals(asValue(payload), action.get(PAYLOAD));
        assertEquals(Value.of(TestQuery.class.getName()), action.get(PAYLOAD_TYPE));
        assertEquals(Value.of(TEST_RESOURCE), subscription.resource());
        assertEquals(Value.UNDEFINED, subscription.environment());
    }

    @Test
    void when_constructAuthorizationSubscriptionForQuery_with_postEnforce_and_resource_then_postEnforceSubscriptionWithResource()
            throws NoSuchMethodException {
        var payload = new TestQuery(TEST_DOCUMENT_IDENTIFIER);
        var query   = new GenericQueryMessage<>(payload, ResponseTypes.instanceOf(TestQueryResult.class));

        var handlerMethod = TestProjection.class.getDeclaredMethod("handle8", TestQuery.class);
        var annotation    = handlerMethod.getAnnotation(PostHandleEnforce.class);
        var queryResult   = Optional.of(new TestQueryResult(TEST_DOCUMENT_IDENTIFIER));

        var subscription = service.constructAuthorizationSubscriptionForQuery(query, annotation, handlerMethod,
                queryResult);

        assertEquals(Value.of(TEST_ANONYMOUS), subscription.subject());
        var action = (ObjectValue) subscription.action();
        assertEquals(Value.of(QUERY), action.get(ACTION_TYPE));
        assertEquals(Value.of(TestQuery.class.getName()), action.get(QUERY_NAME));
        assertEquals(asValue(payload), action.get(PAYLOAD));
        assertEquals(Value.of(TestQuery.class.getName()), action.get(PAYLOAD_TYPE));
        assertEquals(postEnforceResourceNode(), subscription.resource());
        assertEquals(Value.UNDEFINED, subscription.environment());
    }

    @Test
    void when_constructAuthorizationSubscriptionForQuery_with_postEnforce_then_postEnforceSubscription()
            throws NoSuchMethodException {
        var payload = new TestQuery(TEST_DOCUMENT_IDENTIFIER);
        var query   = new GenericQueryMessage<>(payload, ResponseTypes.instanceOf(TestQueryResult.class));

        var handlerMethod = TestProjection.class.getDeclaredMethod("handle9", TestQuery.class);
        var annotation    = handlerMethod.getAnnotation(PostHandleEnforce.class);
        var queryResult   = Optional.of(new TestQueryResult(TEST_DOCUMENT_IDENTIFIER));

        var subscription = service.constructAuthorizationSubscriptionForQuery(query, annotation, handlerMethod,
                queryResult);

        assertEquals(Value.of(TEST_ANONYMOUS), subscription.subject());
        var action = (ObjectValue) subscription.action();
        assertEquals(Value.of(QUERY), action.get(ACTION_TYPE));
        assertEquals(Value.of(TestQuery.class.getName()), action.get(QUERY_NAME));
        assertEquals(asValue(payload), action.get(PAYLOAD));
        assertEquals(Value.of(TestQuery.class.getName()), action.get(PAYLOAD_TYPE));
        assertEquals(projectionInfo("handle9", queryResult.get()), subscription.resource());
        assertEquals(Value.UNDEFINED, subscription.environment());
    }

    @Test
    void when_constructAuthorizationSubscriptionForQuery_with_subscriptionQuery_then_subscriptionWithUpdateType()
            throws NoSuchMethodException {
        var payload = new TestQuery(TEST_DOCUMENT_IDENTIFIER);
        var query   = new GenericSubscriptionQueryMessage<>(payload, ResponseTypes.instanceOf(TestQueryResult.class),
                ResponseTypes.instanceOf(TestQueryUpdate.class));
        query = query.andMetaData(Map.of(UPDATE_RESPONSE_TYPE, query.getUpdateResponseType()));

        var handlerMethod = TestProjection.class.getDeclaredMethod("handle1", TestQuery.class);
        var annotation    = handlerMethod.getAnnotation(PreHandleEnforce.class);
        var queryResult   = Optional.empty();

        var subscription = service.constructAuthorizationSubscriptionForQuery(query, annotation, handlerMethod,
                queryResult);

        assertEquals(Value.of(TEST_ANONYMOUS), subscription.subject());
        var action = (ObjectValue) subscription.action();
        assertEquals(Value.of(QUERY), action.get(ACTION_TYPE));
        assertEquals(Value.of(TestQuery.class.getName()), action.get(QUERY_NAME));
        assertEquals(asValue(payload), action.get(PAYLOAD));
        assertEquals(Value.of(TestQuery.class.getName()), action.get(PAYLOAD_TYPE));
        assertEquals(projectionInfoWithUpdateType("handle1"), subscription.resource());
        assertEquals(Value.UNDEFINED, subscription.environment());
    }

    @Test
    void when_constructAuthorizationSubscriptionForQuery_with_nonEnclosedPayload_then_subscriptionWithoutClassName()
            throws NoSuchMethodException {
        var payload = new NonEnclosedTestQuery(TEST_DOCUMENT_IDENTIFIER);
        var query   = new GenericQueryMessage<>(payload, ResponseTypes.instanceOf(TestQueryResult.class));

        var handlerMethod = TestProjection.class.getDeclaredMethod("handle10", NonEnclosedTestQuery.class);
        var annotation    = handlerMethod.getAnnotation(PreHandleEnforce.class);
        var queryResult   = Optional.empty();

        var subscription = service.constructAuthorizationSubscriptionForQuery(query, annotation, handlerMethod,
                queryResult);

        assertEquals(Value.of(TEST_ANONYMOUS), subscription.subject());
        var action = (ObjectValue) subscription.action();
        assertEquals(Value.of(QUERY), action.get(ACTION_TYPE));
        assertEquals(Value.of(NonEnclosedTestQuery.class.getName()), action.get(QUERY_NAME));
        assertEquals(asValue(payload), action.get(PAYLOAD));
        assertEquals(Value.of(NonEnclosedTestQuery.class.getName()), action.get(PAYLOAD_TYPE));
        assertEquals(projectionInfoOnNonEnclosedPayload("handle10"), subscription.resource());
        assertEquals(Value.UNDEFINED, subscription.environment());
    }

    @Test
    void when_constructAuthorizationSubscriptionForQuery_with_malformedEnvironment_then_exception()
            throws NoSuchMethodException {
        var payload = new TestQuery(TEST_DOCUMENT_IDENTIFIER);
        var query   = new GenericQueryMessage<>(payload, ResponseTypes.instanceOf(TestQueryResult.class));

        var handlerMethod = TestProjection.class.getDeclaredMethod("handle11", TestQuery.class);
        var annotation    = handlerMethod.getAnnotation(PreHandleEnforce.class);
        var queryResult   = Optional.empty();

        assertThrows(SpelEvaluationException.class, () -> service.constructAuthorizationSubscriptionForQuery(query,
                annotation, handlerMethod, queryResult));
    }

    @Test
    void when_constructAuthorizationSubscriptionForQuery_with_environment_then_subscriptionWithSubject()
            throws NoSuchMethodException {
        var payload = new TestQuery(TEST_DOCUMENT_IDENTIFIER);
        var query   = new GenericQueryMessage<>(payload, ResponseTypes.instanceOf(TestQueryResult.class));

        var handlerMethod = TestProjection.class.getDeclaredMethod("handle12", TestQuery.class);
        var annotation    = handlerMethod.getAnnotation(PreHandleEnforce.class);
        var queryResult   = Optional.empty();

        var subscription = service.constructAuthorizationSubscriptionForQuery(query, annotation, handlerMethod,
                queryResult);

        assertEquals(Value.of(TEST_ANONYMOUS), subscription.subject());
        var action = (ObjectValue) subscription.action();
        assertEquals(Value.of(QUERY), action.get(ACTION_TYPE));
        assertEquals(Value.of(TestQuery.class.getName()), action.get(QUERY_NAME));
        assertEquals(asValue(payload), action.get(PAYLOAD));
        assertEquals(Value.of(TestQuery.class.getName()), action.get(PAYLOAD_TYPE));
        assertEquals(projectionInfo("handle12"), subscription.resource());
        assertEquals(Value.of(TEST_ENVIRONMENT), subscription.environment());
    }

    private static Value aggregateInfo() {
        var node = JsonNodeFactory.instance.objectNode();
        node.put(AGGREGATE_TYPE, TestAggregate.class.getSimpleName());
        node.put(AGGREGATE_IDENTIFIER, TEST_AGGREGATE_IDENTIFIER);
        return ValueJsonMarshaller.fromJsonNode(node);
    }

    private static Value info(String aggregateType) {
        var node = JsonNodeFactory.instance.objectNode();
        node.put(AGGREGATE_TYPE, aggregateType);
        node.put(AGGREGATE_IDENTIFIER, TEST_AGGREGATE_IDENTIFIER);
        return ValueJsonMarshaller.fromJsonNode(node);
    }

    private static Value aggregateInfoWithoutIdentifier() {
        var node = JsonNodeFactory.instance.objectNode();
        node.put(AGGREGATE_TYPE, TestAggregate.class.getSimpleName());
        return ValueJsonMarshaller.fromJsonNode(node);
    }

    private static Value handlerInfo() {
        var node = JsonNodeFactory.instance.objectNode();
        node.put(AGGREGATE_IDENTIFIER, TEST_AGGREGATE_IDENTIFIER);
        return ValueJsonMarshaller.fromJsonNode(node);
    }

    private static Value handlerInfoWithoutIdentifier() {
        return ValueJsonMarshaller.fromJsonNode(JsonNodeFactory.instance.objectNode());
    }

    private static Value projectionInfo(String methodName) {
        var node = JsonNodeFactory.instance.objectNode();
        node.put(PROJECTION_CLASS, TestProjection.class.getSimpleName());
        node.put(METHOD_NAME, methodName);
        node.put(RESPONSE_TYPE, TestQueryResult.class.getSimpleName());
        node.put(QUERY_NAME, TestQuery.class.getSimpleName());
        node.put(CLASS_NAME, AuthorizationSubscriptionBuilderServiceTests.class.getSimpleName());
        return ValueJsonMarshaller.fromJsonNode(node);
    }

    private static Value projectionInfo(String methodName, Object queryResult) {
        var node = JsonNodeFactory.instance.objectNode();
        node.put(PROJECTION_CLASS, TestProjection.class.getSimpleName());
        node.put(METHOD_NAME, methodName);
        node.put(RESPONSE_TYPE, TestQueryResult.class.getSimpleName());
        node.put(QUERY_NAME, TestQuery.class.getSimpleName());
        node.set(QUERY_RESULT, mapper.valueToTree(queryResult));
        node.put(CLASS_NAME, AuthorizationSubscriptionBuilderServiceTests.class.getSimpleName());
        return ValueJsonMarshaller.fromJsonNode(node);
    }

    private static Value projectionInfoWithUpdateType(String methodName) {
        var node = JsonNodeFactory.instance.objectNode();
        node.put(PROJECTION_CLASS, TestProjection.class.getSimpleName());
        node.put(METHOD_NAME, methodName);
        node.put(RESPONSE_TYPE, TestQueryResult.class.getSimpleName());
        node.put(QUERY_NAME, TestQuery.class.getSimpleName());
        node.put(QUERY_RESULT, TestQueryUpdate.class.getSimpleName());
        node.put(CLASS_NAME, AuthorizationSubscriptionBuilderServiceTests.class.getSimpleName());
        return ValueJsonMarshaller.fromJsonNode(node);
    }

    private static Value projectionInfoOnNonEnclosedPayload(String methodName) {
        var node = JsonNodeFactory.instance.objectNode();
        node.put(PROJECTION_CLASS, TestProjection.class.getSimpleName());
        node.put(METHOD_NAME, methodName);
        node.put(RESPONSE_TYPE, TestQueryResult.class.getSimpleName());
        node.put(QUERY_NAME, NonEnclosedTestQuery.class.getSimpleName());
        return ValueJsonMarshaller.fromJsonNode(node);
    }

    private static Value postEnforceResourceNode() {
        var node = JsonNodeFactory.instance.objectNode();
        node.set(TEST_RESOURCE, mapper.valueToTree(new TestQueryResult(TEST_DOCUMENT_IDENTIFIER)));
        return ValueJsonMarshaller.fromJsonNode(node);
    }

    private static Value asValue(Object obj) {
        return ValueJsonMarshaller.fromJsonNode(mapper.valueToTree(obj));
    }
}
