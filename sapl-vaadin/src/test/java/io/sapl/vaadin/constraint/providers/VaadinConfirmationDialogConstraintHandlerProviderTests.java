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
package io.sapl.vaadin.constraint.providers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vaadin.flow.component.UI;

import io.sapl.vaadin.UIMock;
import reactor.core.publisher.Mono;

class VaadinConfirmationDialogConstraintHandlerProviderTests {

    private VaadinConfirmationDialogConstraintHandlerProvider vaadinConfirmationDialogConstraintHandlerProvider;

    @BeforeEach
    void setUp() {
        this.vaadinConfirmationDialogConstraintHandlerProvider = spy(
                VaadinConfirmationDialogConstraintHandlerProvider.class);
    }

    @Test
    void when_constraintIsNull_then_providerIsNotResponsible() {
        // GIVEN
        // WHEN
        boolean isResponsibleResult = this.vaadinConfirmationDialogConstraintHandlerProvider.isResponsible(null);

        // THEN
        assertFalse(isResponsibleResult);
    }

    @Test
    void when_constraintIsTaggedCorrectly_then_providerIsResponsible() {
        // GIVEN
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("type", "saplVaadin");
        node.put("id", "requestConfirmation");

        // WHEN
        boolean isResponsibleResult = this.vaadinConfirmationDialogConstraintHandlerProvider.isResponsible(node);

        // THEN
        assertTrue(isResponsibleResult);
    }

    @Test
    void when_constraintHasIncorrectID_then_providerIsNotResponsible() {
        // GIVEN
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("type", "saplVaadin");
        node.put("id", "log");

        // WHEN
        boolean isResponsibleResult = this.vaadinConfirmationDialogConstraintHandlerProvider.isResponsible(node);

        // THEN
        assertFalse(isResponsibleResult);
    }

    @Test
    void when_constraintHasNoID_then_providerIsNotResponsible() {
        // GIVEN
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("type", "saplVaadin");

        // WHEN
        boolean isResponsibleResult = this.vaadinConfirmationDialogConstraintHandlerProvider.isResponsible(node);

        // THEN
        assertFalse(isResponsibleResult);
    }

    @Test
    void when_constraintHasIncorrectType_then_providerIsNotResponsible() {
        // GIVEN
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("type", "test");
        node.put("id", "requestConfirmation");

        // WHEN
        boolean isResponsibleResult = this.vaadinConfirmationDialogConstraintHandlerProvider.isResponsible(node);

        // THEN
        assertFalse(isResponsibleResult);
    }

    @Test
    void when_constraintHasNoType_then_providerIsNotResponsible() {
        // GIVEN
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("id", "requestConfirmation");

        // WHEN
        boolean isResponsibleResult = this.vaadinConfirmationDialogConstraintHandlerProvider.isResponsible(node);

        // THEN
        assertFalse(isResponsibleResult);
    }

    @Test
    void when_constraintIsNull_then_getHandlerReturnsNull() {
        // GIVEN
        ObjectNode node = null;

        // WHEN
        Function<UI, Mono<Boolean>> handler = this.vaadinConfirmationDialogConstraintHandlerProvider.getHandler(node);

        // THEN
        assertNull(handler);
    }

    @Test
    void when_constraintHasDefaultValuesAndDialogIsConfirmed_then_getHandlerReturnsTrue() {
        // GIVEN
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("type", "saplVaadin");
        node.put("id", "requestConfirmation");

        UI mockedUI = UIMock.getMockedUI();
        doAnswer(invocation -> {
            invocation.getArgument(3, Runnable.class).run();
            return null;
        }).when(this.vaadinConfirmationDialogConstraintHandlerProvider).openConfirmDialog(anyString(), anyString(),
                anyString(), any(Runnable.class), anyString(), any(Runnable.class));

        // WHEN
        var getHandler = this.vaadinConfirmationDialogConstraintHandlerProvider.getHandler(node);

        // THEN
        assertEquals(Boolean.TRUE, getHandler.apply(mockedUI).block());
    }

    @Test
    void when_constraintHasCustomValuesAndDialogIsConfirmed_then_getHandlerReturnsTrue() {
        // GIVEN
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("type", "saplVaadin");
        node.put("id", "requestConfirmation");
        node.put("header", "test header");
        node.put("text", "test text");
        node.put("confirmText", "test confirmText");
        node.put("cancelText", "test cancelText");

        UI mockedUI = UIMock.getMockedUI();
        doAnswer(invocation -> {
            invocation.getArgument(3, Runnable.class).run();
            return null;
        }).when(this.vaadinConfirmationDialogConstraintHandlerProvider).openConfirmDialog(anyString(), anyString(),
                anyString(), any(Runnable.class), anyString(), any(Runnable.class));

        // WHEN
        var getHandler = this.vaadinConfirmationDialogConstraintHandlerProvider.getHandler(node);

        // THEN
        assertEquals(Boolean.TRUE, getHandler.apply(mockedUI).block());
    }

    @Test
    void when_constraintHasDefaultValuesAndDialogIsClosed_then_getHandlerReturnsFalse() {
        // GIVEN
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("type", "saplVaadin");
        node.put("id", "requestConfirmation");

        UI mockedUI = UIMock.getMockedUI();
        doAnswer(invocation -> {
            invocation.getArgument(5, Runnable.class).run();
            return null;
        }).when(this.vaadinConfirmationDialogConstraintHandlerProvider).openConfirmDialog(anyString(), anyString(),
                anyString(), any(Runnable.class), anyString(), any(Runnable.class));

        // WHEN
        var getHandler = this.vaadinConfirmationDialogConstraintHandlerProvider.getHandler(node);

        // THEN
        assertEquals(Boolean.FALSE, getHandler.apply(mockedUI).block());
    }

    @Test
    void when_openConfirmationDialogIsCalled_then_aNewConfirmDialogIsOpening() {
        // GIVEN
        try (var mockedConstructor = mockConstruction(VaadinConfirmationDialog.class,
                (confirmDialog, context) -> doNothing().when(confirmDialog).open())) {
            var aVaadinConfirmationDialogConstraintHandlerProvider = spy(
                    VaadinConfirmationDialogConstraintHandlerProvider.class);
            // WHEN
            aVaadinConfirmationDialogConstraintHandlerProvider.openConfirmDialog("header", "text", "confirm", () -> {
            }, "cancel", () -> {
            });
            // THEN
            assertNotNull(mockedConstructor.constructed().get(0));
            verify(mockedConstructor.constructed().get(0), times(1)).open();
        }
    }
}
