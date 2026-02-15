/*
 * Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * SPDX-License-Identifier: Apache-2.0
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
package io.sapl.axon.constrainthandling.provider;

import java.util.Set;

import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.messaging.responsetypes.ResponseTypes;

import io.sapl.api.model.Value;
import io.sapl.api.model.ValueJsonMarshaller;
import io.sapl.axon.constrainthandling.api.ResultConstraintHandlerProvider;
import io.sapl.spring.constraints.providers.ConstraintResponsibility;
import io.sapl.spring.constraints.providers.ContentFilter;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.json.JsonMapper;

/**
 * This provider offers the manipulation of ResultMessage payloads.
 * <p>
 * The constraint must be a JSON Object.
 * <p>
 * The constraint must contain the field "type" with value
 * "filterMessagePayloadContent".
 * <p>
 * See {@link io.sapl.spring.constraints.providers.ContentFilter} for supported
 * actions.
 *
 * @author Dominic Heutelbeck
 * @since 2.1.0
 */
@RequiredArgsConstructor
public class ResponseMessagePayloadFilterProvider implements ResultConstraintHandlerProvider {

    private static final String               CONSTRAINT_TYPE = "filterMessagePayloadContent";
    private static final Set<ResponseType<?>> SUPPORTED_TYPES = Set.of(ResponseTypes.instanceOf(Object.class),
            ResponseTypes.optionalInstanceOf(Object.class), ResponseTypes.multipleInstancesOf(Object.class),
            ResponseTypes.publisherOf(Object.class));

    private final JsonMapper objectMapper;

    @Override
    public boolean isResponsible(Value constraint) {
        return ConstraintResponsibility.isResponsible(constraint, CONSTRAINT_TYPE);
    }

    @Override
    public Set<ResponseType<?>> getSupportedResponseTypes() {
        return SUPPORTED_TYPES;
    }

    @Override
    public Object mapPayload(Object payload, Class<?> clazz, Value constraint) {
        var jsonNode = ValueJsonMarshaller.toJsonNode(constraint);
        return ContentFilter.getHandler(jsonNode, objectMapper).apply(payload);
    }

}
