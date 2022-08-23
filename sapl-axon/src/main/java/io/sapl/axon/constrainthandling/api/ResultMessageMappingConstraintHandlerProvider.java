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
package io.sapl.axon.constrainthandling.api;

import java.util.function.Function;

import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.commandhandling.GenericCommandResultMessage;
import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.GenericResultMessage;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.ResultMessage;
import org.axonframework.queryhandling.GenericQueryResponseMessage;
import org.axonframework.queryhandling.GenericSubscriptionQueryUpdateMessage;
import org.axonframework.queryhandling.QueryResponseMessage;
import org.axonframework.queryhandling.SubscriptionQueryUpdateMessage;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.spring.constraints.api.HasPriority;
import io.sapl.spring.constraints.api.Responsible;
import io.sapl.spring.constraints.api.TypeSupport;

public interface ResultMessageMappingConstraintHandlerProvider<T> extends Responsible, HasPriority, TypeSupport<T> {

	@SuppressWarnings({ "rawtypes", "unchecked" })
	default Function<ResultMessage<?>, ResultMessage<?>> getHandler(JsonNode constraint) {
		return result -> {
			var           newMetaData = mapMetadata(result.getMetaData());
			ResultMessage resultMessage;
			if (result.isExceptional()) {
				var newThrowable = mapThrowable(result.exceptionResult());
				var baseMessage  = new GenericMessage(result.getIdentifier(), result.getPayloadType(),
						result.getPayload(), newMetaData);
				
				resultMessage = new GenericResultMessage(baseMessage, newThrowable);
			} else {
				var newPayload     = mapPayload(result.getPayload(), result.getPayloadType());
				var newPayloadType = mapPayloadType(result.getPayloadType());
				var baseMessage    = new GenericMessage(result.getIdentifier(), newPayloadType, newPayload,
						newMetaData);
				resultMessage = new GenericResultMessage(baseMessage);
			}

			if (result instanceof SubscriptionQueryUpdateMessage) {
				return GenericSubscriptionQueryUpdateMessage.asUpdateMessage(resultMessage);
			} else if (result instanceof QueryResponseMessage) {
				return GenericQueryResponseMessage.asResponseMessage(resultMessage);
			} else if (result instanceof CommandResultMessage) {
				return GenericCommandResultMessage.asCommandResultMessage(resultMessage);
			}

			return resultMessage;
		};
	};

	default Throwable mapThrowable(Throwable exceptionResult) {
		return exceptionResult;
	}

	default Class<?> mapPayloadType(Class<?> payloadType) {
		return payloadType;
	}

	default Object mapPayload(Object payload, Class<?> clazz) {
		return payload;
	}

	default MetaData mapMetadata(MetaData originalMetadata) {
		return originalMetadata;
	}

}