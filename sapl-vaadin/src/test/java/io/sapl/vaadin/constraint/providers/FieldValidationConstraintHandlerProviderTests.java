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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Locale;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.datetimepicker.DateTimePicker;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.timepicker.TimePicker;
import com.vaadin.flow.data.binder.Binder;

import lombok.Data;

class FieldValidationConstraintHandlerProviderTests {

    static class TestForm extends VerticalLayout {
        private IntegerField   integerField;
        private DateTimePicker dateTimeField;
        private TimePicker     timeField;
    }

    @Data
    static class TestData {
        private final Integer integerField = 0;
        private LocalDateTime dateTimeField;
        private LocalTime     timeField;
    }

    private Binder<TestData>      binder;
    private TestForm              form;
    private final JsonNodeFactory JSON   = JsonNodeFactory.instance;
    private final ObjectMapper    MAPPER = new ObjectMapper();
    private final UI              ui     = mock(UI.class);

    @BeforeAll
    static void setUp() {
    }

    @BeforeEach
    void setupTest() {
        // ui
        when(ui.getLocale()).thenReturn(Locale.ENGLISH);
        UI.setCurrent(ui);

        // binder
        binder = spy(new Binder<>(TestData.class));

        // form
        form               = new TestForm();
        form.integerField  = new IntegerField();
        form.dateTimeField = new DateTimePicker();
        form.timeField     = new TimePicker();
    }

    @Test
    void when_bindFieldIsCalled_then_ValidatorIsAddedToBinder() {
        // GIVEN
        var sut = new FieldValidationConstraintHandlerProvider(binder, form);
        // WHEN
        sut.bindField(form.integerField);
        // THEN
        verify(binder).forMemberField(form.integerField);
    }

    @Test
    void when_bindFieldIsCalledWithInvalidField_then_ErrorShouldOccur() {
        // GIVEN
        var sut = new FieldValidationConstraintHandlerProvider(binder, form);
        // WHEN + THEN
        assertThrows(Exception.class, () -> sut.bindField(null));
    }

    @Test
    void when_constraintIsTaggedCorrectly_then_providerIsResponsible() throws JsonProcessingException {
        // GIVEN
        var sut        = new FieldValidationConstraintHandlerProvider(binder, form);
        var constraint = MAPPER.readTree("""
                {
                  "type" : "saplVaadin",
                  "id"   : "validation"
                }
                """);
        // WHEN+THEN
        assertTrue(sut.isResponsible(constraint));
    }

    @Test
    void when_constraintIsTaggedIncorrectlyWithInvalidID_then_providerIsNotResponsible()
            throws JsonProcessingException {
        // GIVEN
        var sut        = new FieldValidationConstraintHandlerProvider(binder, form, new ObjectMapper());
        var constraint = MAPPER.readTree("""
                {
                  "type" : "saplVaadin",
                  "id"   : "showNotification"
                }
                """);
        // WHEN+THEN
        assertFalse(sut.isResponsible(constraint));
    }

    @Test
    void when_constraintIsTaggedIncorrectlyWithInvalidType_then_providerIsNotResponsible()
            throws JsonProcessingException {
        // GIVEN
        var sut        = new FieldValidationConstraintHandlerProvider(binder, form, new ObjectMapper());
        var constraint = MAPPER.readTree("""
                {
                  "type" : "saplVaadin",
                  "id"   : "showNotification"
                }
                """);
        // WHEN+THEN
        assertFalse(sut.isResponsible(constraint));
    }

    @Test
    void when_constraintIsTaggedIncorrectlyWithoutType_then_providerIsNotResponsible() throws JsonProcessingException {
        // GIVEN
        var sut        = new FieldValidationConstraintHandlerProvider(binder, form, new ObjectMapper());
        var constraint = MAPPER.readTree("""
                {
                  "id" : "validation"
                }
                """);
        // WHEN+THEN
        assertFalse(sut.isResponsible(constraint));
    }

