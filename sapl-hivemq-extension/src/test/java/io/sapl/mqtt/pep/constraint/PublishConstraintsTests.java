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
package io.sapl.mqtt.pep.constraint;

import static io.sapl.mqtt.pep.constraint.Constraints.ENVIRONMENT_DISABLED;
import static io.sapl.mqtt.pep.constraint.Constraints.ENVIRONMENT_ENABLED;
import static io.sapl.mqtt.pep.constraint.Constraints.ENVIRONMENT_STATUS;
import static io.sapl.mqtt.pep.constraint.PublishConstraints.ENVIRONMENT_BLACKEN_PAYLOAD;
import static io.sapl.mqtt.pep.constraint.PublishConstraints.ENVIRONMENT_DISCLOSE_LEFT;
import static io.sapl.mqtt.pep.constraint.PublishConstraints.ENVIRONMENT_DISCLOSE_RIGHT;
import static io.sapl.mqtt.pep.constraint.PublishConstraints.ENVIRONMENT_QOS_CONSTRAINT;
import static io.sapl.mqtt.pep.constraint.PublishConstraints.ENVIRONMENT_QOS_LEVEL;
import static io.sapl.mqtt.pep.constraint.PublishConstraints.ENVIRONMENT_REPLACEMENT;
import static io.sapl.mqtt.pep.constraint.PublishConstraints.ENVIRONMENT_REPLACE_CONTENT_TYPE;
import static io.sapl.mqtt.pep.constraint.PublishConstraints.ENVIRONMENT_REPLACE_MESSAGE_EXPIRY_INTERVAL;
import static io.sapl.mqtt.pep.constraint.PublishConstraints.ENVIRONMENT_REPLACE_PAYLOAD;
import static io.sapl.mqtt.pep.constraint.PublishConstraints.ENVIRONMENT_RETAIN_MESSAGE_CONSTRAINT;
import static io.sapl.mqtt.pep.constraint.PublishConstraints.ENVIRONMENT_TIME_INTERVAL;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.hivemq.extension.sdk.api.interceptor.publish.parameter.PublishInboundOutput;
import com.hivemq.extension.sdk.api.packets.publish.ModifiablePublishPacket;
import com.hivemq.extension.sdk.api.packets.publish.PayloadFormatIndicator;

import io.sapl.api.pdp.IdentifiableAuthorizationDecision;

class PublishConstraintsTests {

	@Test
	void when_specifiedConstraintTypeIsNotTextual_then_signalConstraintCouldNotBeHandled() {
		// GIVEN
		var publishInboundOutputMock    = mock(PublishInboundOutput.class);
		var modifiablePublishPacketMock = mock(ModifiablePublishPacket.class);
		when(publishInboundOutputMock.getPublishPacket()).thenReturn(modifiablePublishPacketMock);
		var constraintDetails = new ConstraintDetails("clientId", new IdentifiableAuthorizationDecision(),
				"testTopic", publishInboundOutputMock);
		var constraint        = JsonNodeFactory.instance.objectNode().put(Constraints.ENVIRONMENT_CONSTRAINT_TYPE, 4);

		// WHEN
		var wasSuccessfullyHandled = PublishConstraints.enforcePublishConstraintEntries(constraintDetails, constraint);

		// THEN
		assertFalse(wasSuccessfullyHandled);
	}

	@Test
	void when_settingQosAndQosLevelNotSpecified_then_signalConstraintCouldNotBeHandled() {
		// GIVEN
		var publishInboundOutputMock    = mock(PublishInboundOutput.class);
		var modifiablePublishPacketMock = mock(ModifiablePublishPacket.class);
		when(publishInboundOutputMock.getPublishPacket()).thenReturn(modifiablePublishPacketMock);
		var constraintDetails = new ConstraintDetails("clientId", new IdentifiableAuthorizationDecision(),
				"testTopic", publishInboundOutputMock);
		var constraint        = JsonNodeFactory.instance.objectNode().put(Constraints.ENVIRONMENT_CONSTRAINT_TYPE,
				ENVIRONMENT_QOS_CONSTRAINT);

		// WHEN
		var wasSuccessfullyHandled = PublishConstraints.enforcePublishConstraintEntries(constraintDetails, constraint);

		// THEN
		verify(modifiablePublishPacketMock, never()).setQos(any());
		assertFalse(wasSuccessfullyHandled);
	}

