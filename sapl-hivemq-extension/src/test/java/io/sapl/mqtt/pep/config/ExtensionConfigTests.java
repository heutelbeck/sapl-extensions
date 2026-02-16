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

import static io.sapl.mqtt.pep.config.SaplMqttExtensionConfig.DEFAULT_EMBEDDED_PDP_POLICIES_PATH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;

import jakarta.xml.bind.DataBindingException;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

@DisplayName("Extension config")
class ExtensionConfigTests {

    @Test
    void when_configFileIsIncorrectlySpecified_then_usingDefaultValues() {
        // GIVEN
        var pathToConfig = new File("src/test/resources/config/incorrect");

        // WHEN
        var saplMqttExtensionConfiguration = new SaplExtensionConfiguration(pathToConfig);
        var saplMqttExtensionConfig        = saplMqttExtensionConfiguration.getSaplMqttExtensionConfig();

        // THEN
        assertThat(saplMqttExtensionConfig.getEmbeddedPdpPoliciesPath()).isEqualTo(DEFAULT_EMBEDDED_PDP_POLICIES_PATH);
    }

    @Test
    void when_configFileIsNotFoundInDirectory_then_usingDefaultValues() {
        // GIVEN
        var pathToConfig = new File("src/test/resources/config/incorrect/empty");

        // WHEN
        var saplMqttExtensionConfiguration = new SaplExtensionConfiguration(pathToConfig);
        var saplMqttExtensionConfig        = saplMqttExtensionConfiguration.getSaplMqttExtensionConfig();

        // THEN
        assertThat(saplMqttExtensionConfig.isEmbeddedPdpPoliciesPathRelativeToExtensionHome()).isTrue();
    }

    @Test
    void when_xmlParserCouldNotBeInitialized_then_throwException() {
        // GIVEN
        try (var jaxbContextMock = Mockito.mockStatic(JAXBContext.class)) {

            // WHEN
            jaxbContextMock.when(() -> JAXBContext.newInstance(SaplMqttExtensionConfig.class))
                    .thenThrow(JAXBException.class);

            // THEN
            assertThatThrownBy(ConfigurationXmlParser::new).isInstanceOf(DataBindingException.class);
        }
    }
}
