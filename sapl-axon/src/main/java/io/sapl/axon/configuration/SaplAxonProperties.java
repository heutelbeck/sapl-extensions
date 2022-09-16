package io.sapl.axon.configuration;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/**
 * The subscriptionQueryDecisionCacheTTL property configures how log a
 * authorization subscription is kept alive in case of subscription queries
 * where the initial query handler and update emitter are independently
 * accessed.
 * 
 * @author Dominic Heutelbeck
 * @since 2.1.0
 */
@Data
@ConfigurationProperties("io.sapl.axon")
public class SaplAxonProperties {
	/**
	 * Subscription and cache TTL.
	 */
	Duration subscriptionQueryDecisionCacheTTL = Duration.ofMillis(500L);
}
