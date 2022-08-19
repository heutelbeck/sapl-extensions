package io.sapl.axon.query;

import java.lang.annotation.Annotation;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.axonframework.messaging.Message;
import org.axonframework.messaging.annotation.MessageHandlingMember;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.queryhandling.SubscriptionQueryMessage;
import org.springframework.security.access.AccessDeniedException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.axon.annotations.Annotations;
import io.sapl.axon.annotations.PostHandleEnforce;
import io.sapl.axon.constraints.AxonConstraintHandlerService;
import io.sapl.axon.subscriptions.AxonAuthorizationSubscriptionBuilderService;
import io.sapl.spring.constraints.ConstraintEnforcementService;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
public class QueryPolicyEnforcementPoint<T> extends AbstractAxonPolicyEnforcementPoint<T> {

	private final SaplQueryUpdateEmitter       emitter;
	private final ObjectMapper                 mapper;

	public QueryPolicyEnforcementPoint(MessageHandlingMember<T> delegate, PolicyDecisionPoint pdp,
			ConstraintEnforcementService constraintEnforcementService,
			AxonConstraintHandlerService axonConstraintEnforcementService, SaplQueryUpdateEmitter emitter,
			AxonAuthorizationSubscriptionBuilderService subscriptionBuilder, ObjectMapper mapper) {
		super(delegate, pdp, constraintEnforcementService, axonConstraintEnforcementService, subscriptionBuilder);
		this.emitter = emitter;
		this.mapper  = mapper;
	}

	@Override
	public Object handle(Message<?> message, T source) throws Exception {

		if (isSubscriptionQuery(message)) {
			return handleSubscriptionQuery(message, source);
		}

		return handleSimpleQuery(message, source);
	}

	private Object handleSimpleQuery(Message<?> message, T source) throws Exception {
		log.debug("Handling simple query: {}", message.getPayload());

		if (saplAnnotations.isEmpty()) {
			log.debug("No SAPL annotations on handler. Delegate without policy enforcement");
			delegate.handle(message, source);
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
							.flatMap(enforcePreEnforceDecision(message, source)));
		}

		var queryResult           = preEnforcedQueryResult.orElseGet(() -> callDelegate(message, source));
		var postEnforceAnnotation = saplAnnotations.stream()
				.filter(annotation -> annotation.annotationType().isAssignableFrom(PostHandleEnforce.class))
				.findFirst();

		if (postEnforceAnnotation.isEmpty()) {
			return queryResult.toFuture();
		}

		return queryResult.flatMap(actualQueryResultValue -> {
			var postEnforcementAnnotation = (PostHandleEnforce) postEnforceAnnotation.get();
			log.debug("Building a @PostHandlerEnforce PEP for the query handler of {}. ",
					message.getPayloadType().getSimpleName());
			var postEnforceAuthzSubscription = subscriptionBuilder.constructAuthorizationSubscriptionForQuery(
					(QueryMessage<?, ?>) message, postEnforcementAnnotation, handlerExecutable,
					Optional.of(actualQueryResultValue));
			return pdp.decide(postEnforceAuthzSubscription).defaultIfEmpty(AuthorizationDecision.DENY).next()
					.flatMap(enforcePostEnforceDecision(message, source, actualQueryResultValue,
							postEnforcementAnnotation.genericsType()));
		}).toFuture();
	}

	private Function<AuthorizationDecision, Mono<Object>> enforcePreEnforceDecision(Message<?> message, T source) {
		return decision -> {
			log.debug("PreHandlerEnforce {} for {}", decision, message.getPayloadType());
			if (decision.getDecision() != Decision.PERMIT) {
				return Mono.error(new AccessDeniedException("Access Denied by PDP"));
			}
			return callDelegate(message, source);
		};
	}

	private Function<AuthorizationDecision, Mono<Object>> enforcePostEnforceDecision(Message<?> message, T source,
			Object returnObject, Class<?> clazz) {
		return decision -> {
			log.debug("PostHandlerEnforce {} for {} [{}]", decision, message.getPayloadType(), returnObject);
			if (decision.getDecision() != Decision.PERMIT) {
				return Mono.error(new AccessDeniedException("Access Denied by PDP"));
			}
			if (decision.getResource().isPresent()) {
				try {
					return Mono.just(mapper.treeToValue(decision.getResource().get(), clazz));
				} catch (JsonProcessingException | IllegalArgumentException e) {
					return Mono.error(new AccessDeniedException("Access Denied by PEP."));
				}
			}
			return Mono.just(returnObject);
		};
	}

	@SneakyThrows
	private Mono<Object> callDelegate(Message<?> message, T source) {
		var result = delegate.handle(message, source);
		if (result instanceof CompletableFuture) {
			return Mono.fromFuture(((CompletableFuture<?>) result));
		}

		return Mono.just(result);
	}

	private Object handleSubscriptionQuery(Message<?> message, T source) throws Exception {
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

		var authzSubscription = subscriptionBuilder.constructAuthorizationSubscriptionForQuery(
				(QueryMessage<?, ?>) message, streamingAnnotation.get(), handlerExecutable, Optional.empty());
		var decisions         = pdp.decide(authzSubscription).defaultIfEmpty(AuthorizationDecision.DENY).share()
				.cache(1);
		log.debug("Set authorization mode of emitter {}", streamingAnnotation.get().annotationType().getSimpleName());
		emitter.authozrizeUpdatesForSubscriptionQueryWithId(message.getIdentifier(), decisions,
				streamingAnnotation.get().annotationType());
		log.debug("Build pre-authorization-handler PDP for initial result of subscription query");
		return decisions.next().flatMap(enforcePreEnforceDecision(message, source)).toFuture();
	}

	private Optional<Annotation> uniqueStreamingEnforcementAnnotation() {
		Set<Annotation> streamingEnforcementAnnotations = Annotations.annotationsMatching(saplAnnotations,
				Annotations.SUBSCRIPTION_ANNOTATIONS);
		if (streamingEnforcementAnnotations.size() != 1)
			throw new IllegalStateException(
					"The query handler for a streaming query has more than one SAPL annotation which can be used for policy enforcement on subscription queries.");

		return saplAnnotations.stream().findFirst();
	}

	private boolean isSubscriptionQuery(Message<?> message) {
		return emitter.activeSubscriptions().stream().filter(sameAsHandlededMessage(message)).findFirst().isPresent();
	}

	private Predicate<? super SubscriptionQueryMessage<?, ?, ?>> sameAsHandlededMessage(Message<?> message) {
		return sub -> sub.getIdentifier().equals(message.getIdentifier());
	}
}
