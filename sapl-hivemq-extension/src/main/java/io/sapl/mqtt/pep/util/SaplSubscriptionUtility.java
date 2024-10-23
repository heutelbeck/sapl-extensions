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
package io.sapl.mqtt.pep.util;

import static io.sapl.mqtt.pep.MqttPep.CONNECT_AUTHZ_ACTION;
import static io.sapl.mqtt.pep.MqttPep.PUBLISH_AUTHZ_ACTION;
import static io.sapl.mqtt.pep.MqttPep.SUBSCRIBE_AUTHZ_ACTION;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import javax.annotation.Nullable;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hivemq.extension.sdk.api.auth.parameter.SimpleAuthInput;
import com.hivemq.extension.sdk.api.auth.parameter.SubscriptionAuthorizerInput;
import com.hivemq.extension.sdk.api.interceptor.publish.parameter.PublishInboundInput;
import com.hivemq.extension.sdk.api.packets.connect.WillPublishPacket;
import com.hivemq.extension.sdk.api.packets.general.Qos;
import com.hivemq.extension.sdk.api.packets.publish.PayloadFormatIndicator;
import com.hivemq.extension.sdk.api.services.Services;
import com.hivemq.extension.sdk.api.services.subscription.TopicSubscription;

import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.mqtt.pep.cache.MqttClientState;
import lombok.experimental.UtilityClass;

/**
 * This utility class provides functions to build SAPL subscriptions for the
 * mqtt action enforcement.
 */
@UtilityClass
public class SaplSubscriptionUtility {

    /**
     * References the username of the mqtt client in the SAPL authorization
     * subscription.
     */
    public static final String  ENVIRONMENT_USER_NAME                   = "userName";
    /**
     * References the client id in the SAPL authorization subscription.
     */
    public static final String  ENVIRONMENT_CLIENT_ID                   = "clientId";
    /**
     * References the mqtt action type in the SAPL authorization subscription.
     */
    public static final String  ENVIRONMENT_AUTHZ_ACTION_TYPE           = "type";
    /**
     * References the topic of the mqtt message in the SAPL authorization
     * subscription.
     */
    public static final String  ENVIRONMENT_TOPIC                       = "topic";
    private static final String ENVIRONMENT_QOS                         = "qos";
    private static final String ENVIRONMENT_IS_RETAIN_MESSAGE           = "isRetain";
    private static final String ENVIRONMENT_FORMAT_INDICATOR            = "formatIndicator";
    private static final String ENVIRONMENT_CONTENT_TYPE                = "contentType";
    private static final String ENVIRONMENT_IS_CLEAN_SESSION            = "isCleanSession";
    private static final String ENVIRONMENT_LAST_WILL_TOPIC             = "lastWillTopic";
    private static final String ENVIRONMENT_LAST_WILL_QOS               = "lastWillQos";
    private static final String ENVIRONMENT_LAST_WILL_FORMAT_INDICATOR  = "lastWillFormatIndicator";
    private static final String ENVIRONMENT_LAST_WILL_CONTENT_TYPE      = "lastWillContentType";
    static final String         ENVIRONMENT_LAST_WILL_LAST_WILL_PAYLOAD = "lastWillPayload";
    private static final String ENVIRONMENT_MQTT_VERSION                = "mqttVersion";
    private static final String ENVIRONMENT_BROKER_NAME                 = "brokerName";
    private static final String ENVIRONMENT_BROKER_VERSION              = "brokerVersion";
    private static final String ENVIRONMENT_BROKER_EDITION              = "brokerEdition";
    private static final String BROKER_NAME                             = "HiveMQ";
    private static final String DEFAULT_USER_NAME                       = "anonymous";

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    /**
     * Builds a subscription id specific for the client and the mqtt action.
     *
     * @param clientId the id of the connected client
     * @param mqttAction used to distinguish the kind of mqtt action
     * @return the textual subscription id
     */
    public static String buildSubscriptionId(String clientId, String mqttAction) {
        return String.format("%1s_%2s", clientId, mqttAction);
    }

    /**
     * Builds a subscription id specific for the client, mqtt action and the topic.
     *
     * @param clientId the id of the connected client
     * @param mqttAction used to distinguish the kind of mqtt action
     * @param topic topic of the mqtt subscription or the mqtt publish
     * @return the textual subscription id
     */
    public static String buildSubscriptionId(String clientId, String mqttAction, String topic) {
        return String.format("%1s_%2s", buildSubscriptionId(clientId, mqttAction), topic);
    }

    /**
     * Creates a SAPL authorization subscription for mqtt connection enforcement.
     *
     * @param mqttClientState the currently cached client state
     * @param authnInput contains mqtt connection specifics
     * @return the SAPL authorization subscription for enforcement
     */
    public static AuthorizationSubscription buildSaplAuthzSubscriptionForMqttConnection(MqttClientState mqttClientState,
            SimpleAuthInput authnInput) {
        JsonNode subject     = buildSubjectForEnforcementSubscription(mqttClientState);
        JsonNode action      = buildActionForConnectionEnforcementSubscription(authnInput);
        JsonNode resource    = buildResourceWithBrokerSpecificsForEnforcementSubscription();
        JsonNode environment = buildEnvironmentForConnectionEnforcementSubscription(authnInput);
        return AuthorizationSubscription.of(subject, action, resource, environment);
    }

