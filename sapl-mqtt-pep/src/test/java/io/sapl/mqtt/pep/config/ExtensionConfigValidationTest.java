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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;

import org.junit.jupiter.api.Test;

class ExtensionConfigValidationTest {

    @Test
    void when_noValidBaseUrlSpecified_then_usingDefaultUrl() {
        // GIVEN
        File pathToConfig = new File("src/test/resources/config/validate");

        // WHEN
        SaplExtensionConfiguration saplMqttExtensionConfiguration = new SaplExtensionConfiguration(pathToConfig);
        SaplMqttExtensionConfig saplMqttExtensionConfig = saplMqttExtensionConfiguration.getSaplMqttExtensionConfig();

        // THEN
        assertEquals(DEFAULT_REMOTE_PDP_BASE_URL,
                saplMqttExtensionConfig.getRemotePdpBaseUrl());
    }

    @Test
    void when_noValidPdpImplementationTypeSpecified_then_usingDefaultPdpImplementationType() {
        // GIVEN
        File pathToConfig = new File("src/test/resources/config/validate");

        // WHEN
        SaplExtensionConfiguration saplMqttExtensionConfiguration = new SaplExtensionConfiguration(pathToConfig);
        SaplMqttExtensionConfig saplMqttExtensionConfig = saplMqttExtensionConfiguration.getSaplMqttExtensionConfig();

        // THEN
        assertEquals(DEFAULT_PDP_IMPLEMENTATION,
                saplMqttExtensionConfig.getPdpImplementation());
    }

    @Test
    void when_connectionTimeoutIsSpecifiedWithLessThanOne_then_usingDefaultConnectionTimeout() {
        // GIVEN
        File pathToConfig = new File("src/test/resources/config/validate");

        // WHEN
        SaplExtensionConfiguration saplMqttExtensionConfiguration = new SaplExtensionConfiguration(pathToConfig);
        SaplMqttExtensionConfig saplMqttExtensionConfig = saplMqttExtensionConfiguration.getSaplMqttExtensionConfig();

        // THEN
        assertEquals(DEFAULT_CONNECTION_ENFORCEMENT_TIMEOUT_MILLIS,
                saplMqttExtensionConfig.getConnectionEnforcementTimeoutMillis());
    }

    @Test
    void when_subscriptionTimeoutIsSpecifiedWithLessThanOne_then_usingDefaultSubscriptionTimeout() {
        // GIVEN
        File pathToConfig = new File("src/test/resources/config/validate");

        // WHEN
        SaplExtensionConfiguration saplMqttExtensionConfiguration = new SaplExtensionConfiguration(pathToConfig);
        SaplMqttExtensionConfig saplMqttExtensionConfig = saplMqttExtensionConfiguration.getSaplMqttExtensionConfig();

        // THEN
        assertEquals(DEFAULT_SUBSCRIPTION_ENFORCEMENT_TIMEOUT_MILLIS,
                saplMqttExtensionConfig.getSubscriptionEnforcementTimeoutMillis());
    }

    @Test
    void when_publishTimeoutIsSpecifiedWithLessThanOne_then_usingDefaultPublishTimeout() {
        // GIVEN
        File pathToConfig = new File("src/test/resources/config/validate");

        // WHEN
        SaplExtensionConfiguration saplMqttExtensionConfiguration = new SaplExtensionConfiguration(pathToConfig);
        SaplMqttExtensionConfig saplMqttExtensionConfig = saplMqttExtensionConfiguration.getSaplMqttExtensionConfig();

        // THEN
        assertEquals(DEFAULT_PUBLISH_ENFORCEMENT_TIMEOUT_MILLIS,
                saplMqttExtensionConfig.getPublishEnforcementTimeoutMillis());
    }

    @Test
    void when_remotePdpBackOffFactorIsSpecifiedWithLessThanOne_then_usingDefaultPdpBackOffFactor() {
        // GIVEN
        File pathToConfig = new File("src/test/resources/config/validate");

        // WHEN
        SaplExtensionConfiguration saplMqttExtensionConfiguration = new SaplExtensionConfiguration(pathToConfig);
        SaplMqttExtensionConfig saplMqttExtensionConfig = saplMqttExtensionConfiguration.getSaplMqttExtensionConfig();

        // THEN
        assertEquals(DEFAULT_REMOTE_PDP_BACK_OFF_FACTOR,
                saplMqttExtensionConfig.getRemotePdpBackOffFactor());
    }

    @Test
    void when_firstRemotePdpBackOffMillisAreSpecifiedWithLessThanOne_then_usingDefaultFirstRemotePdpBackOffMillis() {
        // GIVEN
        File pathToConfig = new File("src/test/resources/config/validate");

        // WHEN
        SaplExtensionConfiguration saplMqttExtensionConfiguration = new SaplExtensionConfiguration(pathToConfig);
        SaplMqttExtensionConfig saplMqttExtensionConfig = saplMqttExtensionConfiguration.getSaplMqttExtensionConfig();

        // THEN
        assertEquals(DEFAULT_REMOTE_PDP_FIRST_BACK_OFF_MILLIS,
                saplMqttExtensionConfig.getRemotePdpFirstBackOffMillis());
    }

