package io.sapl.axon.queryhandling;

import java.lang.annotation.Annotation;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.axonframework.messaging.Message;
import org.axonframework.messaging.annotation.MessageHandlingMember;
import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.queryhandling.SubscriptionQueryMessage;
import org.springframework.security.access.AccessDeniedException;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.axon.AbstractAxonPolicyEnforcementPoint;
import io.sapl.axon.Annotations;
import io.sapl.axon.annotation.PostHandleEnforce;
import io.sapl.axon.constrainthandling.ConstraintHandlerService;
import io.sapl.axon.constrainthandling.QueryConstraintHandlerBundle;
import io.sapl.axon.subscription.AuthorizationSubscriptionBuilderService;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
public class QueryPolicyEnforcementPoint<T> extends AbstractAxonPolicyEnforcementPoint<T> {

	private static final Duration SUBSCRIPTION_TIMEOUT = Duration.ofSeconds(5L);

	private final SaplQueryUpdateEmitter emitter;

	public QueryPolicyEnforcementPoint(MessageHandlingMember<T> delegate, PolicyDecisionPoint pdp,
			ConstraintHandlerService axonConstraintEnforcementService, SaplQueryUpdateEmitter emitter,
			AuthorizationSubscriptionBuilderService subscriptionBuilder) {
		super(delegate, pdp, axonConstraintEnforcementService, subscriptionBuilder);
		this.emitter = emitter;
	}

	@Override
	public Object handle(Message<?> message, T source) throws Exception {

		var updateType = updateTypeIfSubscriptionQuery(message);
		if (updateType.isPresent()) {
			return handleSubscriptionQuery((QueryMessage<?, ?>) message, source, updateType);
		}

		return handleSimpleQuery((QueryMessage<?, ?>) message, source);
	}

	private Object handleSimpleQuery(QueryMessage<?, ?> message, T source) throws Exception {
		log.debug("Handling simple query: {}", message.getPayload());

		if (saplAnnotations.isEmpty()) {
			log.debug("No SAPL annotations on handler. Delegate without policy enforcement");
			return delegate.handle(message, source);
		}

		var preEnforceAnnotationsPresent = Annotations.annotationsMatching(saplAnnotations,
				Annotations.QUERY_ANNOTATIONS_IMPLYING_PREENFORCING);
		if (preEnforceAnnotationsPresent.size() > 1) {
			log.error("Only one of the follwoing annotations is allowed on a query handler at the same time: {}",
					Annotations.QUERY_ANNOTATIONS_IMPLYING_PREENFORCING.stream().map(a -> "@" + a.getSimpleName())
							.collect(Collectors.joining(", ")));
			log.error(
					"All of these annotations imply that there must be a policy-enforcement before invoking the annotated method.");
			log.error(
					"If more than one is present, the implied enforcement strategies contradict each other. Please choose one.");
			return Mono.error(new AccessDeniedException("Access denied by PEP")).toFuture();
		}

		Optional<Mono<Object>> preEnforcedQueryResult = Optional.empty();
		if (preEnforceAnnotationsPresent.size() == 1) {
			var preEnforcementAnnotation = preEnforceAnnotationsPresent.stream().findFirst().get();
			log.debug("Building a pre-handler-enforcement (@{}) PEP for the query handler of {}. ",
					preEnforcementAnnotation.annotationType().getSimpleName(),
					message.getPayloadType().getSimpleName());
			var preEnforceAuthzSubscription = subscriptionBuilder.constructAuthorizationSubscriptionForQuery(
					(QueryMessage<?, ?>) message, preEnforcementAnnotation, handlerExecutable, Optional.empty());
			preEnforcedQueryResult = Optional
					.of(pdp.decide(preEnforceAuthzSubscription).defaultIfEmpty(AuthorizationDecision.DENY).next()
							.flatMap(enforcePreEnforceDecision(message, source, Optional.empty())));
		}

		var queryResult           = preEnforcedQueryResult.orElseGet(() -> callDelegate(message, source));
		var postEnforceAnnotation = saplAnnotations.stream()
				.filter(annotation -> annotation.annotationType().isAssignableFrom(PostHandleEnforce.class))
				.findFirst();

		if (postEnforceAnnotation.isEmpty()) {
			return queryResult.toFuture();
		}
		return queryResult.onErrorResume(enforcePostEnforceOnErrorResult(message, postEnforceAnnotation))
				.flatMap(enforcePostEnforceOnSuccessfulQueryResult(message, source, postEnforceAnnotation)).toFuture();
	}

