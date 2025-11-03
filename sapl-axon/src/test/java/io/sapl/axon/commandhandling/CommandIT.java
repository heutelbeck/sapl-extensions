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
package io.sapl.axon.commandhandling;

import org.axonframework.test.server.AxonServerContainer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@DirtiesContext
@Testcontainers
public class CommandIT extends CommandTestsuite {
    private static final int AXON_SERVER_GRPC_PORT = 8124;

    @Container
    @SuppressWarnings("resource") // Fine for tests which are short-lived
    static final AxonServerContainer AXON_SERVER = new AxonServerContainer(
            DockerImageName.parse("axoniq/axonserver:latest-dev"))
            .waitingFor(Wait.forListeningPorts(AXON_SERVER_GRPC_PORT));

    @DynamicPropertySource
    static void registerAxonProperties(DynamicPropertyRegistry registry) {
        registry.add("axon.axonserver.servers",
                () -> AXON_SERVER.getHost() + ":" + AXON_SERVER.getMappedPort(AXON_SERVER_GRPC_PORT));
    }

    @SpringBootApplication(scanBasePackages = { "io.sapl.axon.commandhandling.*" })
    public static class TestApplication {

        public static void main(String[] args) {
            SpringApplication.run(TestApplication.class, args);
        }
    }

    @BeforeAll
    static void beforeAll() {
        isIntegrationTest = true;
    }

    @AfterAll
    static void afterAll() {
        isIntegrationTest = false;
    }
}
