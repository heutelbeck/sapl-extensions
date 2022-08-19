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
package io.sapl.axon.query;

import java.util.Arrays;

import org.axonframework.commandhandling.CommandMessageHandlingMember;
import org.axonframework.messaging.annotation.HandlerEnhancerDefinition;
import org.axonframework.messaging.annotation.MessageHandlingMember;
import org.axonframework.queryhandling.annotation.QueryHandlingMember;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.axon.command.CommandPolicyEnforcementPoint;
import io.sapl.axon.constraints.AxonConstraintHandlerService;
import io.sapl.axon.subscriptions.AxonAuthorizationSubscriptionBuilderService;
import io.sapl.spring.constraints.ConstraintEnforcementService;
import lombok.RequiredArgsConstructor;

/**
 * Default Implementation of Axon HandlerEnhancer which allows the enhancing of
 * CommandHandlingMember with the {@link SAPLCommandHandlingMember}.
 *
 */
@RequiredArgsConstructor
public class SaplHandlerEnhancer implements HandlerEnhancerDefinition {

	private final PolicyDecisionPoint                         pdp;
	private final ConstraintEnforcementService                constraintEnforcementService;
	private final AxonConstraintHandlerService                axonConstraintEnforcementService;
	private final SaplQueryUpdateEmitter                      emitter;
	private final AxonAuthorizationSubscriptionBuilderService subscriptionBuilder;
	private final ObjectMapper                                mapper;

	@Override
	public <T> MessageHandlingMember<T> wrapHandler(MessageHandlingMember<T> original) {

		var interfaces = Arrays.asList(original.getClass().getInterfaces());

		if (interfaces.contains(CommandMessageHandlingMember.class)) {
			return new CommandPolicyEnforcementPoint<>(original);
		}

		if (interfaces.contains(QueryHandlingMember.class)) {
			return new QueryPolicyEnforcementPoint<>(original, pdp, constraintEnforcementService,
					axonConstraintEnforcementService, emitter, subscriptionBuilder, mapper);
		}

		return original;
	}

}