    /**
     * Creates a SAPL authorization subscription for mqtt subscription enforcement.
     *
     * @param mqttClientState the currently cached client state
     * @param topicSubscription contains mqtt subscriptions specifics
     * @return the SAPL authorization subscription for enforcement
     */
    public static AuthorizationSubscription buildSaplAuthzSubscriptionForMqttSubscription(
            MqttClientState mqttClientState, TopicSubscription topicSubscription) {
        JsonNode subject  = buildSubjectForEnforcementSubscription(mqttClientState);
        JsonNode action   = buildActionForSubscriptionEnforcementSubscription(topicSubscription.getQos());
        JsonNode resource = buildResourceForSubscriptionEnforcementSubscription(topicSubscription.getTopicFilter());
        return AuthorizationSubscription.of(subject, action, resource);
    }

    /**
     * Creates a SAPL authorization subscription for mqtt subscription enforcement.
     *
     * @param mqttClientState the currently cached client state
     * @param subscriptionAuthorizerInput contains mqtt subscriptions specifics
     * @return the SAPL authorization subscription for enforcement
     */
    public static AuthorizationSubscription buildSaplAuthzSubscriptionForMqttSubscription(
            MqttClientState mqttClientState, SubscriptionAuthorizerInput subscriptionAuthorizerInput) {
        JsonNode subject  = buildSubjectForEnforcementSubscription(mqttClientState);
        JsonNode action   = buildActionForSubscriptionEnforcementSubscription(
                subscriptionAuthorizerInput.getSubscription().getQos());
        JsonNode resource = buildResourceForSubscriptionEnforcementSubscription(subscriptionAuthorizerInput);
        return AuthorizationSubscription.of(subject, action, resource);
    }

    /**
     * Creates a SAPL authorization subscription for mqtt publish enforcement.
     *
     * @param mqttClientState the currently cached client state
     * @param publishInboundInput contains mqtt publish specifics
     * @return the SAPL authorization subscription for enforcement
     */
    public static AuthorizationSubscription buildSaplAuthzSubscriptionForMqttPublish(MqttClientState mqttClientState,
            PublishInboundInput publishInboundInput) {
        JsonNode subject  = buildSubjectForEnforcementSubscription(mqttClientState);
        JsonNode action   = buildActionForPublishEnforcementSubscription(publishInboundInput);
        JsonNode resource = buildResourceForPublishEnforcementSubscription(publishInboundInput);
        return AuthorizationSubscription.of(subject, action, resource);
    }

    private static JsonNode buildSubjectForEnforcementSubscription(MqttClientState mqttClientState) {
        String userName = DEFAULT_USER_NAME;
        if (mqttClientState.getUserName() != null) { // username is specified
            userName = mqttClientState.getUserName();
        }
        ObjectNode node = JSON.objectNode();
        node.set(ENVIRONMENT_CLIENT_ID, JSON.textNode(mqttClientState.getClientId()));
        node.set(ENVIRONMENT_USER_NAME, JSON.textNode(userName));
        return node;
    }

    private static JsonNode buildActionForConnectionEnforcementSubscription(@Nullable SimpleAuthInput authnInput) {
        Boolean  isCleanSession           = null;
        String   lastWillTopic            = null;
        Integer  lastWillQos              = null;
        String   lastWillFormatIndicator  = null;
        String   lastWillContentType      = null;
        JsonNode lastWillPayloadFormatted = null;
        if (authnInput != null) {
            isCleanSession = authnInput.getConnectPacket().getCleanStart();
            Optional<WillPublishPacket> lastWillAndTestamentPublish = authnInput.getConnectPacket().getWillPublish();
            if (lastWillAndTestamentPublish.isPresent()) { // if last will and testament is specified
                lastWillTopic            = lastWillAndTestamentPublish.get().getTopic();
                lastWillQos              = lastWillAndTestamentPublish.get().getQos().getQosNumber();
                lastWillFormatIndicator  = lastWillAndTestamentPublish.get().getPayloadFormatIndicator()
                        .orElse(PayloadFormatIndicator.UNSPECIFIED).toString().toLowerCase();
                lastWillContentType      = lastWillAndTestamentPublish.get().getContentType().orElse(null);
                lastWillPayloadFormatted = getFormattedLastWillPayload(lastWillAndTestamentPublish.get(),
                        lastWillFormatIndicator);
            }
        }
        return JSON.objectNode().put(ENVIRONMENT_AUTHZ_ACTION_TYPE, CONNECT_AUTHZ_ACTION)
                .put(ENVIRONMENT_IS_CLEAN_SESSION, isCleanSession).put(ENVIRONMENT_LAST_WILL_TOPIC, lastWillTopic)
                .put(ENVIRONMENT_LAST_WILL_QOS, lastWillQos)
                .put(ENVIRONMENT_LAST_WILL_FORMAT_INDICATOR, lastWillFormatIndicator)
                .put(ENVIRONMENT_LAST_WILL_CONTENT_TYPE, lastWillContentType)
                .set(ENVIRONMENT_LAST_WILL_LAST_WILL_PAYLOAD, lastWillPayloadFormatted);
    }

