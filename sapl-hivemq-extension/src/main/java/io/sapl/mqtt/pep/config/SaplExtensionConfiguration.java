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
package io.sapl.mqtt.pep.config;

import static java.lang.Math.min;
import static org.apache.commons.validator.routines.UrlValidator.ALLOW_LOCAL_URLS;

import java.io.File;
import java.io.IOException;

import org.apache.commons.validator.routines.UrlValidator;

import lombok.extern.slf4j.Slf4j;

/**
 * Used to read the sapl mqtt extension configuration file and to validate it's
 * content.
 */
@Slf4j
public class SaplExtensionConfiguration {

    private static final String SAPL_MQTT_EXTENSION_CONFIG_FILE_NAME = "sapl-extension-config.xml";

    private final ConfigurationXmlParser configurationXmlParser = new ConfigurationXmlParser();
    private final File                   extensionHomeFolder;

    /**
     * Creates an object used to unmarshal and validate the sapl mqtt configuration
     * file.
     *
     * @param extensionHomeFolder the folder containing the configuration file
     */
    public SaplExtensionConfiguration(File extensionHomeFolder) {
        this.extensionHomeFolder = extensionHomeFolder;
    }

    /**
     * Selects and reads the configuration file.
     *
     * @return the {@link SaplMqttExtensionConfig} object contains the
     *         configurations for the pep
     */
    public SaplMqttExtensionConfig getSaplMqttExtensionConfig() {
        return readConfigFile(new File(extensionHomeFolder, SAPL_MQTT_EXTENSION_CONFIG_FILE_NAME));
    }

    /**
     * Reads the extension configuration file and evaluates the params.
     *
     * @param saplExtensionConfigFile the configuration file
     * @return the {@link SaplMqttExtensionConfig} object contains the
     *         configurations for the pep
     */
    protected SaplMqttExtensionConfig readConfigFile(File saplExtensionConfigFile) {
        var defaultSaplMqttExtensionConfig = new SaplMqttExtensionConfig();
        if (saplExtensionConfigFile.exists() && saplExtensionConfigFile.canRead()
                && saplExtensionConfigFile.length() > 0) {
            try {
                var newSaplMqttExtensionConfig = configurationXmlParser
                        .unmarshalExtensionConfig(saplExtensionConfigFile);
                return validateConfig(newSaplMqttExtensionConfig, defaultSaplMqttExtensionConfig);
            } catch (IOException e) {
                log.warn("Could not read sapl extension configuration file, reason: {}, using default {}",
                        e.getMessage(), defaultSaplMqttExtensionConfig);
                return defaultSaplMqttExtensionConfig;
            }
        } else {
            log.warn("Unable to read sapl extension configuration file {}, using default {}",
                    saplExtensionConfigFile.getAbsolutePath(), defaultSaplMqttExtensionConfig);
            return defaultSaplMqttExtensionConfig;
        }
    }

    private SaplMqttExtensionConfig validateConfig(SaplMqttExtensionConfig newSaplMqttExtensionConfig,
            SaplMqttExtensionConfig defaultSaplMqttExtensionConfig) {
        validatePdpImplementationType(newSaplMqttExtensionConfig, defaultSaplMqttExtensionConfig);
        validateTimeoutMillis(newSaplMqttExtensionConfig, defaultSaplMqttExtensionConfig);
        validateRemotePdpBaseUrl(newSaplMqttExtensionConfig, defaultSaplMqttExtensionConfig);
        validateRemotePdpBackOff(newSaplMqttExtensionConfig, defaultSaplMqttExtensionConfig);
        return newSaplMqttExtensionConfig;
    }

    private void validateRemotePdpBackOff(SaplMqttExtensionConfig newSaplMqttExtensionConfig,
            SaplMqttExtensionConfig defaultSaplMqttExtensionConfig) {
        validateRemotePdpBackOffFactor(newSaplMqttExtensionConfig, defaultSaplMqttExtensionConfig);
        validateRemotePdpBackOffMillis(newSaplMqttExtensionConfig, defaultSaplMqttExtensionConfig);
    }

    private void validateTimeoutMillis(SaplMqttExtensionConfig newSaplMqttExtensionConfig,
            SaplMqttExtensionConfig defaultSaplMqttExtensionConfig) {
        validateConnectionTimeoutMillis(newSaplMqttExtensionConfig, defaultSaplMqttExtensionConfig);
        validateSubscriptionTimeoutMillis(newSaplMqttExtensionConfig, defaultSaplMqttExtensionConfig);
        validatePublishTimeoutMillis(newSaplMqttExtensionConfig, defaultSaplMqttExtensionConfig);
        validateAuthzSubscriptionTimeoutMillis(newSaplMqttExtensionConfig, defaultSaplMqttExtensionConfig);
    }

