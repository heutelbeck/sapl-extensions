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
package io.sapl.mqtt.pep;

import static io.sapl.mqtt.pep.MqttTestUtil.BROKER_HOST;
import static io.sapl.mqtt.pep.MqttTestUtil.BROKER_PORT;
import static io.sapl.mqtt.pep.MqttTestUtil.buildAndStartBroker;
import static io.sapl.mqtt.pep.MqttTestUtil.stopBroker;
import static org.awaitility.Awaitility.await;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt5.Mqtt5BlockingClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client;
import com.hivemq.client.mqtt.mqtt5.exceptions.Mqtt5ConnAckException;
import com.hivemq.client.mqtt.mqtt5.message.auth.Mqtt5SimpleAuth;
import com.hivemq.client.mqtt.mqtt5.message.connect.connack.Mqtt5ConnAck;
import com.hivemq.client.mqtt.mqtt5.message.connect.connack.Mqtt5ConnAckReasonCode;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import com.hivemq.embedded.EmbeddedHiveMQ;

import io.sapl.mqtt.pep.cache.MqttClientState;

@DisplayName("Connection enforcement integration")
class ConnectionEnforcementTestIT {

    @TempDir
    Path dataFolder;
    @TempDir
    Path configFolder;
    @TempDir
    Path extensionFolder;

    private static final String                                     PUBLISH_CLIENT_ID = "MQTT_CLIENT_PUBLISH";
    private static final ConcurrentHashMap<String, MqttClientState> MQTT_CLIENT_CACHE = new ConcurrentHashMap<>();

    private EmbeddedHiveMQ mqttBroker;

    @BeforeEach
    void beforeEach() {
        mqttBroker = buildAndStartBroker(dataFolder, configFolder, extensionFolder, MQTT_CLIENT_CACHE);
    }

    @AfterEach
    void afterEach() {
        stopBroker(mqttBroker);
    }

    @Test
    void when_clientStartsPermittedConnection_then_acknowledgeConnection() {
        // GIVEN
        Mqtt5BlockingClient blockingMqttClient = Mqtt5Client.builder().identifier(PUBLISH_CLIENT_ID)
                .serverHost(BROKER_HOST).serverPort(BROKER_PORT)
                .willPublish(Mqtt5Publish.builder().topic("lastWillTopic").qos(MqttQos.AT_MOST_ONCE)
                        .payload("lastWillMessage".getBytes(StandardCharsets.UTF_8)).build())
                .buildBlocking();

        // WHEN
        Mqtt5ConnAck connAckMessage = blockingMqttClient.connect();

        // THEN
        assertThat(connAckMessage.getReasonCode().isError()).isFalse();

        // FINALLY
        blockingMqttClient.disconnect();
    }

    @Test
    void when_clientStartsDeniedConnection_then_prohibitConnection() {
        // GIVEN
        Mqtt5BlockingClient blockingMqttClient = Mqtt5Client.builder().identifier(PUBLISH_CLIENT_ID)
                .serverHost(BROKER_HOST).serverPort(BROKER_PORT)
                .simpleAuth(Mqtt5SimpleAuth.builder().username("illegalUserName").build()).buildBlocking();

        // THEN
        assertThatThrownBy(blockingMqttClient::connect).isExactlyInstanceOf(Mqtt5ConnAckException.class)
                .satisfies(e -> assertThat(((Mqtt5ConnAckException) e).getMqttMessage().getReasonCode())
                        .isEqualTo(Mqtt5ConnAckReasonCode.NOT_AUTHORIZED));
        await().atMost(2, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(MQTT_CLIENT_CACHE.containsKey(PUBLISH_CLIENT_ID)).isFalse());
    }

    @Test
    void when_existingConnectionGetsDenied_then_cancelConnection() {
        // GIVEN
        Mqtt5BlockingClient blockingMqttClient = Mqtt5Client.builder().identifier(PUBLISH_CLIENT_ID)
                .serverHost(BROKER_HOST).serverPort(BROKER_PORT)
                .simpleAuth(Mqtt5SimpleAuth.builder().username("toggle").build()).buildBlocking();

        // WHEN
        Mqtt5ConnAck connAckMessage = blockingMqttClient.connect();
        assertThat(connAckMessage.getReasonCode().isError()).isFalse();

        // THEN
        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(blockingMqttClient.getState().isConnected()).isFalse();
            assertThat(MQTT_CLIENT_CACHE.containsKey(PUBLISH_CLIENT_ID)).isFalse();
        });
    }

    @Test
    void when_oneOfMultipleExistingConnectionsGetsDenied_then_doNotCancelOtherConnections() {
        // GIVEN
        String secondClientId = "SECOND_MQTT_CLIENT_PUBLISH";

        Mqtt5BlockingClient blockingMqttClient = Mqtt5Client.builder().identifier(PUBLISH_CLIENT_ID)
                .serverHost(BROKER_HOST).serverPort(BROKER_PORT)
                .simpleAuth(Mqtt5SimpleAuth.builder().username("toggle").build()).buildBlocking();

        Mqtt5BlockingClient secondBlockingMqttClient = Mqtt5Client.builder().identifier(secondClientId)
                .serverHost(BROKER_HOST).serverPort(BROKER_PORT).buildBlocking();

        // WHEN
        Mqtt5ConnAck connAckMessage = secondBlockingMqttClient.connect();
        assertThat(connAckMessage.getReasonCode().isError()).isFalse();

        connAckMessage = blockingMqttClient.connect();
        assertThat(connAckMessage.getReasonCode().isError()).isFalse();

        // THEN
        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(blockingMqttClient.getState().isConnected()).isFalse();
            assertThat(secondBlockingMqttClient.getState().isConnected()).isTrue();

            assertThat(MQTT_CLIENT_CACHE.containsKey(PUBLISH_CLIENT_ID)).isFalse();
            assertThat(MQTT_CLIENT_CACHE.containsKey(secondClientId)).isTrue();
        });

        // FINALLY
        secondBlockingMqttClient.disconnect();
    }
}
