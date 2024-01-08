/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import java.lang.annotation.Annotation;
import java.lang.reflect.Executable;
import java.util.Map;
import java.util.Optional;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.common.ReflectionUtils;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.modelling.command.TargetAggregateIdentifier;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.spring.stereotype.Aggregate;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.SpelEvaluationException;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.axon.annotation.PreHandleEnforce;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

/**
 *
 * The AuthorizationSubscriptionBuilderService Object offers methods to
 * construct the AuthorizationSubscription for Query Messages and for Command
 * Messages.
 *
 * @author Dominic Heutelbeck
 * @since 2.1.0
 */
@Slf4j
@RequiredArgsConstructor
public class AuthorizationSubscriptionBuilderService {
    private static final String ACTION               = "action";
    private static final String ACTION_TYPE          = "actionType";
    private static final String AGGREGATE_IDENTIFIER = "aggregateIdentifier";
    private static final String AGGREGATE_TYPE       = "aggregateType";
    private static final String ANONYMOUS            = "\"anonymous\"";
    private static final String CLASS_NAME           = "className";
    private static final String COMMAND              = "command";
    private static final String COMMAND_NAME         = "commandName";
    private static final String ENVIRONMENT          = "environment";
    private static final String EXECUTABLE           = "executable";
    private static final String MESSAGE              = "message";
    private static final String METADATA             = "metadata";
    private static final String METHOD_NAME          = "methodName";
    private static final String PAYLOAD              = "payload";
    private static final String PAYLOAD_TYPE         = "payloadType";
    private static final String PROJECTION_CLASS     = "projectionClass";
    private static final String QUERY                = "query";
    private static final String QUERY_NAME           = "queryName";
    private static final String QUERY_RESULT         = "queryResult";
    private static final String RESOURCE             = "resource";
    private static final String RESPONSE_TYPE        = "responseType";
    private static final String SUBJECT              = "subject";
    private static final String UPDATE_RESPONSE_TYPE = "updateResponseType";

    private static final JsonNodeFactory  JSON        = JsonNodeFactory.instance;
    private static final ExpressionParser SPEL_PARSER = new SpelExpressionParser();

    private final ObjectMapper mapper;

    /**
     * Executed to get the AuthorizationSubscription for a CommandMessage.
     *
     * @param command          The CommandMessage to be handled.
     * @param handlerObject    The instance of the object with the @CommandHandler
     *                         to be executed.
     * @param methodAnnotation The annotation.
     * @return the AuthorizationSubscription for the Command.
     */
    public AuthorizationSubscription constructAuthorizationSubscriptionForCommand(CommandMessage<?> command,
            Object handlerObject, PreHandleEnforce methodAnnotation) {

        var annotationAttributeValues = AnnotationUtils.getAnnotationAttributes(methodAnnotation);

        var subject     = constructSubjectNode(command, annotationAttributeValues, COMMAND);
        var action      = constructCommandHandlingActionNode(command, annotationAttributeValues, handlerObject);
        var resource    = constructCommandHandlingResourceNode(command, handlerObject, annotationAttributeValues);
        var environment = constructEnvironmentNode(annotationAttributeValues);

        return AuthorizationSubscription.of(subject, action, resource, environment);
    }

    /**
     * Executed to get the AuthorizationSubscription for a QueryMessage.
     *
     * @param queryMessage The QueryMessage to be handled.
     * @param annotation   The SAPL annotation of the @QueryHandler method to be
     *                     executed.
     * @param executable   The Executable representing the @QueryHandler method to
     *                     be executed.
     * @param queryResult  An Optional query result. If the client class is dealing
     *                     with a @PostHandleEnforce annotation, the Optional should
     *                     contain the object returned by the @QueryHandler method.
     *                     Else, leave the Optional empty.
     * @return the AuthorizationSubscription for the Query
     */
    public AuthorizationSubscription constructAuthorizationSubscriptionForQuery(QueryMessage<?, ?> queryMessage,
            Annotation annotation, Executable executable, Optional<?> queryResult) {

        var annotationAttributeValues = AnnotationUtils.getAnnotationAttributes(annotation);

        var subject     = constructSubjectNode(queryMessage, annotationAttributeValues, QUERY);
        var action      = constructQueryHandlingActionNode(queryMessage, annotationAttributeValues, executable);
        var resource    = constructQueryHandlingResourceNode(queryMessage, annotationAttributeValues, executable,
                queryResult);
        var environment = constructEnvironmentNode(annotationAttributeValues);

        return AuthorizationSubscription.of(subject, action, resource, environment);
    }

