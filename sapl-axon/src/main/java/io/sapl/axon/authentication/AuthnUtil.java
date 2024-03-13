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
package io.sapl.axon.authentication;

import org.springframework.security.core.Authentication;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

/**
 * Tool to map Authentication to JSON String.
 *
 * @author Dominic Heutelbeck
 * @since 2.1.0
 */
@UtilityClass
public class AuthnUtil {

    /**
     * @param authentication the Authentication
     * @param mapper         the ObjectMapper
     * @return the Authentication as a JSON String
     */
    @SneakyThrows
    public static String authenticationToJsonString(Authentication authentication, ObjectMapper mapper) {
        if (authentication == null)
            return "\"anonymous\"";

        JsonNode subject = mapper.valueToTree(authentication.getPrincipal());
        if (subject.isObject()) {
            ((ObjectNode) subject).remove("credentials");
            ((ObjectNode) subject).remove("password");
            var principal = subject.get("principal");
            if (principal != null && principal.isObject()) {
                ((ObjectNode) principal).remove("password");
            }
        }
        return mapper.writeValueAsString(subject);
    }

}
