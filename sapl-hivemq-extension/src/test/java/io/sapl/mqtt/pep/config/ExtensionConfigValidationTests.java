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
package io.sapl.mqtt.pep.config;

import static io.sapl.mqtt.pep.config.SaplMqttExtensionConfig.DEFAULT_CONNECTION_ENFORCEMENT_TIMEOUT_MILLIS;
import static io.sapl.mqtt.pep.config.SaplMqttExtensionConfig.DEFAULT_PDP_IMPLEMENTATION;
import static io.sapl.mqtt.pep.config.SaplMqttExtensionConfig.DEFAULT_PUBLISH_ENFORCEMENT_TIMEOUT_MILLIS;
import static io.sapl.mqtt.pep.config.SaplMqttExtensionConfig.DEFAULT_REMOTE_PDP_BACK_OFF_FACTOR;
import static io.sapl.mqtt.pep.config.SaplMqttExtensionConfig.DEFAULT_REMOTE_PDP_BASE_URL;
import static io.sapl.mqtt.pep.config.SaplMqttExtensionConfig.DEFAULT_REMOTE_PDP_CLIENT_SECRET;
import static io.sapl.mqtt.pep.config.SaplMqttExtensionConfig.DEFAULT_REMOTE_PDP_FIRST_BACK_OFF_MILLIS;
import static io.sapl.mqtt.pep.config.SaplMqttExtensionConfig.DEFAULT_REMOTE_PDP_MAX_BACK_OFF_MILLIS;
import static io.sapl.mqtt.pep.config.SaplMqttExtensionConfig.DEFAULT_SUBSCRIPTION_ENFORCEMENT_TIMEOUT_MILLIS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Extension config validation")
class ExtensionConfigValidationTests {

    @Test
    void when_noValidBaseUrlSpecified_then_usingDefaultUrl() {
        // GIVEN
        var pathToConfig = new File("src/test/resources/config/validate");

        // WHEN
        var saplMqttExtensionConfiguration = new SaplExtensionConfiguration(pathToConfig);
        var saplMqttExtensionConfig        = saplMqttExtensionConfiguration.getSaplMqttExtensionConfig();

        // THEN
        assertThat(saplMqttExtensionConfig.getRemotePdpBaseUrl()).isEqualTo(DEFAULT_REMOTE_PDP_BASE_URL);
    }

    @Test
    void when_noValidPdpImplementationTypeSpecified_then_usingDefaultPdpImplementationType() {
        // GIVEN
        var pathToConfig = new File("src/test/resources/config/validate");

        // WHEN
        var saplMqttExtensionConfiguration = new SaplExtensionConfiguration(pathToConfig);
        var saplMqttExtensionConfig        = saplMqttExtensionConfiguration.getSaplMqttExtensionConfig();

        // THEN
        assertThat(saplMqttExtensionConfig.getPdpImplementation()).isEqualTo(DEFAULT_PDP_IMPLEMENTATION);
    }

    @Test
    void when_connectionTimeoutIsSpecifiedWithLessThanOne_then_usingDefaultConnectionTimeout() {
        // GIVEN
        var pathToConfig = new File("src/test/resources/config/validate");

        // WHEN
        var saplMqttExtensionConfiguration = new SaplExtensionConfiguration(pathToConfig);
        var saplMqttExtensionConfig        = saplMqttExtensionConfiguration.getSaplMqttExtensionConfig();

        // THEN
        assertThat(saplMqttExtensionConfig.getConnectionEnforcementTimeoutMillis())
                .isEqualTo(DEFAULT_CONNECTION_ENFORCEMENT_TIMEOUT_MILLIS);
    }

    @Test
    void when_subscriptionTimeoutIsSpecifiedWithLessThanOne_then_usingDefaultSubscriptionTimeout() {
        // GIVEN
        var pathToConfig = new File("src/test/resources/config/validate");

        // WHEN
        var saplMqttExtensionConfiguration = new SaplExtensionConfiguration(pathToConfig);
        var saplMqttExtensionConfig        = saplMqttExtensionConfiguration.getSaplMqttExtensionConfig();

        // THEN
        assertThat(saplMqttExtensionConfig.getSubscriptionEnforcementTimeoutMillis())
                .isEqualTo(DEFAULT_SUBSCRIPTION_ENFORCEMENT_TIMEOUT_MILLIS);
    }

    @Test
    void when_publishTimeoutIsSpecifiedWithLessThanOne_then_usingDefaultPublishTimeout() {
        // GIVEN
        var pathToConfig = new File("src/test/resources/config/validate");

        // WHEN
        var saplMqttExtensionConfiguration = new SaplExtensionConfiguration(pathToConfig);
        var saplMqttExtensionConfig        = saplMqttExtensionConfiguration.getSaplMqttExtensionConfig();

        // THEN
        assertThat(saplMqttExtensionConfig.getPublishEnforcementTimeoutMillis())
                .isEqualTo(DEFAULT_PUBLISH_ENFORCEMENT_TIMEOUT_MILLIS);
    }

    @Test
    void when_remotePdpBackOffFactorIsSpecifiedWithLessThanOne_then_usingDefaultPdpBackOffFactor() {
        // GIVEN
        var pathToConfig = new File("src/test/resources/config/validate");

        // WHEN
        var saplMqttExtensionConfiguration = new SaplExtensionConfiguration(pathToConfig);
        var saplMqttExtensionConfig        = saplMqttExtensionConfiguration.getSaplMqttExtensionConfig();

        // THEN
        assertThat(saplMqttExtensionConfig.getRemotePdpBackOffFactor()).isEqualTo(DEFAULT_REMOTE_PDP_BACK_OFF_FACTOR);
    }

