package io.sapl.axon.constrainthandling.api;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.messaging.responsetypes.ResponseTypes;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * This type of constraint handler provider will remove all content from a
 * {@code Collection} or {@code Optional} not satisfying the predicate indicated
 * by the
 * {@code CollectionAndOptionalFilterPredicateProvider#test(Object, JsonNode)}
 * method.
 * 
 * @author Dominic Heutelbeck
 * @since 2.1.0
 */
public interface CollectionAndOptionalFilterPredicateProvider extends ResultConstraintHandlerProvider {

	/**
	 * {@inheritDoc}
	 */
	default int getPriority() {
		return 10; // Execute before other mapping handlers
	}

	/**
	 * {@inheritDoc}
	 */
	default Set<ResponseType<?>> getSupportedResponseTypes() {
		var type = getContainedType();
		return Set.of(ResponseTypes.multipleInstancesOf(type), ResponseTypes.optionalInstanceOf(type));
	}

	/**
	 * {@inheritDoc}
	 */
	default Object mapPayload(Object payload, Class<?> clazz, JsonNode constraint) {
		if (payload instanceof Optional) {
			return filterOptional((Optional<?>) payload, constraint);
		}
		return filterCollection((Collection<?>) payload, constraint);
	};

	private Optional<?> filterOptional(Optional<?> payload, JsonNode constraint) {
		return payload.filter(x -> test(x, constraint));
	};

	private Collection<?> filterCollection(Collection<?> payload, JsonNode constraint) {
		return payload.stream().filter(x -> test(x, constraint)).collect(Collectors.toList());
	};

	/**
	 * @return The type contained in the {@code Collection} or {@code Optional}.
	 */
	Class<?> getContainedType();

	/**
	 * @param o          The object to test.
	 * @param constraint The constraint
	 * @return true to indicate that {@code o} should stay in the container.
	 */
	boolean test(Object o, JsonNode constraint);
}