	@Test
	void when_settingQosAndQosLevelIsNotAIntegralNumber_then_signalConstraintCouldNotBeHandled() {
		// GIVEN
		var publishInboundOutputMock    = mock(PublishInboundOutput.class);
		var modifiablePublishPacketMock = mock(ModifiablePublishPacket.class);
		when(publishInboundOutputMock.getPublishPacket()).thenReturn(modifiablePublishPacketMock);
		var constraintDetails = new ConstraintDetails("clientId", new IdentifiableAuthorizationDecision(),
				"testTopic", publishInboundOutputMock);
		var constraint        = JsonNodeFactory.instance.objectNode()
				.put(Constraints.ENVIRONMENT_CONSTRAINT_TYPE, ENVIRONMENT_QOS_CONSTRAINT)
				.put(ENVIRONMENT_QOS_LEVEL, 1.2);

		// WHEN
		var wasSuccessfullyHandled = PublishConstraints.enforcePublishConstraintEntries(constraintDetails,
				constraint);

		// THEN
		verify(modifiablePublishPacketMock, never()).setQos(any());
		assertFalse(wasSuccessfullyHandled);
	}

	@ParameterizedTest
	@ValueSource(ints = { -1, 3 })
	void when_settingQosAndQosLevelIsIllegal_then_signalConstraintCouldNotBeHandled(int qosLevel) {
		// GIVEN
		var publishInboundOutputMock    = mock(PublishInboundOutput.class);
		var modifiablePublishPacketMock = mock(ModifiablePublishPacket.class);
		when(publishInboundOutputMock.getPublishPacket()).thenReturn(modifiablePublishPacketMock);
		var constraintDetails = new ConstraintDetails("clientId", new IdentifiableAuthorizationDecision(),
				"testTopic", publishInboundOutputMock);
		var constraint        = JsonNodeFactory.instance.objectNode()
				.put(Constraints.ENVIRONMENT_CONSTRAINT_TYPE, ENVIRONMENT_QOS_CONSTRAINT)
				.put(ENVIRONMENT_QOS_LEVEL, qosLevel);

		// WHEN
		var wasSuccessfullyHandled = PublishConstraints.enforcePublishConstraintEntries(constraintDetails,
				constraint);

		// THEN
		verify(modifiablePublishPacketMock, never()).setQos(any());
		assertFalse(wasSuccessfullyHandled);
	}

	@Test
	void when_constraintSetRetainFlagToEnabled_then_enableRetain() {
		// GIVEN
		var publishInboundOutputMock    = mock(PublishInboundOutput.class);
		var modifiablePublishPacketMock = mock(ModifiablePublishPacket.class);
		when(publishInboundOutputMock.getPublishPacket()).thenReturn(modifiablePublishPacketMock);
		var constraintDetails = new ConstraintDetails("clientId", new IdentifiableAuthorizationDecision(),
				"testTopic", publishInboundOutputMock);
		var constraint        = JsonNodeFactory.instance.objectNode()
				.put(Constraints.ENVIRONMENT_CONSTRAINT_TYPE, ENVIRONMENT_RETAIN_MESSAGE_CONSTRAINT)
				.put(ENVIRONMENT_STATUS, ENVIRONMENT_ENABLED);

		// WHEN
		var wasSuccessfullyHandled = PublishConstraints.enforcePublishConstraintEntries(constraintDetails,
				constraint);

		// THEN
		verify(modifiablePublishPacketMock, times(1)).setRetain(true);
		assertTrue(wasSuccessfullyHandled);
	}

	@Test
	void when_settingRetainFlagAndNoStatusWasSpecified_then_signalConstraintCouldNotBeHandled() {
		// GIVEN
		var publishInboundOutputMock    = mock(PublishInboundOutput.class);
		var modifiablePublishPacketMock = mock(ModifiablePublishPacket.class);
		when(publishInboundOutputMock.getPublishPacket()).thenReturn(modifiablePublishPacketMock);
		var constraintDetails = new ConstraintDetails("clientId", new IdentifiableAuthorizationDecision(),
				"testTopic", publishInboundOutputMock);
		var constraint        = JsonNodeFactory.instance.objectNode()
				.put(Constraints.ENVIRONMENT_CONSTRAINT_TYPE, ENVIRONMENT_RETAIN_MESSAGE_CONSTRAINT);

		// WHEN
		var wasSuccessfullyHandled = PublishConstraints.enforcePublishConstraintEntries(constraintDetails,
				constraint);

		// THEN
		verify(modifiablePublishPacketMock, never()).setRetain(anyBoolean());
		assertFalse(wasSuccessfullyHandled);
	}

