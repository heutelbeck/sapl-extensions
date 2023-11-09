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
package io.sapl.axon.authentication.reactive;

import java.util.Map;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.extensions.reactor.messaging.ReactorMessageDispatchInterceptor;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

/**
 * The ReactiveAuthenticationCommandDispatchInterceptor adds the authentication
 * metadata supplied by the AuthenticationMetadataProvider to all dispatched
 * Commands. This identifies the subject for authorization.
 *
 * @author Dominic Heutelbeck
 * @since 2.1.0
 */
@RequiredArgsConstructor
public class ReactiveAuthenticationCommandDispatchInterceptor
        implements ReactorMessageDispatchInterceptor<CommandMessage<?>> {

    private final ReactiveAuthenticationSupplier authnProvider;

    @Override
    public Mono<CommandMessage<?>> intercept(Mono<CommandMessage<?>> message) {
        return message.flatMap(msg -> {
            if (msg.getMetaData().get("subject") != null)
                return Mono.just(msg);

            return authnProvider.get().map(authn -> msg.andMetaData(Map.of("subject", authn)));
        });
    }

}
