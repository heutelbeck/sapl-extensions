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

package io.sapl.mqtt.pep;

import ch.qos.logback.classic.Level;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt5.Mqtt5BlockingClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client;
import com.hivemq.client.mqtt.mqtt5.exceptions.Mqtt5ConnAckException;
import com.hivemq.client.mqtt.mqtt5.message.auth.Mqtt5SimpleAuth;
import com.hivemq.client.mqtt.mqtt5.message.connect.connack.Mqtt5ConnAck;
import com.hivemq.client.mqtt.mqtt5.message.connect.connack.Mqtt5ConnAckReasonCode;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import io.sapl.mqtt.pep.cache.MqttClientState;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

class   ConnectionEnforcementTest extends SaplMqttPepTest {

    private static final String publishClientId = "MQTT_CLIENT_PUBLISH";
    private static final ConcurrentHashMap<String, MqttClientState> mqttClientCache = new ConcurrentHashMap<>();

    @BeforeAll
    public static void beforeAll() {
        // set logging level
        rootLogger.setLevel(Level.DEBUG);

        embeddedHiveMq = startEmbeddedHiveMqBroker(mqttClientCache);
    }

    @AfterAll
    public static void afterAll() {
        embeddedHiveMq.stop().join();
    }

    @Test
    void when_clientStartsPermittedConnection_then_acknowledgeConnection() {
        // GIVEN
        Mqtt5BlockingClient blockingMqttClient = Mqtt5Client.builder()
                .identifier(publishClientId)
                .serverHost(mqttServerHost)
                .serverPort(mqttServerPort)
                .willPublish(Mqtt5Publish.builder()
                        .topic("lastWillTopic")
                        .qos(MqttQos.AT_MOST_ONCE)
                        .payload("lastWillMessage".getBytes(StandardCharsets.UTF_8))
                        .build())
                .buildBlocking();

        // WHEN
        Mqtt5ConnAck connAckMessage = blockingMqttClient.connect();

        // THEN
        assertFalse(connAckMessage.getReasonCode().isError());

        //FINALLY
        blockingMqttClient.disconnect();
    }

    @Test
    void when_clientStartsDeniedConnection_then_prohibitConnection() {
        // GIVEN
        Mqtt5BlockingClient blockingMqttClient = Mqtt5Client.builder()
                .identifier(publishClientId)
                .serverHost(mqttServerHost)
                .serverPort(mqttServerPort)
                .simpleAuth(Mqtt5SimpleAuth
                        .builder()
                        .username("illegalUserName")
                        .build())
                .buildBlocking();

        // THEN
        Mqtt5ConnAckException connAckException = assertThrowsExactly(Mqtt5ConnAckException.class,
                blockingMqttClient::connect);
        assertEquals(Mqtt5ConnAckReasonCode.NOT_AUTHORIZED, connAckException.getMqttMessage().getReasonCode());
        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() ->
                assertFalse(mqttClientCache.containsKey(publishClientId)));
    }

    @Test
    void when_existingConnectionGetsDenied_then_cancelConnection() {
        // GIVEN
        Mqtt5BlockingClient blockingMqttClient = Mqtt5Client.builder()
                .identifier(publishClientId)
                .serverHost(mqttServerHost)
                .serverPort(mqttServerPort)
                .simpleAuth(Mqtt5SimpleAuth
                        .builder()
                        .username("toggle")
                        .build())
                .buildBlocking();

        //WHEN
        Mqtt5ConnAck connAckMessage = blockingMqttClient.connect();
        assertFalse(connAckMessage.getReasonCode().isError());

        // THEN
        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            assertFalse(blockingMqttClient.getState().isConnected());
            assertFalse(mqttClientCache.containsKey(publishClientId));
        });
    }

    @Test
    void when_oneOfMultipleExistingConnectionsGetsDenied_then_doNotCancelOtherConnections() {
        // GIVEN
        String secondClientId = "SECOND_MQTT_CLIENT_PUBLISH";

        Mqtt5BlockingClient blockingMqttClient = Mqtt5Client.builder()
                .identifier(publishClientId)
                .serverHost(mqttServerHost)
                .serverPort(mqttServerPort)
                .simpleAuth(Mqtt5SimpleAuth
                        .builder()
                        .username("toggle")
                        .build())
                .buildBlocking();

        Mqtt5BlockingClient secondBlockingMqttClient = Mqtt5Client.builder()
                .identifier(secondClientId)
                .serverHost(mqttServerHost)
                .serverPort(mqttServerPort)
                .buildBlocking();

        //WHEN
        Mqtt5ConnAck connAckMessage = secondBlockingMqttClient.connect();
        assertFalse(connAckMessage.getReasonCode().isError());

        connAckMessage = blockingMqttClient.connect();
        assertFalse(connAckMessage.getReasonCode().isError());

        // THEN
        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            assertFalse(blockingMqttClient.getState().isConnected());
            assertTrue(secondBlockingMqttClient.getState().isConnected());

            assertFalse(mqttClientCache.containsKey(publishClientId));
            assertTrue(mqttClientCache.containsKey(secondClientId));
        });

        // FINALLY
        secondBlockingMqttClient.disconnect();
    }
}
