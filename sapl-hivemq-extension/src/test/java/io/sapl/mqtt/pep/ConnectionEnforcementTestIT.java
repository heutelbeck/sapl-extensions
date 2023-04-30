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

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import com.hivemq.embedded.EmbeddedHiveMQ;
import org.junit.jupiter.api.*;

import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt5.Mqtt5BlockingClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client;
import com.hivemq.client.mqtt.mqtt5.exceptions.Mqtt5ConnAckException;
import com.hivemq.client.mqtt.mqtt5.message.auth.Mqtt5SimpleAuth;
import com.hivemq.client.mqtt.mqtt5.message.connect.connack.Mqtt5ConnAck;
import com.hivemq.client.mqtt.mqtt5.message.connect.connack.Mqtt5ConnAckReasonCode;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;

import io.sapl.mqtt.pep.cache.MqttClientState;

class ConnectionEnforcementTestIT extends SaplMqttPepTestUtil {

	private final static String PUBLISH_CLIENT_ID = "MQTT_CLIENT_PUBLISH";
	private final static ConcurrentHashMap<String, MqttClientState> MQTT_CLIENT_CACHE = new ConcurrentHashMap<>();

	private EmbeddedHiveMQ mqttBroker;

	@BeforeEach
	void beforeEach() {
		mqttBroker = buildAndStartBroker(MQTT_CLIENT_CACHE);
	}

	@AfterEach
	void afterEach() {
		stopBroker(mqttBroker);
	}

	@Test
	void when_clientStartsPermittedConnection_then_acknowledgeConnection() {
		// GIVEN
		Mqtt5BlockingClient blockingMqttClient = Mqtt5Client.builder()
				.identifier(PUBLISH_CLIENT_ID)
				.serverHost(BROKER_HOST)
				.serverPort(BROKER_PORT)
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

		// FINALLY
		blockingMqttClient.disconnect();
	}

	@Test
	void when_clientStartsDeniedConnection_then_prohibitConnection() {
		// GIVEN
		Mqtt5BlockingClient blockingMqttClient = Mqtt5Client.builder()
				.identifier(PUBLISH_CLIENT_ID)
				.serverHost(BROKER_HOST)
				.serverPort(BROKER_PORT)
				.simpleAuth(Mqtt5SimpleAuth
						.builder()
						.username("illegalUserName")
						.build())
				.buildBlocking();

		// THEN
		Mqtt5ConnAckException connAckException = assertThrowsExactly(Mqtt5ConnAckException.class,
				blockingMqttClient::connect);
		assertEquals(Mqtt5ConnAckReasonCode.NOT_AUTHORIZED, connAckException.getMqttMessage().getReasonCode());
		await().atMost(2, TimeUnit.SECONDS)
				.untilAsserted(() -> assertFalse(MQTT_CLIENT_CACHE.containsKey(PUBLISH_CLIENT_ID)));
	}

	@Test
	void when_existingConnectionGetsDenied_then_cancelConnection() {
		// GIVEN
		Mqtt5BlockingClient blockingMqttClient = Mqtt5Client.builder()
				.identifier(PUBLISH_CLIENT_ID)
				.serverHost(BROKER_HOST)
				.serverPort(BROKER_PORT)
				.simpleAuth(Mqtt5SimpleAuth
						.builder()
						.username("toggle")
						.build())
				.buildBlocking();

		// WHEN
		Mqtt5ConnAck connAckMessage = blockingMqttClient.connect();
		assertFalse(connAckMessage.getReasonCode().isError());

		// THEN
		await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
			assertFalse(blockingMqttClient.getState().isConnected());
			assertFalse(MQTT_CLIENT_CACHE.containsKey(PUBLISH_CLIENT_ID));
		});
	}

	@Test
	void when_oneOfMultipleExistingConnectionsGetsDenied_then_doNotCancelOtherConnections() {
		// GIVEN
		String secondClientId = "SECOND_MQTT_CLIENT_PUBLISH";

		Mqtt5BlockingClient blockingMqttClient = Mqtt5Client.builder()
				.identifier(PUBLISH_CLIENT_ID)
				.serverHost(BROKER_HOST)
				.serverPort(BROKER_PORT)
				.simpleAuth(Mqtt5SimpleAuth
						.builder()
						.username("toggle")
						.build())
				.buildBlocking();

		Mqtt5BlockingClient secondBlockingMqttClient = Mqtt5Client.builder()
				.identifier(secondClientId)
				.serverHost(BROKER_HOST)
				.serverPort(BROKER_PORT)
				.buildBlocking();

		// WHEN
		Mqtt5ConnAck connAckMessage = secondBlockingMqttClient.connect();
		assertFalse(connAckMessage.getReasonCode().isError());

		connAckMessage = blockingMqttClient.connect();
		assertFalse(connAckMessage.getReasonCode().isError());

		// THEN
		await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
			assertFalse(blockingMqttClient.getState().isConnected());
			assertTrue(secondBlockingMqttClient.getState().isConnected());

			assertFalse(MQTT_CLIENT_CACHE.containsKey(PUBLISH_CLIENT_ID));
			assertTrue(MQTT_CLIENT_CACHE.containsKey(secondClientId));
		});

		// FINALLY
		secondBlockingMqttClient.disconnect();
	}
}