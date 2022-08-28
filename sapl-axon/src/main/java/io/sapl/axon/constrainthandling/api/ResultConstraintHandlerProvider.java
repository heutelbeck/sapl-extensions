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

public interface ResultConstraintHandlerProvider<T> extends Responsible, HasPriority, TypeSupport<T> {

	default Function<Object, Object> getHandler(JsonNode constraint) {
		return result -> {
			if (result instanceof ResultMessage)
				return getResultMessageHandler((ResultMessage<?>) result, constraint);

			return mapPayload(constraint, result, Object.class);
		};
	};

	@SuppressWarnings({ "rawtypes", "unchecked" })
	default ResultMessage<?> getResultMessageHandler(ResultMessage<?> result, JsonNode constraint) {
		var           newMetaData = mapMetadata(constraint, result.getMetaData());
		ResultMessage resultMessage;
		if (result.isExceptional()) {
			var newThrowable = mapThrowable(constraint, result.exceptionResult());
			var baseMessage  = new GenericMessage(result.getIdentifier(), result.getPayloadType(), result.getPayload(),
					newMetaData);

			resultMessage = new GenericResultMessage(baseMessage, newThrowable);
		} else {
			var newPayload     = mapPayload(constraint, result.getPayload(), result.getPayloadType());
			var newPayloadType = mapPayloadType(constraint, result.getPayloadType());
			var baseMessage    = new GenericMessage(result.getIdentifier(), newPayloadType, newPayload, newMetaData);
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
	}

	default Throwable mapThrowable(JsonNode constraint, Throwable exceptionResult) {
		return exceptionResult;
	}

	default Class<?> mapPayloadType(JsonNode constraint, Class<?> payloadType) {
		return payloadType;
	}

	default Object mapPayload(JsonNode constraint, Object payload, Class<?> clazz) {
		return payload;
	}

	default MetaData mapMetadata(JsonNode constraint, MetaData originalMetadata) {
		return originalMetadata;
	}

}