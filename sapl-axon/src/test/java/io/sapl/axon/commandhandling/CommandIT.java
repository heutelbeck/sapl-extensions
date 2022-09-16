package io.sapl.axon.commandhandling;

import java.time.Duration;

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

}