	@Test
	void when_settingRetainFlagAndStatusIsNotTextual_then_signalConstraintCouldNotBeHandled() {
		// GIVEN
		var publishInboundOutputMock    = mock(PublishInboundOutput.class);
		var modifiablePublishPacketMock = mock(ModifiablePublishPacket.class);
		when(publishInboundOutputMock.getPublishPacket()).thenReturn(modifiablePublishPacketMock);
		var constraintDetails = new ConstraintDetails("clientId", new IdentifiableAuthorizationDecision(),
				"testTopic", publishInboundOutputMock);
		var constraint        = JsonNodeFactory.instance.objectNode()
				.put(Constraints.ENVIRONMENT_CONSTRAINT_TYPE, ENVIRONMENT_RETAIN_MESSAGE_CONSTRAINT)
				.put(ENVIRONMENT_STATUS, 1);

		// WHEN
		var wasSuccessfullyHandled = PublishConstraints.enforcePublishConstraintEntries(constraintDetails,
				constraint);

		// THEN
		verify(modifiablePublishPacketMock, never()).setRetain(anyBoolean());
		assertFalse(wasSuccessfullyHandled);
	}

	@Test
	void when_settingRetainFlagAndStatusIsIllegalTextual_then_signalConstraintCouldNotBeHandled() {
		// GIVEN
		var publishInboundOutputMock    = mock(PublishInboundOutput.class);
		var modifiablePublishPacketMock = mock(ModifiablePublishPacket.class);
		when(publishInboundOutputMock.getPublishPacket()).thenReturn(modifiablePublishPacketMock);
		var constraintDetails = new ConstraintDetails("clientId", new IdentifiableAuthorizationDecision(),
				"testTopic", publishInboundOutputMock);
		var constraint        = JsonNodeFactory.instance.objectNode()
				.put(Constraints.ENVIRONMENT_CONSTRAINT_TYPE, ENVIRONMENT_RETAIN_MESSAGE_CONSTRAINT)
				.put(ENVIRONMENT_STATUS, "status");

		// WHEN
		var wasSuccessfullyHandled = PublishConstraints.enforcePublishConstraintEntries(constraintDetails,
				constraint);

		// THEN
		verify(modifiablePublishPacketMock, never()).setRetain(anyBoolean());
		assertFalse(wasSuccessfullyHandled);
	}

	@Test
	void when_settingRetainFlagAndStatusIsDisabled_then_setStatusAndSignalSuccessfulConstraintHandling() {
		// GIVEN
		var publishInboundOutputMock    = mock(PublishInboundOutput.class);
		var modifiablePublishPacketMock = mock(ModifiablePublishPacket.class);
		when(publishInboundOutputMock.getPublishPacket()).thenReturn(modifiablePublishPacketMock);
		var constraintDetails = new ConstraintDetails("clientId", new IdentifiableAuthorizationDecision(),
				"testTopic", publishInboundOutputMock);
		var constraint        = JsonNodeFactory.instance.objectNode()
				.put(Constraints.ENVIRONMENT_CONSTRAINT_TYPE, ENVIRONMENT_RETAIN_MESSAGE_CONSTRAINT)
				.put(ENVIRONMENT_STATUS, ENVIRONMENT_DISABLED);

		// WHEN
		var wasSuccessfullyHandled = PublishConstraints.enforcePublishConstraintEntries(constraintDetails,
				constraint);

		// THEN
		verify(modifiablePublishPacketMock, times(1)).setRetain(false);
		assertTrue(wasSuccessfullyHandled);
	}

	@Test
	void when_settingMessageExpiryIntervalAndNoTimeIntervalWasSpecified_then_signalConstraintCouldNotBeHandled() {
		// GIVEN
		var publishInboundOutputMock    = mock(PublishInboundOutput.class);
		var modifiablePublishPacketMock = mock(ModifiablePublishPacket.class);
		when(publishInboundOutputMock.getPublishPacket()).thenReturn(modifiablePublishPacketMock);
		var constraintDetails = new ConstraintDetails("clientId", new IdentifiableAuthorizationDecision(),
				"testTopic", publishInboundOutputMock);
		var constraint        = JsonNodeFactory.instance.objectNode()
				.put(Constraints.ENVIRONMENT_CONSTRAINT_TYPE, ENVIRONMENT_REPLACE_MESSAGE_EXPIRY_INTERVAL);

		// WHEN
		var wasSuccessfullyHandled = PublishConstraints.enforcePublishConstraintEntries(constraintDetails,
				constraint);

		// THEN
		verify(modifiablePublishPacketMock, never()).setMessageExpiryInterval(anyLong());
		assertFalse(wasSuccessfullyHandled);
	}

