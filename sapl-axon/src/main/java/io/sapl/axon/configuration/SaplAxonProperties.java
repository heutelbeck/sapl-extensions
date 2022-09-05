package io.sapl.axon.configuration;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

@Data
@ConfigurationProperties("io.sapl.axon")
public class SaplAxonProperties {
	Duration subscriptionQueryDecisionCacheTTL = Duration.ofMillis(500L);
}