    private JsonNode constructResourceNode(QueryMessage<?, ?> message, Executable executable, Optional<?> queryResult) {
        var resourceNode = JSON.objectNode();
        var responseType = message.getResponseType().getExpectedResponseType().getSimpleName();

        resourceNode.put(PROJECTION_CLASS, executable.getDeclaringClass().getSimpleName());
        resourceNode.put(METHOD_NAME, executable.getName());
        resourceNode.put(RESPONSE_TYPE, responseType);
        resourceNode.put(QUERY_NAME, message.getPayloadType().getSimpleName());

        var updateResponseType = Optional
                .<ResponseType<?>>ofNullable((ResponseType<?>) message.getMetaData().get(UPDATE_RESPONSE_TYPE));

        updateResponseType.ifPresent(updResponseType -> resourceNode.put(QUERY_RESULT,
                updResponseType.getExpectedResponseType().getSimpleName()));

        queryResult.ifPresent(result -> resourceNode.set(QUERY_RESULT, mapper.valueToTree(result)));

        if (message.getPayloadType().getEnclosingClass() != null)
            resourceNode.put(CLASS_NAME, message.getPayloadType().getEnclosingClass().getSimpleName());

        return resourceNode;
    }

    private <T> JsonNode constructResourceWithAggregateInformation(Message<?> message, T aggregate) {
        var json          = JSON.objectNode();
        var aggregateType = message.getMetaData().get(AGGREGATE_TYPE);
        if (aggregateType != null) {
            json.set(AGGREGATE_TYPE, mapper.valueToTree(aggregateType));
        } else if (aggregate != null) {
            if (aggregate.getClass().isAnnotationPresent(Aggregate.class))
                json.put(AGGREGATE_TYPE, aggregate.getClass().getSimpleName());
        }

        aggregateIdFromMessage(message).ifPresent(id -> json.set(AGGREGATE_IDENTIFIER, mapper.valueToTree(id)));

        return json;
    }

    private Optional<Object> aggregateIdFromMessage(Message<?> message) {
        var fields = message.getPayloadType().getDeclaredFields();
        for (var field : fields) {
            if (field.isAnnotationPresent(TargetAggregateIdentifier.class)) {
                try {
                    return Optional.ofNullable(ReflectionUtils.getFieldValue(field, message.getPayload()));
                } catch (IllegalStateException e) {
                    log.error("Could not get TargetAggregateIdentifier from message. {}", e.getLocalizedMessage());
                    return Optional.empty();
                }
            }
        }
        return Optional.empty();
    }

    private JsonNode messageToJson(Message<?> message) {
        var json = JSON.objectNode();
        if (message instanceof QueryMessage) {
            json.set(ACTION_TYPE, JSON.textNode(QUERY));
            json.set(QUERY_NAME, JSON.textNode(((QueryMessage<?, ?>) message).getQueryName()));
        } else if (message instanceof CommandMessage) {
            json.set(ACTION_TYPE, JSON.textNode(COMMAND));
            json.set(COMMAND_NAME, JSON.textNode(((CommandMessage<?>) message).getCommandName()));
        }

        json.set(PAYLOAD, mapper.valueToTree(message.getPayload()));
        json.set(PAYLOAD_TYPE, mapper.valueToTree(message.getPayloadType()));

        if (!message.getMetaData().isEmpty()) {
            json.set(METADATA, mapper.valueToTree(message.getMetaData()));
        }
        return json;
    }

    private JsonNode constructCommandHandlingActionNode(CommandMessage<?> message,
            Map<String, Object> annotationAttributes, Object commandHandlingObject) {
        var spelExpression = annotationAttributes.get(ACTION);
        if (expressionNotSet(spelExpression))
            return messageToJson(message);

        var spelEvaluationContext = new StandardEvaluationContext(commandHandlingObject);
        spelEvaluationContext.setVariable(MESSAGE, message);
        spelEvaluationContext.setVariable(COMMAND, message.getPayload());
        spelEvaluationContext.setVariable(METADATA, message.getMetaData());
        return evaluateSpel((String) spelExpression, spelEvaluationContext);

    }

