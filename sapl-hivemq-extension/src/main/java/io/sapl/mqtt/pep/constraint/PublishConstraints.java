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

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nullable;

import com.fasterxml.jackson.databind.JsonNode;
import com.hivemq.extension.sdk.api.packets.general.Qos;
import com.hivemq.extension.sdk.api.packets.publish.ModifiablePublishPacket;
import com.hivemq.extension.sdk.api.packets.publish.PayloadFormatIndicator;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

/**
 * This utility class provides constraints handling functions for mqtt publishes.
 */
@Slf4j
@UtilityClass
public class PublishConstraints extends Constraints {

    static final String ENVIRONMENT_QOS_CONSTRAINT = "setQos";
    static final String ENVIRONMENT_QOS_LEVEL = "qosLevel";
    static final String ENVIRONMENT_RETAIN_MESSAGE_CONSTRAINT = "retainMessage";
    static final String ENVIRONMENT_REPLACE_CONTENT_TYPE = "replaceContentType";
    static final String ENVIRONMENT_REPLACEMENT = "replacement";
    static final String ENVIRONMENT_REPLACE_PAYLOAD = "replacePayload";
    static final String ENVIRONMENT_REPLACE_MESSAGE_EXPIRY_INTERVAL = "replaceMessageExpiryInterval";
    static final String ENVIRONMENT_TIME_INTERVAL = "timeInterval";
    static final String ENVIRONMENT_BLACKEN_PAYLOAD = "blackenPayload";
    static final String ENVIRONMENT_DISCLOSE_LEFT = "discloseLeft";
    static final String ENVIRONMENT_DISCLOSE_RIGHT = "discloseRight";

    private static final String DEFAULT_REPLACEMENT = "X";
    private static final int DEFAULT_NUMBER_OF_CHARACTERS_TO_SHOW_LEFT = 0;
    private static final int DEFAULT_NUMBER_OF_CHARACTERS_TO_SHOW_RIGHT = 0;

    private static final String MIME_TYPE_PLAIN_TEXT = "text/plain";
    private static final String ILLEGAL_PARAMETER_DISCLOSE_LEFT =
            "Illegal parameter for left disclosure. Expecting a positive integer.";
    private static final String ILLEGAL_PARAMETER_DISCLOSE_RIGHT =
            "Illegal parameter for right disclosure. Expecting a positive integer.";
    private static final String ILLEGAL_PARAMETER_REPLACEMENT = "Illegal parameter for replacement. Expecting a string.";


    @FunctionalInterface
    private interface PublishConstraintHandlingFunction<S, M, J> {
        boolean fulfill(S clientId, M publishPacket, J constraint) throws CharacterCodingException;
    }

    private static final Map<String, PublishConstraintHandlingFunction<String, ModifiablePublishPacket, JsonNode>>
            PUBLISH_CONSTRAINTS = new HashMap<>();

    static {
        PUBLISH_CONSTRAINTS.put(ENVIRONMENT_QOS_CONSTRAINT, PublishConstraints::setQos);
        PUBLISH_CONSTRAINTS.put(ENVIRONMENT_RETAIN_MESSAGE_CONSTRAINT, PublishConstraints::setRetainFlag);
        PUBLISH_CONSTRAINTS.put(ENVIRONMENT_REPLACE_CONTENT_TYPE, PublishConstraints::setContentType);
        PUBLISH_CONSTRAINTS.put(ENVIRONMENT_REPLACE_PAYLOAD, PublishConstraints::setPayloadText);
        PUBLISH_CONSTRAINTS.put(ENVIRONMENT_BLACKEN_PAYLOAD, PublishConstraints::blackenPayloadText);
        PUBLISH_CONSTRAINTS.put(ENVIRONMENT_REPLACE_MESSAGE_EXPIRY_INTERVAL,
                PublishConstraints::setMessageExpiryInterval);
    }

    static boolean enforcePublishConstraintEntries(ConstraintDetails constraintDetails, JsonNode constraint) {
        ModifiablePublishPacket publishPacket = constraintDetails.getPublishInboundOutput().getPublishPacket();
        String clientId = constraintDetails.getClientId();
        PublishConstraintHandlingFunction<String, ModifiablePublishPacket, JsonNode>
                publishConstraintHandlingFunction = null;
        if (constraint.has(ENVIRONMENT_CONSTRAINT_TYPE) && constraint.get(ENVIRONMENT_CONSTRAINT_TYPE).isTextual()) {
            String constraintType = constraint.get(ENVIRONMENT_CONSTRAINT_TYPE).asText();
            publishConstraintHandlingFunction = PUBLISH_CONSTRAINTS.get(constraintType);
        }

        if (publishConstraintHandlingFunction == null) { // returns false if the constraint entry couldn't be handled
            log.warn("No valid constraint handler found for client '{}' and constraint '{}'. " +
                    "Please check your policy specification.", clientId, constraint);
            return false;
        } else {
            try {
                return publishConstraintHandlingFunction.fulfill(clientId, publishPacket, constraint);
            } catch (IllegalArgumentException | CharacterCodingException exception) {
                log.warn("Failed to handle constraint '{}': {}", constraint.get(ENVIRONMENT_CONSTRAINT_TYPE).asText(),
                        exception.getMessage());
                return false;
            }
        }
    }

