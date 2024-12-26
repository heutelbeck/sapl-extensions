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
package io.sapl.axon.authentication.servlet;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.queryhandling.QueryMessage;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * The AuthenticationCommandDispatchInterceptor adds the authentication metadata
 * supplied by the AuthenticationSupplier to all dispatched Queries. This
 * identifies the subject for authorization.
 *
 * @author Dominic Heutelbeck
 * @since 2.1.0
 */
@RequiredArgsConstructor
public class AuthenticationQueryDispatchInterceptor implements MessageDispatchInterceptor<QueryMessage<?, ?>> {

    private final AuthenticationSupplier authnProvider;

    /**
     * Adds the subject's authentication data to the "subject" field in the metadata
     * as a JSON String.
     */
    @Override
    public @NonNull BiFunction<Integer, QueryMessage<?, ?>, QueryMessage<?, ?>> handle(
            @NonNull List<? extends QueryMessage<?, ?>> messages) {
        return (index, query) -> {
            if (query.getMetaData().get("subject") != null)
                return query;
            return query.andMetaData(Map.of("subject", authnProvider.get()));
        };
    }
}