	@Test
	void when_settingMessageExpiryIntervalAndIllegalValueTypeWasSpecified_then_signalConstraintCouldNotBeHandled() {
		// GIVEN
		var publishInboundOutputMock    = mock(PublishInboundOutput.class);
		var modifiablePublishPacketMock = mock(ModifiablePublishPacket.class);
		when(publishInboundOutputMock.getPublishPacket()).thenReturn(modifiablePublishPacketMock);
		var constraintDetails = new ConstraintDetails("clientId", new IdentifiableAuthorizationDecision(),
				"testTopic", publishInboundOutputMock);
		var constraint        = JsonNodeFactory.instance.objectNode()
				.put(Constraints.ENVIRONMENT_CONSTRAINT_TYPE, ENVIRONMENT_REPLACE_MESSAGE_EXPIRY_INTERVAL)
				.put(ENVIRONMENT_TIME_INTERVAL, "five");

		// WHEN
		var wasSuccessfullyHandled = PublishConstraints.enforcePublishConstraintEntries(constraintDetails,
				constraint);

		// THEN
		verify(modifiablePublishPacketMock, never()).setMessageExpiryInterval(anyLong());
		assertFalse(wasSuccessfullyHandled);
	}

	@Test
	void when_settingMessageExpiryIntervalAndIllegalExpiryIntervalWasSpecified_then_signalConstraintCouldNotBeHandled() {
		// GIVEN
		var publishInboundOutputMock    = mock(PublishInboundOutput.class);
		var modifiablePublishPacketMock = mock(ModifiablePublishPacket.class);
		doThrow(IllegalArgumentException.class).when(modifiablePublishPacketMock).setMessageExpiryInterval(-1);
		when(publishInboundOutputMock.getPublishPacket()).thenReturn(modifiablePublishPacketMock);
		var constraintDetails = new ConstraintDetails("clientId", new IdentifiableAuthorizationDecision(),
				"testTopic", publishInboundOutputMock);
		var constraint        = JsonNodeFactory.instance.objectNode()
				.put(Constraints.ENVIRONMENT_CONSTRAINT_TYPE, ENVIRONMENT_REPLACE_MESSAGE_EXPIRY_INTERVAL)
				.put(ENVIRONMENT_TIME_INTERVAL, -1);

		// WHEN
		var wasSuccessfullyHandled = PublishConstraints.enforcePublishConstraintEntries(constraintDetails,
				constraint);

		// THEN
		verify(modifiablePublishPacketMock, times(1)).setMessageExpiryInterval(anyLong());
		assertFalse(wasSuccessfullyHandled);
	}

	@Test
	void when_settingContentTypeAndNoReplacementWasSpecified_then_signalConstraintCouldNotBeHandled() {
		// GIVEN
		var publishInboundOutputMock    = mock(PublishInboundOutput.class);
		var modifiablePublishPacketMock = mock(ModifiablePublishPacket.class);
		when(publishInboundOutputMock.getPublishPacket()).thenReturn(modifiablePublishPacketMock);
		var constraintDetails = new ConstraintDetails("clientId", new IdentifiableAuthorizationDecision(),
				"testTopic", publishInboundOutputMock);
		var constraint        = JsonNodeFactory.instance.objectNode()
				.put(Constraints.ENVIRONMENT_CONSTRAINT_TYPE, ENVIRONMENT_REPLACE_CONTENT_TYPE);

		// WHEN
		var wasSuccessfullyHandled = PublishConstraints.enforcePublishConstraintEntries(constraintDetails,
				constraint);

		// THEN
		verify(modifiablePublishPacketMock, never()).setContentType(anyString());
		assertFalse(wasSuccessfullyHandled);
	}

