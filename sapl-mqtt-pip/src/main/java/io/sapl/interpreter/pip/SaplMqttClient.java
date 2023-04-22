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

package io.sapl.interpreter.pip;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.datatypes.MqttTopicFilter;
import com.hivemq.client.mqtt.exceptions.MqttClientStateException;
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client;
import com.hivemq.client.mqtt.mqtt5.message.auth.Mqtt5SimpleAuth;
import com.hivemq.client.mqtt.mqtt5.message.connect.connack.Mqtt5ConnAck;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import com.hivemq.client.mqtt.mqtt5.message.subscribe.Mqtt5Subscribe;
import com.hivemq.client.mqtt.mqtt5.message.unsubscribe.Mqtt5Unsubscribe;
import com.hivemq.client.mqtt.mqtt5.reactor.Mqtt5ReactorClient;
import io.sapl.api.interpreter.Val;
import io.sapl.interpreter.pip.util.DefaultResponseConfig;
import io.sapl.interpreter.pip.util.ErrorUtility;
import io.sapl.interpreter.pip.util.MqttClientValues;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuple4;
import reactor.util.function.Tuples;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static io.sapl.interpreter.pip.util.DefaultResponseUtility.getDefaultVal;
import static io.sapl.interpreter.pip.util.DefaultResponseUtility.getDefaultResponseConfig;
import static io.sapl.interpreter.pip.util.ErrorUtility.*;
import static io.sapl.interpreter.pip.util.PayloadFormatUtility.*;
import static io.sapl.interpreter.pip.util.SubscriptionUtility.*;
import static io.sapl.interpreter.pip.util.ConfigUtility.*;

/**
 * This mqtt client allows the user to receive mqtt messages of subscribed topics from a mqtt broker.
 */
@Slf4j
public class SaplMqttClient {

    /**
     * The reference for the client id in configurations.
     */
    public static final String ENVIRONMENT_CLIENT_ID = "clientId";
    /**
     * The reference for the broker address in configurations.
     */
    public static final String ENVIRONMENT_BROKER_ADDRESS = "brokerAddress";
    /**
     * The reference for the broker port in configurations.
     */
    public static final String ENVIRONMENT_BROKER_PORT = "brokerPort";
    private static final String ENVIRONMENT_MQTT_PIP_CONFIG = "mqttPipConfig";
    private static final String ENVIRONMENT_USERNAME = "username";
    private static final String ENVIRONMENT_QOS = "defaultQos";
    private static final String DEFAULT_CLIENT_ID = "mqtt_pip"; //A random UUID is added to the default value
    private static final String DEFAULT_USERNAME = "";
    private static final String DEFAULT_BROKER_ADDRESS = "localhost";
    private static final int DEFAULT_BROKER_PORT = 1883;
    private static final int DEFAULT_QOS = 0; //AT_MOST_ONCE

    static final ConcurrentHashMap<Integer, MqttClientValues> mqttClientCache = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, DefaultResponseConfig> defaultResponseConfigCache = new ConcurrentHashMap<>();

    /**
     * This method returns a reactive stream of mqtt messages of one or many subscribed topics.
     * @param topic A string or array of topic(s) for subscription.
     * @param config The configuration specified in the PDP configuration file.
     * @return A {@link Flux} of messages of the subscribed topic(s).
     */
    protected Flux<Val> buildSaplMqttMessageFlux(Val topic, Map<String, JsonNode> config) {
        return buildSaplMqttMessageFlux(topic, config, null, Val.UNDEFINED);
    }

    /**
     * This method returns a reactive stream of mqtt messages of one or many subscribed topics.
     * @param topic A string or array of topic(s) for subscription.
     * @param config The configuration specified in the PDP configuration file.
     * @param qos A {@link Flux} of the quality of service level of the mqtt subscription to the broker.
     *            Possible values: 0, 1, 2. This variable may be null.
     * @return A {@link Flux} of messages of the subscribed topic(s).
     */
    protected Flux<Val> buildSaplMqttMessageFlux(Val topic, Map<String, JsonNode> config, Val qos) {
        return buildSaplMqttMessageFlux(topic, config, qos, Val.UNDEFINED);
    }