    private static boolean setQos(String clientId, ModifiablePublishPacket publishPacket, JsonNode constraint) {
        JsonNode qosLevelJson = constraint.get(ENVIRONMENT_QOS_LEVEL);
        if (qosLevelJson != null) {
            if (!qosLevelJson.canConvertToExactIntegral() || qosLevelJson.asInt() > 2 || qosLevelJson.asInt() < 0) {
                log.warn("Specified illegal quality of service level for message of topic '{}' of client '{}': {}",
                        publishPacket.getTopic(), clientId, qosLevelJson);
                return false;
            }
            var qos = qosLevelJson.asInt();
            publishPacket.setQos(Qos.valueOf(qos));
            log.info("Changed qos of message '{}' of client '{}' to: {}", publishPacket.getTopic(), clientId, qos);
            return true;
        } else {
            log.warn("No qos level specified for mqtt publish constraint for topic '{}' of client '{}'.",
                    publishPacket.getTopic(), clientId);
            return false;
        }
    }

    private static boolean setRetainFlag(String clientId, ModifiablePublishPacket publishPacket, JsonNode constraint) {
        JsonNode statusJson = constraint.get(ENVIRONMENT_STATUS);
        if (statusJson != null && statusJson.isTextual()) {
            String status = statusJson.asText();
            if (ENVIRONMENT_ENABLED.equals(status)) {
                publishPacket.setRetain(true);
                log.info("Changed retain flag of message '{}' of client '{}' to 'true'.",
                        publishPacket.getTopic(), clientId);
                return true;
            }
            if (ENVIRONMENT_DISABLED.equals(status)) {
                publishPacket.setRetain(false);
                log.info("Changed retain flag of message '{}' of client '{}' to 'false'.",
                        publishPacket.getTopic(), clientId);
                return true;
            }
            log.warn("Specified illegal value for the retain flag for message of topic '{}' of client '{}': {}",
                    publishPacket.getTopic(), clientId, statusJson);
        } else {
            log.warn("No textual status for the retain message constraint for topic '{}' of client '{}' was specified",
                    publishPacket.getTopic(), clientId);
        }
        return false;
    }

    private static boolean setMessageExpiryInterval(String clientId, ModifiablePublishPacket publishPacket,
                                                    JsonNode constraint) {
        JsonNode timeIntervalJson = constraint.get(ENVIRONMENT_TIME_INTERVAL);
        if (timeIntervalJson != null && timeIntervalJson.canConvertToExactIntegral()) {
            var expiryInterval = timeIntervalJson.asLong();
            publishPacket.setMessageExpiryInterval(expiryInterval);
            log.info("Changed expiry interval of message '{}' of client '{}' to '{}' seconds.",
                    publishPacket.getTopic(), clientId, expiryInterval);
            return true;
        } else {
            log.warn("No time interval to replace the message expiry interval of topic '{}' of client '{}' " +
                    "was specified.", publishPacket.getTopic(), clientId);
            return false;
        }
    }

    private static boolean setContentType(String clientId, ModifiablePublishPacket publishPacket, JsonNode constraint) {
        JsonNode replacementJson = constraint.get(ENVIRONMENT_REPLACEMENT);
        if (replacementJson != null && replacementJson.isTextual()) {
            String replacement = replacementJson.asText();
            // the content type must be a UTF-8 encoded String
            publishPacket.setContentType(replacement);
            log.info("Changed content type of message '{}' of client '{}' to: {}", publishPacket.getTopic(),
                    clientId, replacement);
            return true;
        } else {
            log.warn("No replacement of content type constraint for message of topic '{}' of client '{}' specified.",
                    publishPacket.getTopic(), clientId);
            return false;
        }
    }

    private static boolean setPayloadText(String clientId, ModifiablePublishPacket publishPacket, JsonNode constraint) {
        JsonNode replacementJson = constraint.get(ENVIRONMENT_REPLACEMENT);
        if (replacementJson != null && replacementJson.isTextual()) {
            String replacement = replacementJson.asText();
            // the payload must be a UTF-8 encoded String
            byte[] payload = replacement.getBytes(StandardCharsets.UTF_8);
            publishPacket.setPayload(ByteBuffer.wrap(payload));
            // if payload is textual the payload format indicator must be set
            publishPacket.setPayloadFormatIndicator(PayloadFormatIndicator.UTF_8);
            log.info("Changed payload of message '{}' of client '{}' to: {}", publishPacket.getTopic(), clientId,
                    StandardCharsets.UTF_8.decode(ByteBuffer.wrap(payload)));
            return true;
        } else {
            log.warn("No replacement of payload constraint for message of topic '{}' of client '{}' specified.",
                    publishPacket.getTopic(), clientId);
        }
        return false;
    }

