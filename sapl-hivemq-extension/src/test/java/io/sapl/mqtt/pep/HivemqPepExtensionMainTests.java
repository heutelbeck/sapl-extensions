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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.hivemq.extension.sdk.api.parameter.ExtensionInformation;
import com.hivemq.extension.sdk.api.parameter.ExtensionStartInput;
import com.hivemq.extension.sdk.api.parameter.ExtensionStartOutput;

import io.sapl.mqtt.pep.extension.ConfigInitUtility;
import io.sapl.mqtt.pep.extension.PdpInitUtility;

class HivemqPepExtensionMainTests {

    @Test
    void when_noPepIsBuild_then_preventExtensionStartup() {
        // GIVEN
        var extensionStartInput      = mock(ExtensionStartInput.class);
        var extensionStartOutput     = mock(ExtensionStartOutput.class);
        var extensionInformationMock = mock(ExtensionInformation.class);
        when(extensionStartInput.getExtensionInformation()).thenReturn(extensionInformationMock);
        when(extensionInformationMock.getName()).thenReturn("test-extension");

        var hivemqPepExtensionMain = new HivemqPepExtensionMain();

        try (var configInitHelperMock = Mockito.mockStatic(ConfigInitUtility.class);
                var pdpInitHelperMock = Mockito.mockStatic(PdpInitUtility.class)) {
            configInitHelperMock.when(() -> ConfigInitUtility.getSaplMqttExtensionConfig(any(), any()))
                    .thenReturn(null);
            pdpInitHelperMock.when(() -> PdpInitUtility.buildPdp(any(), any(), any())).thenReturn(null);

            // WHEN
            hivemqPepExtensionMain.extensionStart(extensionStartInput, extensionStartOutput);

        }
        // THEN
        verify(extensionStartOutput, times(1)).preventExtensionStartup(any());
    }
}