    /**
     * This method returns a reactive stream of mqtt messages of one or many subscribed topics.
     * @param topic A string or array of topic(s) for subscription.
     * @param config The configuration specified in the PDP configuration file.
     * @param qos A {@link Flux} of the quality of service level of the mqtt subscription to the broker.
     *            Possible values: 0, 1, 2. This variable can be null.
     * @param mqttPipConfig An {@link ArrayNode} of {@link ObjectNode}s or only a single {@link ObjectNode} containing
     *                      configurations for the pip as a mqtt client. Each {@link ObjectNode} specifies the
     *                      configuration of a single mqtt client. Therefore, it is possible for the pip to build
     *                      multiple mqtt clients, that is the pip can subscribe to topics by different brokers.
     *                      This variable may be null.
     * @return A {@link Flux} of messages of the subscribed topic(s).
     */
    protected Flux<Val> buildSaplMqttMessageFlux(Val topic, Map<String, JsonNode> config, Val qos,
                                               Val mqttPipConfig) {
        // building mqtt message flux
        try {
            JsonNode pipMqttClientConfig = config.isEmpty() ? null : config.get(ENVIRONMENT_MQTT_PIP_CONFIG);
            Flux<Val> messageFlux = buildMqttMessageFlux(topic, qos, mqttPipConfig, pipMqttClientConfig);
            return addDefaultValueToMessageFlux(pipMqttClientConfig, mqttPipConfig, messageFlux)
                    .onErrorResume(error -> {
                        log.error("An error occurred on the sapl mqtt message flux: {}", error.getMessage());
                        return Flux.just(Val.error(error.getMessage())); // policy will be indeterminate
                    });

        } catch (Exception e) {
            log.error("An exception occurred while building the mqtt message flux: {}", e.getMessage());
            return Flux.just(Val.error("Failed to build stream of messages."));
        }
    }

    private Flux<Val> buildMqttMessageFlux(Val topic, Val qos, Val mqttPipConfig,
                                                    JsonNode pipMqttClientConfig) {
        Sinks.Many<Val> emitterUndefined = Sinks.many().multicast().directAllOrNothing(); // used to emit Val.UNDEFINED downstream behind retry
        Flux<Val> mqttMessageFlux = buildFluxOfConfigParams(qos, mqttPipConfig, pipMqttClientConfig)
                .map(params -> getConnectionAndSubscription(topic, pipMqttClientConfig, params))
                .flatMap(this::connectAndSubscribe)
                .map(this::getValFromMqttPublishMessage)
                .share()
                .retryWhen(getRetrySpec(pipMqttClientConfig)
                        .doBeforeRetry(retrySignal ->
                                emitValueOnRetry(pipMqttClientConfig, emitterUndefined, retrySignal)));

        return Flux.merge(mqttMessageFlux, emitterUndefined.asFlux());
    }

    private Flux<Val> addDefaultValueToMessageFlux(JsonNode pipMqttClientConfig, Val mqttPipConfig,
                                                   Flux<Val> messageFlux) {
        var messageFluxUuid = UUID.randomUUID();
        return Flux.merge(messageFlux, Mono.just(mqttPipConfig)
                        .map(pipConfigParams -> determineDefaultResponse(
                                messageFluxUuid, pipMqttClientConfig, pipConfigParams))
                        .delayUntil(val -> Mono.just(val).delayElement(Duration.ofMillis(
                                defaultResponseConfigCache.get(messageFluxUuid).getDefaultResponseTimeout())))
                        .takeUntilOther(messageFlux))
                .doFinally(signalType -> defaultResponseConfigCache.remove(messageFluxUuid));
    }

