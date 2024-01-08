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

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.MetaData;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.spring.constraints.api.HasPriority;
import io.sapl.spring.constraints.api.Responsible;

/**
 *
 * Base interface for implementing constraint handlers that can intercept a
 * CommandMessage.
 * <p>
 * Users can choose to overwrite the
 * {@link CommandConstraintHandlerProvider#getHandler} method to completely
 * change the behavior, or to overwrite one of the specialized methods to only
 * consume the message or to map individual aspect of the message.
 *
 * @author Dominic Heutelbeck
 * @since 2.1.0
 */
public interface CommandConstraintHandlerProvider extends Responsible, HasPriority {

    /**
     * @param constraint The constraint required by the authorization decision.
     * @return The handler triggering all required side effects and potentially
     *         changing the message.
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    default Function<CommandMessage<?>, CommandMessage<?>> getHandler(JsonNode constraint) {
        return command -> {
            accept(command, constraint);
            var newPayload     = mapPayload(command.getPayload(), command.getPayloadType(), constraint);
            var newPayloadType = mapPayloadType(command.getPayloadType(), constraint);
            var newMetaData    = mapMetadata(command.getMetaData(), constraint);
            var newCommandName = mapCommandName(command.getCommandName(), constraint);
            var baseMessage    = new GenericMessage(command.getIdentifier(), newPayloadType, newPayload, newMetaData);
            return new GenericCommandMessage(baseMessage, newCommandName);
        };
    }

    /**
     * Method for triggering side-effects.
     *
     * @param message    The message.
     * @param constraint The constraint.
     */
    default void accept(CommandMessage<?> message, JsonNode constraint) {
        // NOOP
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
     * Method to change the command name.
     *
     * @param commandName The original command name.
     * @param constraint  The constraint.
     * @return A potentially updated commandName.
     */
    default String mapCommandName(String commandName, JsonNode constraint) {
        return commandName;
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