    private static boolean blackenPayloadText(String clientId, ModifiablePublishPacket publishPacket,
                                                JsonNode constraint) throws CharacterCodingException {
        var payloadFormatIndicator =
                publishPacket.getPayloadFormatIndicator().orElse(PayloadFormatIndicator.UNSPECIFIED);
        String contentType = publishPacket.getContentType().orElse(null);

        if (payloadFormatIndicator == PayloadFormatIndicator.UTF_8 || MIME_TYPE_PLAIN_TEXT.equals(contentType)) {
            String payloadUtf8 = getUtf8PayloadOfPublishPacket(publishPacket);
            if (payloadUtf8 != null) { // if payload was specified in UTF-8
                setBlackenedPayloadInPublishPacket(publishPacket, payloadUtf8, constraint);
            }
            return true;
        } else { // not a UTF-8 payload
            log.warn("Blacken constraint could not be fulfilled because in the message of topic '{}' of client '{}' " +
                    "the payload is not indicated as UTF-8.", publishPacket.getTopic(), clientId);
            return false;
        }
    }

    @Nullable
    private static String getUtf8PayloadOfPublishPacket(ModifiablePublishPacket publishPacket)
            throws CharacterCodingException {
        String payloadUtf8 = null;
        Optional<ByteBuffer> optionalPayloadByteBuffer = publishPacket.getPayload();
        if (optionalPayloadByteBuffer.isPresent()) {
            var payloadBytebuffer = optionalPayloadByteBuffer.get();
            CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();
            var charBuffer = decoder.decode(payloadBytebuffer);
            payloadUtf8 = charBuffer.toString();
        }
        return payloadUtf8;
    }

    private static void setBlackenedPayloadInPublishPacket(ModifiablePublishPacket publishPacket, String payload,
                                                           JsonNode constraint) {
        int discloseLeft = extractNumberOfCharactersToDiscloseOnTheLeftSideFromParametersOrUseDefault(constraint);
        int discloseRight = extractNumberOfCharactersToDiscloseOnTheRightSideFromParametersOrUseDefault(constraint);
        var replacement = extractReplacementStringFromParametersOrUseDefault(constraint);
        String blackenedPayload = blackenText(payload, replacement, discloseLeft, discloseRight);
        publishPacket.setPayload(ByteBuffer.wrap(blackenedPayload.getBytes(StandardCharsets.UTF_8)));
    }

    private static String blackenText(String originalText, String replacement, int discloseLeft, int discloseRight) {
        if (discloseLeft + discloseRight >= originalText.length()) {
            return originalText;
        } else {
            var blackenedText = new StringBuilder();
            if (discloseLeft > 0) {
                blackenedText.append(originalText, 0, discloseLeft);
            }

            int replacedChars = originalText.length() - discloseLeft - discloseRight;
            blackenedText.append(String.valueOf(replacement).repeat(Math.max(0, replacedChars)));
            if (discloseRight > 0) {
                blackenedText.append(originalText.substring(discloseLeft + replacedChars));
            }

            return blackenedText.toString();
        }
    }

    private static int extractNumberOfCharactersToDiscloseOnTheLeftSideFromParametersOrUseDefault(JsonNode constraint) {
        return extractNumberOfCharactersToDiscloseOneSiteFromParametersOrUseDefault(
                DEFAULT_NUMBER_OF_CHARACTERS_TO_SHOW_LEFT, constraint, ENVIRONMENT_DISCLOSE_LEFT,
                ILLEGAL_PARAMETER_DISCLOSE_LEFT);
    }

    private static int extractNumberOfCharactersToDiscloseOnTheRightSideFromParametersOrUseDefault(JsonNode constraint) {
        return extractNumberOfCharactersToDiscloseOneSiteFromParametersOrUseDefault(
                DEFAULT_NUMBER_OF_CHARACTERS_TO_SHOW_RIGHT, constraint, ENVIRONMENT_DISCLOSE_RIGHT,
                ILLEGAL_PARAMETER_DISCLOSE_RIGHT);
    }

    private static int extractNumberOfCharactersToDiscloseOneSiteFromParametersOrUseDefault(
            int defaultNumberOfCharactersToShowOnSite, JsonNode constraint, String siteToDisclose,
            String illegalDiscloseParameterExceptionMessage) {
        int discloseOnSite = defaultNumberOfCharactersToShowOnSite;
        JsonNode discloseOnSiteJson = constraint.get(siteToDisclose);
        if (discloseOnSiteJson != null) {
            if (!discloseOnSiteJson.isNumber() || discloseOnSiteJson.asInt() < 0) {
                throw new IllegalArgumentException(illegalDiscloseParameterExceptionMessage);
            }
            discloseOnSite = discloseOnSiteJson.asInt();
        }
        return discloseOnSite;
    }

    private static String extractReplacementStringFromParametersOrUseDefault(JsonNode constraint) {
        var replacementString = DEFAULT_REPLACEMENT;
        JsonNode replacementStringJson = constraint.get(ENVIRONMENT_REPLACEMENT);
        if (replacementStringJson != null) {
            if (!replacementStringJson.isTextual()) {
                throw new IllegalArgumentException(ILLEGAL_PARAMETER_REPLACEMENT);
            }
            replacementString = replacementStringJson.asText();
        }
        return replacementString;
    }
}