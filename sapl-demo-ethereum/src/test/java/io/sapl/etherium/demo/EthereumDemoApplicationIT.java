/*
 * Copyright © 2020-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.etherium.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Collection;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.ethereum.demo.EthereumDemoApplication;
import io.sapl.ethereum.demo.helper.AccessCertificate;
import io.sapl.ethereum.demo.helper.EthConnect;
import io.sapl.ethereum.demo.security.PrinterUser;
import io.sapl.ethereum.demo.security.PrinterUserService;

@Disabled
@DirtiesContext
@Testcontainers
@SpringJUnitConfig
@SpringBootTest(classes = EthereumDemoApplication.class)
class EthereumDemoApplicationIT {

    private static final JsonNodeFactory JSON                           = JsonNodeFactory.instance;
    private static final Duration        TIMEOUT_FOR_GANACHE_CLI_SPINUP = Duration.ofSeconds(10);
    private static final int             GANACHE_SERVER_PORT            = 8545;
    private static final String          MNEMONIC                       = "defense decade prosper portion dove educate sing auction camera minute sing loyal";
    private static final String[]        STARTUP_COMMAND                = new String[] { "ganache-cli", "--mnemonic",
            String.format("\"%s\"", MNEMONIC), };
    private static final String          STARTUP_LOG_MESSAGE            = ".*Listening on 0.0.0.0:"
            + GANACHE_SERVER_PORT + ".*\\n";

    static Network network = Network.newNetwork();

    @Container
    @SuppressWarnings("resource") // Fine for tests which are short-lived
    static GenericContainer<?> ganacheCli = new GenericContainer<>(DockerImageName.parse("trufflesuite/ganache-cli"))
            .withCommand(STARTUP_COMMAND).withExposedPorts(GANACHE_SERVER_PORT)
            .waitingFor(Wait.forLogMessage(STARTUP_LOG_MESSAGE, 1)).withStartupTimeout(TIMEOUT_FOR_GANACHE_CLI_SPINUP)
            .withNetwork(network);

    static Collection<PrinterUser> demoUserSource() {
        return PrinterUserService.DEMO_USERS;
    }

    @Autowired
    AccessCertificate accessCertificate;

    @Autowired
    EthConnect ethConnect;

    @Autowired
    ObjectMapper mapper;

    @Autowired
    PolicyDecisionPoint pdp;

    @Test
    void contextLoads(ApplicationContext context) {
        assertThat(context).isNotNull();
    }

    @ParameterizedTest
    @MethodSource("demoUserSource")
    void when_makeTemplatePayment_then_ok(PrinterUser user) {
        var authentication = new UsernamePasswordAuthenticationToken(user, user.getPassword(), user.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        ethConnect.makePayment(user, "1");
        var subscription = buildSubscription(user, "access", "paidTemplate");
        assertThat(pdp.decide(subscription).blockFirst()).isNotNull();
    }

    private AuthorizationSubscription buildSubscription(Object user, String action, String resource) {
        return new AuthorizationSubscription(mapper.convertValue(user, JsonNode.class), JSON.textNode(action),
                JSON.textNode(resource), null);
    }
}
