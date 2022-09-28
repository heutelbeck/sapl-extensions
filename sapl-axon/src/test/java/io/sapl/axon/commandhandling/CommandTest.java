package io.sapl.axon.commandhandling;

import org.axonframework.eventsourcing.eventstore.EventStorageEngine;
import org.axonframework.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

public class CommandTest extends CommandTestsuite {

	@SpringBootApplication(scanBasePackages = { "io.sapl.axon.commandhandling.*" })
	public static class TestApplication {

		public static void main(String[] args) {
			SpringApplication.run(TestApplication.class, args);
		}
	}

	@DynamicPropertySource
	static void registerAxonProperties(DynamicPropertyRegistry registry) {
		registry.add("axon.axonserver.enabled", () -> "false");
	}

	@Configuration
	static class EmbeddedEventstoreConfiguration {
		@Bean
		public EventStorageEngine storageEngine() {
			return new InMemoryEventStorageEngine();
		}
	}

}
