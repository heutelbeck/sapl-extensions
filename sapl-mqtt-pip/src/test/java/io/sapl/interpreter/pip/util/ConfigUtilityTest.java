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

package io.sapl.interpreter.pip.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.sapl.api.interpreter.Val;
import org.junit.jupiter.api.Test;

import java.util.NoSuchElementException;

import static io.sapl.interpreter.pip.SaplMqttClient.*;
import static io.sapl.interpreter.pip.util.ConfigUtility.*;
import static io.sapl.interpreter.pip.util.ErrorUtility.ENVIRONMENT_ERROR_RETRY_ATTEMPTS;
import static org.junit.jupiter.api.Assertions.*;

class ConfigUtilityTest {

    private static final String ENVIRONMENT_DEFAULT_MESSAGE = "defaultMessage";

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    @Test
    void when_jsonValueIsSpecifiedInMqttClientConfig_then_returnJsonValue() {
        // GIVEN
        JsonNode defaultConfig = JSON.nullNode();
        JsonNode mqttPipConfig = JSON.objectNode()
                .set(ENVIRONMENT_DEFAULT_MESSAGE, JSON.objectNode()
                        .put("key", "value"));

        // WHEN
        var config = ConfigUtility.getConfigValueOrDefault(mqttPipConfig,
                ENVIRONMENT_DEFAULT_MESSAGE, defaultConfig);

        // THEN
        assertEquals("value", config.get("key").asText());
    }

    @Test
    void when_jsonValueIsNotSpecifiedInMqttClientConfig_then_returnDefaultJsonValue() {
        // GIVEN
        JsonNode defaultConfig = JSON.objectNode()
                .put("key", "value");
        JsonNode mqttPipConfig = JSON.objectNode();

        // WHEN
        var config = ConfigUtility.getConfigValueOrDefault(mqttPipConfig,
                ENVIRONMENT_DEFAULT_MESSAGE, defaultConfig);

        // THEN
        assertEquals("value", config.get("key").asText());
    }

    @Test
    void when_longValueIsSpecifiedInMqttClientConfig_then_returnLongValue() {
        // GIVEN
        long defaultConfig = 5;
        JsonNode mqttPipConfig = JSON.objectNode()
                .put(ENVIRONMENT_ERROR_RETRY_ATTEMPTS, 6)
                .put(ENVIRONMENT_DEFAULT_BROKER_CONFIG_NAME, "production")
                .set(ENVIRONMENT_BROKER_CONFIG, JSON.arrayNode()
                        .add(JSON.objectNode()
                                .put(ENVIRONMENT_BROKER_CONFIG_NAME, "production")
                                .put(ENVIRONMENT_BROKER_ADDRESS, "localhost")
                                .put(ENVIRONMENT_BROKER_PORT, 1883)
                                .put(ENVIRONMENT_CLIENT_ID, "mqttPipDefault")));

        // WHEN
        var config = ConfigUtility.getConfigValueOrDefault(mqttPipConfig,
                ENVIRONMENT_ERROR_RETRY_ATTEMPTS, defaultConfig);

        // THEN
        assertEquals(6, config);
    }

    @Test
    void when_getMqttBrokerConfigIsCalledWithEmptyMqttClientConfig_then_throwNoSuchElementException() {
        // GIVEN
        Val undefined = Val.UNDEFINED;

        // THEN
        assertThrowsExactly(NoSuchElementException.class,
                ()-> ConfigUtility.getMqttBrokerConfig(null, undefined));
    }

    @Test
    void when_gettingMqttBrokerConfigAndNoConfigParamsAndEmptyConfigInPdpConfig_then_throwException() {
        // GIVEN
        JsonNode mqttPipConfig = JSON.objectNode()
                .put(ENVIRONMENT_DEFAULT_BROKER_CONFIG_NAME, "production")
                .set(ENVIRONMENT_BROKER_CONFIG, JSON.nullNode());

        // THEN
        assertThrowsExactly(NoSuchElementException.class,
                () -> ConfigUtility.getMqttBrokerConfig(mqttPipConfig, Val.UNDEFINED));
    }

    @Test
    void when_gettingMqttBrokerConfigAndNoReferenceInConfigParamsAndEmptyConfigInPdpConfig_then_throwException() {
        // GIVEN
        JsonNode mqttPipConfig = JSON.objectNode()
                .put(ENVIRONMENT_DEFAULT_BROKER_CONFIG_NAME, "production")
                .set(ENVIRONMENT_BROKER_CONFIG, JSON.nullNode());

        // THEN
        assertThrowsExactly(NoSuchElementException.class,
                () -> ConfigUtility.getMqttBrokerConfig(mqttPipConfig, Val.FALSE));
    }

    @Test
    void when_gettingMqttBrokerConfigAndReferencingEmptyPdpConfig_then_throwException() {
        // GIVEN
        JsonNode mqttPipConfig = JSON.objectNode()
                .put(ENVIRONMENT_DEFAULT_BROKER_CONFIG_NAME, "production")
                .set(ENVIRONMENT_BROKER_CONFIG, JSON.nullNode());

        // THEN
        assertThrowsExactly(NoSuchElementException.class,
                () -> ConfigUtility.getMqttBrokerConfig(mqttPipConfig, Val.of("reference")));
    }

