/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import static io.sapl.mqtt.pep.config.SaplMqttExtensionConfig.DEFAULT_EMBEDDED_PDP_POLICIES_PATH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;

import javax.xml.bind.DataBindingException;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ExtensionConfigTests {

    @Test
    void when_configFileIsIncorrectlySpecified_then_usingDefaultValues() {
        // GIVEN
        var pathToConfig = new File("src/test/resources/config/incorrect");

        // WHEN
        var saplMqttExtensionConfiguration = new SaplExtensionConfiguration(pathToConfig);
        var saplMqttExtensionConfig        = saplMqttExtensionConfiguration.getSaplMqttExtensionConfig();

        // THEN
        assertEquals(DEFAULT_EMBEDDED_PDP_POLICIES_PATH, saplMqttExtensionConfig.getEmbeddedPdpPoliciesPath());
    }

    @Test
    void when_configFileIsNotFoundInDirectory_then_usingDefaultValues() {
        // GIVEN
        var pathToConfig = new File("src/test/resources/config/incorrect/empty");

        // WHEN
        var saplMqttExtensionConfiguration = new SaplExtensionConfiguration(pathToConfig);
        var saplMqttExtensionConfig        = saplMqttExtensionConfiguration.getSaplMqttExtensionConfig();

        // THEN
        assertTrue(saplMqttExtensionConfig.isEmbeddedPdpPoliciesPathRelativeToExtensionHome());
    }

    @Test
    void when_xmlParserCouldNotBeInitialized_then_throwException() {
        // GIVEN
        try (var jaxbContextMock = Mockito.mockStatic(JAXBContext.class)) {

            // WHEN
            jaxbContextMock.when(() -> JAXBContext.newInstance(SaplMqttExtensionConfig.class))
                    .thenThrow(JAXBException.class);

            // THEN
            Assertions.assertThrows(DataBindingException.class, ConfigurationXmlParser::new);
        }
    }
}