    private Object constructEnvironmentNode(Map<String, Object> annotationAttributes) {
        var spelExpression = annotationAttributes.get(ENVIRONMENT);
        if (expressionNotSet(spelExpression))
            return null;

        return evaluateSpel((String) spelExpression, new StandardEvaluationContext());
    }

    private JsonNode constructQueryHandlingActionNode(Message<?> message, Map<String, Object> annotationAttributes,
            Executable executable) {
        var spelExpression = annotationAttributes.get(ACTION);
        if (expressionNotSet(spelExpression))
            return messageToJson(message);

        var spelEvaluationContext = new StandardEvaluationContext();
        spelEvaluationContext.setVariable(MESSAGE, message);
        spelEvaluationContext.setVariable(QUERY, message.getPayload());
        spelEvaluationContext.setVariable(METADATA, message.getMetaData());
        spelEvaluationContext.setVariable(EXECUTABLE, executable);
        return evaluateSpel((String) spelExpression, spelEvaluationContext);
    }

    private JsonNode constructQueryHandlingResourceNode(QueryMessage<?, ?> message,
            Map<String, Object> annotationAttributes, Executable executable, Optional<?> queryResult) {
        var spelExpression = annotationAttributes.get(RESOURCE);
        if (expressionNotSet(spelExpression))
            return constructResourceNode(message, executable, queryResult);

        var spelEvaluationContext = new StandardEvaluationContext();
        spelEvaluationContext.setVariable(MESSAGE, message);
        spelEvaluationContext.setVariable(QUERY, message.getPayload());
        spelEvaluationContext.setVariable(METADATA, message.getMetaData());
        spelEvaluationContext.setVariable(EXECUTABLE, executable);
        queryResult.ifPresent(result -> spelEvaluationContext.setVariable(QUERY_RESULT, result));
        return evaluateSpel((String) spelExpression, spelEvaluationContext);
    }

    private <T> JsonNode constructCommandHandlingResourceNode(Message<?> message, T aggregate,
            Map<String, Object> annotationAttributes) {
        var spelExpression = annotationAttributes.get(RESOURCE);
        if (expressionNotSet(spelExpression)) {
            return constructResourceWithAggregateInformation(message, aggregate);
        }

        var spelEvaluationContext = new StandardEvaluationContext(aggregate);
        spelEvaluationContext.setVariable(MESSAGE, message);
        spelEvaluationContext.setVariable(COMMAND, message.getPayload());
        spelEvaluationContext.setVariable(METADATA, message.getMetaData());
        return evaluateSpel((String) spelExpression, spelEvaluationContext);
    }

    @SneakyThrows
    private JsonNode constructSubjectNode(Message<?> message, Map<String, Object> annotationAttributes,
            String payloadId) {
        var subject        = message.getMetaData().getOrDefault(SUBJECT, ANONYMOUS);
        var spelExpression = annotationAttributes.get(SUBJECT);

        if (expressionNotSet(spelExpression))
            return mapper.readTree((String) subject);

        var spelEvaluationContext = new StandardEvaluationContext();
        spelEvaluationContext.setVariable(MESSAGE, message);
        spelEvaluationContext.setVariable(payloadId, message.getPayload());
        spelEvaluationContext.setVariable(SUBJECT, subject);
        spelEvaluationContext.setVariable(METADATA, message.getMetaData());
        return evaluateSpel((String) spelExpression, spelEvaluationContext);
    }

    private JsonNode evaluateSpel(String spelExpression, EvaluationContext spelEvaluationContext) {
        var expression = SPEL_PARSER.parseExpression(spelExpression);
        try {
            var evaluationResult = expression.getValue(spelEvaluationContext);
            return mapper.valueToTree(evaluationResult);
        } catch (SpelEvaluationException | NullPointerException e) {
            log.error(
                    "Failed to evaluate the SpEL expression \"{}\" during"
                            + " construction of an AuthorizationSubscription. Error: {}",
                    spelExpression, e.getLocalizedMessage());
            throw e;
        }
    }

    private boolean expressionNotSet(Object spelExpression) {
        return !(spelExpression instanceof String) || ((String) spelExpression).isBlank();
    }

}