	@Test
	void when_settingContentTypeAndReplacementIsNotTextual_then_signalConstraintCouldNotBeHandled() {
		// GIVEN
		var publishInboundOutputMock    = mock(PublishInboundOutput.class);
		var modifiablePublishPacketMock = mock(ModifiablePublishPacket.class);
		when(publishInboundOutputMock.getPublishPacket()).thenReturn(modifiablePublishPacketMock);
		var constraintDetails = new ConstraintDetails("clientId", new IdentifiableAuthorizationDecision(),
				"testTopic", publishInboundOutputMock);
		var constraint        = JsonNodeFactory.instance.objectNode()
				.put(Constraints.ENVIRONMENT_CONSTRAINT_TYPE, ENVIRONMENT_REPLACE_CONTENT_TYPE)
				.put(ENVIRONMENT_REPLACEMENT, 5);

		// WHEN
		var wasSuccessfullyHandled = PublishConstraints.enforcePublishConstraintEntries(constraintDetails,
				constraint);

		// THEN
		verify(modifiablePublishPacketMock, never()).setContentType(anyString());
		assertFalse(wasSuccessfullyHandled);
	}

	@Test
	void when_settingContentTypeAndTheReplacementIsNotAValidUTF8String_then_signalConstraintCouldNotBeHandled() {
		// GIVEN
		var publishInboundOutputMock    = mock(PublishInboundOutput.class);
		var modifiablePublishPacketMock = mock(ModifiablePublishPacket.class);
		doThrow(IllegalArgumentException.class).when(modifiablePublishPacketMock).setContentType("notAValidUTF8String");
		when(publishInboundOutputMock.getPublishPacket()).thenReturn(modifiablePublishPacketMock);
		var constraintDetails = new ConstraintDetails("clientId", new IdentifiableAuthorizationDecision(),
				"testTopic", publishInboundOutputMock);
		var constraint        = JsonNodeFactory.instance.objectNode()
				.put(Constraints.ENVIRONMENT_CONSTRAINT_TYPE, ENVIRONMENT_REPLACE_CONTENT_TYPE)
				.put(ENVIRONMENT_REPLACEMENT, "notAValidUTF8String");

		// WHEN
		var wasSuccessfullyHandled = PublishConstraints.enforcePublishConstraintEntries(constraintDetails,
				constraint);

		// THEN
		verify(modifiablePublishPacketMock, times(1)).setContentType(anyString());
		assertFalse(wasSuccessfullyHandled);
	}

	@Test
	void when_settingPayloadAndNoReplacementWasSpecified_then_signalConstraintCouldNotBeHandled() {
		// GIVEN
		var publishInboundOutputMock    = mock(PublishInboundOutput.class);
		var modifiablePublishPacketMock = mock(ModifiablePublishPacket.class);
		when(publishInboundOutputMock.getPublishPacket()).thenReturn(modifiablePublishPacketMock);
		var constraintDetails = new ConstraintDetails("clientId", new IdentifiableAuthorizationDecision(),
				"testTopic", publishInboundOutputMock);
		var constraint        = JsonNodeFactory.instance.objectNode()
				.put(Constraints.ENVIRONMENT_CONSTRAINT_TYPE, ENVIRONMENT_REPLACE_PAYLOAD);

		// WHEN
		var wasSuccessfullyHandled = PublishConstraints.enforcePublishConstraintEntries(constraintDetails,
				constraint);

		// THEN
		verify(modifiablePublishPacketMock, never()).setPayload(any(ByteBuffer.class));
		assertFalse(wasSuccessfullyHandled);
	}

	@Test
	void when_settingPayloadAndReplacementIsNotTextual_then_signalConstraintCouldNotBeHandled() {
		// GIVEN
		var publishInboundOutputMock    = mock(PublishInboundOutput.class);
		var modifiablePublishPacketMock = mock(ModifiablePublishPacket.class);
		when(publishInboundOutputMock.getPublishPacket()).thenReturn(modifiablePublishPacketMock);
		var constraintDetails = new ConstraintDetails("clientId", new IdentifiableAuthorizationDecision(),
				"testTopic", publishInboundOutputMock);
		var constraint        = JsonNodeFactory.instance.objectNode()
				.put(Constraints.ENVIRONMENT_CONSTRAINT_TYPE, ENVIRONMENT_REPLACE_PAYLOAD)
				.put(ENVIRONMENT_REPLACEMENT, 5);

		// WHEN
		var wasSuccessfullyHandled = PublishConstraints.enforcePublishConstraintEntries(constraintDetails,
				constraint);

		// THEN
		verify(modifiablePublishPacketMock, never()).setPayload(any(ByteBuffer.class));
		assertFalse(wasSuccessfullyHandled);
	}

