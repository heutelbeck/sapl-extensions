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
package io.sapl.vaadin;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import com.vaadin.flow.component.UI;

import reactor.core.publisher.Mono;

/**
 * This class is a container class for the following constraint handler types: -
 * Runnable handlers - Consumer handlers: UI is passed - Vaadin function
 * handlers: UI and current decision is passed
 */
public class VaadinConstraintHandlerBundle {
    public final List<Function<UI, Mono<Boolean>>> vaadinFunctionHandlerList = new LinkedList<>();
    public final List<Consumer<UI>>                consumerHandlerList       = new LinkedList<>();
    public final List<Runnable>                    runnableHandlerList       = new LinkedList<>();
}
