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

package io.sapl.axon.constrainthandling.legacy.api;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.annotation.DefaultParameterResolverFactory;
import org.axonframework.messaging.annotation.MultiParameterResolverFactory;
import org.axonframework.messaging.annotation.ParameterResolver;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.axonframework.queryhandling.GenericQueryMessage;
import org.axonframework.spring.config.annotation.SpringBeanParameterResolverFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.security.access.AccessDeniedException;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.axon.constrainthandling.AxonConstraintHandlerService;
import io.sapl.axon.constrainthandling.api.OnDecisionConstraintHandlerProvider;

public class AxonConstraintHandlerBundle<T, R, U extends Message<T>> {

	final Map<JsonNode, List<Method>>    aggregateRootHandlers         = new HashMap<>();
	final Map<JsonNode, List<Method>>    aggregateMemberHandlers       = new HashMap<>();
	
	/**
	 * Invokes constraint handler methods provided by aggregate root and aggregate
	 * members identified by the {@link io.sapl.axon.annotation.ConstraintHandler}
	 * annotation.
	 * 
	 * @param aggregateRoot      aggregate root object
	 * @param entity             aggregate member entity object
	 * @param commandMessage     commandMessage
	 * @param applicationContext the application context
	 */
	public void invokeAggregateConstraintHandlerMethods(Object aggregateRoot, Optional<Object> entity,
			Message<?> commandMessage, ApplicationContext applicationContext) {
		invokeMethods(aggregateRootHandlers, aggregateRoot, commandMessage, applicationContext);
		if (entity.isEmpty()) {
			return;
		}
		invokeMethods(aggregateMemberHandlers, entity.get(), commandMessage, applicationContext);
	}

	private void invokeMethods(Map<JsonNode, List<Method>> handlers, Object contextObject, Message<?> message,
			ApplicationContext applicationContext) {
		for (Map.Entry<JsonNode, List<Method>> entry : handlers.entrySet()) {
			var methods    = entry.getValue();
			var constraint = entry.getKey();

			methods.forEach((method) -> {
				var arguments = resolveArgumentsForMethodParameters(method, constraint, message, applicationContext);

				try {
					method.invoke(contextObject, arguments.toArray());
				} catch (Throwable t) {
//					var isObligation = isObligationPerConstraint.get(constraint);
//					if (isObligation)
						throw new AccessDeniedException("Failed to invoke aggregate constraint handler", t);
				}
			});
		}
	}

	private List<Object> resolveArgumentsForMethodParameters(Method method, JsonNode constraint, Message<?> message,
			ApplicationContext applicationContext) {
		var parameters = method.getParameters();

		List<ParameterResolverFactory> resolverFactories = new ArrayList<>();
		resolverFactories.add(new DefaultParameterResolverFactory());
		resolverFactories.add(new SpringBeanParameterResolverFactory(applicationContext));
		var multiParameterResolverFactory = new MultiParameterResolverFactory(resolverFactories);

		List<Object> resolvedArguments = new ArrayList<>();
		int          parameterIndex    = 0;
		for (var parameter : parameters) {
			if (JsonNode.class.isAssignableFrom(parameter.getType())) {
				resolvedArguments.add(constraint);
			} else {
				ParameterResolver<?> factory = multiParameterResolverFactory.createInstance(method, parameters,
						parameterIndex);
				if (factory != null) {
					resolvedArguments.add(factory.resolveParameterValue(message));
				} else {
					resolvedArguments.add(null);
				}
			}
			parameterIndex++;
		}
		return resolvedArguments;
	}

}
