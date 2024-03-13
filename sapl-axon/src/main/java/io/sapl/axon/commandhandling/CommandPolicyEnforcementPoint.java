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
package io.sapl.axon.commandhandling;

import java.lang.reflect.Executable;
import java.util.Optional;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.annotation.MessageHandlingMember;
import org.axonframework.messaging.annotation.WrappedMessageHandlingMember;
import org.springframework.security.access.AccessDeniedException;

import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.axon.annotation.PreHandleEnforce;
import io.sapl.axon.constrainthandling.ConstraintHandlerService;
import io.sapl.axon.subscription.AuthorizationSubscriptionBuilderService;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Wrapper for command handlers establishing a Policy Enforcement Point.
 *
 * @author Dominic Heutelbeck
 * @since 2.1.0
 *
 * @param <T> The type of the handing object.
 */
@Slf4j
public class CommandPolicyEnforcementPoint<T> extends WrappedMessageHandlingMember<T> {

    private static final String                           ACCESS_DENIED = "Access Denied";
    private final PolicyDecisionPoint                     pdp;
    private final MessageHandlingMember<T>                delegate;
    private final AuthorizationSubscriptionBuilderService subscriptionBuilder;
    private final ConstraintHandlerService                axonConstraintEnforcementService;

    /**
     * Instantiate a CommandPolicyEnforcementPoint.
     *
     * @param delegate                         The delegate handler.
     * @param pdp                              The Policy Decision Point.
     * @param axonConstraintEnforcementService The ConstraintHandlerService.
     * @param subscriptionBuilder              The
     *                                         AuthorizationSubscriptionBuilderService.
     */
    public CommandPolicyEnforcementPoint(MessageHandlingMember<T> delegate, PolicyDecisionPoint pdp,
            ConstraintHandlerService axonConstraintEnforcementService,
            AuthorizationSubscriptionBuilderService subscriptionBuilder) {
        super(delegate);
        this.delegate                         = delegate;
        this.pdp                              = pdp;
        this.axonConstraintEnforcementService = axonConstraintEnforcementService;
        this.subscriptionBuilder              = subscriptionBuilder;
    }

    /**
     *
     */
    @Override
    public Object handle(@NonNull Message<?> message, T aggregate) throws Exception {
        var preEnforceAnnotation = findPreEnforceAnnotation();
        if (preEnforceAnnotation.isPresent()) {
            return preEnforcePolices((CommandMessage<?>) message, aggregate, preEnforceAnnotation.get());
        } else {
            return delegate.handle(message, aggregate);
        }
    }

    private Optional<PreHandleEnforce> findPreEnforceAnnotation() {
        return delegate.unwrap(Executable.class)
                .flatMap(executable -> Optional.ofNullable(executable.getAnnotation(PreHandleEnforce.class)));
    }

    private Object preEnforcePolices(CommandMessage<?> command, T aggregate, PreHandleEnforce preHandleEnforce)
            throws Exception {

        var authzSubscription = subscriptionBuilder.constructAuthorizationSubscriptionForCommand(command, aggregate,
                preHandleEnforce);
        /*
         * The next line includes blocking IO. As far as we know, this cannot be done
         * asynchronously in the current version of Axon.
         */
        var decision = pdp.decide(authzSubscription).next().toFuture().get();

        if (decision == null) {
            log.error("PDP returned null.");
            throw new AccessDeniedException(ACCESS_DENIED);
        }

        var executable = delegate.unwrap(Executable.class);
        var bundle     = axonConstraintEnforcementService.buildPreEnforceCommandConstraintHandlerBundle(decision,
                aggregate, executable, command);
        try {
            bundle.executeOnDecisionHandlers(decision, command);
        } catch (Exception t) {
            log.error("command on decision constraint handlers failed: {}", t.getMessage(), t);
            throw bundle.executeOnErrorHandlers(new AccessDeniedException(ACCESS_DENIED,t));
        }

        if (decision.getDecision() != Decision.PERMIT) {
            throw bundle.executeOnErrorHandlers(new AccessDeniedException(ACCESS_DENIED));
        }

        try {
            bundle.executeAggregateConstraintHandlerMethods();
        } catch (Exception t) {
            log.error("command aggregate constraint handlers failed: {}", t.getMessage(), t);
            throw bundle.executeOnErrorHandlers(new AccessDeniedException(ACCESS_DENIED, t));
        }

        CommandMessage<?> mappedCommand;
        try {
            mappedCommand = bundle.executeCommandMappingHandlers(command);
        } catch (Exception t) {
            log.error("command mapping constraint handlers failed: {}", t.getMessage(), t);
            throw bundle.executeOnErrorHandlers(new AccessDeniedException(ACCESS_DENIED, t));
        }

        Object result;
        try {
            result = delegate.handle(mappedCommand, aggregate);
        } catch (Exception t) {
            throw bundle.executeOnErrorHandlers(t);
        }

        Object mappedResult;
        try {
            mappedResult = bundle.executePostHandlingHandlers(result);
        } catch (Exception t) {
            log.error("command result mapping failed: {}", t.getMessage(), t);
            throw bundle.executeOnErrorHandlers(new AccessDeniedException(ACCESS_DENIED, t));
        }

        return mappedResult;

    }

}
