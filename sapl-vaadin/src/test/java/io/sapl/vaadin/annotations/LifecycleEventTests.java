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
package io.sapl.vaadin.annotations;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

import io.sapl.vaadin.annotation.annotations.LifecycleEvent;

public class LifecycleEventTests {

    @Test
    void when_BeforeEnterEvent_isNotNull() {
        assertNotNull(LifecycleEvent.BEFORE_ENTER_EVENT);
    }

    @Test
    void when_BeforeLeaveEvent_isNotNull() {
        assertNotNull(LifecycleEvent.BEFORE_LEAVE_EVENT);
    }
}
