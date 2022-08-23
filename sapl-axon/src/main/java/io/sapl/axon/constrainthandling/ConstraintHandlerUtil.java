package io.sapl.axon.constrainthandling;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.axonframework.commandhandling.CommandMessage;
import org.springframework.expression.spel.SpelEvaluationException;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.axon.annotation.ConstraintHandler;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@UtilityClass
public class ConstraintHandlerUtil {
	private final static SpelExpressionParser PARSER = new SpelExpressionParser();

	public static <U extends CommandMessage<?>, T> List<Method> collectAggregateConstraintHandlerMethods(
			List<Method> methods, JsonNode constraint, U command, T handlerObject) {

		return  methods.stream()
				.filter(method -> isMethodResponsible(method, constraint, command, handlerObject))
				.collect(Collectors.toList());
	}

	private static <U extends CommandMessage<?>, T> boolean isMethodResponsible(Method method, JsonNode constraint,
			U command, T handlerObject) {
		var annotation = (ConstraintHandler) method.getAnnotation(ConstraintHandler.class);

		if (annotation == null)
			return false;

		var annotationValue = annotation.value();
		if (annotationValue.isBlank()) {
			return true;
		}

		var context = new StandardEvaluationContext();
		context.setVariable("constraint", constraint);
		context.setVariable("command", command);
		context.setVariable("handlerObject", handlerObject);
		var expression = PARSER.parseExpression(annotationValue);

		Object value = null;
		try {
			value = expression.getValue(context);
		} catch (SpelEvaluationException | NullPointerException e) {
			log.warn("Failed to evaluate \"{}\" on Class {} Method {}", annotationValue,
					handlerObject.getClass().getName(), method.getName());
			return false;
		}

		if (value == null)
			return false;

		if (value instanceof Boolean)
			return (Boolean) value;

		log.warn("Expression returned non Boolean ({}). \"{}\" on Class {} Method {}", value, annotationValue,
				handlerObject.getClass().getName(), method.getName());
		return false;
	}

}
