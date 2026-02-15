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

import io.sapl.api.model.Value;
import io.sapl.api.model.ValueJsonMarshaller;
import io.sapl.axon.constrainthandling.api.CollectionAndOptionalFilterPredicateProvider;
import io.sapl.spring.constraints.providers.ConstraintResponsibility;
import io.sapl.spring.constraints.providers.ContentFilter;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.json.JsonMapper;

/**
 * This provider offers a filter based on predicates specified in the
 * constraints.
 * <p>
 * The constraint must be a JSON Object.
 * <p>
 * The constraint must contain the field "type" with value
 * "filterMessagePayloadPredicate".
 * <p>
 * See {@link io.sapl.spring.constraints.providers.ContentFilter} for supported
 * conditions.
 *
 * @author Dominic Heutelbeck
 * @since 2.1.0
 */
@RequiredArgsConstructor
public class ResponseMessagePayloadFilterPredicateProvider
        implements CollectionAndOptionalFilterPredicateProvider<Object> {

    private static final String CONSTRAINT_TYPE = "filterMessagePayloadPredicate";

    private final JsonMapper objectMapper;

    @Override
    public boolean isResponsible(Value constraint) {
        return ConstraintResponsibility.isResponsible(constraint, CONSTRAINT_TYPE);
    }

    @Override
    public Class<Object> getContainedType() {
        return Object.class;
    }

    @Override
    public boolean test(Object o, Value constraint) {
        var jsonNode = ValueJsonMarshaller.toJsonNode(constraint);
        return ContentFilter.predicateFromConditions(jsonNode, objectMapper).test(o);
    }

}
