/*
 * Copyright © 2017-2022 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import java.util.Arrays;

import org.axonframework.commandhandling.CommandMessageHandlingMember;
import org.axonframework.messaging.annotation.HandlerEnhancerDefinition;
import org.axonframework.messaging.annotation.MessageHandlingMember;
import org.axonframework.queryhandling.annotation.QueryHandlingMember;

import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.axon.commandhandling.CommandPolicyEnforcementPoint;
import io.sapl.axon.constrainthandling.ConstraintHandlerService;
import io.sapl.axon.queryhandling.QueryPolicyEnforcementPoint;
import io.sapl.axon.queryhandling.SaplQueryUpdateEmitter;
import io.sapl.axon.subscription.AuthorizationSubscriptionBuilderService;
import lombok.RequiredArgsConstructor;

/**
 * 
 * The SaplHandlerEnhancer is responsible for adding Policy Enforcement Points
 * to all command and query handlers in the system.
 *
 * Upon application startup, Axon scans all classes for the @CommandHandler
 * or @QueryHandler annotations. All of these are registered as
 * MessageHandlingMembers and this Service is called for each of them, which
 * then wraps all handlers with the respective CommandPolicyEnforcementPoint or
 * QueryPolicyEnforcementPoint.
 * 
 * @author Dominic Heutelbeck
 * @since 2.1.0
 */
@RequiredArgsConstructor
public class SaplHandlerEnhancer implements HandlerEnhancerDefinition {

	private final PolicyDecisionPoint                     pdp;
	private final ConstraintHandlerService                axonConstraintEnforcementService;
	private final SaplQueryUpdateEmitter                  emitter;
	private final AuthorizationSubscriptionBuilderService subscriptionBuilder;
	private final SaplAxonProperties                      properties;

	@Override
	public <T> MessageHandlingMember<T> wrapHandler(MessageHandlingMember<T> original) {

		var interfaces = Arrays.asList(original.getClass().getInterfaces());

		if (interfaces.contains(CommandMessageHandlingMember.class)) {
			return new CommandPolicyEnforcementPoint<>(original, pdp, axonConstraintEnforcementService,
					subscriptionBuilder);
		}

		if (interfaces.contains(QueryHandlingMember.class)) {
			return new QueryPolicyEnforcementPoint<>(original, pdp, axonConstraintEnforcementService, emitter,
					subscriptionBuilder, properties);
		}

		return original;
	}

}
