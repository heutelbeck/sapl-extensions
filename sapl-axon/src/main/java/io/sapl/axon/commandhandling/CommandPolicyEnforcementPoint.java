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
	public Object handle(Message<?> message, T aggregate) throws Exception {
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
			throw new AccessDeniedException("Access Denied");
		}

		var executable = delegate.unwrap(Executable.class);
		var bundle     = axonConstraintEnforcementService.buildPreEnforceCommandConstraintHandlerBundle(decision,
				aggregate, executable, command);
		try {
			bundle.executeOnDecisionHandlers(decision, command);
		} catch (Exception t) {
			log.error("command on decision constraint handlers failed: {}", t.getMessage(), t);
			throw bundle.executeOnErrorHandlers(new AccessDeniedException("Access Denied"));
		}

		if (decision.getDecision() != Decision.PERMIT) {
			throw bundle.executeOnErrorHandlers(new AccessDeniedException("Access Denied"));
		}

		try {
			bundle.executeAggregateConstraintHandlerMethods();
		} catch (Exception t) {
			log.error("command aggregate constraint handlers failed: {}", t.getMessage(), t);
			throw bundle.executeOnErrorHandlers(new AccessDeniedException("Access Denied"));
		}

		CommandMessage<?> mappedCommand = null;
		try {
			mappedCommand = bundle.executeCommandMappingHandlers(command);
		} catch (Exception t) {
			log.error("command mapping constraint handlers failed: {}", t.getMessage(), t);
			throw bundle.executeOnErrorHandlers(new AccessDeniedException("Access Denied"));
		}

		Object result = null;
		try {
			result = delegate.handle(mappedCommand, aggregate);
		} catch (Exception t) {
			throw bundle.executeOnErrorHandlers(t);
		}

		Object mappedResult = null;
		try {
			mappedResult = bundle.executePostHandlingHandlers(result);
		} catch (Exception t) {
			log.error("command result mapping failed: {}", t.getMessage(), t);
			throw bundle.executeOnErrorHandlers(new AccessDeniedException("Access Denied"));
		}

		return mappedResult;

	}

}
