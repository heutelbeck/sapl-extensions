/*
 * Copyright © 2019-2022 Dominic Heutelbeck (dominic@heutelbeck.com)
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

package io.sapl.mqtt.pep.constraint;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.hivemq.extension.sdk.api.interceptor.publish.parameter.PublishInboundOutput;
import com.hivemq.extension.sdk.api.packets.publish.ModifiablePublishPacket;
import com.hivemq.extension.sdk.api.packets.publish.PayloadFormatIndicator;
import io.sapl.api.pdp.IdentifiableAuthorizationDecision;
import io.sapl.mqtt.pep.cache.MqttClientState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static io.sapl.mqtt.pep.constraint.PublishConstraints.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class PublishConstraintsTest {

    @Test
    void when_specifiedConstraintTypeIsNotTextual_then_signalConstraintCouldNotBeHandled() {
        // GIVEN
        PublishInboundOutput publishInboundOutputMock = mock(PublishInboundOutput.class);
        ModifiablePublishPacket modifiablePublishPacketMock = mock(ModifiablePublishPacket.class);
        when(publishInboundOutputMock.getPublishPacket()).thenReturn(modifiablePublishPacketMock);
        ConstraintDetails constraintDetails =
                new ConstraintDetails("clientId", new IdentifiableAuthorizationDecision(),
                        "testTopic", publishInboundOutputMock);
        JsonNode constraint = JsonNodeFactory.instance.objectNode()
                .put(Constraints.ENVIRONMENT_CONSTRAINT_TYPE, 4);

        // WHEN
        boolean wasSuccessfullyHandled =
                PublishConstraints.enforcePublishConstraintEntries(constraintDetails, constraint);

        // THEN
        assertFalse(wasSuccessfullyHandled);
    }

    @Test
    void when_settingQosAndQosLevelNotSpecified_then_signalConstraintCouldNotBeHandled() {
        // GIVEN
        PublishInboundOutput publishInboundOutputMock = mock(PublishInboundOutput.class);
        ModifiablePublishPacket modifiablePublishPacketMock = mock(ModifiablePublishPacket.class);
        when(publishInboundOutputMock.getPublishPacket()).thenReturn(modifiablePublishPacketMock);
        ConstraintDetails constraintDetails =
                new ConstraintDetails("clientId", new IdentifiableAuthorizationDecision(),
                        "testTopic", publishInboundOutputMock);
        JsonNode constraint = JsonNodeFactory.instance.objectNode()
                .put(Constraints.ENVIRONMENT_CONSTRAINT_TYPE, ENVIRONMENT_QOS_CONSTRAINT);

        // WHEN
        boolean wasSuccessfullyHandled =
                PublishConstraints.enforcePublishConstraintEntries(constraintDetails, constraint);

        // THEN
        verify(modifiablePublishPacketMock, never()).setQos(any());
        assertFalse(wasSuccessfullyHandled);
    }

    @Test
    void when_settingQosAndQosLevelIsNotAIntegralNumber_then_signalConstraintCouldNotBeHandled() {
        // GIVEN
        PublishInboundOutput publishInboundOutputMock = mock(PublishInboundOutput.class);
        ModifiablePublishPacket modifiablePublishPacketMock = mock(ModifiablePublishPacket.class);
        when(publishInboundOutputMock.getPublishPacket()).thenReturn(modifiablePublishPacketMock);
        ConstraintDetails constraintDetails =
                new ConstraintDetails("clientId", new IdentifiableAuthorizationDecision(),
                        "testTopic", publishInboundOutputMock);
        JsonNode constraint = JsonNodeFactory.instance.objectNode()
                .put(Constraints.ENVIRONMENT_CONSTRAINT_TYPE, ENVIRONMENT_QOS_CONSTRAINT)
                .put(ENVIRONMENT_QOS_LEVEL, 1.2);

        // WHEN
        boolean wasSuccessfullyHandled =
                PublishConstraints.enforcePublishConstraintEntries(constraintDetails, constraint);

        // THEN
        verify(modifiablePublishPacketMock, never()).setQos(any());
        assertFalse(wasSuccessfullyHandled);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 3})
    void when_settingQosAndQosLevelIsIllegal_then_signalConstraintCouldNotBeHandled(int qosLevel) {
        // GIVEN
        PublishInboundOutput publishInboundOutputMock = mock(PublishInboundOutput.class);
        ModifiablePublishPacket modifiablePublishPacketMock = mock(ModifiablePublishPacket.class);
        when(publishInboundOutputMock.getPublishPacket()).thenReturn(modifiablePublishPacketMock);
        ConstraintDetails constraintDetails =
                new ConstraintDetails("clientId", new IdentifiableAuthorizationDecision(),
                        "testTopic", publishInboundOutputMock);
        JsonNode constraint = JsonNodeFactory.instance.objectNode()
                .put(Constraints.ENVIRONMENT_CONSTRAINT_TYPE, ENVIRONMENT_QOS_CONSTRAINT)
                .put(ENVIRONMENT_QOS_LEVEL, qosLevel);

        // WHEN
        boolean wasSuccessfullyHandled =
                PublishConstraints.enforcePublishConstraintEntries(constraintDetails, constraint);

        // THEN
        verify(modifiablePublishPacketMock, never()).setQos(any());
        assertFalse(wasSuccessfullyHandled);
    }

    @Test
    void when_constraintSetRetainFlagToEnabled_then_enableRetain() {
        // GIVEN
        PublishInboundOutput publishInboundOutputMock = mock(PublishInboundOutput.class);
        ModifiablePublishPacket modifiablePublishPacketMock = mock(ModifiablePublishPacket.class);
        when(publishInboundOutputMock.getPublishPacket()).thenReturn(modifiablePublishPacketMock);
        ConstraintDetails constraintDetails =
                new ConstraintDetails("clientId", new IdentifiableAuthorizationDecision(),
                        "testTopic", publishInboundOutputMock);
        JsonNode constraint = JsonNodeFactory.instance.objectNode()
                .put(Constraints.ENVIRONMENT_CONSTRAINT_TYPE, ENVIRONMENT_RETAIN_MESSAGE_CONSTRAINT)
                .put(ENVIRONMENT_STATUS, ENVIRONMENT_ENABLED);

        // WHEN
        boolean wasSuccessfullyHandled =
                PublishConstraints.enforcePublishConstraintEntries(constraintDetails, constraint);

        // THEN
        verify(modifiablePublishPacketMock, times(1)).setRetain(true);
        assertTrue(wasSuccessfullyHandled);
    }

    @Test
    void when_settingRetainFlagAndNoStatusWasSpecified_then_signalConstraintCouldNotBeHandled() {
        // GIVEN
        PublishInboundOutput publishInboundOutputMock = mock(PublishInboundOutput.class);
        ModifiablePublishPacket modifiablePublishPacketMock = mock(ModifiablePublishPacket.class);
        when(publishInboundOutputMock.getPublishPacket()).thenReturn(modifiablePublishPacketMock);
        ConstraintDetails constraintDetails =
                new ConstraintDetails("clientId", new IdentifiableAuthorizationDecision(),
                        "testTopic", publishInboundOutputMock);
        JsonNode constraint = JsonNodeFactory.instance.objectNode()
                .put(Constraints.ENVIRONMENT_CONSTRAINT_TYPE, ENVIRONMENT_RETAIN_MESSAGE_CONSTRAINT);

        // WHEN
        boolean wasSuccessfullyHandled =
                PublishConstraints.enforcePublishConstraintEntries(constraintDetails, constraint);

        // THEN
        verify(modifiablePublishPacketMock, never()).setRetain(anyBoolean());
        assertFalse(wasSuccessfullyHandled);
    }

    @Test
    void when_settingRetainFlagAndStatusIsNotTextual_then_signalConstraintCouldNotBeHandled() {
        // GIVEN
        PublishInboundOutput publishInboundOutputMock = mock(PublishInboundOutput.class);
        ModifiablePublishPacket modifiablePublishPacketMock = mock(ModifiablePublishPacket.class);
        when(publishInboundOutputMock.getPublishPacket()).thenReturn(modifiablePublishPacketMock);
        ConstraintDetails constraintDetails =
                new ConstraintDetails("clientId", new IdentifiableAuthorizationDecision(),
                        "testTopic", publishInboundOutputMock);
        JsonNode constraint = JsonNodeFactory.instance.objectNode()
                .put(Constraints.ENVIRONMENT_CONSTRAINT_TYPE, ENVIRONMENT_RETAIN_MESSAGE_CONSTRAINT)
                .put(ENVIRONMENT_STATUS, 1);

        // WHEN
        boolean wasSuccessfullyHandled =
                PublishConstraints.enforcePublishConstraintEntries(constraintDetails, constraint);

        // THEN
        verify(modifiablePublishPacketMock, never()).setRetain(anyBoolean());
        assertFalse(wasSuccessfullyHandled);
    }

    @Test
    void when_settingRetainFlagAndStatusIsIllegalTextual_then_signalConstraintCouldNotBeHandled() {
        // GIVEN
        PublishInboundOutput publishInboundOutputMock = mock(PublishInboundOutput.class);
        ModifiablePublishPacket modifiablePublishPacketMock = mock(ModifiablePublishPacket.class);
        when(publishInboundOutputMock.getPublishPacket()).thenReturn(modifiablePublishPacketMock);
        ConstraintDetails constraintDetails =
                new ConstraintDetails("clientId", new IdentifiableAuthorizationDecision(),
                        "testTopic", publishInboundOutputMock);
        JsonNode constraint = JsonNodeFactory.instance.objectNode()
                .put(Constraints.ENVIRONMENT_CONSTRAINT_TYPE, ENVIRONMENT_RETAIN_MESSAGE_CONSTRAINT)
                .put(ENVIRONMENT_STATUS, "status");

        // WHEN
        boolean wasSuccessfullyHandled =
                PublishConstraints.enforcePublishConstraintEntries(constraintDetails, constraint);

        // THEN
        verify(modifiablePublishPacketMock, never()).setRetain(anyBoolean());
        assertFalse(wasSuccessfullyHandled);
    }

    @Test
    void when_settingRetainFlagAndStatusIsDisabled_then_setStatusAndSignalSuccessfulConstraintHandling() {
        // GIVEN
        PublishInboundOutput publishInboundOutputMock = mock(PublishInboundOutput.class);
        ModifiablePublishPacket modifiablePublishPacketMock = mock(ModifiablePublishPacket.class);
        when(publishInboundOutputMock.getPublishPacket()).thenReturn(modifiablePublishPacketMock);
        ConstraintDetails constraintDetails =
                new ConstraintDetails("clientId", new IdentifiableAuthorizationDecision(),
                        "testTopic", publishInboundOutputMock);
        JsonNode constraint = JsonNodeFactory.instance.objectNode()
                .put(Constraints.ENVIRONMENT_CONSTRAINT_TYPE, ENVIRONMENT_RETAIN_MESSAGE_CONSTRAINT)
                .put(ENVIRONMENT_STATUS, ENVIRONMENT_DISABLED);

        // WHEN
        boolean wasSuccessfullyHandled =
                PublishConstraints.enforcePublishConstraintEntries(constraintDetails, constraint);

        // THEN
        verify(modifiablePublishPacketMock, times(1)).setRetain(false);
        assertTrue(wasSuccessfullyHandled);
    }

    @Test
    void when_settingMessageExpiryIntervalAndNoTimeIntervalWasSpecified_then_signalConstraintCouldNotBeHandled() {
        // GIVEN
        PublishInboundOutput publishInboundOutputMock = mock(PublishInboundOutput.class);
        ModifiablePublishPacket modifiablePublishPacketMock = mock(ModifiablePublishPacket.class);
        when(publishInboundOutputMock.getPublishPacket()).thenReturn(modifiablePublishPacketMock);
        ConstraintDetails constraintDetails =
                new ConstraintDetails("clientId", new IdentifiableAuthorizationDecision(),
                        "testTopic", publishInboundOutputMock);
        JsonNode constraint = JsonNodeFactory.instance.objectNode()
                .put(Constraints.ENVIRONMENT_CONSTRAINT_TYPE, ENVIRONMENT_REPLACE_MESSAGE_EXPIRY_INTERVAL);

        // WHEN
        boolean wasSuccessfullyHandled =
                PublishConstraints.enforcePublishConstraintEntries(constraintDetails, constraint);

        // THEN
        verify(modifiablePublishPacketMock, never()).setMessageExpiryInterval(anyLong());
        assertFalse(wasSuccessfullyHandled);
    }

    @Test
    void when_settingMessageExpiryIntervalAndIllegalValueTypeWasSpecified_then_signalConstraintCouldNotBeHandled() {
        // GIVEN
        PublishInboundOutput publishInboundOutputMock = mock(PublishInboundOutput.class);
        ModifiablePublishPacket modifiablePublishPacketMock = mock(ModifiablePublishPacket.class);
        when(publishInboundOutputMock.getPublishPacket()).thenReturn(modifiablePublishPacketMock);
        ConstraintDetails constraintDetails =
                new ConstraintDetails("clientId", new IdentifiableAuthorizationDecision(),
                        "testTopic", publishInboundOutputMock);
        JsonNode constraint = JsonNodeFactory.instance.objectNode()
                .put(Constraints.ENVIRONMENT_CONSTRAINT_TYPE, ENVIRONMENT_REPLACE_MESSAGE_EXPIRY_INTERVAL)
                .put(ENVIRONMENT_TIME_INTERVAL, "five");

        // WHEN
        boolean wasSuccessfullyHandled =
                PublishConstraints.enforcePublishConstraintEntries(constraintDetails, constraint);

        // THEN
        verify(modifiablePublishPacketMock, never()).setMessageExpiryInterval(anyLong());
        assertFalse(wasSuccessfullyHandled);
    }

    @Test
    void when_settingMessageExpiryIntervalAndIllegalExpiryIntervalWasSpecified_then_signalConstraintCouldNotBeHandled() {
        // GIVEN
        PublishInboundOutput publishInboundOutputMock = mock(PublishInboundOutput.class);
        ModifiablePublishPacket modifiablePublishPacketMock = mock(ModifiablePublishPacket.class);
        doThrow(IllegalArgumentException.class).when(modifiablePublishPacketMock).setMessageExpiryInterval(-1);
        when(publishInboundOutputMock.getPublishPacket()).thenReturn(modifiablePublishPacketMock);
        ConstraintDetails constraintDetails =
                new ConstraintDetails("clientId", new IdentifiableAuthorizationDecision(),
                        "testTopic", publishInboundOutputMock);
        JsonNode constraint = JsonNodeFactory.instance.objectNode()
                .put(Constraints.ENVIRONMENT_CONSTRAINT_TYPE, ENVIRONMENT_REPLACE_MESSAGE_EXPIRY_INTERVAL)
                .put(ENVIRONMENT_TIME_INTERVAL, -1);

        // WHEN
        boolean wasSuccessfullyHandled =
                PublishConstraints.enforcePublishConstraintEntries(constraintDetails, constraint);

        // THEN
        verify(modifiablePublishPacketMock, times(1)).setMessageExpiryInterval(anyLong());
        assertFalse(wasSuccessfullyHandled);
    }

    @Test
    void when_settingContentTypeAndNoReplacementWasSpecified_then_signalConstraintCouldNotBeHandled() {
        // GIVEN
        PublishInboundOutput publishInboundOutputMock = mock(PublishInboundOutput.class);
        ModifiablePublishPacket modifiablePublishPacketMock = mock(ModifiablePublishPacket.class);
        when(publishInboundOutputMock.getPublishPacket()).thenReturn(modifiablePublishPacketMock);
        ConstraintDetails constraintDetails =
                new ConstraintDetails("clientId", new IdentifiableAuthorizationDecision(),
                        "testTopic", publishInboundOutputMock);
        JsonNode constraint = JsonNodeFactory.instance.objectNode()
                .put(Constraints.ENVIRONMENT_CONSTRAINT_TYPE, ENVIRONMENT_REPLACE_CONTENT_TYPE);

        // WHEN
        boolean wasSuccessfullyHandled =
                PublishConstraints.enforcePublishConstraintEntries(constraintDetails, constraint);

        // THEN
        verify(modifiablePublishPacketMock, never()).setContentType(anyString());
        assertFalse(wasSuccessfullyHandled);
    }

    @Test
    void when_settingContentTypeAndReplacementIsNotTextual_then_signalConstraintCouldNotBeHandled() {
        // GIVEN
        PublishInboundOutput publishInboundOutputMock = mock(PublishInboundOutput.class);
        ModifiablePublishPacket modifiablePublishPacketMock = mock(ModifiablePublishPacket.class);
        when(publishInboundOutputMock.getPublishPacket()).thenReturn(modifiablePublishPacketMock);
        ConstraintDetails constraintDetails =
                new ConstraintDetails("clientId", new IdentifiableAuthorizationDecision(),
                        "testTopic", publishInboundOutputMock);
        JsonNode constraint = JsonNodeFactory.instance.objectNode()
                .put(Constraints.ENVIRONMENT_CONSTRAINT_TYPE, ENVIRONMENT_REPLACE_CONTENT_TYPE)
                .put(ENVIRONMENT_REPLACEMENT, 5);

        // WHEN
        boolean wasSuccessfullyHandled =
                PublishConstraints.enforcePublishConstraintEntries(constraintDetails, constraint);

        // THEN
        verify(modifiablePublishPacketMock, never()).setContentType(anyString());
        assertFalse(wasSuccessfullyHandled);
    }

    @Test
    void when_settingContentTypeAndTheReplacementIsNotAValidUTF8String_then_signalConstraintCouldNotBeHandled() {
        // GIVEN
        PublishInboundOutput publishInboundOutputMock = mock(PublishInboundOutput.class);
        ModifiablePublishPacket modifiablePublishPacketMock = mock(ModifiablePublishPacket.class);
        doThrow(IllegalArgumentException.class).when(modifiablePublishPacketMock).setContentType("notAValidUTF8String");
        when(publishInboundOutputMock.getPublishPacket()).thenReturn(modifiablePublishPacketMock);
        ConstraintDetails constraintDetails =
                new ConstraintDetails("clientId", new IdentifiableAuthorizationDecision(),
                        "testTopic", publishInboundOutputMock);
        JsonNode constraint = JsonNodeFactory.instance.objectNode()
                .put(Constraints.ENVIRONMENT_CONSTRAINT_TYPE, ENVIRONMENT_REPLACE_CONTENT_TYPE)
                .put(ENVIRONMENT_REPLACEMENT, "notAValidUTF8String");

        // WHEN
        boolean wasSuccessfullyHandled =
                PublishConstraints.enforcePublishConstraintEntries(constraintDetails, constraint);

        // THEN
        verify(modifiablePublishPacketMock, times(1)).setContentType(anyString());
        assertFalse(wasSuccessfullyHandled);
    }

    @Test
    void when_settingPayloadAndNoReplacementWasSpecified_then_signalConstraintCouldNotBeHandled() {
        // GIVEN
        PublishInboundOutput publishInboundOutputMock = mock(PublishInboundOutput.class);
        ModifiablePublishPacket modifiablePublishPacketMock = mock(ModifiablePublishPacket.class);
        when(publishInboundOutputMock.getPublishPacket()).thenReturn(modifiablePublishPacketMock);
        ConstraintDetails constraintDetails =
                new ConstraintDetails("clientId", new IdentifiableAuthorizationDecision(),
                        "testTopic", publishInboundOutputMock);
        JsonNode constraint = JsonNodeFactory.instance.objectNode()
                .put(Constraints.ENVIRONMENT_CONSTRAINT_TYPE, ENVIRONMENT_REPLACE_PAYLOAD);

        // WHEN
        boolean wasSuccessfullyHandled =
                PublishConstraints.enforcePublishConstraintEntries(constraintDetails, constraint);

        // THEN
        verify(modifiablePublishPacketMock, never()).setPayload(any(ByteBuffer.class));
        assertFalse(wasSuccessfullyHandled);
    }

    @Test
    void when_settingPayloadAndReplacementIsNotTextual_then_signalConstraintCouldNotBeHandled() {
        // GIVEN
        PublishInboundOutput publishInboundOutputMock = mock(PublishInboundOutput.class);
        ModifiablePublishPacket modifiablePublishPacketMock = mock(ModifiablePublishPacket.class);
        when(publishInboundOutputMock.getPublishPacket()).thenReturn(modifiablePublishPacketMock);
        ConstraintDetails constraintDetails =
                new ConstraintDetails("clientId", new IdentifiableAuthorizationDecision(),
                        "testTopic", publishInboundOutputMock);
        JsonNode constraint = JsonNodeFactory.instance.objectNode()
                .put(Constraints.ENVIRONMENT_CONSTRAINT_TYPE, ENVIRONMENT_REPLACE_PAYLOAD)
                .put(ENVIRONMENT_REPLACEMENT, 5);

        // WHEN
        boolean wasSuccessfullyHandled =
                PublishConstraints.enforcePublishConstraintEntries(constraintDetails, constraint);

        // THEN
        verify(modifiablePublishPacketMock, never()).setPayload(any(ByteBuffer.class));
        assertFalse(wasSuccessfullyHandled);
    }

    @Test
    void when_blackenPayloadAndContentTypeAndPayloadFormatIndicatorAreNotSpecified_then_signalConstraintCouldNotBeHandled() {
        // GIVEN
        PublishInboundOutput publishInboundOutputMock = mock(PublishInboundOutput.class);
        ModifiablePublishPacket modifiablePublishPacketMock = mock(ModifiablePublishPacket.class);
        when(publishInboundOutputMock.getPublishPacket()).thenReturn(modifiablePublishPacketMock);
        ConstraintDetails constraintDetails =
                new ConstraintDetails("clientId", new IdentifiableAuthorizationDecision(),
                        "testTopic", publishInboundOutputMock);
        JsonNode constraint = JsonNodeFactory.instance.objectNode()
                .put(Constraints.ENVIRONMENT_CONSTRAINT_TYPE, ENVIRONMENT_BLACKEN_PAYLOAD)
                .put(ENVIRONMENT_REPLACEMENT, "*");

        // WHEN
        boolean wasSuccessfullyHandled =
                PublishConstraints.enforcePublishConstraintEntries(constraintDetails, constraint);

        // THEN
        verify(modifiablePublishPacketMock, never()).setPayload(any(ByteBuffer.class));
        assertFalse(wasSuccessfullyHandled);
    }

    @Test
    void when_blackenPayloadAndMessageContainsNoPayload_then_signalSuccessfulHandlingOfConstraint() {
        // GIVEN
        PublishInboundOutput publishInboundOutputMock = mock(PublishInboundOutput.class);
        ModifiablePublishPacket modifiablePublishPacketMock = mock(ModifiablePublishPacket.class);
        when(modifiablePublishPacketMock.getPayloadFormatIndicator())
                .thenReturn(Optional.of(PayloadFormatIndicator.UTF_8));
        when(publishInboundOutputMock.getPublishPacket()).thenReturn(modifiablePublishPacketMock);
        ConstraintDetails constraintDetails =
                new ConstraintDetails("clientId", new IdentifiableAuthorizationDecision(),
                        "testTopic", publishInboundOutputMock);
        JsonNode constraint = JsonNodeFactory.instance.objectNode()
                .put(Constraints.ENVIRONMENT_CONSTRAINT_TYPE, ENVIRONMENT_BLACKEN_PAYLOAD)
                .put(ENVIRONMENT_REPLACEMENT, "*");

        // WHEN
        boolean wasSuccessfullyHandled =
                PublishConstraints.enforcePublishConstraintEntries(constraintDetails, constraint);

        // THEN
        verify(modifiablePublishPacketMock, never()).setPayload(any(ByteBuffer.class));
        assertTrue(wasSuccessfullyHandled);
    }

    @Test
    void when_blackenPayloadAndADiscloseParameterIsNotSpecified_then_usingDefaultDiscloseParameter() {
        // GIVEN
        PublishInboundOutput publishInboundOutputMock = mock(PublishInboundOutput.class);
        ModifiablePublishPacket modifiablePublishPacketMock = mock(ModifiablePublishPacket.class);
        when(modifiablePublishPacketMock.getPayloadFormatIndicator())
                .thenReturn(Optional.of(PayloadFormatIndicator.UTF_8));
        when(modifiablePublishPacketMock.getPayload())
                .thenReturn(Optional.of(ByteBuffer.wrap("testPayload".getBytes(StandardCharsets.UTF_8))));
        when(publishInboundOutputMock.getPublishPacket()).thenReturn(modifiablePublishPacketMock);
        ConstraintDetails constraintDetails =
                new ConstraintDetails("clientId", new IdentifiableAuthorizationDecision(),
                        "testTopic", publishInboundOutputMock);
        JsonNode constraint = JsonNodeFactory.instance.objectNode()
                .put(Constraints.ENVIRONMENT_CONSTRAINT_TYPE, ENVIRONMENT_BLACKEN_PAYLOAD)
                .put(ENVIRONMENT_REPLACEMENT, "*")
                .put(ENVIRONMENT_DISCLOSE_LEFT, 1);

        // WHEN
        boolean wasSuccessfullyHandled =
                PublishConstraints.enforcePublishConstraintEntries(constraintDetails, constraint);

        // THEN
        verify(modifiablePublishPacketMock, times(1))
                .setPayload(ByteBuffer.wrap("t**********".getBytes(StandardCharsets.UTF_8)));
        assertTrue(wasSuccessfullyHandled);
    }

    @Test
    void when_blackenPayloadAndDiscloseParameterIsNotANumber_then_signalConstraintCouldNotBeHandled() {
        // GIVEN
        PublishInboundOutput publishInboundOutputMock = mock(PublishInboundOutput.class);
        ModifiablePublishPacket modifiablePublishPacketMock = mock(ModifiablePublishPacket.class);
        when(modifiablePublishPacketMock.getPayloadFormatIndicator())
                .thenReturn(Optional.of(PayloadFormatIndicator.UTF_8));
        when(modifiablePublishPacketMock.getPayload())
                .thenReturn(Optional.of(ByteBuffer.wrap("testPayload".getBytes(StandardCharsets.UTF_8))));
        when(publishInboundOutputMock.getPublishPacket()).thenReturn(modifiablePublishPacketMock);
        ConstraintDetails constraintDetails =
                new ConstraintDetails("clientId", new IdentifiableAuthorizationDecision(),
                        "testTopic", publishInboundOutputMock);
        JsonNode constraint = JsonNodeFactory.instance.objectNode()
                .put(Constraints.ENVIRONMENT_CONSTRAINT_TYPE, ENVIRONMENT_BLACKEN_PAYLOAD)
                .put(ENVIRONMENT_REPLACEMENT, "*")
                .put(ENVIRONMENT_DISCLOSE_LEFT, 1)
                .put(ENVIRONMENT_DISCLOSE_RIGHT, "one");

        // WHEN
        boolean wasSuccessfullyHandled =
                PublishConstraints.enforcePublishConstraintEntries(constraintDetails, constraint);

        // THEN
        verify(modifiablePublishPacketMock, never())
                .setPayload(ByteBuffer.wrap("t**********".getBytes(StandardCharsets.UTF_8)));
        assertFalse(wasSuccessfullyHandled);
    }

    @Test
    void when_blackenPayloadAndDiscloseParameterIsNegative_then_signalConstraintCouldNotBeHandled() {
        // GIVEN
        PublishInboundOutput publishInboundOutputMock = mock(PublishInboundOutput.class);
        ModifiablePublishPacket modifiablePublishPacketMock = mock(ModifiablePublishPacket.class);
        when(modifiablePublishPacketMock.getPayloadFormatIndicator())
                .thenReturn(Optional.of(PayloadFormatIndicator.UTF_8));
        when(modifiablePublishPacketMock.getPayload())
                .thenReturn(Optional.of(ByteBuffer.wrap("testPayload".getBytes(StandardCharsets.UTF_8))));
        when(publishInboundOutputMock.getPublishPacket()).thenReturn(modifiablePublishPacketMock);
        ConstraintDetails constraintDetails =
                new ConstraintDetails("clientId", new IdentifiableAuthorizationDecision(),
                        "testTopic", publishInboundOutputMock);
        JsonNode constraint = JsonNodeFactory.instance.objectNode()
                .put(Constraints.ENVIRONMENT_CONSTRAINT_TYPE, ENVIRONMENT_BLACKEN_PAYLOAD)
                .put(ENVIRONMENT_REPLACEMENT, "*")
                .put(ENVIRONMENT_DISCLOSE_LEFT, 1)
                .put(ENVIRONMENT_DISCLOSE_RIGHT, -1);

        // WHEN
        boolean wasSuccessfullyHandled =
                PublishConstraints.enforcePublishConstraintEntries(constraintDetails, constraint);

        // THEN
        verify(modifiablePublishPacketMock, never())
                .setPayload(ByteBuffer.wrap("t**********".getBytes(StandardCharsets.UTF_8)));
        assertFalse(wasSuccessfullyHandled);
    }

    @Test
    void when_blackenPayloadAndNoReplacementWasSpecified_then_useDefaultReplacement() {
        // GIVEN
        PublishInboundOutput publishInboundOutputMock = mock(PublishInboundOutput.class);
        ModifiablePublishPacket modifiablePublishPacketMock = mock(ModifiablePublishPacket.class);
        when(modifiablePublishPacketMock.getPayloadFormatIndicator())
                .thenReturn(Optional.of(PayloadFormatIndicator.UTF_8));
        when(modifiablePublishPacketMock.getPayload())
                .thenReturn(Optional.of(ByteBuffer.wrap("testPayload".getBytes(StandardCharsets.UTF_8))));
        when(publishInboundOutputMock.getPublishPacket()).thenReturn(modifiablePublishPacketMock);
        ConstraintDetails constraintDetails =
                new ConstraintDetails("clientId", new IdentifiableAuthorizationDecision(),
                        "testTopic", publishInboundOutputMock);
        JsonNode constraint = JsonNodeFactory.instance.objectNode()
                .put(Constraints.ENVIRONMENT_CONSTRAINT_TYPE, ENVIRONMENT_BLACKEN_PAYLOAD)
                .put(ENVIRONMENT_DISCLOSE_LEFT, 1)
                .put(ENVIRONMENT_DISCLOSE_RIGHT, 1);

        // WHEN
        boolean wasSuccessfullyHandled =
                PublishConstraints.enforcePublishConstraintEntries(constraintDetails, constraint);

        // THEN
        verify(modifiablePublishPacketMock, times(1))
                .setPayload(ByteBuffer.wrap("tXXXXXXXXXd".getBytes(StandardCharsets.UTF_8)));
        assertTrue(wasSuccessfullyHandled);
    }

    @Test
    void when_blackenPayloadAndReplacementIsNotTextual_then_signalConstraintCouldNotBeHandled() {
        // GIVEN
        PublishInboundOutput publishInboundOutputMock = mock(PublishInboundOutput.class);
        ModifiablePublishPacket modifiablePublishPacketMock = mock(ModifiablePublishPacket.class);
        when(modifiablePublishPacketMock.getPayloadFormatIndicator())
                .thenReturn(Optional.of(PayloadFormatIndicator.UTF_8));
        when(modifiablePublishPacketMock.getPayload())
                .thenReturn(Optional.of(ByteBuffer.wrap("testPayload".getBytes(StandardCharsets.UTF_8))));
        when(publishInboundOutputMock.getPublishPacket()).thenReturn(modifiablePublishPacketMock);
        ConstraintDetails constraintDetails =
                new ConstraintDetails("clientId", new IdentifiableAuthorizationDecision(),
                        "testTopic", publishInboundOutputMock);
        JsonNode constraint = JsonNodeFactory.instance.objectNode()
                .put(Constraints.ENVIRONMENT_CONSTRAINT_TYPE, ENVIRONMENT_BLACKEN_PAYLOAD)
                .put(ENVIRONMENT_REPLACEMENT, 5)
                .put(ENVIRONMENT_DISCLOSE_LEFT, 1)
                .put(ENVIRONMENT_DISCLOSE_RIGHT, 1);

        // WHEN
        boolean wasSuccessfullyHandled =
                PublishConstraints.enforcePublishConstraintEntries(constraintDetails, constraint);

        // THEN
        verify(modifiablePublishPacketMock, never())
                .setPayload(ByteBuffer.wrap("tXXXXXXXXXd".getBytes(StandardCharsets.UTF_8)));
        assertFalse(wasSuccessfullyHandled);
    }

    @Test
    void when_blackenPayloadAndDisclosedCharactersAreMoreOrEqualThanTheLengthOfThePayload_then_doNotBlackenAnyCharacter() {
        // GIVEN
        PublishInboundOutput publishInboundOutputMock = mock(PublishInboundOutput.class);
        ModifiablePublishPacket modifiablePublishPacketMock = mock(ModifiablePublishPacket.class);
        when(modifiablePublishPacketMock.getPayloadFormatIndicator())
                .thenReturn(Optional.of(PayloadFormatIndicator.UTF_8));
        when(modifiablePublishPacketMock.getPayload())
                .thenReturn(Optional.of(ByteBuffer.wrap("testPayload".getBytes(StandardCharsets.UTF_8))));
        when(publishInboundOutputMock.getPublishPacket()).thenReturn(modifiablePublishPacketMock);
        ConstraintDetails constraintDetails =
                new ConstraintDetails("clientId", new IdentifiableAuthorizationDecision(),
                        "testTopic", publishInboundOutputMock);
        JsonNode constraint = JsonNodeFactory.instance.objectNode()
                .put(Constraints.ENVIRONMENT_CONSTRAINT_TYPE, ENVIRONMENT_BLACKEN_PAYLOAD)
                .put(ENVIRONMENT_REPLACEMENT, "X")
                .put(ENVIRONMENT_DISCLOSE_LEFT, 5)
                .put(ENVIRONMENT_DISCLOSE_RIGHT, 6);

        // WHEN
        boolean wasSuccessfullyHandled =
                PublishConstraints.enforcePublishConstraintEntries(constraintDetails, constraint);

        // THEN
        verify(modifiablePublishPacketMock, times(1))
                .setPayload(ByteBuffer.wrap("testPayload".getBytes(StandardCharsets.UTF_8)));
        assertTrue(wasSuccessfullyHandled);
    }
}