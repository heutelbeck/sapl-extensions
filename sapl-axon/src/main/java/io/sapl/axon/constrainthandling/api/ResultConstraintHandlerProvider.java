/*
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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

/**
 *
 * Base interface for implementing constraint handlers that can intercept a
 * ResultMessage or a raw result of a query handler method.
 * <p>
 * Users can choose to overwrite the
 * {@link ResultConstraintHandlerProvider#getHandler} method to completely
 * change the behavior, or to overwrite one of the specialized methods to only
 * consume the message or to map individual aspect of the message.
 *
 * @author Dominic Heutelbeck
 * @since 2.1.0
 */
public interface ResultConstraintHandlerProvider extends Responsible, HasPriority, ResponseTypeSupport {

    /**
     * @param constraint The constraint required by the authorization decision.
     * @return The handler triggering all required side effects and potentially
     *         changing the result.
     */
    default Function<Object, Object> getHandler(JsonNode constraint) {
        return result -> {
            if (result instanceof ResultMessage)
                return getResultMessageHandler((ResultMessage<?>) result, constraint);

            return mapPayload(result, Object.class, constraint);
        };
    }

    /**
     * Handling of result messages.
     *
     * @param result     Original result.
     * @param constraint The constraint.
     * @return Potentially updated message.
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    default ResultMessage<?> getResultMessageHandler(ResultMessage<?> result, JsonNode constraint) {
        accept(result, constraint);
        var           newMetaData = mapMetadata(result.getMetaData(), constraint);
        ResultMessage resultMessage;
        if (result.isExceptional()) {
            var newThrowable = mapThrowable(result.exceptionResult(), constraint);
            var baseMessage  = new GenericMessage(result.getIdentifier(), result.getPayloadType(), null, newMetaData);
            resultMessage = new GenericResultMessage(baseMessage, newThrowable);
        } else {
            var newPayload     = mapPayload(result.getPayload(), result.getPayloadType(), constraint);
            var newPayloadType = mapPayloadType(result.getPayloadType(), constraint);
            var baseMessage    = new GenericMessage(result.getIdentifier(), newPayloadType, newPayload, newMetaData);
            resultMessage = new GenericResultMessage(baseMessage);
        }

        if (result instanceof SubscriptionQueryUpdateMessage) {
            return GenericSubscriptionQueryUpdateMessage.asUpdateMessage(resultMessage);
        } else if (result instanceof QueryResponseMessage) {
            return new GenericQueryResponseMessage(resultMessage);
        } else if (result instanceof CommandResultMessage) {
            return GenericCommandResultMessage.asCommandResultMessage(resultMessage);
        }

        return resultMessage;
    }

    /**
     * Method for triggering side-effects.
     *
     * @param message    The message.
     * @param constraint The constraint.
     */
    default void accept(ResultMessage<?> message, JsonNode constraint) {
        // NOOP
    }

    /**
     * Method to change an Exception.
     *
     * @param exceptionResult The original Exception.
     * @param constraint      The constraint.
     * @return Potentially updated Exception.
     */
    default Throwable mapThrowable(Throwable exceptionResult, JsonNode constraint) {
        return exceptionResult;
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
