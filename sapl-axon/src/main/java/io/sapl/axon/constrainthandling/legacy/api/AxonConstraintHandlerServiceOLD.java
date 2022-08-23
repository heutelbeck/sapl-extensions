package io.sapl.axon.constrainthandling.legacy.api;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.axonframework.messaging.Message;
import org.axonframework.messaging.responsetypes.ResponseType;
import org.springframework.expression.spel.SpelEvaluationException;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.security.access.AccessDeniedException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.axon.annotation.ConstraintHandler;
import io.sapl.spring.constraints.api.ConsumerConstraintHandlerProvider;
import io.sapl.spring.constraints.api.MappingConstraintHandlerProvider;

public class AxonConstraintHandlerServiceOLD {

	private final ObjectMapper mapper;

	public AxonConstraintHandlerServiceOLD(ObjectMapper mapper) {
		this.mapper = mapper;
	}

	public <C, R, U extends Message<C>> AxonConstraintHandlerBundle<C, R, U> createCommandBundle(
			AuthorizationDecision decision, U message, Class<R> responseType,
			Optional<List<Method>> aggregateConstraintMethods, Optional<Object> entity,
			Optional<List<Method>> entityConstraintMethods) {
		var bundle = new AxonConstraintHandlerBundle<C, R, U>();
		failIfResourcePresent(decision);
		decision.getObligations().ifPresent(obligations -> obligations.forEach(obligation -> {
			var numberOfHandlers =  addAggregateConstraintHandlers(bundle, obligation, true, message,
					aggregateConstraintMethods, entity, entityConstraintMethods);

			if (numberOfHandlers == 0)
				throw new AccessDeniedException(
						String.format("No handler found for obligation: %s", obligation.asText()));
		}));

		decision.getAdvice().ifPresent(advices -> advices.forEach(advice -> {
			addAggregateConstraintHandlers(bundle, advice, false, message, aggregateConstraintMethods, entity,
					entityConstraintMethods);
		}));

		return bundle;
	}

	private <Q, R, U extends Message<Q>> int addAggregateConstraintHandlers(AxonConstraintHandlerBundle<Q, R, U> bundle,
			JsonNode constraint, boolean isObligation, U message, Optional<List<Method>> aggregateConstraintMethods,
			Optional<Object> entity, Optional<List<Method>> entityConstraintMethods) {
		int numberOfHandlers      = 0;
		var aggregateRootHandlers = collectAggregateConstraintHandlerMethods(aggregateConstraintMethods, constraint,
				message);
		bundle.aggregateRootHandlers.put(constraint, aggregateRootHandlers);

		numberOfHandlers += aggregateRootHandlers.size();

		if (entity.isPresent()) {
			var aggregateEntityHandlers = collectAggregateConstraintHandlerMethods(entityConstraintMethods, constraint,
					message);
			bundle.aggregateMemberHandlers.put(constraint, aggregateEntityHandlers);
			numberOfHandlers += aggregateEntityHandlers.size();
		}

		if (numberOfHandlers > 0) {
			//bundle.isObligationPerConstraint.put(constraint, isObligation);
		}

		return numberOfHandlers;
	}

	private <U extends Message<?>> boolean isMethodResponsibleForConstraintHandling(JsonNode constraint, U message,
			Method commandHandlingMethod) {
		ConstraintHandler annotation      = commandHandlingMethod.getAnnotation(ConstraintHandler.class);
		var               annotationValue = annotation.value();

		if (annotationValue.isBlank()) {
			return true;
		}

		var context = new StandardEvaluationContext();
		context.setVariable("constraint", constraint);
		context.setVariable("commandMessage", message);
		var expressionParser = new SpelExpressionParser();
		var expression       = expressionParser.parseExpression(annotationValue);

		try {
			var value = expression.getValue(context);
			if (value == null) {
				return false;
			}
			return (Boolean) value;
		} catch (SpelEvaluationException | NullPointerException e) {
			return false;
		}
	}


	private <U extends Message<?>> List<Method> collectAggregateConstraintHandlerMethods(
			Optional<List<Method>> constraintHandlerMethods, JsonNode constraint, U message) {

		return constraintHandlerMethods.map(methods -> methods.stream()
				.filter((method) -> isMethodResponsibleForConstraintHandling(constraint, message, method))
				.collect(Collectors.toList())).orElse(Collections.emptyList());
	}


	private void failIfResourcePresent(AuthorizationDecision decision) {
		if (decision.getResource().isPresent())
			throw new AccessDeniedException(
					"Decision attempted to modify " + "resource, which is not supported by this implementation.");
	}

}