	@Test
	void when_blackenPayloadAndContentTypeAndPayloadFormatIndicatorAreNotSpecified_then_signalConstraintCouldNotBeHandled() {
		// GIVEN
		var publishInboundOutputMock    = mock(PublishInboundOutput.class);
		var modifiablePublishPacketMock = mock(ModifiablePublishPacket.class);
		when(publishInboundOutputMock.getPublishPacket()).thenReturn(modifiablePublishPacketMock);
		var constraintDetails = new ConstraintDetails("clientId", new IdentifiableAuthorizationDecision(),
				"testTopic", publishInboundOutputMock);
		var constraint        = JsonNodeFactory.instance.objectNode()
				.put(Constraints.ENVIRONMENT_CONSTRAINT_TYPE, ENVIRONMENT_BLACKEN_PAYLOAD)
				.put(ENVIRONMENT_REPLACEMENT, "*");

		// WHEN
		var wasSuccessfullyHandled = PublishConstraints.enforcePublishConstraintEntries(constraintDetails,
				constraint);

		// THEN
		verify(modifiablePublishPacketMock, never()).setPayload(any(ByteBuffer.class));
		assertFalse(wasSuccessfullyHandled);
	}

	@Test
	void when_blackenPayloadAndMessageContainsNoPayload_then_signalSuccessfulHandlingOfConstraint() {
		// GIVEN
		var publishInboundOutputMock    = mock(PublishInboundOutput.class);
		var modifiablePublishPacketMock = mock(ModifiablePublishPacket.class);
		when(modifiablePublishPacketMock.getPayloadFormatIndicator())
				.thenReturn(Optional.of(PayloadFormatIndicator.UTF_8));
		when(publishInboundOutputMock.getPublishPacket()).thenReturn(modifiablePublishPacketMock);
		var constraintDetails = new ConstraintDetails("clientId", new IdentifiableAuthorizationDecision(),
				"testTopic", publishInboundOutputMock);
		var constraint        = JsonNodeFactory.instance.objectNode()
				.put(Constraints.ENVIRONMENT_CONSTRAINT_TYPE, ENVIRONMENT_BLACKEN_PAYLOAD)
				.put(ENVIRONMENT_REPLACEMENT, "*");

		// WHEN
		var wasSuccessfullyHandled = PublishConstraints.enforcePublishConstraintEntries(constraintDetails,
				constraint);

		// THEN
		verify(modifiablePublishPacketMock, never()).setPayload(any(ByteBuffer.class));
		assertTrue(wasSuccessfullyHandled);
	}

	@Test
	void when_blackenPayloadAndADiscloseParameterIsNotSpecified_then_usingDefaultDiscloseParameter() {
		// GIVEN
		var publishInboundOutputMock    = mock(PublishInboundOutput.class);
		var modifiablePublishPacketMock = mock(ModifiablePublishPacket.class);
		when(modifiablePublishPacketMock.getPayloadFormatIndicator())
				.thenReturn(Optional.of(PayloadFormatIndicator.UTF_8));
		when(modifiablePublishPacketMock.getPayload())
				.thenReturn(Optional.of(ByteBuffer.wrap("testPayload".getBytes(StandardCharsets.UTF_8))));
		when(publishInboundOutputMock.getPublishPacket()).thenReturn(modifiablePublishPacketMock);
		var constraintDetails = new ConstraintDetails("clientId", new IdentifiableAuthorizationDecision(),
				"testTopic", publishInboundOutputMock);
		var constraint        = JsonNodeFactory.instance.objectNode()
				.put(Constraints.ENVIRONMENT_CONSTRAINT_TYPE, ENVIRONMENT_BLACKEN_PAYLOAD)
				.put(ENVIRONMENT_REPLACEMENT, "*")
				.put(ENVIRONMENT_DISCLOSE_LEFT, 1);

		// WHEN
		var wasSuccessfullyHandled = PublishConstraints.enforcePublishConstraintEntries(constraintDetails,
				constraint);

		// THEN
		verify(modifiablePublishPacketMock, times(1))
				.setPayload(ByteBuffer.wrap("t**********".getBytes(StandardCharsets.UTF_8)));
		assertTrue(wasSuccessfullyHandled);
	}

