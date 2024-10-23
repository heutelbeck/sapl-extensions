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
package io.sapl.mqtt.pep;

import static io.sapl.mqtt.pep.extension.ConfigInitUtility.getSaplMqttExtensionConfig;
import static io.sapl.mqtt.pep.extension.PdpInitUtility.buildPdp;

import java.io.File;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.hivemq.extension.sdk.api.ExtensionMain;
import com.hivemq.extension.sdk.api.annotations.NotNull;
import com.hivemq.extension.sdk.api.parameter.ExtensionStartInput;
import com.hivemq.extension.sdk.api.parameter.ExtensionStartOutput;
import com.hivemq.extension.sdk.api.parameter.ExtensionStopInput;
import com.hivemq.extension.sdk.api.parameter.ExtensionStopOutput;

import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.mqtt.pep.cache.MqttClientState;
import lombok.extern.slf4j.Slf4j;

/**
 * This HiveMq broker extension starts a PEP to enforce mqtt actions.
 */
@Slf4j
public class HivemqPepExtensionMain implements ExtensionMain {

    private String                                       policiesPath;
    private PolicyDecisionPoint                          pdp;
    private final String                                 saplExtensionConfigPath;
    private final ConcurrentMap<String, MqttClientState> mqttClientCache;

    /**
     * Initialize the HiveMq extension for startup and enforcement.
     */
    public HivemqPepExtensionMain() {
        this(null);
    }

    /**
     * Initialize the HiveMq extension for startup and enforcement.
     *
     * @param saplExtensionConfigPath path to the extension configuration file
     */
    public HivemqPepExtensionMain(String saplExtensionConfigPath) {
        this(null, saplExtensionConfigPath);
    }

    /**
     * Initialize the HiveMq extension for startup and enforcement.
     *
     * @param policiesPath path to the policy files to evaluate by the pdp
     * @param saplExtensionConfigPath path to the extension configuration file
     */
    public HivemqPepExtensionMain(String policiesPath, String saplExtensionConfigPath) {
        this(policiesPath, saplExtensionConfigPath, null);
    }

    /**
     * Initialize the HiveMq extension for startup and enforcement.
     *
     * @param policiesPath path to the policy files to evaluate by the pdp
     * @param saplExtensionConfigPath path to the extension configuration file
     * @param mqttClientCache used to cache the state of different enforcements
     */
    public HivemqPepExtensionMain(String policiesPath, String saplExtensionConfigPath,
            ConcurrentMap<String, MqttClientState> mqttClientCache) {
        this.policiesPath            = policiesPath;
        this.saplExtensionConfigPath = saplExtensionConfigPath;
        this.mqttClientCache         = Objects.requireNonNullElseGet(mqttClientCache, ConcurrentHashMap::new);
    }

    /**
     * Initialize the HiveMq extension for startup and enforcement.
     *
     * @param saplExtensionConfigPath path to the extension configuration file
     * @param pdp used by the pep to request authorization decisions
     */
    public HivemqPepExtensionMain(String saplExtensionConfigPath, PolicyDecisionPoint pdp) {
        this(saplExtensionConfigPath, pdp, null);
    }

    /**
     * Initialize the HiveMq extension for startup and enforcement.
     *
     * @param saplExtensionConfigPath path to the extension configuration file
     * @param pdp used by the pep to request authorization decisions
     * @param mqttClientCache used to cache the state of different enforcements
     */
    public HivemqPepExtensionMain(String saplExtensionConfigPath, PolicyDecisionPoint pdp,
            ConcurrentMap<String, MqttClientState> mqttClientCache) {
        this.pdp                     = pdp;
        this.saplExtensionConfigPath = saplExtensionConfigPath;
        this.mqttClientCache         = Objects.requireNonNullElseGet(mqttClientCache, ConcurrentHashMap::new);
    }

    /**
     * This method is used by the hivemq broker for extension startup. The sapl mqtt
     * pep will be started.
     */
    @Override
    public void extensionStart(@NotNull ExtensionStartInput extensionStartInput,
            @NotNull ExtensionStartOutput extensionStartOutput) {
        log.info("Starting extension '{}'", extensionStartInput.getExtensionInformation().getName());

        startMqttPep(extensionStartInput, extensionStartOutput);
    }

    /**
     * This method is called by the hivemq broker to stop the extension.
     */
    @Override
    public void extensionStop(@NotNull ExtensionStopInput extensionStopInput,
            @NotNull ExtensionStopOutput extensionStopOutput) {
        log.info("Shut down extension '{}'", extensionStopInput.getExtensionInformation().getName());
    }

    private void startMqttPep(ExtensionStartInput extensionStartInput, ExtensionStartOutput extensionStartOutput) {
        File extensionHomeFolder = extensionStartInput.getExtensionInformation().getExtensionHomeFolder();
        try {
            var                 saplMqttExtensionConfig = getSaplMqttExtensionConfig(extensionHomeFolder,
                    saplExtensionConfigPath);
            PolicyDecisionPoint constructedPdp          = Objects.requireNonNullElse(this.pdp,
                    buildPdp(saplMqttExtensionConfig, extensionHomeFolder, policiesPath));

            log.info("Using following sapl mqtt extension config: {}", saplMqttExtensionConfig);
            MqttPep mqttPep = new MqttPep(constructedPdp, saplMqttExtensionConfig, mqttClientCache);
            mqttPep.startEnforcement();
        } catch (Exception e) {
            extensionStartOutput.preventExtensionStartup("A critical failure during extension startup occurred. "
                    + "The initialisation of the mqtt policy enforcement point failed.");
        }
    }
}
