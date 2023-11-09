/*
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.axon.commandhandling;

import java.time.Duration;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@DirtiesContext
@Testcontainers
public class CommandIT extends CommandTestsuite {
    private static final int AXON_SERVER_GRPC_PORT = 8124;

    private static final long TIMEOUT_FOR_AXON_SERVER_SPINUP = 40L;

    @Container
    static GenericContainer<?> axonServer = new GenericContainer<>(DockerImageName.parse("axoniq/axonserver"))
            .withExposedPorts(8024, 8124).waitingFor(Wait.forHttp("/actuator/info").forPort(8024))
            .withStartupTimeout(Duration.ofSeconds(TIMEOUT_FOR_AXON_SERVER_SPINUP));

    @DynamicPropertySource
    static void registerAxonProperties(DynamicPropertyRegistry registry) {
        registry.add("axon.axonserver.servers",
                () -> axonServer.getHost() + ":" + axonServer.getMappedPort(AXON_SERVER_GRPC_PORT));
    }

    @SpringBootApplication(scanBasePackages = { "io.sapl.axon.commandhandling.*" })
    public static class TestApplication {

        public static void main(String[] args) {
            SpringApplication.run(TestApplication.class, args);
        }
    }

    @BeforeAll
    public static void beforeAll() {
        isIntegrationTest = true;
    }

    @AfterAll
    public static void afterAll() {
        isIntegrationTest = false;
    }
}