	private Function<? super Object, ? extends Mono<? extends Object>> enforcePostEnforceOnSuccessfulQueryResult(
			QueryMessage<?, ?> message, T source, Optional<Annotation> postEnforceAnnotation) {
		return actualQueryResultValue -> {
			var postEnforcementAnnotation = (PostHandleEnforce) postEnforceAnnotation.get();
			log.debug("Building a @PostHandlerEnforce PEP for the query handler of {}. ",
					message.getPayloadType().getSimpleName());
			var postEnforceAuthzSubscription = subscriptionBuilder.constructAuthorizationSubscriptionForQuery(
					(QueryMessage<?, ?>) message, postEnforcementAnnotation, handlerExecutable,
					Optional.of(actualQueryResultValue));
			return pdp.decide(postEnforceAuthzSubscription).defaultIfEmpty(AuthorizationDecision.DENY).next()
					.flatMap(enforcePostEnforceDecision(message, actualQueryResultValue));
		};
	}

	private Function<? super Throwable, ? extends Mono<? extends Object>> enforcePostEnforceOnErrorResult(
			QueryMessage<?, ?> message, Optional<Annotation> postEnforceAnnotation) {
		return error -> {
			var postEnforcementAnnotation = (PostHandleEnforce) postEnforceAnnotation.get();
			log.debug("Building a @PostHandlerEnforce PEP (error value) for the query handler of {}. ",
					message.getPayloadType().getSimpleName());
			var postEnforceAuthzSubscription = subscriptionBuilder.constructAuthorizationSubscriptionForQuery(
					(QueryMessage<?, ?>) message, postEnforcementAnnotation, handlerExecutable, Optional.of(error));
			return pdp.decide(postEnforceAuthzSubscription).defaultIfEmpty(AuthorizationDecision.DENY).next()
					.flatMap(enforcePostEnforceDecisionOnErrorResult(message, error));
		};
	}

	@SuppressWarnings("unchecked")
	private Function<AuthorizationDecision, Mono<Object>> enforcePreEnforceDecision(QueryMessage<?, ?> message,
			T source, Optional<ResponseType<?>> updateType) {
		return decision -> {
			@SuppressWarnings("rawtypes")
			QueryConstraintHandlerBundle constraintHandler = null;
			try {
				constraintHandler = axonConstraintEnforcementService.buildQueryPreHandlerBundle(decision,
						message.getResponseType(), updateType);
			} catch (AccessDeniedException e) {
				return Mono.error(e);
			}

			try {
				constraintHandler.executeOnDecisionHandlers(decision, message);
			} catch (AccessDeniedException error) {
				return Mono.error(constraintHandler.executeOnErrorHandlers(error));
			}
			if (decision.getDecision() != Decision.PERMIT) {
				var error = new AccessDeniedException("Access Denied");
				return Mono.error(constraintHandler.executeOnErrorHandlers(error));
			}
			try {
				QueryMessage<?, ?> updatedQuery = constraintHandler.executePreHandlingHandlers(message);
				return callDelegate(updatedQuery, source).map(replaceResourceIfRequired(decision, message))
						.map(constraintHandler::executePostHandlingHandlers)
						.onErrorMap(constraintHandler::executeOnErrorHandlers);
			} catch (AccessDeniedException error) {
				return Mono.error(constraintHandler.executeOnErrorHandlers(error));
			}
		};
	}

	private Function<Object, Object> replaceResourceIfRequired(AuthorizationDecision decision,
			QueryMessage<?, ?> message) {
		return o -> {
			if (decision.getResource().isPresent())
				return axonConstraintEnforcementService.deserializeResource(decision.getResource().get(),
						message.getResponseType());
			return o;
		};
	}

	@SuppressWarnings("unchecked")
	private Function<AuthorizationDecision, Mono<Object>> enforcePostEnforceDecisionOnErrorResult(
			QueryMessage<?, ?> message, Throwable error) {
		return decision -> {
			log.debug("PostHandlerEnforce {} for error {}", decision, error.getMessage());
			@SuppressWarnings("rawtypes")
			QueryConstraintHandlerBundle constraintHandler = null;
			try {
				constraintHandler = axonConstraintEnforcementService.buildQueryPostHandlerBundle(decision,
						message.getResponseType());
			} catch (AccessDeniedException e) {
				return Mono.error(e);
			}

			try {
				constraintHandler.executeOnDecisionHandlers(decision, message);
			} catch (AccessDeniedException e) {
				return Mono.error(constraintHandler.executeOnErrorHandlers(e));
			}

			if (decision.getDecision() != Decision.PERMIT) {
				var e = new AccessDeniedException("Access Denied");
				return Mono.error(constraintHandler.executeOnErrorHandlers(e));
			}

			return Mono.error(constraintHandler.executeOnErrorHandlers(error));
		};
	}