    @Test
    void when_firstRemotePdpBackOffMillisAreSpecifiedWithLessThanOne_then_usingDefaultFirstRemotePdpBackOffMillis() {
        // GIVEN
        var pathToConfig = new File("src/test/resources/config/validate");

        // WHEN
        var saplMqttExtensionConfiguration = new SaplExtensionConfiguration(pathToConfig);
        var saplMqttExtensionConfig        = saplMqttExtensionConfiguration.getSaplMqttExtensionConfig();

        // THEN
        assertThat(saplMqttExtensionConfig.getRemotePdpFirstBackOffMillis())
                .isEqualTo(DEFAULT_REMOTE_PDP_FIRST_BACK_OFF_MILLIS);
    }

    @Test
    void when_maxRemotePdpBackOffMillisAreSpecifiedWithLessThanOne_then_usingDefaultMaxRemotePdpBackOffMillis() {
        // GIVEN
        var pathToConfig = new File("src/test/resources/config/validate");

        // WHEN
        var saplMqttExtensionConfiguration = new SaplExtensionConfiguration(pathToConfig);
        var saplMqttExtensionConfig        = saplMqttExtensionConfiguration.getSaplMqttExtensionConfig();

        // THEN
        assertThat(saplMqttExtensionConfig.getRemotePdpMaxBackOffMillis())
                .isEqualTo(DEFAULT_REMOTE_PDP_MAX_BACK_OFF_MILLIS);
    }

    @Test
    void when_firstRemotePdpBackOffMillisAreGreaterThanMaxMillis_then_usingDefaultRemotePdpBackOffMillis() {
        // GIVEN
        var pathToConfig = new File("src/test/resources/config/validate/pdp-backoff");

        // WHEN
        var saplMqttExtensionConfiguration = new SaplExtensionConfiguration(pathToConfig);
        var saplMqttExtensionConfig        = saplMqttExtensionConfiguration.getSaplMqttExtensionConfig();

        // THEN
        assertThat(saplMqttExtensionConfig.getRemotePdpMaxBackOffMillis())
                .isEqualTo(DEFAULT_REMOTE_PDP_MAX_BACK_OFF_MILLIS);
        assertThat(saplMqttExtensionConfig.getRemotePdpFirstBackOffMillis())
                .isEqualTo(DEFAULT_REMOTE_PDP_FIRST_BACK_OFF_MILLIS);
    }

    @Test
    void when_saplSubscriptionTimeoutIsShorterPublishEnforcement_then_usingMinimalSaplSubscriptionTimeout() {
        // GIVEN
        var pathToConfig = new File("src/test/resources/config/validate/authz-sub-timeout-against-mqtt-pub");

        // WHEN
        var saplMqttExtensionConfiguration = new SaplExtensionConfiguration(pathToConfig);
        var saplMqttExtensionConfig        = saplMqttExtensionConfiguration.getSaplMqttExtensionConfig();

        // THEN
        assertThat(saplMqttExtensionConfig.getAuthzSubscriptionTimeoutMillis()).isEqualTo(1000);
    }

    @Test
    void when_saplSubscriptionTimeoutIsShorterThanSubscriptionEnforcement_then_usingMinimalSaplSubscriptionTimeout() {
        // GIVEN
        var pathToConfig = new File("src/test/resources/config/validate/authz-sub-timeout-against-mqtt-sub");

        // WHEN
        var saplMqttExtensionConfiguration = new SaplExtensionConfiguration(pathToConfig);
        var saplMqttExtensionConfig        = saplMqttExtensionConfiguration.getSaplMqttExtensionConfig();

        // THEN
        assertThat(saplMqttExtensionConfig.getAuthzSubscriptionTimeoutMillis()).isEqualTo(1000);
    }

    @Test
    void when_saplExtensionConfigFileIsNotExisting_then_useDefaultConfig() {
        // GIVEN
        var saplExtensionConfiguration = new SaplExtensionConfiguration(new File("src/test/resources/config/illegal"));

        // WHEN
        var saplMqttExtensionConfig = saplExtensionConfiguration.getSaplMqttExtensionConfig();

        // THEN
        assertThat(saplMqttExtensionConfig.getPublishEnforcementTimeoutMillis())
                .isEqualTo(DEFAULT_PUBLISH_ENFORCEMENT_TIMEOUT_MILLIS);
    }

    @Test
    void when_saplExtensionConfigFileIsNotReadable_then_useDefaultConfig() {
        // GIVEN
        var fileMock = mock(File.class);
        when(fileMock.exists()).thenReturn(Boolean.TRUE);
        when(fileMock.canRead()).thenReturn(Boolean.FALSE);
        var saplExtensionConfiguration = new SaplExtensionConfiguration(fileMock);

        // WHEN
        var saplMqttExtensionConfig = saplExtensionConfiguration.readConfigFile(fileMock);

        // THEN
        assertThat(saplMqttExtensionConfig.getRemotePdpClientSecret()).isEqualTo(DEFAULT_REMOTE_PDP_CLIENT_SECRET);
    }

    @Test
    void when_saplExtensionConfigFileIsEmpty_then_useDefaultConfig() {
        // GIVEN
        var fileMock = mock(File.class);
        when(fileMock.exists()).thenReturn(Boolean.TRUE);
        when(fileMock.canRead()).thenReturn(Boolean.TRUE);
        when(fileMock.length()).thenReturn(0L);
        var saplExtensionConfiguration = new SaplExtensionConfiguration(fileMock);

        // WHEN
        var saplMqttExtensionConfig = saplExtensionConfiguration.readConfigFile(fileMock);

        // THEN
        assertThat(saplMqttExtensionConfig.getRemotePdpClientSecret()).isEqualTo(DEFAULT_REMOTE_PDP_CLIENT_SECRET);
    }
}
