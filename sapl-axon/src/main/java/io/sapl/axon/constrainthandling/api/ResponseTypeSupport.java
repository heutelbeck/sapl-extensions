package io.sapl.axon.constrainthandling.api;

import java.lang.reflect.Type;
import java.util.Set;

import org.axonframework.messaging.responsetypes.ResponseType;

/**
 * Interface for constraint handlers requiring a specific response type to be
 * present to work properly.
 * 
 * @author Dominic Heutelbeck
 * @since 2.1.0
 * 
 */
public interface ResponseTypeSupport {

	/**
	 * @return the supported ResponseTypes
	 */
	Set<ResponseType<?>> getSupportedResponseTypes();

	/**
	 * Checks if the constraint handler can work with the given type.
	 * 
	 * @param responseType the response {@link java.lang.reflect.Type} of the
	 *                     handler which is matched against
	 * @return true if a response can be converted based on the given
	 *         {@code responseType} and false if it cannot
	 */
	default boolean supports(Type responseType) {
		return getSupportedResponseTypes().stream().filter(type -> type.matches(responseType)).findFirst().isPresent();
	};

	/**
	 * Checks if the constraint handler can work with the given type.
	 * 
	 * @param responseType the response {@link java.lang.reflect.Type} of the
	 *                     handler which is matched against
	 * @return true if a response can be converted based on the given
	 *         {@code responseType} and false if it cannot
	 */
	default boolean supports(ResponseType<?> responseType) {
		return supports(responseType.getExpectedResponseType());
	};
}