    @Test
    void when_gettingMqttBrokerConfigAndBrokerConfigNameDoesNotEqualTheReferencedConfiguration_then_throwException() {
        // GIVEN
        JsonNode mqttPipConfig = JSON.objectNode()
                .put(ENVIRONMENT_DEFAULT_BROKER_CONFIG_NAME, "production")
                .set(ENVIRONMENT_BROKER_CONFIG, JSON.objectNode()
                        .put(ENVIRONMENT_BROKER_CONFIG_NAME, "production")
                        .put(ENVIRONMENT_BROKER_ADDRESS, "localhost")
                        .put(ENVIRONMENT_BROKER_PORT, 1883)
                        .put(ENVIRONMENT_CLIENT_ID, "mqttPipDefault"));

        // THEN
        assertThrowsExactly(NoSuchElementException.class,
                () -> ConfigUtility.getMqttBrokerConfig(mqttPipConfig, Val.of("reference")));
    }

    @Test
    void when_gettingMqttBrokerConfigAndReferenceViaParamsButNoReferenceSpecifiedInPdpConfigArray_then_throwException() {
        // GIVEN
        JsonNode mqttPipConfig = JSON.objectNode()
                .put(ENVIRONMENT_DEFAULT_BROKER_CONFIG_NAME, "production")
                .set(ENVIRONMENT_BROKER_CONFIG, JSON.objectNode()
                        .put(ENVIRONMENT_BROKER_ADDRESS, "localhost")
                        .put(ENVIRONMENT_BROKER_PORT, 1883)
                        .put(ENVIRONMENT_CLIENT_ID, "mqttPipDefault"));

        // THEN
        assertThrowsExactly(NoSuchElementException.class,
                () -> ConfigUtility.getMqttBrokerConfig(mqttPipConfig, Val.of("reference")));
    }

    @Test
    void when_gettingMqttBrokerConfigAndReferenceIsSpecifiedInParamsButNoReferenceSpecifiedInPdpConfigObject_then_throwException() {
        // GIVEN
        JsonNode mqttPipConfig = JSON.objectNode()
                .put(ENVIRONMENT_DEFAULT_BROKER_CONFIG_NAME, "production")
                .set(ENVIRONMENT_BROKER_CONFIG, JSON.arrayNode()
                                .add(JSON.objectNode()
                                        .put(ENVIRONMENT_BROKER_ADDRESS, "localhost")
                                        .put(ENVIRONMENT_BROKER_PORT, 1883)
                                        .put(ENVIRONMENT_CLIENT_ID, "mqttPipDefault")));

        // THEN
        assertThrowsExactly(NoSuchElementException.class,
                () -> ConfigUtility.getMqttBrokerConfig(mqttPipConfig, Val.UNDEFINED));
    }

    @Test
    void when_configAsArrayInPdpJsonIsReferencedViaAttributeFinder_then_getReferencedConfig () {
        // GIVEN
        JsonNode mqttPipConfig = JSON.objectNode()
                .put(ENVIRONMENT_DEFAULT_BROKER_CONFIG_NAME, "production")
                .set(ENVIRONMENT_BROKER_CONFIG, JSON.arrayNode()
                        .add(JSON.objectNode()
                            .put(ENVIRONMENT_BROKER_CONFIG_NAME, "production")
                            .put(ENVIRONMENT_BROKER_ADDRESS, "localhost")
                            .put(ENVIRONMENT_BROKER_PORT, 1883)
                            .put(ENVIRONMENT_CLIENT_ID, "mqttPipDefault"))
                        .add(JSON.objectNode()
                                .put(ENVIRONMENT_BROKER_CONFIG_NAME, "broker2")
                                .put(ENVIRONMENT_BROKER_ADDRESS, "localhost")
                                .put(ENVIRONMENT_BROKER_PORT, 1883)
                                .put(ENVIRONMENT_CLIENT_ID, "broker2")));
        Val brokerConfig = Val.of("broker2");

        // WHEN
        var config = ConfigUtility.getMqttBrokerConfig(mqttPipConfig, brokerConfig);

        // THEN
        assertEquals("broker2", config.get(ENVIRONMENT_CLIENT_ID).asText());
    }

    @Test
    void when_configAsObjectInPdpJsonIsReferencedViaAttributeFinder_then_getReferencedConfig () {
        // GIVEN
        JsonNode mqttPipConfig = JSON.objectNode()
                .put(ENVIRONMENT_DEFAULT_BROKER_CONFIG_NAME, "production")
                .set(ENVIRONMENT_BROKER_CONFIG, JSON.objectNode()
                                .put(ENVIRONMENT_BROKER_CONFIG_NAME, "broker2")
                                .put(ENVIRONMENT_BROKER_ADDRESS, "localhost")
                                .put(ENVIRONMENT_BROKER_PORT, 1883)
                                .put(ENVIRONMENT_CLIENT_ID, "broker2"));
        Val brokerConfig = Val.of("broker2");

        // WHEN
        var config = ConfigUtility.getMqttBrokerConfig(mqttPipConfig, brokerConfig);

        // THEN
        assertEquals("broker2", config.get(ENVIRONMENT_CLIENT_ID).asText());
    }

    @Test
    void when_configAsObjectInPdpJsonAndNoAttributeFinderParam_then_getConfigFromPdpJson() {
        // GIVEN
        Val undefined = Val.UNDEFINED;
        JsonNode mqttPipConfig = JSON.objectNode()
                .put(ENVIRONMENT_DEFAULT_BROKER_CONFIG_NAME, "production")
                .set(ENVIRONMENT_BROKER_CONFIG, JSON.objectNode()
                        .put(ENVIRONMENT_BROKER_CONFIG_NAME, "production")
                        .put(ENVIRONMENT_BROKER_ADDRESS, "localhost")
                        .put(ENVIRONMENT_BROKER_PORT, 1883)
                        .put(ENVIRONMENT_CLIENT_ID, "production"));

        // WHEN
        var config = ConfigUtility.getMqttBrokerConfig(mqttPipConfig, undefined);

        // THEN
        assertEquals("production", config.get(ENVIRONMENT_CLIENT_ID).asText());
    }


}