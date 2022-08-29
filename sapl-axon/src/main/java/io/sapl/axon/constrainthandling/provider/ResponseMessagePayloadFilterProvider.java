package io.sapl.axon.constrainthandling.provider;

import java.util.Objects;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.axon.constrainthandling.api.ResultConstraintHandlerProvider;
import io.sapl.spring.constraints.providers.ContentFilterUtil;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ResponseMessagePayloadFilterProvider implements ResultConstraintHandlerProvider<Object> {

	private static final String CONSTRAINT_TYPE = "filterMessagePayloadContent";
	private static final String TYPE            = "type";

	private final ObjectMapper objectMapper;

	@Override
	public boolean isResponsible(JsonNode constraint) {
		if (constraint == null || !constraint.isObject())
			return false;

		var type = constraint.get(TYPE);

		if (Objects.isNull(type) || !type.isTextual())
			return false;

		return CONSTRAINT_TYPE.equals(type.asText());
	}

	@Override
	public Class<Object> getSupportedType() {
		return Object.class;
	}

	@Override
	public Object mapPayload(JsonNode constraint, Object payload, Class<?> clazz) {
		return ContentFilterUtil.getHandler(constraint, objectMapper).apply(payload);
	}
}
