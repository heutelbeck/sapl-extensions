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
package io.sapl.axon.queryhandling;

import org.axonframework.messaging.responsetypes.ResponseType;
import org.springframework.security.access.AccessDeniedException;

import lombok.RequiredArgsConstructor;
import lombok.Value;

/**
 * Utility class for wrapping update response payloads, enabling recoverable
 * subscription queries.
 *
 * @author Dominic Heutelbeck
 * @since 2.1.0
 *
 * @param <U> Payload type.
 */
@Value
@RequiredArgsConstructor
public class RecoverableResponse<U> {

    /**
     * Metadata key for the original update response type.
     */
    public static final String RECOVERABLE_UPDATE_TYPE_KEY = "recoverableUpdateType";

    ResponseType<U> responseType;
    U               payload;

    /**
     * Unwraps the wrapped response. No response present implies an
     * AccessDeniedException. In a map operation, this enables onErrorContinue.
     *
     * @return Unwrapped update, or an AccessDeniedException.
     */
    public U unwrap() {
        if (payload == null)
            throw new AccessDeniedException("Access Denied");

        return payload;
    }

    /**
     * @return true, if no payload present.
     */
    public boolean isAccessDenied() {
        return payload == null;
    }

    /**
     * Utility factory method creating access denied responses.
     *
     * @param <T>          The Response type.
     * @param responseType The Response type.
     * @return An access denied response.
     */
    public static <T> RecoverableResponse<T> accessDenied(ResponseType<T> responseType) {
        return new RecoverableResponse<>(responseType, null);
    }
}
