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
import com.hivemq.client.mqtt.mqtt5.Mqtt5BlockingClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client;
import com.hivemq.client.mqtt.mqtt5.exceptions.Mqtt5ConnAckException;
import com.hivemq.client.mqtt.mqtt5.exceptions.Mqtt5PubAckException;
import com.hivemq.client.mqtt.mqtt5.exceptions.Mqtt5SubAckException;
import com.hivemq.client.mqtt.mqtt5.message.connect.connack.Mqtt5ConnAckReasonCode;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import com.hivemq.client.mqtt.mqtt5.message.publish.puback.Mqtt5PubAckReasonCode;
import com.hivemq.client.mqtt.mqtt5.message.subscribe.Mqtt5Subscribe;
import com.hivemq.client.mqtt.mqtt5.message.subscribe.suback.Mqtt5SubAckReasonCode;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.IdentifiableAuthorizationDecision;
import io.sapl.api.pdp.MultiAuthorizationSubscription;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.interpreter.InitializationException;
import io.sapl.mqtt.pep.util.SaplSubscriptionUtility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MqttActionEnforcementTimeoutTest extends SaplMqttPepTest {

    @BeforeAll
    public static void beforeAll() {
        // set logging level
        rootLogger.setLevel(Level.DEBUG);
    }

    @Test
    void when_timeoutWhileConnecting_then_denyConnection() {
        // GIVEN
        Flux<IdentifiableAuthorizationDecision> mqttConnectionDecisionFlux =
                Flux.never();

        PolicyDecisionPoint pdpMock = mock(PolicyDecisionPoint.class);
        when(pdpMock.decide(any(MultiAuthorizationSubscription.class)))
                .thenReturn(mqttConnectionDecisionFlux);

        String connectClientId = "connectClient";
        Mqtt5BlockingClient blockingMqttClient = Mqtt5Client.builder()
                .identifier(connectClientId)
                .serverHost(mqttServerHost)
                .serverPort(mqttServerPort)
                .buildBlocking();

        embeddedHiveMq = startEmbeddedHiveMqBroker(pdpMock,
                "src/test/resources/config/timeout/connection");

        // THEN
        Mqtt5ConnAckException connAckException = assertThrowsExactly(Mqtt5ConnAckException.class,
                blockingMqttClient::connect);
        assertEquals(Mqtt5ConnAckReasonCode.NOT_AUTHORIZED, connAckException.getMqttMessage().getReasonCode());

        // FINALLY
        embeddedHiveMq.stop().join();
    }

    @Test
    void when_timeoutWhileSubscribing_then_denySubscription() throws InitializationException {
        // GIVEN
        String subscriptionClientId = "MQTT_CLIENT_SUBSCRIBE";
        String subscriptionClientMqttConnectionSaplSubscriptionId =
                SaplSubscriptionUtility.buildSubscriptionId(subscriptionClientId, MqttPep.CONNECT_AUTHZ_ACTION);

        Flux<IdentifiableAuthorizationDecision> mqttConnectionDecisionFlux =
                Flux.just(new IdentifiableAuthorizationDecision(
                        subscriptionClientMqttConnectionSaplSubscriptionId, AuthorizationDecision.PERMIT));
        Flux<IdentifiableAuthorizationDecision> mqttSubscriptionDecisionFlux =
                Flux.never();

        PolicyDecisionPoint pdpMock = mock(PolicyDecisionPoint.class);
        when(pdpMock.decide(any(MultiAuthorizationSubscription.class)))
                .thenReturn(mqttConnectionDecisionFlux)
                .thenReturn(mqttSubscriptionDecisionFlux);

        Mqtt5Subscribe subscribeMessage = buildMqttSubscribeMessage("topic");

        // WHEN
        embeddedHiveMq = startEmbeddedHiveMqBroker(pdpMock,
                "src/test/resources/config/timeout/subscription");
        mqttClientSubscribe = startMqttClient(subscriptionClientId);

        Mqtt5SubAckException subAckException = assertThrowsExactly(Mqtt5SubAckException.class,
                ()->mqttClientSubscribe.subscribe(subscribeMessage));

        // THEN
        assertEquals(Mqtt5SubAckReasonCode.NOT_AUTHORIZED, subAckException.getMqttMessage().getReasonCodes().get(0));

        // FINALLY
        embeddedHiveMq.stop().join();
    }

    @Test
    void when_timeoutWhilePublishing_then_denyPublish() throws InitializationException {
        // GIVEN
        String publishClientId = "MQTT_CLIENT_PUBLISH";
        String publishClientMqttConnectionSaplSubscriptionId =
                SaplSubscriptionUtility.buildSubscriptionId(publishClientId, MqttPep.CONNECT_AUTHZ_ACTION);

        Flux<IdentifiableAuthorizationDecision> mqttConnectionDecisionFlux =
                Flux.just(new IdentifiableAuthorizationDecision(
                        publishClientMqttConnectionSaplSubscriptionId, AuthorizationDecision.PERMIT));
        Flux<IdentifiableAuthorizationDecision> mqttPublishDecisionFlux =
                Flux.never();

        PolicyDecisionPoint pdpMock = mock(PolicyDecisionPoint.class);
        when(pdpMock.decide(any(MultiAuthorizationSubscription.class)))
                .thenReturn(mqttConnectionDecisionFlux)
                .thenReturn(mqttPublishDecisionFlux);

        Mqtt5Publish publishMessage = buildMqttPublishMessage("denied_publish",
                1, false);

        // WHEN
        embeddedHiveMq = startEmbeddedHiveMqBroker(pdpMock,
                "src/test/resources/config/timeout/publish");
        mqttClientPublish = startMqttClient(publishClientId);

        Mqtt5PubAckException pubAckException = assertThrowsExactly(Mqtt5PubAckException.class,
                ()->mqttClientPublish.publish(publishMessage));

        // THEN
        assertEquals(Mqtt5PubAckReasonCode.NOT_AUTHORIZED, pubAckException.getMqttMessage().getReasonCode());

        // FINALLY
        embeddedHiveMq.stop().join();
    }
}