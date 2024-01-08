/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.axon.configuration;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/**
 * The subscriptionQueryDecisionCacheTTL property configures how log an
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
