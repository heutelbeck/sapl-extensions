/*
 * Copyright © 2017-2022 Dominic Heutelbeck (dominic@heutelbeck.com)
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
import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.beanutils.PropertyUtils;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.annotation.MessageHandlingMember;
import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.modelling.command.TargetAggregateIdentifier;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.spring.stereotype.Aggregate;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.axon.annotation.PreHandleEnforce;
import io.sapl.spring.method.metadata.PreEnforce;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

/**
 * The AuthorizationSubscriptionBuilderService Object offers methods to get the
 * AuthorizationSubscription for Query Messages and for Command Messages.
 */
@Slf4j
@RequiredArgsConstructor
public class AuthorizationSubscriptionBuilderService {
	protected static final String ACTION               = "action";
	protected static final String ACTION_TYPE          = "actionType";
	protected static final String AGGREGATE            = "aggregate";
	protected static final String AGGREGATE_IDENTIFIER = "aggregateIdentifier";
	protected static final String AGGREGATE_TYPE       = "aggregateType";
	protected static final String ANONYMOUS            = "\"anonymous\"";
	protected static final String CLASS_NAME           = "className";
	protected static final String COMMAND              = "command";
	protected static final String COMMAND_NAME         = "commandName";
	protected static final String ENVIRONMENT          = "environment";
	protected static final String EXECUTABLE           = "executable";
	protected static final String MESSAGE              = "message";
	protected static final String METADATA             = "metadata";
	protected static final String METHOD_NAME          = "methodName";
	protected static final String PAYLOAD              = "payload";
	protected static final String PAYLOAD_TYPE         = "payloadType";
	protected static final String PROJECTION_CLASS     = "projectionClass";
	protected static final String QUERY                = "query";
	protected static final String QUERY_NAME           = "queryName";
	protected static final String QUERY_RESULT         = "queryResult";
	protected static final String RESOURCE             = "resource";
	protected static final String RESPONSE_TYPE        = "responseType";
	protected static final String SUBJECT              = "subject";
	protected static final String UPDATE_RESPONSE_TYPE = "updateResponseType";

	protected static final JsonNodeFactory  JSON        = JsonNodeFactory.instance;
	protected static final ExpressionParser SPEL_PARSER = new SpelExpressionParser();

	protected final ObjectMapper mapper;

	/**
	 * Executed to get the AuthorizationSubscription for a Command Message. It gets
	 * the message, target and the delegate and returns the constructed
	 * AuthorizationSubscription.
	 *
	 * @param message   Representation of a Message, containing a Payload and
	 *                  MetaData
	 * @param aggregate Object, which contains state and methods to alter that state
	 * @param delegate  Command handler, needed to set parameter from annotation
	 * @return the AuthorizationSubscription for the Command
	 */
	public AuthorizationSubscription constructAuthorizationSubscriptionForCommand(CommandMessage<?> message,
			Object aggregate, PreHandleEnforce methodAnnotation) {

		var annotationAttributeValues = AnnotationUtils.getAnnotationAttributes(methodAnnotation);

		var subject     = retrieveSubject(message, annotationAttributeValues);
		var action      = retrieveCommandAction(message, annotationAttributeValues, AGGREGATE, aggregate);
		var resource    = retrieveResourceFromTarget(message, aggregate, annotationAttributeValues);
		var environment = retrieveEnvironment(annotationAttributeValues);

		return AuthorizationSubscription.of(subject, action, resource, environment);
	}

	/**
	 * Executed to get the AuthorizationSubscription for a Query Message. It gets
	 * the queryMessage and the annotation and returns the constructed
	 * AuthorizationSubscription.
	 *
	 * @param queryMessage Representation of a QueryMessage, containing a Payload
	 *                     and MetaData
	 * @param annotation   Annotation from the respective method
	 * @param executable   an executable
	 * @param queryResult  a query result
	 * @return the AuthorizationSubscription for the Query
	 */
	public AuthorizationSubscription constructAuthorizationSubscriptionForQuery(QueryMessage<?, ?> queryMessage,
			Annotation annotation, Executable executable, Optional<?> queryResult) {

		var annotationAttributeValues = AnnotationUtils.getAnnotationAttributes(annotation);

		var subject     = retrieveSubject(queryMessage, annotationAttributeValues);
		var action      = retrieveQueryAction(queryMessage, annotationAttributeValues, executable);
		var resource    = retrieveResourceFromQueryMessage(queryMessage, annotationAttributeValues, executable,
				queryResult);
		var environment = retrieveEnvironment(annotationAttributeValues);

		return AuthorizationSubscription.of(subject, action, resource, environment);
	}

