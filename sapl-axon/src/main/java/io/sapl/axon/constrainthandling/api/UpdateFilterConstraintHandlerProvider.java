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
package io.sapl.axon.constrainthandling.api;

import java.util.function.Predicate;

import org.axonframework.messaging.ResultMessage;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.spring.constraints.api.Responsible;

/**
 * Base interface to for implementing constraint handlers that allow for the
 * filtering of update messages.
 * <p>
 * Only update messages will be emitted, if the respective predicate is true for
 * it.
 * <p>
 *
 * @author Dominic Heutelbeck
 * @since 2.1.0
 */
public interface UpdateFilterConstraintHandlerProvider extends Responsible, ResponseTypeSupport {

    /**
     * Create the predicate for the given constraint.
     *
     * @param constraint The constraint.
     * @return A filter predicate.
     */
    Predicate<ResultMessage<?>> getHandler(JsonNode constraint);

}
