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
package io.sapl.vaadin.constraint.providers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;

import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.notification.Notification;

import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import io.sapl.vaadin.UIMock;
import reactor.core.publisher.Mono;

class VaadinNotificationConstraintHandlerProviderTests {

    private VaadinNotificationConstraintHandlerProvider vaadinNotificationConstraintHandlerProvider;

    @BeforeEach
    void setUp() {
        this.vaadinNotificationConstraintHandlerProvider = new VaadinNotificationConstraintHandlerProvider();
    }

    @Test
    void when_constraintIsTaggedCorrectly_then_providerIsResponsible() {
        // GIVEN
        ObjectValue node = ObjectValue.builder().put("type", Value.of("saplVaadin"))
                .put("id", Value.of("showNotification")).build();

        // WHEN
        boolean isResponsibleResult = this.vaadinNotificationConstraintHandlerProvider.isResponsible(node);

        // THEN
        assertTrue(isResponsibleResult);
    }

    @Test
    void when_constraintHasIncorrectID_then_providerIsNotResponsible() {
        // GIVEN
        ObjectValue node = ObjectValue.builder().put("type", Value.of("saplVaadin")).put("id", Value.of("log")).build();

        // WHEN
        boolean isResponsibleResult = this.vaadinNotificationConstraintHandlerProvider.isResponsible(node);

        // THEN
        assertFalse(isResponsibleResult);
    }

    @Test
    void when_constraintHasNoID_then_providerIsNotResponsible() {
        // GIVEN
        ObjectValue node = ObjectValue.builder().put("type", Value.of("saplVaadin")).build();

        // WHEN
        boolean isResponsibleResult = this.vaadinNotificationConstraintHandlerProvider.isResponsible(node);

        // THEN
        assertFalse(isResponsibleResult);
    }

    @Test
    void when_constraintHasIncorrectType_then_providerIsNotResponsible() {
        // GIVEN
        ObjectValue node = ObjectValue.builder().put("type", Value.of("test")).put("id", Value.of("showNotification"))
                .build();

        // WHEN
        boolean isResponsibleResult = this.vaadinNotificationConstraintHandlerProvider.isResponsible(node);

        // THEN
        assertFalse(isResponsibleResult);
    }

    @Test
    void when_constraintHasNoType_then_providerIsNotResponsible() {
        // GIVEN
        ObjectValue node = ObjectValue.builder().put("id", Value.of("showNotification")).build();

        // WHEN
        boolean isResponsibleResult = this.vaadinNotificationConstraintHandlerProvider.isResponsible(node);

        // THEN
        assertFalse(isResponsibleResult);
    }

    @Test
    void when_constraintIsNull_then_providerIsNotResponsible() {
        // GIVEN
        // WHEN
        boolean isResponsibleResult = this.vaadinNotificationConstraintHandlerProvider.isResponsible(null);

        // THEN
        assertFalse(isResponsibleResult);
    }

    @Test
    void when_constraintIsNull_then_getHandlerReturnsNull() {
        // GIVEN
        // WHEN
        Function<UI, Mono<Boolean>> handler = this.vaadinNotificationConstraintHandlerProvider.getHandler(null);

        // THEN
        assertNull(handler);
    }

    @Test
    void when_constraintHasCustomValues_then_notificationsIsShownAndReturnsTrue() {
        // GIVEN
        ObjectValue node     = ObjectValue.builder().put("type", Value.of("saplVaadin"))
                .put("id", Value.of("showNotification")).put("message", Value.of("text message"))
                .put("duration", Value.of(6000)).put("position", Value.of("TOP_START")).build();
        var         mockedUI = UIMock.getMockedUI();

        // mock Notification.show()
        MockedStatic<Notification> notificationMock = mockStatic(Notification.class);
        notificationMock.when(() -> Notification.show(anyString(), anyInt(), any(Notification.Position.class)))
                .then(invocationOnMock -> {
                    assertEquals("text message", invocationOnMock.getArgument(0));
                    assertEquals(6000, (Integer) invocationOnMock.getArgument(1));
                    assertEquals(Notification.Position.TOP_START, invocationOnMock.getArgument(2));
                    return null;
                });

        // WHEN
        var getHandler = this.vaadinNotificationConstraintHandlerProvider.getHandler(node);

        // THEN
        assertEquals(Boolean.TRUE, getHandler.apply(mockedUI).block());
        notificationMock.close();
    }

    @Test
    void when_constraintHasCustomValuesAndInvalidPosition_then_notificationsIsShownAndReturnsTrue() {
        // GIVEN
        ObjectValue node     = ObjectValue.builder().put("type", Value.of("saplVaadin"))
                .put("id", Value.of("showNotification")).put("message", Value.of("text message"))
                .put("position", Value.of("invalid_value")).build();
        var         mockedUI = UIMock.getMockedUI();

        // mock Notification.show()
        MockedStatic<Notification> notificationMock = mockStatic(Notification.class);
        notificationMock.when(() -> Notification.show(anyString(), anyInt(), any(Notification.Position.class)))
                .then(invocationOnMock -> {
                    assertEquals("text message", invocationOnMock.getArgument(0));
                    assertEquals(5000, (Integer) invocationOnMock.getArgument(1));
                    assertEquals(Notification.Position.TOP_STRETCH, invocationOnMock.getArgument(2));
                    return null;
                });

        // WHEN
        var getHandler = this.vaadinNotificationConstraintHandlerProvider.getHandler(node);

        // THEN
        assertEquals(Boolean.TRUE, getHandler.apply(mockedUI).block());
        notificationMock.close();
    }

    @Test
    void when_constraintHasCustomValuesAndNoPosition_then_notificationsIsShownAndReturnsTrue() {
        // GIVEN
        ObjectValue node     = ObjectValue.builder().put("type", Value.of("saplVaadin"))
                .put("id", Value.of("showNotification")).build();
        var         mockedUI = UIMock.getMockedUI();

        // mock Notification.show()
        MockedStatic<Notification> notificationMock = mockStatic(Notification.class);
        notificationMock.when(() -> Notification.show(anyString(), anyInt(), any(Notification.Position.class)))
                .then(invocationOnMock -> {
                    assertEquals(5000, (Integer) invocationOnMock.getArgument(1));
                    assertEquals(Notification.Position.TOP_STRETCH, invocationOnMock.getArgument(2));
                    return null;
                });

        // WHEN
        var getHandler = this.vaadinNotificationConstraintHandlerProvider.getHandler(node);

        // THEN
        assertEquals(Boolean.TRUE, getHandler.apply(mockedUI).block());
        notificationMock.close();
    }
}