	protected JsonNode constructResourceNode(QueryMessage<?, ?> message, Executable executable,
			Optional<?> queryResult) {
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

	protected <T> JsonNode constructResourceWithAggregateInformation(Message<?> message, T aggregate) {
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

	protected Optional<Object> aggregateIdFromMessage(Message<?> message) {
		var fields = message.getPayloadType().getDeclaredFields();
		for (var field : fields) {
			if (field.isAnnotationPresent(TargetAggregateIdentifier.class)) {
				try {
					return Optional.ofNullable(PropertyUtils.getProperty(message.getPayload(), field.getName()));
				} catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
					log.error("Could not get TargetAggregateIdentifier from message", e);
					return Optional.empty();
				}
			}
		}
		return Optional.empty();
	}

	protected JsonNode messageToJson(Message<?> message) {
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

	protected JsonNode retrieveCommandAction(CommandMessage<?> message, Map<String, Object> annotationAttributes,
			String keyForCommandHandlingObject, Object commandHandlingObject) {
		var spelExpression = annotationAttributes.get(ACTION);
		if (expressionNotSet(spelExpression))
			return messageToJson(message);

		var seplEvaluationContext = new StandardEvaluationContext();
		seplEvaluationContext.setVariable(MESSAGE, message);
		seplEvaluationContext.setVariable(PAYLOAD, message.getPayload());
		seplEvaluationContext.setVariable(keyForCommandHandlingObject, commandHandlingObject);
		return evaluateSpEL((String) spelExpression, seplEvaluationContext);

	}

	protected Object retrieveEnvironment(Map<String, Object> annotationAttributes) {
		var spelExpression = annotationAttributes.get(ENVIRONMENT);
		if (expressionNotSet(spelExpression))
			return null;

		return evaluateSpEL((String) spelExpression, new StandardEvaluationContext());
	}

	protected <T> Annotation retrievePreEnforceAnnotation(MessageHandlingMember<T> handler) {
		var method = handler.unwrap(Executable.class).orElseThrow();
		return AnnotationUtils.getAnnotation(method, PreEnforce.class);
	}

	protected JsonNode retrieveQueryAction(Message<?> message, Map<String, Object> annotationAttributes,
			Executable executable) {
		var spelExpression = annotationAttributes.get(ACTION);
		if (expressionNotSet(spelExpression))
			return messageToJson(message);

		var seplEvaluationContext = new StandardEvaluationContext();
		seplEvaluationContext.setVariable(MESSAGE, message);
		seplEvaluationContext.setVariable(PAYLOAD, message.getPayload());
		seplEvaluationContext.setVariable(EXECUTABLE, executable);
		return evaluateSpEL((String) spelExpression, seplEvaluationContext);
	}

	protected JsonNode retrieveResourceFromQueryMessage(QueryMessage<?, ?> message,
			Map<String, Object> annotationAttributes, Executable executable, Optional<?> queryResult) {
		var spelExpression = annotationAttributes.get(RESOURCE);
		if (expressionNotSet(spelExpression))
			return constructResourceNode(message, executable, queryResult);

		var seplEvaluationContext = new StandardEvaluationContext();
		seplEvaluationContext.setVariable(MESSAGE, message);
		seplEvaluationContext.setVariable(PAYLOAD, message.getPayload());
		seplEvaluationContext.setVariable(EXECUTABLE, executable);
		queryResult.ifPresent(result -> seplEvaluationContext.setVariable(QUERY_RESULT, result));
		return evaluateSpEL((String) spelExpression, seplEvaluationContext);
	}

	protected <T> JsonNode retrieveResourceFromTarget(Message<?> message, T aggregate,
			Map<String, Object> annotationAttributes) {
		var spelExpression = annotationAttributes.get(RESOURCE);
		if (expressionNotSet(spelExpression)) {
			return constructResourceWithAggregateInformation(message, aggregate);
		}

		var seplEvaluationContext = new StandardEvaluationContext();
		seplEvaluationContext.setVariable(MESSAGE, message);
		seplEvaluationContext.setVariable(PAYLOAD, message.getPayload());
		seplEvaluationContext.setVariable(AGGREGATE, aggregate);
		return evaluateSpEL((String) spelExpression, seplEvaluationContext);
	}

	@SneakyThrows
	protected JsonNode retrieveSubject(Message<?> message, Map<String, Object> annotationAttributes) {
		var subject = message.getMetaData().getOrDefault(SUBJECT, ANONYMOUS);

		var spelExpression = annotationAttributes.get(SUBJECT);

		if (expressionNotSet(spelExpression))
			return mapper.readTree((String) subject);

		var seplEvaluationContext = new StandardEvaluationContext();
		seplEvaluationContext.setVariable(MESSAGE, message);
		seplEvaluationContext.setVariable(PAYLOAD, message.getPayload());
		seplEvaluationContext.setVariable(SUBJECT, subject);
		return evaluateSpEL((String) spelExpression, seplEvaluationContext);
	}

	protected JsonNode evaluateSpEL(String spelExpression, EvaluationContext seplEvaluationContext) {
		var expression       = SPEL_PARSER.parseExpression(spelExpression);
		var evaluationResult = expression.getValue(seplEvaluationContext);
		return mapper.valueToTree(evaluationResult);
	}

	protected boolean expressionNotSet(Object spelExpression) {
		return !(spelExpression instanceof String) || ((String) spelExpression).isBlank();
	}

}
