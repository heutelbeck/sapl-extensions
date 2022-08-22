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

import java.util.function.Function;

import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.queryhandling.GenericQueryMessage;
import org.axonframework.queryhandling.GenericSubscriptionQueryMessage;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.queryhandling.SubscriptionQueryMessage;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.spring.constraints.api.HasPriority;
import io.sapl.spring.constraints.api.Responsible;

public interface QueryMessageMappingConstraintHandlerProvider extends Responsible, HasPriority {

	@SuppressWarnings({ "rawtypes", "unchecked" })
	default Function<QueryMessage<?, ?>, QueryMessage<?, ?>> getHandler(JsonNode constraint) {
		return query -> {
			var newPayload      = mapPayload(query.getPayload(), query.getPayloadType());
			var newPayloadType  = mapPayloadType(query.getPayloadType());
			var newMetaData     = mapMetadata(query.getMetaData());
			var newQueryName    = mapQueryName(query.getQueryName());
			var newResponseType = mapReponseType(query.getResponseType());
			var baseMessage     = new GenericMessage(query.getIdentifier(), newPayloadType, newPayload, newMetaData);
			if (query instanceof SubscriptionQueryMessage) {
				var newUpdateResponseType = mapUpdateResponseType(
						((SubscriptionQueryMessage) query).getUpdateResponseType());
				return new GenericSubscriptionQueryMessage(baseMessage, newQueryName, newResponseType,
						newUpdateResponseType);

			}
			return new GenericQueryMessage(baseMessage, newQueryName, newResponseType);
		};
	};

	default ResponseType<?> mapReponseType(ResponseType<?> responseType) {
		return responseType;
	}

	default ResponseType<?> mapUpdateResponseType(ResponseType<?> updateResponseType) {
		return updateResponseType;
	}

	default Class<?> mapPayloadType(Class<?> payloadType) {
		return payloadType;
	}

	default Object mapPayload(Object payload, Class<?> clazz) {
		return payload;
	}

	default String mapQueryName(String queryName) {
		return queryName;
	}

	default MetaData mapMetadata(MetaData originalMetadata) {
		return originalMetadata;
	}

}
