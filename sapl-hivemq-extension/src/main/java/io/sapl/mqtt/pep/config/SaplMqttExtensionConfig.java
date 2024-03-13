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

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Used to specify the sapl mqtt extension configuration file and to define the
 * default configuration values.
 */
@Data
@NoArgsConstructor
@XmlType(propOrder = {})
@XmlAccessorType(XmlAccessType.NONE)
@XmlRootElement(name = SaplMqttExtensionConfig.ENVIRONMENT_ROOT_ELEMENT)
public class SaplMqttExtensionConfig {

    /**
     * Specifies the root tag for the sapl mqtt extension configuration file.
     */
    public static final String  ENVIRONMENT_ROOT_ELEMENT                                             = "sapl-extension-config";
    private static final String ENVIRONMENT_CONNECTION_ENFORCEMENT_TIMEOUT_MILLIS                    = "connection-enforcement-timeout-millis";
    private static final String ENVIRONMENT_SUBSCRIPTION_ENFORCEMENT_TIMEOUT_MILLIS                  = "subscription-enforcement-timeout-millis";
    private static final String ENVIRONMENT_PUBLISH_ENFORCEMENT_TIMEOUT_MILLIS                       = "publish-enforcement-timeout-millis";
    private static final String ENVIRONMENT_AUTHZ_SUBSCRIPTION_TIMEOUT_MILLIS                        = "authz-subscription-timeout-millis";
    /**
     * References the pdp implementation type in the sapl mqtt extension
     * configuration file.
     */
    public static final String  ENVIRONMENT_PDP_IMPLEMENTATION                                       = "pdp-implementation";
    private static final String ENVIRONMENT_EMBEDDED_PDP_POLICIES_PATH                               = "embedded-pdp-policies-path";
    private static final String ENVIRONMENT_IS_EMBEDDED_PDP_POLICIES_PATH_RELATIVE_TO_EXTENSION_HOME = "embedded-pdp-policies-path-is-relative-to-extension-home";
    /**
     * References the url of a remote PDP in the sapl mqtt extension configuration
     * file.
     */
    public static final String  ENVIRONMENT_REMOTE_PDP_BASE_URL                                      = "remote-pdp-base-url";
    /**
     * References the client key in the sapl mqtt extension configuration file for
     * communications with the remote PDP.
     */
    public static final String  ENVIRONMENT_REMOTE_PDP_CLIENT_KEY                                    = "remote-pdp-client-key";
    /**
     * References the client secret in the sapl mqtt extension configuration file
     * for communications with the remote PDP.
     */
    public static final String  ENVIRONMENT_REMOTE_PDP_CLIENT_SECRET                                 = "remote-pdp-client-secret";
    private static final String ENVIRONMENT_REMOTE_PDP_FIRST_BACK_OFF_MILLIS                         = "remote-pdp-first-back-off-millis";
    private static final String ENVIRONMENT_REMOTE_PDP_MAX_BACK_OFF_MILLIS                           = "remote-pdp-max-back-off-millis";
    private static final String ENVIRONMENT_REMOTE_PDP_BACK_OFF_FACTOR                               = "remote-pdp-back-off-factor";

