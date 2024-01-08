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
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

class VaadinConfirmationDialogTests {

    @Test
    void closeAndConfirm() {
        // GIVEN
        AtomicReference<Boolean> result                   = new AtomicReference<>();
        var                      vaadinConfirmationDialog = spy(new VaadinConfirmationDialog("header", "text body",
                "confirm", () -> result.set(true), "cancel", () -> result.set(false)));

        // WHEN
        vaadinConfirmationDialog.closeAndConfirm();

        // THEN
        verify(vaadinConfirmationDialog, times(1)).close();
        assertEquals(true, result.get());
    }

    @Test
    void closeAndCancel() {
        // GIVEN
        AtomicReference<Boolean> result                   = new AtomicReference<>();
        var                      vaadinConfirmationDialog = spy(new VaadinConfirmationDialog("header", "text body",
                "confirm", () -> result.set(true), "cancel", () -> result.set(false)));

        // WHEN
        vaadinConfirmationDialog.closeAndCancel();

        // THEN
        verify(vaadinConfirmationDialog, times(1)).close();
        assertEquals(false, result.get());
    }
}