    @Test
    void when_maxRemotePdpBackOffMillisAreSpecifiedWithLessThanOne_then_usingDefaultMaxRemotePdpBackOffMillis() {
        // GIVEN
        File pathToConfig = new File("src/test/resources/config/validate");

        // WHEN
        SaplExtensionConfiguration saplMqttExtensionConfiguration = new SaplExtensionConfiguration(pathToConfig);
        SaplMqttExtensionConfig saplMqttExtensionConfig = saplMqttExtensionConfiguration.getSaplMqttExtensionConfig();

        // THEN
        assertEquals(DEFAULT_REMOTE_PDP_MAX_BACK_OFF_MILLIS,
                saplMqttExtensionConfig.getRemotePdpMaxBackOffMillis());
    }

    @Test
    void when_firstRemotePdpBackOffMillisAreGreaterThanMaxMillis_then_usingDefaultRemotePdpBackOffMillis() {
        // GIVEN
        File pathToConfig = new File("src/test/resources/config/validate/pdp-backoff");

        // WHEN
        SaplExtensionConfiguration saplMqttExtensionConfiguration = new SaplExtensionConfiguration(pathToConfig);
        SaplMqttExtensionConfig saplMqttExtensionConfig = saplMqttExtensionConfiguration.getSaplMqttExtensionConfig();

        // THEN
        assertEquals(DEFAULT_REMOTE_PDP_MAX_BACK_OFF_MILLIS,
                saplMqttExtensionConfig.getRemotePdpMaxBackOffMillis());
        assertEquals(DEFAULT_REMOTE_PDP_FIRST_BACK_OFF_MILLIS,
                saplMqttExtensionConfig.getRemotePdpFirstBackOffMillis());
    }

    @Test
    void when_saplSubscriptionTimeoutIsShorterPublishEnforcement_then_usingMinimalSaplSubscriptionTimeout() {
        // GIVEN
        File pathToConfig = new File("src/test/resources/config/validate/authz-sub-timeout-against-mqtt-pub");

        // WHEN
        SaplExtensionConfiguration saplMqttExtensionConfiguration = new SaplExtensionConfiguration(pathToConfig);
        SaplMqttExtensionConfig saplMqttExtensionConfig = saplMqttExtensionConfiguration.getSaplMqttExtensionConfig();

        // THEN
        assertEquals(1000, saplMqttExtensionConfig.getAuthzSubscriptionTimeoutMillis());
    }

    @Test
    void when_saplSubscriptionTimeoutIsShorterThanSubscriptionEnforcement_then_usingMinimalSaplSubscriptionTimeout() {
        // GIVEN
        File pathToConfig = new File("src/test/resources/config/validate/authz-sub-timeout-against-mqtt-sub");

        // WHEN
        SaplExtensionConfiguration saplMqttExtensionConfiguration = new SaplExtensionConfiguration(pathToConfig);
        SaplMqttExtensionConfig saplMqttExtensionConfig = saplMqttExtensionConfiguration.getSaplMqttExtensionConfig();

        // THEN
        assertEquals(1000, saplMqttExtensionConfig.getAuthzSubscriptionTimeoutMillis());
    }

    @Test
    void when_saplExtensionConfigFileIsNotExisting_then_useDefaultConfig() {
        // GIVEN
        SaplExtensionConfiguration saplExtensionConfiguration =
                new SaplExtensionConfiguration(new File("src/test/resources/config/illegal"));

        // WHEN
       SaplMqttExtensionConfig saplMqttExtensionConfig =  saplExtensionConfiguration.getSaplMqttExtensionConfig();

        // THEN
        assertEquals(DEFAULT_PUBLISH_ENFORCEMENT_TIMEOUT_MILLIS,
                saplMqttExtensionConfig.getPublishEnforcementTimeoutMillis());
    }

    @Test
    void when_saplExtensionConfigFileIsNotReadable_then_useDefaultConfig() {
        // GIVEN
        File fileMock = mock(File.class);
        when(fileMock.exists()).thenReturn(true);
        when(fileMock.canRead()).thenReturn(false);
        SaplExtensionConfiguration saplExtensionConfiguration = new SaplExtensionConfiguration(fileMock);

        // WHEN
        SaplMqttExtensionConfig saplMqttExtensionConfig = saplExtensionConfiguration.readConfigFile(fileMock);

        // THEN
        assertEquals(DEFAULT_REMOTE_PDP_CLIENT_SECRET,
                saplMqttExtensionConfig.getRemotePdpClientSecret());
    }

    @Test
    void when_saplExtensionConfigFileIsEmpty_then_useDefaultConfig() {
        // GIVEN
        File fileMock = mock(File.class);
        when(fileMock.exists()).thenReturn(true);
        when(fileMock.canRead()).thenReturn(true);
        when(fileMock.length()).thenReturn(0L);
        SaplExtensionConfiguration saplExtensionConfiguration = new SaplExtensionConfiguration(fileMock);

        // WHEN
        SaplMqttExtensionConfig saplMqttExtensionConfig = saplExtensionConfiguration.readConfigFile(fileMock);

        // THEN
        assertEquals(DEFAULT_REMOTE_PDP_CLIENT_SECRET,
                saplMqttExtensionConfig.getRemotePdpClientSecret());
    }
}