    static final String          DEFAULT_PDP_IMPLEMENTATION                                       = "embedded";                        // alternatively
                                                                                                                                       // "remote"
    static final String          DEFAULT_EMBEDDED_PDP_POLICIES_PATH                               = "policies";
    /**
     * The default url to reach the remote PDP.
     */
    public static final String   DEFAULT_REMOTE_PDP_BASE_URL                                      = "https://localhost:8080";
    /**
     * The default client key for communications with the remote PDP.
     */
    public static final String   DEFAULT_REMOTE_PDP_CLIENT_KEY                                    = "xwuUaRD65G";
    /**
     * The default client secret for communications with the remote PDP. This was
     * previously the encoded one. Why?
     */
    public static final String   DEFAULT_REMOTE_PDP_CLIENT_SECRET                                 = "3j_PK71bjy!hN3*xq.xZqveU)t5hKLR_";
    static final int             DEFAULT_CONNECTION_ENFORCEMENT_TIMEOUT_MILLIS                    = 5000;
    static final int             DEFAULT_SUBSCRIPTION_ENFORCEMENT_TIMEOUT_MILLIS                  = 5000;
    static final int             DEFAULT_PUBLISH_ENFORCEMENT_TIMEOUT_MILLIS                       = 5000;
    private static final int     DEFAULT_AUTHZ_SUBSCRIPTION_TIMEOUT_MILLIS                        = 100_000;
    static final int             DEFAULT_REMOTE_PDP_FIRST_BACK_OFF_MILLIS                         = 500;
    static final int             DEFAULT_REMOTE_PDP_MAX_BACK_OFF_MILLIS                           = 5000;
    static final int             DEFAULT_REMOTE_PDP_BACK_OFF_FACTOR                               = 2;
    private static final boolean DEFAULT_IS_EMBEDDED_PDP_POLICIES_PATH_RELATIVE_TO_EXTENSION_HOME = true;

    /**
     * This method is a copy constructor to replicate another
     * {@link SaplExtensionConfiguration}.
     *
     * @param saplMqttExtensionConfig the {@link SaplExtensionConfiguration} to copy
     */
    public SaplMqttExtensionConfig(SaplMqttExtensionConfig saplMqttExtensionConfig) {
        this.connectionEnforcementTimeoutMillis               = saplMqttExtensionConfig.connectionEnforcementTimeoutMillis;
        this.subscriptionEnforcementTimeoutMillis             = saplMqttExtensionConfig.subscriptionEnforcementTimeoutMillis;
        this.publishEnforcementTimeoutMillis                  = saplMqttExtensionConfig.publishEnforcementTimeoutMillis;
        this.authzSubscriptionTimeoutMillis                   = saplMqttExtensionConfig.authzSubscriptionTimeoutMillis;
        this.pdpImplementation                                = saplMqttExtensionConfig.pdpImplementation;
        this.embeddedPdpPoliciesPath                          = saplMqttExtensionConfig.embeddedPdpPoliciesPath;
        this.isEmbeddedPdpPoliciesPathRelativeToExtensionHome = saplMqttExtensionConfig.isEmbeddedPdpPoliciesPathRelativeToExtensionHome;
        this.remotePdpFirstBackOffMillis                      = saplMqttExtensionConfig.remotePdpFirstBackOffMillis;
        this.remotePdpMaxBackOffMillis                        = saplMqttExtensionConfig.remotePdpMaxBackOffMillis;
        this.remotePdpBackOffFactor                           = saplMqttExtensionConfig.remotePdpBackOffFactor;
        this.remotePdpBaseUrl                                 = saplMqttExtensionConfig.remotePdpBaseUrl;
        this.remotePdpClientKey                               = saplMqttExtensionConfig.remotePdpClientKey;
        this.remotePdpClientSecret                            = saplMqttExtensionConfig.remotePdpClientSecret;
    }

    @XmlElement(name = ENVIRONMENT_CONNECTION_ENFORCEMENT_TIMEOUT_MILLIS, defaultValue = ""
            + DEFAULT_CONNECTION_ENFORCEMENT_TIMEOUT_MILLIS)
    private int connectionEnforcementTimeoutMillis = DEFAULT_CONNECTION_ENFORCEMENT_TIMEOUT_MILLIS;

    @XmlElement(name = ENVIRONMENT_SUBSCRIPTION_ENFORCEMENT_TIMEOUT_MILLIS, defaultValue = ""
            + DEFAULT_SUBSCRIPTION_ENFORCEMENT_TIMEOUT_MILLIS)
    private int subscriptionEnforcementTimeoutMillis = DEFAULT_SUBSCRIPTION_ENFORCEMENT_TIMEOUT_MILLIS;

