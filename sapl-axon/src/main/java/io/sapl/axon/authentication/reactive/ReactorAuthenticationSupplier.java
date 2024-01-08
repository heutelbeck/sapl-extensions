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
package io.sapl.axon.authentication.reactive;

import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.axon.authentication.AuthnUtil;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import reactor.core.publisher.Mono;

/**
 *
 * Default implementation of an AuthenticationSupplier. The subject is read from
 * the Webflux ReactiveSecurityContextHolder and serialized as Jackson
 * JsonObject. The service removes to remove credentials and passwords from the
 * created authentication data.
 *
 * @author Dominic Heutelbeck
 * @since 2.1.0
 */
@RequiredArgsConstructor
public class ReactorAuthenticationSupplier implements ReactiveAuthenticationSupplier {

    private final static String ANONYMOUS = "\"anonymous\"";

    private final ObjectMapper mapper;

    /**
     * Extracts the authentication information from the Spring SecurityContext.
     * Defaults to "anonymous" if no authentication is present.
     */
    @Override
    @SneakyThrows
    public Mono<String> get() {
        return ReactiveSecurityContextHolder.getContext().map(SecurityContext::getAuthentication)
                .map(authn -> AuthnUtil.authenticationToJsonString(authn, mapper)).onErrorResume(e -> Mono.empty())
                .defaultIfEmpty(ANONYMOUS);
    }

}
