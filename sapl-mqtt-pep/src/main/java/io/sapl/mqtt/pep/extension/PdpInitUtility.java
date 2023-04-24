package io.sapl.mqtt.pep.extension;

import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.interpreter.InitializationException;
import io.sapl.mqtt.pep.config.SaplMqttExtensionConfig;
import io.sapl.pdp.EmbeddedPolicyDecisionPoint;
import io.sapl.pdp.PolicyDecisionPointFactory;
import io.sapl.pdp.remote.RemoteHttpPolicyDecisionPoint;
import io.sapl.pdp.remote.RemotePolicyDecisionPoint;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.util.Objects;

import static io.sapl.mqtt.pep.config.SaplMqttExtensionConfig.DEFAULT_REMOTE_PDP_CLIENT_KEY;
import static io.sapl.mqtt.pep.config.SaplMqttExtensionConfig.DEFAULT_REMOTE_PDP_CLIENT_SECRET;

/**
 * This utility class provides functions for initialization of an embedded or remote PDP.
 */
@Slf4j
@UtilityClass
public class PdpInitUtility {

    static final String EMBEDDED_PDP_IDENTIFIER = "embedded";
    static final String REMOTE_PDP_IDENTIFIER = "remote";

    /**
     * Builds an embedded or remote sapl policy decision point.
     * @param saplMqttExtensionConfig contains the configurations of the sapl mqtt pep extension
     * @param extensionHomeFolder If the {@link SaplMqttExtensionConfig} specifies that the by configuration
     *                            provided policies path is relative to the extension home folder, use this
     *                            extension home folder.
     * @param policiesPath The path to find the specified sapl policies. In case the path is null
     *                     the {@link SaplMqttExtensionConfig} respectively the extension home folder
     *                     will be used to extract the policy path from.
     * @return an embedded or remote sapl policy decision point
     */
    public static PolicyDecisionPoint buildPdp(SaplMqttExtensionConfig saplMqttExtensionConfig,
                                               File extensionHomeFolder, String policiesPath) {
        String pdpImplementation = saplMqttExtensionConfig.getPdpImplementation();
        if (REMOTE_PDP_IDENTIFIER.equals(pdpImplementation)) {
            return buildRemotePdp(saplMqttExtensionConfig);
        } else if (EMBEDDED_PDP_IDENTIFIER.equals(pdpImplementation)) {
            return buildEmbeddedPdp(saplMqttExtensionConfig, extensionHomeFolder, policiesPath);
        } else {
            return null;
        }
    }

    private static EmbeddedPolicyDecisionPoint buildEmbeddedPdp(SaplMqttExtensionConfig saplMqttExtensionConfig,
                                                         File extensionHomeFolder, String policiesPath) {
        try {
            return PolicyDecisionPointFactory.filesystemPolicyDecisionPoint(
                    getPoliciesPath(saplMqttExtensionConfig, extensionHomeFolder, policiesPath));
        } catch (InitializationException e) {
            log.error("Failed to build embedded pdp on extension startup with following reason: {}", e.getMessage());
            return null;
        }
    }

    private static RemoteHttpPolicyDecisionPoint buildRemotePdp(SaplMqttExtensionConfig saplMqttExtensionConfig) {
        String clientKey = saplMqttExtensionConfig.getRemotePdpClientKey();
        String clientSecret = saplMqttExtensionConfig.getRemotePdpClientSecret();
        warnWhenUsingDefaultConfigValuesForRemotePdp(clientKey, clientSecret);

        String baseUrl = saplMqttExtensionConfig.getRemotePdpBaseUrl();
        var remotePdp = RemotePolicyDecisionPoint.builder()
                .http().baseUrl(baseUrl)
                .basicAuth(clientKey, clientSecret)
                .build();
        setRemotePdpBackOff(saplMqttExtensionConfig, remotePdp);
        return remotePdp;
    }

    private static String getPoliciesPath(SaplMqttExtensionConfig saplMqttExtensionConfig, File extensionHomeFolder,
                                         String policiesPath) {
        return Objects.requireNonNullElseGet(policiesPath,
                () -> getPoliciesPathFromConfig(saplMqttExtensionConfig, extensionHomeFolder));
    }

    private static String getPoliciesPathFromConfig(SaplMqttExtensionConfig saplMqttExtensionConfig,
                                                    File extensionHomeFolder) {
        if (saplMqttExtensionConfig.isEmbeddedPdpPoliciesPathRelativeToExtensionHome()) {
            return extensionHomeFolder + assureLeadingFileSeparator(assureRightFileSeparatorUsage(
                    saplMqttExtensionConfig.getEmbeddedPdpPoliciesPath()));
        } else {
            return assureRightFileSeparatorUsage(saplMqttExtensionConfig.getEmbeddedPdpPoliciesPath());
        }
    }

    private static void setRemotePdpBackOff(SaplMqttExtensionConfig saplMqttExtensionConfig,
                                           RemoteHttpPolicyDecisionPoint remotePdp) {
        remotePdp.setFirstBackoffMillis(saplMqttExtensionConfig.getRemotePdpFirstBackOffMillis());
        remotePdp.setMaxBackOffMillis(saplMqttExtensionConfig.getRemotePdpMaxBackOffMillis());
        remotePdp.setBackoffFactor(saplMqttExtensionConfig.getRemotePdpBackOffFactor());
    }

    private static void warnWhenUsingDefaultConfigValuesForRemotePdp(String clientKey, String clientSecret) {
        if (DEFAULT_REMOTE_PDP_CLIENT_KEY.equals(clientKey)) {
            log.warn("Sapl mqtt extension is using the default key to connect to the remote pdp. " +
                    "Due to security reason, please change the key.");
        }
        if (DEFAULT_REMOTE_PDP_CLIENT_SECRET.equals(clientSecret)) {
            log.warn("Sapl mqtt extension is using the default secret to connect to the remote pdp. " +
                    "Due to security reason, please change the key.");
        }
    }

    private static String assureRightFileSeparatorUsage(String path) {
        return path.replace("/", File.separator);
    }

    private static String assureLeadingFileSeparator(String path) {
        if (!path.startsWith(File.separator)) {
            return File.separator + path;
        }
        return path;
    }
}