    private void validateRemotePdpBackOffMillis(SaplMqttExtensionConfig newSaplMqttExtensionConfig,
            SaplMqttExtensionConfig defaultSaplMqttExtensionConfig) {
        validateFirstRemotePdpBackOffMillis(newSaplMqttExtensionConfig, defaultSaplMqttExtensionConfig);
        validateMaxRemotePdpBackOffMillis(newSaplMqttExtensionConfig, defaultSaplMqttExtensionConfig);
        compareRemotePdpBackOffMillis(newSaplMqttExtensionConfig, defaultSaplMqttExtensionConfig);
    }

    private void validateRemotePdpBaseUrl(SaplMqttExtensionConfig newSaplMqttExtensionConfig,
            SaplMqttExtensionConfig defaultSaplMqttExtensionConfig) {
        var     urlValidator   = new UrlValidator(ALLOW_LOCAL_URLS);
        boolean isBaseUrlValid = urlValidator.isValid(newSaplMqttExtensionConfig.getRemotePdpBaseUrl());
        if (!isBaseUrlValid) {
            log.warn("No valid url ('{}') specified for remote pdp, using default: {}",
                    newSaplMqttExtensionConfig.getRemotePdpBaseUrl(),
                    defaultSaplMqttExtensionConfig.getRemotePdpBaseUrl());
            newSaplMqttExtensionConfig.setRemotePdpBaseUrl(defaultSaplMqttExtensionConfig.getRemotePdpBaseUrl());
        }
    }

    private void validatePdpImplementationType(SaplMqttExtensionConfig newSaplMqttExtensionConfig,
            SaplMqttExtensionConfig defaultSaplMqttExtensionConfig) {
        if (!("embedded".equals(newSaplMqttExtensionConfig.getPdpImplementation())
                || "remote".equals(newSaplMqttExtensionConfig.getPdpImplementation()))) {
            log.warn("The pdp implementation must be of 'embedded' or 'remote', using default: {}",
                    defaultSaplMqttExtensionConfig.getPdpImplementation());
            newSaplMqttExtensionConfig.setPdpImplementation(defaultSaplMqttExtensionConfig.getPdpImplementation());
        }
    }

    private void validateConnectionTimeoutMillis(SaplMqttExtensionConfig newSaplMqttExtensionConfig,
            SaplMqttExtensionConfig defaultSaplMqttExtensionConfig) {
        if (newSaplMqttExtensionConfig.getConnectionEnforcementTimeoutMillis() <= 0) {
            log.warn("The connection enforcement timeout frame must be greater than 0 milliseconds, using default: {}",
                    defaultSaplMqttExtensionConfig.getConnectionEnforcementTimeoutMillis());
            newSaplMqttExtensionConfig.setConnectionEnforcementTimeoutMillis(
                    defaultSaplMqttExtensionConfig.getConnectionEnforcementTimeoutMillis());
        }
    }

    private void validateSubscriptionTimeoutMillis(SaplMqttExtensionConfig newSaplMqttExtensionConfig,
            SaplMqttExtensionConfig defaultSaplMqttExtensionConfig) {
        if (newSaplMqttExtensionConfig.getSubscriptionEnforcementTimeoutMillis() <= 0) {
            log.warn(
                    "The subscription enforcement timeout frame must be greater than 0 milliseconds, using default: {}",
                    defaultSaplMqttExtensionConfig.getSubscriptionEnforcementTimeoutMillis());
            newSaplMqttExtensionConfig.setSubscriptionEnforcementTimeoutMillis(
                    defaultSaplMqttExtensionConfig.getSubscriptionEnforcementTimeoutMillis());
        }
    }

    private void validatePublishTimeoutMillis(SaplMqttExtensionConfig newSaplMqttExtensionConfig,
            SaplMqttExtensionConfig defaultSaplMqttExtensionConfig) {
        if (newSaplMqttExtensionConfig.getPublishEnforcementTimeoutMillis() <= 0) {
            log.warn("The publish enforcement timeout frame must be greater than 0 milliseconds, using default: {}",
                    defaultSaplMqttExtensionConfig.getPublishEnforcementTimeoutMillis());
            newSaplMqttExtensionConfig.setPublishEnforcementTimeoutMillis(
                    defaultSaplMqttExtensionConfig.getPublishEnforcementTimeoutMillis());
        }
    }