    @XmlElement(name = ENVIRONMENT_PUBLISH_ENFORCEMENT_TIMEOUT_MILLIS, defaultValue = ""
            + DEFAULT_PUBLISH_ENFORCEMENT_TIMEOUT_MILLIS)
    private int publishEnforcementTimeoutMillis = DEFAULT_PUBLISH_ENFORCEMENT_TIMEOUT_MILLIS;

    @XmlElement(name = ENVIRONMENT_AUTHZ_SUBSCRIPTION_TIMEOUT_MILLIS, defaultValue = ""
            + DEFAULT_AUTHZ_SUBSCRIPTION_TIMEOUT_MILLIS)
    private int authzSubscriptionTimeoutMillis = DEFAULT_AUTHZ_SUBSCRIPTION_TIMEOUT_MILLIS;

    @XmlElement(name = ENVIRONMENT_PDP_IMPLEMENTATION, defaultValue = DEFAULT_PDP_IMPLEMENTATION)
    private String pdpImplementation = DEFAULT_PDP_IMPLEMENTATION;

    @XmlElement(name = ENVIRONMENT_EMBEDDED_PDP_POLICIES_PATH, defaultValue = DEFAULT_EMBEDDED_PDP_POLICIES_PATH)
    private String embeddedPdpPoliciesPath = DEFAULT_EMBEDDED_PDP_POLICIES_PATH;

    @XmlElement(name = ENVIRONMENT_IS_EMBEDDED_PDP_POLICIES_PATH_RELATIVE_TO_EXTENSION_HOME, defaultValue = ""
            + DEFAULT_IS_EMBEDDED_PDP_POLICIES_PATH_RELATIVE_TO_EXTENSION_HOME)
    private boolean isEmbeddedPdpPoliciesPathRelativeToExtensionHome = DEFAULT_IS_EMBEDDED_PDP_POLICIES_PATH_RELATIVE_TO_EXTENSION_HOME;

    @XmlElement(name = ENVIRONMENT_REMOTE_PDP_FIRST_BACK_OFF_MILLIS, defaultValue = ""
            + DEFAULT_REMOTE_PDP_FIRST_BACK_OFF_MILLIS)
    private int remotePdpFirstBackOffMillis = DEFAULT_REMOTE_PDP_FIRST_BACK_OFF_MILLIS;

    @XmlElement(name = ENVIRONMENT_REMOTE_PDP_MAX_BACK_OFF_MILLIS, defaultValue = ""
            + DEFAULT_REMOTE_PDP_MAX_BACK_OFF_MILLIS)
    private int remotePdpMaxBackOffMillis = DEFAULT_REMOTE_PDP_MAX_BACK_OFF_MILLIS;

    @XmlElement(name = ENVIRONMENT_REMOTE_PDP_BACK_OFF_FACTOR, defaultValue = "" + DEFAULT_REMOTE_PDP_BACK_OFF_FACTOR)
    private int remotePdpBackOffFactor = DEFAULT_REMOTE_PDP_BACK_OFF_FACTOR;

    @XmlElement(name = ENVIRONMENT_REMOTE_PDP_BASE_URL, defaultValue = DEFAULT_REMOTE_PDP_BASE_URL)
    private String remotePdpBaseUrl = DEFAULT_REMOTE_PDP_BASE_URL;

    @XmlElement(name = ENVIRONMENT_REMOTE_PDP_CLIENT_KEY, defaultValue = DEFAULT_REMOTE_PDP_CLIENT_KEY)
    private String remotePdpClientKey = DEFAULT_REMOTE_PDP_CLIENT_KEY;

    @XmlElement(name = ENVIRONMENT_REMOTE_PDP_CLIENT_SECRET, defaultValue = DEFAULT_REMOTE_PDP_CLIENT_SECRET)
    private String remotePdpClientSecret = DEFAULT_REMOTE_PDP_CLIENT_SECRET;
}