    private Val determineDefaultResponse(UUID messageFluxUuid, JsonNode pipMqttClientConfig, Val pipConfigParams) {
        var defaultResponseConfig = getDefaultResponseConfig(pipMqttClientConfig, pipConfigParams);
        defaultResponseConfigCache.put(messageFluxUuid, defaultResponseConfig);
        return getDefaultVal(defaultResponseConfig);
    }

    private Val getValFromMqttPublishMessage(Mqtt5Publish publishMessage) {
        int payloadFormatIndicator = getPayloadFormatIndicator(publishMessage);
        String contentType = getContentType(publishMessage);

        if (publishMessage.getPayloadFormatIndicator().isEmpty() && // no indicator in mqtt versions less than 5
                isValidUtf8String(publishMessage.getPayloadAsBytes())) {
            return Val.of(new String(publishMessage.getPayloadAsBytes(), StandardCharsets.UTF_8));

        } else if (payloadFormatIndicator == 1) { // payload is utf-8 encoded
            if ("application/json".equals(contentType)) { // mime type 'application/json'
                return getValOfJson(publishMessage);
            } else { // content type not specified or specific content type not implemented yet
                return Val.of(new String(publishMessage.getPayloadAsBytes(), StandardCharsets.UTF_8));
            }
        }

        return Val.of(convertBytesToArrayNode(publishMessage.getPayloadAsBytes()));
    }

    private Flux<Tuple2<Val, Val>> buildFluxOfConfigParams(Val qos, Val mqttPipConfig, JsonNode pipMqttClientConfig) {
        if (qos == null) { // if qos is not specified in attribute finder
            qos = Val.of(getConfigValueOrDefault(pipMqttClientConfig, ENVIRONMENT_QOS, DEFAULT_QOS));
        }
        ObjectNode mqttBrokerConfig = getMqttBrokerConfig(pipMqttClientConfig, mqttPipConfig); // broker config from attribute finder or pdp.json
        return Flux.just(Tuples.of(qos, Val.of(mqttBrokerConfig)));
    }

    private Tuple4<Mqtt5ReactorClient, Mono<Mqtt5ConnAck>, Flux<Mqtt5Publish>, Integer> getConnectionAndSubscription(
            Val topic, JsonNode pipMqttClientConfig, Tuple2<Val, Val> params) {
        var mqttBrokerConfig = params.getT2().getObjectNode();
        int brokerConfigHash = mqttBrokerConfig.hashCode();
        var clientValues = getOrBuildMqttClientValues(mqttBrokerConfig,
                brokerConfigHash, pipMqttClientConfig);

        var qos = params.getT1();
        Flux<Mqtt5Publish> mqttSubscription = buildMqttSubscription(brokerConfigHash, topic, qos);

        return Tuples.of(clientValues.getMqttReactorClient(), clientValues.getClientConnection(), mqttSubscription,
                brokerConfigHash);
    }

    private Flux<Mqtt5Publish> connectAndSubscribe(Tuple4<Mqtt5ReactorClient, Mono<Mqtt5ConnAck>,
                                                    Flux<Mqtt5Publish>, Integer> buildParams) {
        Mono<Mqtt5ConnAck> clientConnection = buildParams.getT2();
        Flux<Mqtt5Publish> mqttSubscription = buildParams.getT3();
        int brokerConfigHash = buildParams.getT4();
        return  clientConnection.thenMany(mqttSubscription)
                .doOnError(ErrorUtility::isErrorRelevantToRemoveClientCache,
                        throwable -> mqttClientCache.remove(brokerConfigHash));
    }