    private void validateAuthzSubscriptionTimeoutMillis(SaplMqttExtensionConfig newSaplMqttExtensionConfig,
            SaplMqttExtensionConfig defaultSaplMqttExtensionConfig) {
        int authzSubscriptionTimeout        = newSaplMqttExtensionConfig.getAuthzSubscriptionTimeoutMillis();
        int defaultAuthzSubscriptionTimeout = defaultSaplMqttExtensionConfig.getAuthzSubscriptionTimeoutMillis();
        int publishEnforcementTimeout       = newSaplMqttExtensionConfig.getPublishEnforcementTimeoutMillis();
        int subscriptionEnforcementTimeout  = newSaplMqttExtensionConfig.getSubscriptionEnforcementTimeoutMillis();

        if (authzSubscriptionTimeout < publishEnforcementTimeout
                || authzSubscriptionTimeout < subscriptionEnforcementTimeout) {
            int minimumAuthzSubscriptionTimeout = min(defaultAuthzSubscriptionTimeout,
                    min(publishEnforcementTimeout, subscriptionEnforcementTimeout));
            log.warn(
                    "The authorization subscription timeout must be at least equal than the timeout of the "
                            + "publish enforcement or of the subscription enforcement, using minimum timeout: {}",
                    minimumAuthzSubscriptionTimeout);
            newSaplMqttExtensionConfig.setAuthzSubscriptionTimeoutMillis(minimumAuthzSubscriptionTimeout);
        }
    }

    private void validateRemotePdpBackOffFactor(SaplMqttExtensionConfig newSaplMqttExtensionConfig,
            SaplMqttExtensionConfig defaultSaplMqttExtensionConfig) {
        if (newSaplMqttExtensionConfig.getRemotePdpBackOffFactor() < 1) {
            log.warn("The remote pdp backoff factor must be greater than or equal to 1, using default: {}",
                    defaultSaplMqttExtensionConfig.getRemotePdpBackOffFactor());
            newSaplMqttExtensionConfig
                    .setRemotePdpBackOffFactor(defaultSaplMqttExtensionConfig.getRemotePdpBackOffFactor());
        }
    }

    private void validateFirstRemotePdpBackOffMillis(SaplMqttExtensionConfig newSaplMqttExtensionConfig,
            SaplMqttExtensionConfig defaultSaplMqttExtensionConfig) {
        if (newSaplMqttExtensionConfig.getRemotePdpFirstBackOffMillis() < 1) {
            log.warn("The first remote pdp backoff must be greater than or equal to 1, using default: {}",
                    defaultSaplMqttExtensionConfig.getRemotePdpFirstBackOffMillis());
            newSaplMqttExtensionConfig
                    .setRemotePdpFirstBackOffMillis(defaultSaplMqttExtensionConfig.getRemotePdpFirstBackOffMillis());
        }
    }

    private void validateMaxRemotePdpBackOffMillis(SaplMqttExtensionConfig newSaplMqttExtensionConfig,
            SaplMqttExtensionConfig defaultSaplMqttExtensionConfig) {
        if (newSaplMqttExtensionConfig.getRemotePdpMaxBackOffMillis() < 1) {
            log.warn("The max remote pdp backoff must be greater than or equal to 1, using default: {}",
                    defaultSaplMqttExtensionConfig.getRemotePdpMaxBackOffMillis());
            newSaplMqttExtensionConfig
                    .setRemotePdpMaxBackOffMillis(defaultSaplMqttExtensionConfig.getRemotePdpMaxBackOffMillis());
        }
    }

    private void compareRemotePdpBackOffMillis(SaplMqttExtensionConfig newSaplMqttExtensionConfig,
            SaplMqttExtensionConfig defaultSaplMqttExtensionConfig) {
        if (newSaplMqttExtensionConfig.getRemotePdpFirstBackOffMillis() > newSaplMqttExtensionConfig
                .getRemotePdpMaxBackOffMillis()) {
            log.warn(
                    "The max remote pdp backoff must be greater than or equal to the fist remote pdp backoff, "
                            + "using default: First: '{}', Max: '{}'",
                    defaultSaplMqttExtensionConfig.getRemotePdpFirstBackOffMillis(),
                    defaultSaplMqttExtensionConfig.getRemotePdpMaxBackOffMillis());
            newSaplMqttExtensionConfig
                    .setRemotePdpFirstBackOffMillis(defaultSaplMqttExtensionConfig.getRemotePdpFirstBackOffMillis());
            newSaplMqttExtensionConfig
                    .setRemotePdpMaxBackOffMillis(defaultSaplMqttExtensionConfig.getRemotePdpMaxBackOffMillis());
        }
    }

}
