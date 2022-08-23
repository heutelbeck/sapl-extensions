package io.sapl.axon.queryhandling;

import java.lang.annotation.Annotation;
import java.lang.reflect.Executable;
import java.util.Arrays;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.axonframework.messaging.annotation.MessageHandlingMember;
import org.axonframework.messaging.annotation.WrappedMessageHandlingMember;

import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.axon.constrainthandling.AxonConstraintHandlerService;
import io.sapl.axon.subscriptions.AxonAuthorizationSubscriptionBuilderService;

public abstract class AbstractAxonPolicyEnforcementPoint<T> extends WrappedMessageHandlingMember<T> {

	protected final PolicyDecisionPoint                         pdp;
	protected final Set<Annotation>                             saplAnnotations;
	protected final MessageHandlingMember<T>                    delegate;
	protected final AxonAuthorizationSubscriptionBuilderService subscriptionBuilder;
	protected final Executable                                  handlerExecutable;
	protected final AxonConstraintHandlerService                axonConstraintEnforcementService;

	protected AbstractAxonPolicyEnforcementPoint(MessageHandlingMember<T> delegate, PolicyDecisionPoint pdp,
			AxonConstraintHandlerService axonConstraintEnforcementService,
			AxonAuthorizationSubscriptionBuilderService subscriptionBuilder) {
		super(delegate);
		this.delegate                         = delegate;
		this.pdp                              = pdp;
		this.axonConstraintEnforcementService = axonConstraintEnforcementService;
		this.subscriptionBuilder              = subscriptionBuilder;
		this.handlerExecutable                = delegate.unwrap(Executable.class)
				.orElseThrow(() -> new IllegalStateException(
						"No underlying method or constructor found while wrapping the CommandHandlingMember."));
		this.saplAnnotations                  = saplAnnotationsOnUnderlyingExecutable(delegate);
	}

	private final Set<Annotation> saplAnnotationsOnUnderlyingExecutable(MessageHandlingMember<T> delegate) {
		var allAnnotationsOnExecutable = handlerExecutable.getDeclaredAnnotations();
		return Arrays.stream(allAnnotationsOnExecutable).filter(this::isSaplAnnotation)
				.collect(Collectors.toUnmodifiableSet());
	}

	private boolean isSaplAnnotation(Annotation annotation) {
		return Annotations.SAPL_AXON_ANNOTATIONS.stream().filter(matchesAnnotationType(annotation)).findFirst()
				.isPresent();
	}

	private Predicate<? super Class<?>> matchesAnnotationType(Annotation annotation) {
		return aSaplAnnotation -> annotation.annotationType().isAssignableFrom(aSaplAnnotation);
	}

}
