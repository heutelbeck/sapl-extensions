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
package io.sapl.vaadin.annotation;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinServiceInitListener;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(value = "module.enabled", havingValue = "true", matchIfMissing = true)
public class LifecycleNavigationConfiguration implements VaadinServiceInitListener {

    private final VaadinNavigationPepService vaadinNavigationPepService;

    @Override
    public void serviceInit(ServiceInitEvent event) {

        event.getSource().addUIInitListener(uiEvent -> {
            final UI ui = uiEvent.getUI();
            ui.addBeforeEnterListener(vaadinNavigationPepService::beforeEnter);
            ui.addBeforeLeaveListener(vaadinNavigationPepService::beforeLeave);
        });
    }

}
