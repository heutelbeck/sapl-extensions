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

import org.springframework.security.core.context.SecurityContextHolder;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.axon.authentication.AuthnUtil;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

/**
 *
 * Default implementation of an AuthenticationSupplier. The subject is read from
 * the Spring Security SecurityContextHolder and serialized as Jackson
 * JsonObject. The service removes to remove credentials and passwords from the
 * created authentication data.
 *
 * @author Dominic Heutelbeck
 * @since 2.1.0
 */
@RequiredArgsConstructor
public class ServletAuthenticationSupplier implements AuthenticationSupplier {

    private final ObjectMapper mapper;

    /**
     * Extracts the authentication information from the Spring SecurityContext.
     * Defaults to "anonymous" if no authentication is present.
     */
    @Override
    @SneakyThrows
    public String get() {
        return AuthnUtil.authenticationToJsonString(SecurityContextHolder.getContext().getAuthentication(), mapper);
    }

}
