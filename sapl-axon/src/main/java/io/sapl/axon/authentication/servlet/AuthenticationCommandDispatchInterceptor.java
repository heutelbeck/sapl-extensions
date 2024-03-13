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
package io.sapl.axon.authentication.servlet;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.messaging.MessageDispatchInterceptor;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * The AuthenticationCommandDispatchInterceptor adds the authentication metadata
 * supplied by the AuthenticationMetadataProvider to all dispatched Commands.
 * This identifies the subject for authorization.
 *
 * @author Dominic Heutelbeck
 * @since 2.1.0
 */
@RequiredArgsConstructor
public class AuthenticationCommandDispatchInterceptor implements MessageDispatchInterceptor<CommandMessage<?>> {

    private final AuthenticationSupplier authnProvider;

    /**
     * Adds the subject's authentication data to the "subject" field in the metadata
     * as a JSON String.
     */
    @Override
    public @NonNull BiFunction<Integer, CommandMessage<?>, CommandMessage<?>> handle(
            @NonNull List<? extends CommandMessage<?>> messages) {
        return (index, command) -> {
            if (command.getMetaData().get("subject") != null)
                return command;
            return command.andMetaData(Map.of("subject", authnProvider.get()));
        };
    }

}
