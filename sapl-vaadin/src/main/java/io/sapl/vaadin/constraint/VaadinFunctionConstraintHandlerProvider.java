/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.vaadin.constraint;

import java.util.Iterator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import com.fasterxml.jackson.databind.JsonNode;
import com.vaadin.flow.component.UI;

import reactor.core.publisher.Mono;

public interface VaadinFunctionConstraintHandlerProvider {
    boolean isResponsible(JsonNode constraint);

    Function<UI, Mono<Boolean>> getHandler(JsonNode constraint);

    static VaadinFunctionConstraintHandlerProvider of(Predicate<JsonNode> isResponsible,
            Function<UI, Mono<Boolean>> handler) {
        return new VaadinFunctionConstraintHandlerProvider() {

            @Override
            public boolean isResponsible(JsonNode constraint) {
                return isResponsible.test(constraint);
            }

            @Override
            public Function<UI, Mono<Boolean>> getHandler(JsonNode constraint) {
                return handler;
            }
        };
    }

    static VaadinFunctionConstraintHandlerProvider of(JsonNode constraintFilter, Consumer<JsonNode> handler) {
        return new VaadinFunctionConstraintHandlerProvider() {

            @Override
            public boolean isResponsible(JsonNode constraint) {
                boolean isResponsible = true;
                for (Iterator<String> it = constraintFilter.fieldNames(); it.hasNext();) {
                    String filterField = it.next();
                    if (!constraint.has(filterField)
                            || !constraint.get(filterField).equals(constraintFilter.get(filterField))) {
                        isResponsible = false;
                    }
                }
                return isResponsible;
            }

            @Override
            public Function<UI, Mono<Boolean>> getHandler(JsonNode constraint) {
                return ui -> {
                    handler.accept(constraint);
                    return Mono.just(Boolean.TRUE);
                };
            }
        };
    }

    static VaadinFunctionConstraintHandlerProvider of(JsonNode constraintFilter,
            Function<JsonNode, Mono<Boolean>> handler) {
        return new VaadinFunctionConstraintHandlerProvider() {
            @Override
            public boolean isResponsible(JsonNode constraint) {
                boolean isResponsible = true;
                for (Iterator<String> it = constraintFilter.fieldNames(); it.hasNext();) {
                    String filterField = it.next();
                    if (!constraint.has(filterField)
                            || !constraint.get(filterField).equals(constraintFilter.get(filterField))) {
                        isResponsible = false;
                    }
                }
                return isResponsible;
            }

            @Override
            public Function<UI, Mono<Boolean>> getHandler(JsonNode constraint) {
                return ui -> handler.apply(constraint);
            }
        };
    }
}