	@Test
	void when_blackenPayloadAndDiscloseParameterIsNotANumber_then_signalConstraintCouldNotBeHandled() {
		// GIVEN
		var publishInboundOutputMock    = mock(PublishInboundOutput.class);
		var modifiablePublishPacketMock = mock(ModifiablePublishPacket.class);
		when(modifiablePublishPacketMock.getPayloadFormatIndicator())
				.thenReturn(Optional.of(PayloadFormatIndicator.UTF_8));
		when(modifiablePublishPacketMock.getPayload())
				.thenReturn(Optional.of(ByteBuffer.wrap("testPayload".getBytes(StandardCharsets.UTF_8))));
		when(publishInboundOutputMock.getPublishPacket()).thenReturn(modifiablePublishPacketMock);
		var constraintDetails = new ConstraintDetails("clientId", new IdentifiableAuthorizationDecision(),
				"testTopic", publishInboundOutputMock);
		var constraint        = JsonNodeFactory.instance.objectNode()
				.put(Constraints.ENVIRONMENT_CONSTRAINT_TYPE, ENVIRONMENT_BLACKEN_PAYLOAD)
				.put(ENVIRONMENT_REPLACEMENT, "*")
				.put(ENVIRONMENT_DISCLOSE_LEFT, 1)
				.put(ENVIRONMENT_DISCLOSE_RIGHT, "one");

		// WHEN
		var wasSuccessfullyHandled = PublishConstraints.enforcePublishConstraintEntries(constraintDetails, constraint);

		// THEN
		verify(modifiablePublishPacketMock, never())
				.setPayload(ByteBuffer.wrap("t**********".getBytes(StandardCharsets.UTF_8)));
		assertFalse(wasSuccessfullyHandled);
	}

	@Test
	void when_blackenPayloadAndDiscloseParameterIsNegative_then_signalConstraintCouldNotBeHandled() {
		// GIVEN
		var publishInboundOutputMock    = mock(PublishInboundOutput.class);
		var modifiablePublishPacketMock = mock(ModifiablePublishPacket.class);
		when(modifiablePublishPacketMock.getPayloadFormatIndicator())
				.thenReturn(Optional.of(PayloadFormatIndicator.UTF_8));
		when(modifiablePublishPacketMock.getPayload())
				.thenReturn(Optional.of(ByteBuffer.wrap("testPayload".getBytes(StandardCharsets.UTF_8))));
		when(publishInboundOutputMock.getPublishPacket()).thenReturn(modifiablePublishPacketMock);
		var constraintDetails = new ConstraintDetails("clientId", new IdentifiableAuthorizationDecision(),
				"testTopic", publishInboundOutputMock);
		var constraint        = JsonNodeFactory.instance.objectNode()
				.put(Constraints.ENVIRONMENT_CONSTRAINT_TYPE, ENVIRONMENT_BLACKEN_PAYLOAD)
				.put(ENVIRONMENT_REPLACEMENT, "*")
				.put(ENVIRONMENT_DISCLOSE_LEFT, 1)
				.put(ENVIRONMENT_DISCLOSE_RIGHT, -1);

		// WHEN
		var wasSuccessfullyHandled = PublishConstraints.enforcePublishConstraintEntries(constraintDetails,
				constraint);

		// THEN
		verify(modifiablePublishPacketMock, never())
				.setPayload(ByteBuffer.wrap("t**********".getBytes(StandardCharsets.UTF_8)));
		assertFalse(wasSuccessfullyHandled);
	}

	@Test
	void when_blackenPayloadAndNoReplacementWasSpecified_then_useDefaultReplacement() {
		// GIVEN
		var publishInboundOutputMock    = mock(PublishInboundOutput.class);
		var modifiablePublishPacketMock = mock(ModifiablePublishPacket.class);
		when(modifiablePublishPacketMock.getPayloadFormatIndicator())
				.thenReturn(Optional.of(PayloadFormatIndicator.UTF_8));
		when(modifiablePublishPacketMock.getPayload())
				.thenReturn(Optional.of(ByteBuffer.wrap("testPayload".getBytes(StandardCharsets.UTF_8))));
		when(publishInboundOutputMock.getPublishPacket()).thenReturn(modifiablePublishPacketMock);
		var constraintDetails = new ConstraintDetails("clientId", new IdentifiableAuthorizationDecision(),
				"testTopic", publishInboundOutputMock);
		var constraint        = JsonNodeFactory.instance.objectNode()
				.put(Constraints.ENVIRONMENT_CONSTRAINT_TYPE, ENVIRONMENT_BLACKEN_PAYLOAD)
				.put(ENVIRONMENT_DISCLOSE_LEFT, 1)
				.put(ENVIRONMENT_DISCLOSE_RIGHT, 1);

		// WHEN
		var wasSuccessfullyHandled = PublishConstraints.enforcePublishConstraintEntries(constraintDetails,
				constraint);

		// THEN
		verify(modifiablePublishPacketMock, times(1))
				.setPayload(ByteBuffer.wrap("tXXXXXXXXXd".getBytes(StandardCharsets.UTF_8)));
		assertTrue(wasSuccessfullyHandled);
	}