    @Test
    void when_constraintIsTaggedIncorrectlyWithoutID_then_providerIsNotResponsible() throws JsonProcessingException {
        // GIVEN
        var sut        = new FieldValidationConstraintHandlerProvider(binder, form, new ObjectMapper());
        var constraint = MAPPER.readTree("""
                {
                  "type" : "saplVaadin"
                }
                """);
        // WHEN+THEN
        assertFalse(sut.isResponsible(constraint));
    }

    @Test
    void when_constraintIsEmptyOrNull_then_providerIsNotResponsible() {
        // GIVEN
        var        sut        = new FieldValidationConstraintHandlerProvider(binder, form, new ObjectMapper());
        ObjectNode constraint = JSON.objectNode();
        // WHEN+THEN
        assertFalse(sut.isResponsible(constraint));
        assertFalse(sut.isResponsible(null));
    }

    @Test
    void when_getSupportedTypeIsCalled_then_resultIsValid() {
        // GIVEN
        var sut = new FieldValidationConstraintHandlerProvider(binder, form, new ObjectMapper());
        // WHEN+THEN
        assertNull(sut.getSupportedType());
    }

    @Test
    void when_constraintInDecision_then_validValueIsDetectedCorrectly() throws JsonProcessingException {
        // GIVEN
        var sut = new FieldValidationConstraintHandlerProvider(binder, form);
        sut.bindField(form.integerField);
        binder.bindInstanceFields(form);

        // constraint
        var constraint = MAPPER.readTree("""
                {
                  "type" : "saplVaadin",
                  "id"   : "validation",
                  "fields" : {
                    "integerField" : {
                      "$schema" : "http://json-schema.org/draft-07/schema#",
                      "type"    : "number",
                      "maximum" : 20,
                      "message" : "maximum is limited to 20"
                    }
                  }
                }
                """);

        // WHEN
        sut.getHandler(constraint).accept(ui);
        form.integerField.setValue(10);

        // THEN
        assertFalse(form.integerField.isInvalid());
    }

    @Test
    void when_constraintHasNoFields_then_updateValidationSchemesDoNothing() throws JsonProcessingException {
        // GIVEN
        var mockedForm = spy(form);
        var sut        = new FieldValidationConstraintHandlerProvider(binder, mockedForm);
        sut.bindField(mockedForm.integerField);

        // constraint
        var constraint = MAPPER.readTree("""
                {
                  "type" : "saplVaadin",
                  "id"   : "validation"
                }
                """);

        // WHEN
        sut.getHandler(constraint).accept(ui);

        // THEN
        verifyNoInteractions(mockedForm);
    }

    @Test
    void when_constraintInDecision_then_invalidValueIsDetected() throws JsonProcessingException {
        // GIVEN
        var sut = new FieldValidationConstraintHandlerProvider(binder, form);
        sut.bindField(form.integerField);
        binder.bindInstanceFields(form);
        UI ui = mock(UI.class);

        // constraint
        var constraint = MAPPER.readTree("""
                {
                  "type" : "saplVaadin",
                  "id"   : "validation",
                  "fields" : {
                    "integerField" : {
                      "type"    : "number",
                      "maximum" : 20
                    }
                  }
                }
                """);

        // WHEN
        sut.getHandler(constraint).accept(ui);
        form.integerField.setValue(21);

        // THEN
        assertTrue(form.integerField.isInvalid());
    }

    @Test
    void when_constraintInDecision_then_invalidValueIsDetectedWithCustomMessage() throws JsonProcessingException {
        // GIVEN
        var sut = new FieldValidationConstraintHandlerProvider(binder, form);
        sut.bindField(form.integerField);
        binder.bindInstanceFields(form);
        UI ui = mock(UI.class);

        // constraint
        var constraint = MAPPER.readTree("""
                {
                  "type" : "saplVaadin",
                  "id"   : "validation",
                  "fields" : {
                    "integerField" : {
                      "$schema" : "http://json-schema.org/draft-07/schema#",
                      "type"    : "number",
                      "maximum" : 20,
                      "message" : "maximum is limited to 20"
                    }
                  }
                }
                """);

        // WHEN
        sut.getHandler(constraint).accept(ui);
        form.integerField.setValue(21);

        // THEN
        assertTrue(form.integerField.isInvalid());
    }

