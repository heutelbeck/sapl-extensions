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

package io.sapl.mqtt.pep.extension;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;

import org.junit.jupiter.api.Test;

import io.sapl.mqtt.pep.config.SaplMqttExtensionConfig;

class ConfigInitUtilityTest {

    @Test
    void when_extensionConfigPathIsNotSpecified_then_useDefaultPathInExtensionHome() {
        // GIVEN
        File extensionHomeFolder = new File("src/test/resources/config");

        // WHEN
        SaplMqttExtensionConfig extensionConfig =
                ConfigInitUtility.getSaplMqttExtensionConfig(extensionHomeFolder, null);

        // THEN
        assertEquals(6000, extensionConfig.getConnectionEnforcementTimeoutMillis());
    }
}
