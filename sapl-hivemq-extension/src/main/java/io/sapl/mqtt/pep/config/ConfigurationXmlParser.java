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

import java.io.File;
import java.io.IOException;

import javax.xml.bind.DataBindingException;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;

import com.hivemq.extension.sdk.api.annotations.ThreadSafe;

import lombok.extern.slf4j.Slf4j;

/**
 * Used to unmarshal the sapl mqtt extension configuration file.
 */
@Slf4j
@ThreadSafe
public final class ConfigurationXmlParser {

    private final JAXBContext jaxb;

    /**
     * Initializes the xml parser for unmarshalling the sapl mqtt extension
     * configuration file.
     */
    ConfigurationXmlParser() {
        try {
            jaxb = JAXBContext.newInstance(SaplMqttExtensionConfig.class);
        } catch (JAXBException e) {
            log.error("Could not initialize XML parser in sapl mqtt pep: {}", e.getMessage());
            throw new DataBindingException("Could not initialize XML parser.", e);
        }
    }

    /**
     * Unmarshal the xml sapl mqtt extension configuration file for further usage.
     *
     * @param file the sapl mqtt extension configuration file
     * @return a java object representation of the sapl mqtt extension configuration
     * file
     * @throws IOException is thrown when unmarshalling does fail
     */
    SaplMqttExtensionConfig unmarshalExtensionConfig(File file) throws IOException {
        try {
            var unmarshaller = jaxb.createUnmarshaller();
            return (SaplMqttExtensionConfig) unmarshaller.unmarshal(file);
        } catch (JAXBException e) {
            log.error("Could not unmarshal XML configuration in sapl mqtt pep {}", e.getMessage());
            throw new IOException("Could not unmarshal XML configuration.", e);
        }
    }

}
