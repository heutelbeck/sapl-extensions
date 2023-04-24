package io.sapl.mqtt.pep.config;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import javax.xml.bind.DataBindingException;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import java.io.File;

import static io.sapl.mqtt.pep.config.SaplMqttExtensionConfig.DEFAULT_EMBEDDED_PDP_POLICIES_PATH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExtensionConfigTest {

    @Test
    void when_configFileIsIncorrectlySpecified_then_usingDefaultValues() {
        // GIVEN
        File pathToConfig = new File("src/test/resources/config/incorrect");

        // WHEN
        SaplExtensionConfiguration saplMqttExtensionConfiguration = new SaplExtensionConfiguration(pathToConfig);
        SaplMqttExtensionConfig saplMqttExtensionConfig = saplMqttExtensionConfiguration.getSaplMqttExtensionConfig();

        // THEN
        assertEquals(DEFAULT_EMBEDDED_PDP_POLICIES_PATH,
                saplMqttExtensionConfig.getEmbeddedPdpPoliciesPath());
    }

    @Test
    void when_configFileIsNotFoundInDirectory_then_usingDefaultValues() {
        // GIVEN
        File pathToConfig = new File("src/test/resources/config/incorrect/empty");

        // WHEN
        SaplExtensionConfiguration saplMqttExtensionConfiguration = new SaplExtensionConfiguration(pathToConfig);
        SaplMqttExtensionConfig saplMqttExtensionConfig = saplMqttExtensionConfiguration.getSaplMqttExtensionConfig();

        // THEN
        assertTrue(saplMqttExtensionConfig.isEmbeddedPdpPoliciesPathRelativeToExtensionHome());
    }

    @Test
    void when_xmlParserCouldNotBeInitialized_then_throwException() {
        // GIVEN
        try (MockedStatic<JAXBContext> jaxbContextMock = Mockito.mockStatic(JAXBContext.class)) {

            // WHEN
            jaxbContextMock.when(() -> JAXBContext.newInstance(SaplMqttExtensionConfig.class))
                    .thenThrow(JAXBException.class);

            // THEN
            Assertions.assertThrows(DataBindingException.class, ConfigurationXmlParser::new);
        }
    }
}