	@SuppressWarnings("unchecked")
	private Function<AuthorizationDecision, Mono<Object>> enforcePostEnforceDecision(QueryMessage<?, ?> message,
			Object returnObject) {
		return decision -> {
			log.debug("PostHandlerEnforce {} for {} [{}]", decision, message.getPayloadType(), returnObject);
			@SuppressWarnings("rawtypes")
			QueryConstraintHandlerBundle constraintHandler = null;
			try {
				constraintHandler = axonConstraintEnforcementService.buildQueryPostHandlerBundle(decision,
						message.getResponseType());
			} catch (AccessDeniedException e) {
				return Mono.error(e);
			}

			try {
				constraintHandler.executeOnDecisionHandlers(decision, message);
			} catch (AccessDeniedException error) {
				return Mono.error(constraintHandler.executeOnErrorHandlers(error));
			}

			if (decision.getDecision() != Decision.PERMIT) {
				var error = new AccessDeniedException("Access Denied");
				return Mono.error(constraintHandler.executeOnErrorHandlers(error));
			}

			var resultObject = returnObject;
			if (decision.getResource().isPresent()) {
				try {
					resultObject = axonConstraintEnforcementService.deserializeResource(decision.getResource().get(),
							message.getResponseType());
				} catch (AccessDeniedException e) {
					return Mono.error(constraintHandler.executeOnErrorHandlers(e));
				}
			}
			try {
				resultObject = constraintHandler.executePostHandlingHandlers(resultObject);
			} catch (AccessDeniedException error) {
				return Mono.error(constraintHandler.executeOnErrorHandlers(error));
			}
			return Mono.just(resultObject);
		};
	}

	private Mono<Object> callDelegate(Message<?> message, T source) {
		Object result = null;
		try {
			result = delegate.handle(message, source);
		} catch (Exception e) {
			return Mono.error(e);
		}

		if (result instanceof CompletableFuture) {
			return Mono.fromFuture(((CompletableFuture<?>) result));
		}

		return result != null ? Mono.just(result) : Mono.empty();
	}

	private Object handleSubscriptionQuery(QueryMessage<?, ?> message, T source, Optional<ResponseType<?>> updateType)
			throws Exception {
		log.debug("Handling subscription query: {}", message.getPayload());

		if (saplAnnotations.isEmpty()) {
			log.debug("No SAPL annotations on handler. Delegate without policy enforcement");
			emitter.authozrizeUpdatesForSubscriptionQueryWithId(message.getIdentifier());
			return delegate.handle(message, source);
		}

		var streamingAnnotation = uniqueStreamingEnforcementAnnotation();

		if (streamingAnnotation.isEmpty()) {
			log.debug(
					"No SAPL annotation for streaming present. Authorize all updates and delegate to potential handling of @PostHandleEnforce.");
			emitter.authozrizeUpdatesForSubscriptionQueryWithId(message.getIdentifier());
			return handleSimpleQuery(message, source);
		}

		var authzSubscription = subscriptionBuilder.constructAuthorizationSubscriptionForQuery(message,
				streamingAnnotation.get(), handlerExecutable, Optional.empty());
		var decisions         = pdp.decide(authzSubscription).defaultIfEmpty(AuthorizationDecision.DENY);
		var tap               = DecisionStreamTapping.tapForInitialValue(decisions, SUBSCRIPTION_TIMEOUT);
		var initialDecision   = tap.getT1();
		var tappedDecisions   = tap.getT2();

		log.debug("Set authorization mode of emitter {}", streamingAnnotation.get().annotationType().getSimpleName());
		emitter.authozrizeUpdatesForSubscriptionQueryWithId(message.getIdentifier(), tappedDecisions,
				streamingAnnotation.get().annotationType());
		log.debug("Build pre-authorization-handler PDP for initial result of subscription query");

		return initialDecision.flatMap(enforcePreEnforceDecision(message, source, updateType)).toFuture();
	}

	private Optional<Annotation> uniqueStreamingEnforcementAnnotation() {
		Set<Annotation> streamingEnforcementAnnotations = Annotations.annotationsMatching(saplAnnotations,
				Annotations.SUBSCRIPTION_ANNOTATIONS);
		if (streamingEnforcementAnnotations.size() != 1)
			throw new IllegalStateException(
					"The query handler for a streaming query has more than one SAPL annotation which can be used for policy enforcement on subscription queries.");

		return saplAnnotations.stream().findFirst();
	}

	private Optional<ResponseType<?>> updateTypeIfSubscriptionQuery(Message<?> message) {
		return emitter.activeSubscriptions().stream().filter(sameAsHandlededMessage(message)).findFirst()
				.map(SubscriptionQueryMessage::getUpdateResponseType);
	}

	private Predicate<? super SubscriptionQueryMessage<?, ?, ?>> sameAsHandlededMessage(Message<?> message) {
		return sub -> sub.getIdentifier().equals(message.getIdentifier());
	}

}