    private static JsonNode getFormattedLastWillPayload(WillPublishPacket willPublishPacket,
            String lastWillFormatIndicator) {
        JsonNode   lastWillPayloadFormatted;
        ByteBuffer lastWillPayload = willPublishPacket.getPayload().orElse(null);
        if (lastWillPayload == null) { // no payload specified
            lastWillPayloadFormatted = JSON.nullNode();
        } else if (lastWillFormatIndicator.equals(PayloadFormatIndicator.UTF_8.toString().toLowerCase())) { // payload
                                                                                                            // is utf-8
                                                                                                            // encoded
            lastWillPayloadFormatted = JSON.textNode(new String(lastWillPayload.array(), StandardCharsets.UTF_8));
        } else {
            var lastWillPayloadBytes = new byte[lastWillPayload.remaining()];
            lastWillPayloadFormatted = JSON.binaryNode(lastWillPayloadBytes);
        }
        return lastWillPayloadFormatted;
    }

    private static JsonNode buildActionForSubscriptionEnforcementSubscription(Qos qualityOfService) {
        int qosLevel = qualityOfService.getQosNumber();
        return JSON.objectNode().put(ENVIRONMENT_AUTHZ_ACTION_TYPE, SUBSCRIBE_AUTHZ_ACTION).put(ENVIRONMENT_QOS,
                qosLevel);
    }

    private static JsonNode buildActionForPublishEnforcementSubscription(PublishInboundInput publishInboundInput) {
        int     qos           = publishInboundInput.getPublishPacket().getQos().getQosNumber();
        boolean retainMessage = publishInboundInput.getPublishPacket().getRetain();
        return JSON.objectNode().put(ENVIRONMENT_AUTHZ_ACTION_TYPE, PUBLISH_AUTHZ_ACTION).put(ENVIRONMENT_QOS, qos)
                .put(ENVIRONMENT_IS_RETAIN_MESSAGE, retainMessage);
    }

    private static JsonNode buildResourceForPublishEnforcementSubscription(PublishInboundInput publishInboundInput) {
        var        publishPacket   = publishInboundInput.getPublishPacket();
        String     topic           = publishPacket.getTopic();
        String     formatIndicator = publishInboundInput.getPublishPacket().getPayloadFormatIndicator()
                .orElse(PayloadFormatIndicator.UNSPECIFIED).toString().toLowerCase();
        String     contentType     = publishInboundInput.getPublishPacket().getContentType().orElse(null);
        ObjectNode resource        = buildResourceWithBrokerSpecificsForEnforcementSubscription();
        return resource.put(ENVIRONMENT_TOPIC, topic).put(ENVIRONMENT_FORMAT_INDICATOR, formatIndicator)
                .put(ENVIRONMENT_CONTENT_TYPE, contentType);
    }

    private static JsonNode buildResourceForSubscriptionEnforcementSubscription(
            SubscriptionAuthorizerInput subscriptionAuthorizerInput) {
        String topic = subscriptionAuthorizerInput.getSubscription().getTopicFilter();
        return buildResourceForSubscriptionEnforcementSubscription(topic);
    }

    private static JsonNode buildResourceForSubscriptionEnforcementSubscription(String topic) {
        ObjectNode resource = buildResourceWithBrokerSpecificsForEnforcementSubscription();
        return resource.put(ENVIRONMENT_TOPIC, topic);
    }

    private static ObjectNode buildResourceWithBrokerSpecificsForEnforcementSubscription() {
        String brokerVersion = Services.adminService().getServerInformation().getVersion();
        String brokerEdition = Services.adminService().getLicenseInformation().getEdition().toString().toLowerCase();
        return JSON.objectNode().put(ENVIRONMENT_BROKER_NAME, BROKER_NAME)
                .put(ENVIRONMENT_BROKER_VERSION, brokerVersion).put(ENVIRONMENT_BROKER_EDITION, brokerEdition);
    }

    private static ObjectNode buildEnvironmentForConnectionEnforcementSubscription(
            @Nullable SimpleAuthInput authnInput) {
        String mqttVersion = null;
        if (authnInput != null) {
            mqttVersion = authnInput.getConnectionInformation().getMqttVersion().toString();
        }
        return JSON.objectNode().put(ENVIRONMENT_MQTT_VERSION, mqttVersion);
    }
}
