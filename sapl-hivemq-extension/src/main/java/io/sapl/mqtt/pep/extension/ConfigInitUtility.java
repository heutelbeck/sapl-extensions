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
package io.sapl.mqtt.pep.extension;

import java.io.File;
import java.util.Objects;

import io.sapl.mqtt.pep.config.SaplExtensionConfiguration;
import io.sapl.mqtt.pep.config.SaplMqttExtensionConfig;
import lombok.experimental.UtilityClass;

/**
 * This utility class provides functions for initialisation of the extension
 * configuration.
 */
@UtilityClass
public class ConfigInitUtility {

	private static final String DEFAULT_SAPL_EXTENSION_CONFIG_PATH = "";

	/**
	 * Used to get the sapl mqtt extension configuration from the sapl mqtt
	 * extension configuration file.
	 * 
	 * @param extensionHomeFolder     Used to find the sapl mqtt extension
	 *                                configuration file in case no full path is
	 *                                specified.
	 * @param saplExtensionConfigPath The full path to the sapl mqtt extension
	 *                                configuration file. If the path is not
	 *                                specified the configuration file will be tried
	 *                                to find in the extension home folder.
	 * @return the sapl mqtt extension configuration
	 */
	public static SaplMqttExtensionConfig getSaplMqttExtensionConfig(File extensionHomeFolder,
			String saplExtensionConfigPath) {
		var configPath = new File(getSaplExtensionConfigPath(extensionHomeFolder, saplExtensionConfigPath));
		return new SaplExtensionConfiguration(configPath)
				.getSaplMqttExtensionConfig();
	}

	private static String getSaplExtensionConfigPath(File extensionHomeFolder, String saplExtensionConfigPath) {
		return Objects.requireNonNullElseGet(saplExtensionConfigPath,
				() -> extensionHomeFolder + DEFAULT_SAPL_EXTENSION_CONFIG_PATH);
	}
}
