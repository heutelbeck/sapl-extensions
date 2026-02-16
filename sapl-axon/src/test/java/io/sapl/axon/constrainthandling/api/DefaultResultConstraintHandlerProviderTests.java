/*
 * Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import static io.sapl.axon.TestUtilities.matches;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.axonframework.commandhandling.GenericCommandResultMessage;
import org.axonframework.messaging.GenericResultMessage;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.ResultMessage;
import org.axonframework.queryhandling.GenericQueryResponseMessage;
import org.axonframework.queryhandling.GenericSubscriptionQueryUpdateMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.sapl.api.model.Value;

@DisplayName("Default result constraint handler provider")
class DefaultResultConstraintHandlerProviderTests {

    private static final Value            DEFAULT_CONSTRAINT          = Value.of("default test constraint");
    private static final String           DEFAULT_RESULT              = "default test result";
    private static final Throwable        DEFAULT_THROWABLE           = new Throwable("default test throwable");
    private static final ResultMessage<?> DEFAULT_RESULT_MESSAGE      = new GenericResultMessage<String>(
            DEFAULT_RESULT);
    private static final ResultMessage<?> DEFAULT_EXCEPTIONAL_MESSAGE = new GenericResultMessage<String>(
            DEFAULT_THROWABLE);

    private ResultConstraintHandlerProvider defaultProvider;

    @BeforeEach
    void beforeEach() {
        defaultProvider = mock(ResultConstraintHandlerProvider.class);
        when(defaultProvider.getHandler(any(Value.class))).thenCallRealMethod();
        when(defaultProvider.getResultMessageHandler(any(ResultMessage.class), any(Value.class))).thenCallRealMethod();
        when(defaultProvider.mapThrowable(any(Throwable.class), any(Value.class))).thenCallRealMethod();
        when(defaultProvider.mapPayloadType(any(Class.class), any(Value.class))).thenCallRealMethod();
        when(defaultProvider.mapPayload(any(Object.class), any(Class.class), any(Value.class))).thenCallRealMethod();
        when(defaultProvider.mapMetadata(any(MetaData.class), any(Value.class))).thenCallRealMethod();
    }

    @Test
    void when_default_then_identity() {
        var handler       = defaultProvider.getHandler(DEFAULT_CONSTRAINT);
        var handledResult = handler.apply(DEFAULT_RESULT_MESSAGE);

        assertThat(matches(DEFAULT_RESULT_MESSAGE, handledResult)).isTrue();
        verify(defaultProvider, times(1)).accept(DEFAULT_RESULT_MESSAGE, DEFAULT_CONSTRAINT);
    }

    @Test
    void when_default_with_mappedMetadata_then_newMetaData() {
        var metaData = new MetaData(Map.of("key1", "value1", "key2", "value2"));
        when(defaultProvider.mapMetadata(any(MetaData.class), any(Value.class))).thenReturn(metaData);

        var handler       = defaultProvider.getHandler(DEFAULT_CONSTRAINT);
        var handledResult = handler.apply(DEFAULT_RESULT_MESSAGE);

        assertThatCode(() -> {
            var resultMessage = (ResultMessage<?>) handledResult;
            assertThat(resultMessage.getIdentifier()).isEqualTo(DEFAULT_RESULT_MESSAGE.getIdentifier());
            assertThat(resultMessage.getPayloadType()).isEqualTo(DEFAULT_RESULT_MESSAGE.getPayloadType());
            assertThat(resultMessage.isExceptional()).isEqualTo(DEFAULT_RESULT_MESSAGE.isExceptional());
            assertThat(resultMessage.getPayload()).isEqualTo(DEFAULT_RESULT_MESSAGE.getPayload());
            assertThat(resultMessage.getMetaData()).isEqualTo(metaData);
        }).doesNotThrowAnyException();
        verify(defaultProvider, times(1)).accept(DEFAULT_RESULT_MESSAGE, DEFAULT_CONSTRAINT);
    }

    @Test
    void when_default_with_exceptionalResult_then_identity() {
        var handler       = defaultProvider.getHandler(DEFAULT_CONSTRAINT);
        var handledResult = handler.apply(DEFAULT_EXCEPTIONAL_MESSAGE);

        assertThat(matches(DEFAULT_EXCEPTIONAL_MESSAGE, handledResult)).isTrue();
        verify(defaultProvider, times(1)).accept(DEFAULT_EXCEPTIONAL_MESSAGE, DEFAULT_CONSTRAINT);
    }

    @Test
    void when_default_with_exceptionalResult_and_mappedThrowable_then_newThrowable() {
        var throwable = new Throwable("message1");
        when(defaultProvider.mapThrowable(any(Throwable.class), any(Value.class))).thenReturn(throwable);

        var handler       = defaultProvider.getHandler(DEFAULT_CONSTRAINT);
        var handledResult = handler.apply(DEFAULT_EXCEPTIONAL_MESSAGE);

        assertThatCode(() -> {
            var resultMessage = (ResultMessage<?>) handledResult;
            assertThat(resultMessage.getIdentifier()).isEqualTo(DEFAULT_EXCEPTIONAL_MESSAGE.getIdentifier());
            assertThat(resultMessage.getPayloadType()).isEqualTo(DEFAULT_EXCEPTIONAL_MESSAGE.getPayloadType());
            assertThat(resultMessage.isExceptional()).isEqualTo(DEFAULT_EXCEPTIONAL_MESSAGE.isExceptional());
            assertThat(resultMessage.exceptionResult().getClass()).isEqualTo(throwable.getClass());
            assertThat(resultMessage.exceptionResult().getLocalizedMessage())
                    .isEqualTo(throwable.getLocalizedMessage());
        }).doesNotThrowAnyException();
        verify(defaultProvider, times(1)).accept(DEFAULT_EXCEPTIONAL_MESSAGE, DEFAULT_CONSTRAINT);
    }

    @Test
    void when_default_with_subscriptionQueryUpdateMessage_then_identity() {
        var result = new GenericSubscriptionQueryUpdateMessage<>(DEFAULT_RESULT);

        var handler       = defaultProvider.getHandler(DEFAULT_CONSTRAINT);
        var handledResult = handler.apply(result);

        assertThat(matches(result, handledResult)).isTrue();
        verify(defaultProvider, times(1)).accept(result, DEFAULT_CONSTRAINT);
    }

    @Test
    void when_default_with_queryResponseMessage_then_identity() {
        var result = new GenericQueryResponseMessage<>(DEFAULT_RESULT);

        var handler       = defaultProvider.getHandler(DEFAULT_CONSTRAINT);
        var handledResult = handler.apply(result);

        assertThat(matches(result, handledResult)).isTrue();
        verify(defaultProvider, times(1)).accept(result, DEFAULT_CONSTRAINT);
    }

    @Test
    void when_default_with_commandResultMessage_then_identity() {
        var result = new GenericCommandResultMessage<>(DEFAULT_RESULT);

        var handler       = defaultProvider.getHandler(DEFAULT_CONSTRAINT);
        var handledResult = handler.apply(result);

        assertThat(matches(result, handledResult)).isTrue();
        verify(defaultProvider, times(1)).accept(result, DEFAULT_CONSTRAINT);
    }

    @Test
    void when_default_with_nonResultMessage_then_mapPayload() {
        var handler       = defaultProvider.getHandler(DEFAULT_CONSTRAINT);
        var handledResult = handler.apply("not a response message!");

        assertThat(handledResult).isEqualTo("not a response message!");
        verify(defaultProvider, times(1)).mapPayload("not a response message!", Object.class, DEFAULT_CONSTRAINT);
    }
}