    private Flux<Mqtt5Publish> buildMqttSubscription(int brokerConfigHash, Val topic, Val qos) {
        Mqtt5Subscribe topicSubscription = buildTopicSubscription(topic, qos);
        MqttClientValues mqttClientValues = Objects.requireNonNull(mqttClientCache.get(brokerConfigHash));

        Mqtt5ReactorClient mqttClientReactor = mqttClientValues.getMqttReactorClient();
        return mqttClientReactor
                // FluxWithSingle is a combination of the single 'subscription acknowledgement' message
                // and a flux of published messages
                .subscribePublishes(topicSubscription)
                // Register callbacks to print messages when receiving the SUBACK or matching PUBLISH messages.
                .doOnSingle(mqtt5SubAck -> {
                    addSubscriptionsCountToSubscriptionList(mqttClientValues, mqtt5SubAck, topicSubscription);
                    log.info("Mqtt client '{}' subscribed to topic(s) '{}' with reason codes: {}",
                            getClientId(mqttClientReactor), topic, mqtt5SubAck.getReasonCodes());
                })
                .doOnNext(mqtt5Publish ->
                        log.debug("Mqtt client '{}' received message of topic '{}' with QoS '{}'.",
                                getClientId(mqttClientReactor),
                                mqtt5Publish.getTopic(), mqtt5Publish.getQos()))
                .onErrorResume(ErrorUtility::isClientCausedDisconnect, throwable -> Mono.empty())
                .doOnCancel(() -> handleMessageFluxCancel(brokerConfigHash, topic));
        }

    private MqttClientValues getOrBuildMqttClientValues(ObjectNode mqttBrokerConfig, int brokerConfigHash,
                                                        JsonNode pipMqttClientConfig) {
        MqttClientValues clientValues = mqttClientCache.get(brokerConfigHash);
        if (clientValues == null) {
            clientValues = buildClientValues(mqttBrokerConfig, brokerConfigHash, pipMqttClientConfig);
        }
        return clientValues;
    }

    private MqttClientValues buildClientValues(ObjectNode mqttBrokerConfig, int brokerConfigHash,
                                               JsonNode pipMqttClientConfig) {
        String clientId = getConfigValueOrDefault(mqttBrokerConfig, ENVIRONMENT_CLIENT_ID,
                getConfigValueOrDefault(pipMqttClientConfig, ENVIRONMENT_CLIENT_ID, DEFAULT_CLIENT_ID));
        Mqtt5ReactorClient mqttClientReactor = buildMqttReactorClient(mqttBrokerConfig, pipMqttClientConfig);
        Mono<Mqtt5ConnAck> mqttClientConnection = buildClientConnection(mqttClientReactor).share();
        var clientValues = new MqttClientValues(clientId, mqttClientReactor, mqttBrokerConfig,
                mqttClientConnection);
        mqttClientCache.put(brokerConfigHash, clientValues);
        return clientValues;
    }

    private Mqtt5ReactorClient buildMqttReactorClient(JsonNode mqttBrokerConfig, JsonNode pipMqttClientConfig) {
        return Mqtt5ReactorClient.from(buildMqttClient(mqttBrokerConfig, pipMqttClientConfig));
    }

    private Mqtt5Client buildMqttClient(JsonNode mqttBrokerConfig, JsonNode pipMqttClientConfig) {
        return MqttClient.builder()
                .useMqttVersion5()
                .identifier(getConfigValueOrDefault(mqttBrokerConfig, ENVIRONMENT_CLIENT_ID,
                        getConfigValueOrDefault(pipMqttClientConfig, ENVIRONMENT_CLIENT_ID, DEFAULT_CLIENT_ID)))
                .serverAddress(InetSocketAddress.createUnresolved(
                        getConfigValueOrDefault(mqttBrokerConfig, ENVIRONMENT_BROKER_ADDRESS, getConfigValueOrDefault(
                                pipMqttClientConfig, ENVIRONMENT_BROKER_ADDRESS, DEFAULT_BROKER_ADDRESS)),
                        getConfigValueOrDefault(mqttBrokerConfig, ENVIRONMENT_BROKER_PORT, getConfigValueOrDefault(
                                pipMqttClientConfig, ENVIRONMENT_BROKER_PORT, DEFAULT_BROKER_PORT))))
                .simpleAuth(buildAuthn(mqttBrokerConfig))
                .build();
    }

