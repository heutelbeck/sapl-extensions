/*
 * Copyright © 2017-2022 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.axon.constraints.api;

import java.util.function.Predicate;

import org.axonframework.messaging.ResultMessage;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.spring.constraints.api.Responsible;
import io.sapl.spring.constraints.api.TypeSupport;

public interface ResultMessageFilterPredicateConstraintHandlerProvider<T>
		extends Responsible, TypeSupport<T> {

	Predicate<ResultMessage<T>> getHandler(JsonNode constraint);

}