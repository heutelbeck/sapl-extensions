/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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

/**
 *
 * Base interface for implementing constraint handlers that can intercept a
 * QueryMessage.
 * <p>
 * Users can choose to overwrite the
 * {@link QueryConstraintHandlerProvider#getHandler} method to completely change
 * the behavior, or to overwrite one of the specialized methods to only consume
 * the message or to map individual aspect of the message.
 *
 * @author Dominic Heutelbeck
 * @since 2.1.0
 */
public interface QueryConstraintHandlerProvider extends Responsible, HasPriority {

    /**
     * @param constraint The constraint required by the authorization decision.
     * @return The handler triggering all required side effects and potentially
     *         changing the message.
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    default Function<QueryMessage<?, ?>, QueryMessage<?, ?>> getHandler(JsonNode constraint) {
        return query -> {
            accept(query, constraint);
            var newPayload      = mapPayload(query.getPayload(), query.getPayloadType(), constraint);
            var newPayloadType  = mapPayloadType(query.getPayloadType(), constraint);
            var newMetaData     = mapMetadata(query.getMetaData(), constraint);
            var newQueryName    = mapQueryName(query.getQueryName(), constraint);
            var newResponseType = mapResponseType(query.getResponseType(), constraint);
            var baseMessage     = new GenericMessage(query.getIdentifier(), newPayloadType, newPayload, newMetaData);
            if (query instanceof SubscriptionQueryMessage) {
                var newUpdateResponseType = mapUpdateResponseType(
                        ((SubscriptionQueryMessage) query).getUpdateResponseType(), constraint);
                return new GenericSubscriptionQueryMessage(baseMessage, newQueryName, newResponseType,
                        newUpdateResponseType);

            }
            return new GenericQueryMessage(baseMessage, newQueryName, newResponseType);
        };
    }

    /**
     *
     * Method for triggering side-effects.
     *
     * @param message    The message.
     * @param constraint The constraint.
     */
    default void accept(QueryMessage<?, ?> message, JsonNode constraint) {
        // NOOP
    }

    /**
     * Method to change the response type.
     *
     * @param responseType The original response type.
     * @param constraint   The constraint.
     * @return A potentially updated response type.
     */
    default ResponseType<?> mapResponseType(ResponseType<?> responseType, JsonNode constraint) {
        return responseType;
    }

    /**
     * Method to change the updateResponseType type.
     *
     * @param updateResponseType The original updateResponseType type.
     * @param constraint         The constraint.
     * @return A potentially updated updateResponseType type.
     */
    default ResponseType<?> mapUpdateResponseType(ResponseType<?> updateResponseType, JsonNode constraint) {
        return updateResponseType;
    }

    /**
     * Method to change the payload type.
     *
     * @param payloadType The original payload type.
     * @param constraint  The constraint.
     * @return A potentially updated payload type.
     */
    default Class<?> mapPayloadType(Class<?> payloadType, JsonNode constraint) {
        return payloadType;
    }

    /**
     * Method to change the payload.
     *
     * @param payload    The original payload.
     * @param clazz      The type of the payload.
     * @param constraint The constraint.
     * @return A potentially updated payload.
     */
    default Object mapPayload(Object payload, Class<?> clazz, JsonNode constraint) {
        return payload;
    }

    /**
     * Method to change the query name.
     *
     * @param queryName  The original query name.
     * @param constraint The constraint.
     * @return A potentially updated queryName.
     */
    default String mapQueryName(String queryName, JsonNode constraint) {
        return queryName;
    }

    /**
     * Method to change the metadata.
     *
     * @param originalMetadata The original metadata.
     * @param constraint       The constraint.
     * @return Potentially updated metadata.
     */
    default MetaData mapMetadata(MetaData originalMetadata, JsonNode constraint) {
        return originalMetadata;
    }

}