    private Mono<Mqtt5ConnAck> buildClientConnection(Mqtt5ReactorClient mqttClientReactor) {
        return mqttClientReactor.connect()
                // Register a callback to print a message when receiving the CONNACK message
                .doOnSuccess(mqtt5ConnAck ->
                        log.info("Successfully established connection for client '{}': {}",
                                getClientId(mqttClientReactor), mqtt5ConnAck.getReasonCode()))
                .doOnError(throwable ->
                        log.error("Mqtt client '{}' connection failed: {}",
                                getClientId(mqttClientReactor), throwable.getMessage()))
                .ignoreElement();
    }

    private Mqtt5SimpleAuth buildAuthn(JsonNode config) {
        return Mqtt5SimpleAuth.builder()
                .username(getConfigValueOrDefault(config, ENVIRONMENT_USERNAME, DEFAULT_USERNAME))
                .password(getPassword(config))
                .build();
    }

    private void handleMessageFluxCancel(int brokerConfigHash, Val topic) {
        unsubscribeTopics(brokerConfigHash, topic);
        MqttClientValues mqttClientValuesDisconnect = mqttClientCache.get(brokerConfigHash);
        if (mqttClientValuesDisconnect != null
                && mqttClientValuesDisconnect.isTopicSubscriptionsCountMapEmpty()) {
            disconnectClient(brokerConfigHash, mqttClientValuesDisconnect);
        }
    }

    private void unsubscribeTopics(int brokerConfigHash, Val topics) {
        MqttClientValues mqttClientValues = mqttClientCache.get(brokerConfigHash);
        if (mqttClientValues != null) {
            Collection<MqttTopicFilter> mqttTopicFilters = getMqttTopicFiltersToUnsubscribeAndReduceCount(
                    topics, mqttClientValues);

            Mqtt5Unsubscribe unsubscribeMessage =
                    Mqtt5Unsubscribe.builder().addTopicFilters(mqttTopicFilters).build();
            unsubscribeWithMessage(mqttClientValues, unsubscribeMessage);
        }
    }

    private void disconnectClient(int brokerConfigHash, MqttClientValues mqttClientValues) {
        mqttClientCache.remove(brokerConfigHash);
        String clientId = mqttClientValues.getClientId();
        mqttClientValues.getMqttReactorClient().disconnect()
                .onErrorResume(MqttClientStateException.class, e -> Mono.empty()) // if client already disconnected
                .doOnSuccess(success -> log.info("Client '{}' disconnected successfully.", clientId))
                .subscribe();
    }

    private Collection<MqttTopicFilter> getMqttTopicFiltersToUnsubscribeAndReduceCount(
            Val topics, MqttClientValues mqttClientValues) {
        Collection<MqttTopicFilter> mqttTopicFilters = new LinkedList<>();
        if (topics.isArray()){
            for (var topicNode : topics.getArrayNode()) {
                String topic = topicNode.asText();
                boolean isTopicExisting = mqttClientValues.countTopicSubscriptionsCountMapDown(topic);
                if (!isTopicExisting) {
                    mqttTopicFilters.add(MqttTopicFilter.of(topic));
                }
            }
        } else {
            String topic = topics.getText();
            boolean isTopicExisting = mqttClientValues.countTopicSubscriptionsCountMapDown(topic);
            if (!isTopicExisting) {
                mqttTopicFilters.add(MqttTopicFilter.of(topic));
            }
        }
        return mqttTopicFilters;
    }

    private void unsubscribeWithMessage(MqttClientValues clientRecord, Mqtt5Unsubscribe unsubscribeMessage) {
        clientRecord.getMqttReactorClient()
                .unsubscribe(unsubscribeMessage)
                // in case the client is already disconnected unsubscribing does not emit any value
                // including an exception. Therefore, the timeout method is necessary against buffer overflows.
                .timeout(Duration.ofMillis(10000), Mono.empty())
                .subscribe();
    }
}
