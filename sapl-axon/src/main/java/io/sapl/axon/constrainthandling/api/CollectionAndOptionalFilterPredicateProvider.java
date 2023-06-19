package io.sapl.axon.constrainthandling.api;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.messaging.responsetypes.ResponseTypes;

import com.fasterxml.jackson.databind.JsonNode;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * This type of constraint handler provider will remove all content from a
 * {@code Collection} or {@code Optional} not satisfying the predicate indicated
 * by the
 * {@code CollectionAndOptionalFilterPredicateProvider#test(Object, JsonNode)}
 * method.
 * 
 * @param <T> pay load type
 * 
 * @author Dominic Heutelbeck
 * @since 2.1.0
 */
public interface CollectionAndOptionalFilterPredicateProvider<T> extends ResultConstraintHandlerProvider {

	@Override
	default int getPriority() {
		return 1000; // Execute before other mapping handlers
	}

	default Set<ResponseType<?>> getSupportedResponseTypes() {
		var type = getContainedType();
		return Set.of(ResponseTypes.multipleInstancesOf(type), ResponseTypes.optionalInstanceOf(type),
				ResponseTypes.publisherOf(type));
	}

	@Override
	@SuppressWarnings("unchecked")
	default Object mapPayload(Object payload, Class<?> clazz, JsonNode constraint) {
		if (payload instanceof Optional) {
			return filterOptional((Optional<T>) payload, constraint);
		}

		if (payload instanceof Mono) {
			return filterMono((Mono<T>) payload, constraint);
		}

		if (payload instanceof Flux) {
			return filterFlux((Flux<T>) payload, constraint);
		}

		return filterCollection((Collection<T>) payload, constraint);
	}

	/**
	 * @param payload    the Flux payload
	 * @param constraint the constraint
	 * @return a Flux only containing elements where the predicate is true
	 */
	default Object filterFlux(Flux<T> payload, JsonNode constraint) {
		return payload.filter(x -> test(x, constraint));
	}

	/**
	 * @param payload    the Mono payload
	 * @param constraint the constraint
	 * @return The original if the predicate was true for the content, else an empty
	 *         Mono.
	 */
	default Object filterMono(Mono<T> payload, JsonNode constraint) {
		return payload.filter(x -> test(x, constraint));
	}

	private Optional<T> filterOptional(Optional<T> payload, JsonNode constraint) {
		return payload.filter(x -> test(x, constraint));
	}

	private Collection<T> filterCollection(Collection<T> payload, JsonNode constraint) {
		return payload.stream().filter(x -> test(x, constraint)).toList();
	}

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