	@Test
	void when_blackenPayloadAndReplacementIsNotTextual_then_signalConstraintCouldNotBeHandled() {
		// GIVEN
		var publishInboundOutputMock    = mock(PublishInboundOutput.class);
		var modifiablePublishPacketMock = mock(ModifiablePublishPacket.class);
		when(modifiablePublishPacketMock.getPayloadFormatIndicator())
				.thenReturn(Optional.of(PayloadFormatIndicator.UTF_8));
		when(modifiablePublishPacketMock.getPayload())
				.thenReturn(Optional.of(ByteBuffer.wrap("testPayload".getBytes(StandardCharsets.UTF_8))));
		when(publishInboundOutputMock.getPublishPacket()).thenReturn(modifiablePublishPacketMock);
		var constraintDetails = new ConstraintDetails("clientId", new IdentifiableAuthorizationDecision(),
				"testTopic", publishInboundOutputMock);
		var constraint        = JsonNodeFactory.instance.objectNode()
				.put(Constraints.ENVIRONMENT_CONSTRAINT_TYPE, ENVIRONMENT_BLACKEN_PAYLOAD)
				.put(ENVIRONMENT_REPLACEMENT, 5)
				.put(ENVIRONMENT_DISCLOSE_LEFT, 1)
				.put(ENVIRONMENT_DISCLOSE_RIGHT, 1);

		// WHEN
		var wasSuccessfullyHandled = PublishConstraints.enforcePublishConstraintEntries(constraintDetails, constraint);

		// THEN
		verify(modifiablePublishPacketMock, never())
				.setPayload(ByteBuffer.wrap("tXXXXXXXXXd".getBytes(StandardCharsets.UTF_8)));
		assertFalse(wasSuccessfullyHandled);
	}

	@Test
	void when_blackenPayloadAndDisclosedCharactersAreMoreOrEqualThanTheLengthOfThePayload_then_doNotBlackenAnyCharacter() {
		// GIVEN
		var publishInboundOutputMock    = mock(PublishInboundOutput.class);
		var modifiablePublishPacketMock = mock(ModifiablePublishPacket.class);
		when(modifiablePublishPacketMock.getPayloadFormatIndicator())
				.thenReturn(Optional.of(PayloadFormatIndicator.UTF_8));
		when(modifiablePublishPacketMock.getPayload())
				.thenReturn(Optional.of(ByteBuffer.wrap("testPayload".getBytes(StandardCharsets.UTF_8))));
		when(publishInboundOutputMock.getPublishPacket()).thenReturn(modifiablePublishPacketMock);
		var constraintDetails = new ConstraintDetails("clientId", new IdentifiableAuthorizationDecision(), "testTopic",
				publishInboundOutputMock);
		var constraint        = JsonNodeFactory.instance.objectNode()
				.put(Constraints.ENVIRONMENT_CONSTRAINT_TYPE, ENVIRONMENT_BLACKEN_PAYLOAD)
				.put(ENVIRONMENT_REPLACEMENT, "X")
				.put(ENVIRONMENT_DISCLOSE_LEFT, 5)
				.put(ENVIRONMENT_DISCLOSE_RIGHT, 6);

		// WHEN
		var wasSuccessfullyHandled = PublishConstraints.enforcePublishConstraintEntries(constraintDetails, constraint);

		// THEN
		verify(modifiablePublishPacketMock, times(1))
				.setPayload(ByteBuffer.wrap("testPayload".getBytes(StandardCharsets.UTF_8)));
		assertTrue(wasSuccessfullyHandled);
	}
}