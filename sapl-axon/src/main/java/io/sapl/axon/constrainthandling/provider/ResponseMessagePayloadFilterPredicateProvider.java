package io.sapl.axon.constrainthandling.provider;

import java.util.Objects;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.axon.constrainthandling.api.CollectionAndOptionalFilterPredicateProvider;
import io.sapl.spring.constraints.providers.ContentFilterUtil;
import lombok.RequiredArgsConstructor;

/**
 * This provider offers a filter based on predicates specified in the
 * contraints.
 * 
 * The constraint must be a JSON Object.
 * 
 * The constraint must contain the field "type" with value
 * "filterMessagePayloadPredicate".
 * 
 * See {@link io.sapl.spring.constraints.providers.ContentFilterUtil} for
 * supported conditions.
 * 
 * @author Dominic Heutelbeck
 * @since 2.1.0
 */
@RequiredArgsConstructor
public class ResponseMessagePayloadFilterPredicateProvider
		implements CollectionAndOptionalFilterPredicateProvider<Object> {

	private static final String CONSTRAINT_TYPE = "filterMessagePayloadPredicate";
	private static final String TYPE            = "type";

	private final ObjectMapper objectMapper;

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isResponsible(JsonNode constraint) {
		if (constraint == null || !constraint.isObject())
			return false;

		var type = constraint.get(TYPE);

		if (Objects.isNull(type) || !type.isTextual())
			return false;

		return CONSTRAINT_TYPE.equals(type.asText());
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Class<Object> getContainedType() {
		return Object.class;
	}

	@Override
	public boolean test(Object o, JsonNode constraint) {
		return ContentFilterUtil.predicateFromConditions(constraint, objectMapper).test(o);
	}

}
