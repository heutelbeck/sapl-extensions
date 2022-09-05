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
public interface CollectionAndOptionalFilterPredicateProvider<T> extends ResultConstraintHandlerProvider {

	/**
	 * {@inheritDoc}
	 */
	default int getPriority() {
		return 1000; // Execute before other mapping handlers
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
	@SuppressWarnings("unchecked")
	default Object mapPayload(Object payload, Class<?> clazz, JsonNode constraint) {
		if (payload instanceof Optional) {
			return filterOptional((Optional<T>) payload, constraint);
		}
		return filterCollection((Collection<T>) payload, constraint);
	};

	private Optional<T> filterOptional(Optional<T> payload, JsonNode constraint) {
		return payload.filter(x -> test(x, constraint));
	};

	private Collection<T> filterCollection(Collection<T> payload, JsonNode constraint) {
		return payload.stream().filter(x -> test(x, constraint)).collect(Collectors.toList());
	};

	/**
	 * @return The type contained in the {@code Collection} or {@code Optional}.
	 */
	Class<T> getContainedType();

	/**
	 * @param o          The object to test.
	 * @param constraint The constraint
	 * @return true to indicate that {@code o} should stay in the container.
	 */
	boolean test(T o, JsonNode constraint);
}
