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
package io.sapl.axon.constrainthandling.api;

import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import org.axonframework.messaging.responsetypes.MultipleInstancesResponseType;
import org.axonframework.messaging.responsetypes.OptionalResponseType;
import org.axonframework.messaging.responsetypes.PublisherResponseType;
import org.axonframework.messaging.responsetypes.ResponseType;
import org.reactivestreams.Publisher;

/**
 * Interface for constraint handlers requiring a specific response type to be
 * present to work properly.
 *
 * @author Dominic Heutelbeck
 * @since 2.1.0
 */
public interface ResponseTypeSupport {

    /**
     * @return the supported ResponseTypes
     */
    Set<ResponseType<?>> getSupportedResponseTypes();

    /**
     * Checks if the constraint handler can work with the given type.
     *
     * @param responseType the response {@link java.lang.reflect.Type} of the
     * handler which is matched against
     * @return true if a response can be converted based on the given
     * {@code responseType} and false if it cannot
     */
    default boolean supports(Class<?> responseType) {
        return getSupportedResponseTypes().stream().anyMatch(isSupportedType(responseType));
    }

    private Predicate<? super ResponseType<?>> isSupportedType(Class<?> payloadType) {
        return type -> {
            if (type instanceof MultipleInstancesResponseType) {
                return payloadType.isArray() || Iterable.class.isAssignableFrom(payloadType);
            }
            if (type instanceof OptionalResponseType) {
                return Optional.class.isAssignableFrom(payloadType);
            }
            if (type instanceof PublisherResponseType) {
                return Publisher.class.isAssignableFrom(payloadType);
            }
            return type.getExpectedResponseType().isAssignableFrom(payloadType);
        };
    }

    /**
     * Checks if the constraint handler can work with the given type.
     *
     * @param responseType the response {@link java.lang.reflect.Type} of the
     * handler which is matched against
     * @return true if a response can be converted based on the given
     * {@code responseType} and false if it cannot
     */
    default boolean supports(ResponseType<?> responseType) {
        return getSupportedResponseTypes().stream().anyMatch(compatibleResponseType(responseType));
    }

    private Predicate<? super ResponseType<?>> compatibleResponseType(ResponseType<?> responseType) {
        return supportedType -> {
            if (!supportedType.getClass().equals(responseType.getClass()))
                return false;
            return supportedType.getExpectedResponseType().isAssignableFrom(responseType.getExpectedResponseType());
        };
    }
}