    @Test
    void when_constraintForUnboundFieldInDecision_then_throwException() throws JsonProcessingException {
        // GIVEN
        var sut = new FieldValidationConstraintHandlerProvider(binder, form);
        sut.bindField(form.integerField);
        binder.bindInstanceFields(form);

        // constraint
        var constraint = MAPPER.readTree("""
                {
                  "type" : "saplVaadin",
                  "id"   : "validation",
                  "fields" : {
                    "field42" : {
                      "$schema" : "http://json-schema.org/draft-07/schema#",
                      "type"    : "number",
                      "maximum" : 20,
                      "message" : "maximum is limited to 20"
                    }
                  }
                }
                """);
        // WHEN+THEN
        assertThrows(AccessDeniedException.class, () -> sut.getHandler(constraint));
    }

    @Test
    void when_constraintIsNull_then_nullHandlerIsReturned() {
        // GIVEN
        var sut = new FieldValidationConstraintHandlerProvider(binder, form);
        sut.bindField(form.integerField);
        binder.bindInstanceFields(form);

        // WHEN+THEN
        assertNull(sut.getHandler(null));
    }

    @Test
    void when_constraintHasDateTimeFormat_then_constraintIsApplied()
            throws JsonMappingException, JsonProcessingException {
        // GIVEN
        var sut = new FieldValidationConstraintHandlerProvider(binder, form);
        sut.bindField(form.dateTimeField);
        binder.bindInstanceFields(form);

        // constraint
        var constraint = MAPPER.readTree("""
                {
                  "type"   : "saplVaadin",
                  "id"     : "validation",
                  "fields" : {
                    "dateTimeField" : {
                      "type"  : "string",
                      "format": "date-time"
                    }
                  }
                }
                """);

        // WHEN+THEN
        sut.getHandler(constraint).accept(ui);
        // check valid value
        form.dateTimeField.setValue(LocalDateTime.parse("2022-04-01T10:00:00"));
        assertFalse(form.dateTimeField.isInvalid());
    }

    @Test
    void when_constraintHasTimeFormat_then_constraintIsApplied() throws JsonProcessingException {
        // GIVEN
        var sut = new FieldValidationConstraintHandlerProvider(binder, form);
        sut.bindField(form.timeField);
        binder.bindInstanceFields(form);

        // constraint
        var constraint = MAPPER.readTree("""
                {
                  "type" : "saplVaadin",
                  "id"   : "validation",
                  "fields" : {
                    "timeField" : {
                      "type"   : "string",
                      "format" : "time"
                    }
                  }
                }
                """);

        // WHEN+THEN
        sut.getHandler(constraint).accept(ui);
        // check valid value
        form.timeField.setValue(LocalTime.parse("10:00:00"));
        assertFalse(form.dateTimeField.isInvalid());
    }

    @Test
    void when_fieldCauseReflectionException_then_isFieldBoundReturnsFalse() throws IllegalAccessException {
        // GIVEN
        var sut         = new FieldValidationConstraintHandlerProvider(binder, form);
        var mockedField = mock(Field.class);
        doThrow(IllegalArgumentException.class).when(mockedField).get(any());
        // WHEN
        var isFieldBound = sut.isFieldBound(mockedField, null, null);
        // THEN
        assertFalse(isFieldBound);
    }

    @Test
    void when_constraintHasEmptyFields_then_updateValidationSchemesDoNothing() throws JsonProcessingException {
        // GIVEN
        var sut = new FieldValidationConstraintHandlerProvider(binder, form);
        sut.bindField(form.integerField);
        binder.bindInstanceFields(form);

        // constraint
        var constraint = MAPPER.readTree("""
                {
                  "type" : "saplVaadin",
                  "id"   : "validation"
                }
                """);
        // WHEN+THEN
        sut.getHandler(constraint).accept(ui);
        // THEN
        form.integerField.setValue(21);
        assertFalse(form.integerField.isInvalid());
    }
}
