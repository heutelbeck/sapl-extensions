package io.sapl.axon.query;

import org.axonframework.eventsourcing.eventstore.EmbeddedEventStore;
import org.axonframework.eventsourcing.eventstore.EventStorageEngine;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import org.axonframework.spring.config.AxonConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;


public class QueryTest extends QueryTestsuite {
	@DynamicPropertySource
	static void registerAxonProperties(DynamicPropertyRegistry registry) {
		registry.add("axon.axonserver.enabled", () -> "false");
	}

	@Configuration
	static class EmbeddedEventstoreConfiguration {
		@Bean
		public EmbeddedEventStore eventStore(EventStorageEngine storageEngine, AxonConfiguration configuration) {
			return EmbeddedEventStore.builder().storageEngine(storageEngine)
					.messageMonitor(configuration.messageMonitor(EventStore.class, "eventStore")).build();
		}

		@Bean
		public EventStorageEngine storageEngine() {
			return new InMemoryEventStorageEngine();
		}
	}

	@SpringBootApplication
	static class TestApplication {

		public static void main(String[] args) {
			SpringApplication.run(TestApplication.class, args);
		}
	}
}
