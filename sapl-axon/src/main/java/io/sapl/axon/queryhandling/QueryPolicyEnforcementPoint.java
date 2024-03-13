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
package io.sapl.axon.queryhandling;

import java.lang.annotation.Annotation;
import java.lang.reflect.Executable;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import lombok.NonNull;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.annotation.MessageHandlingMember;
import org.axonframework.messaging.annotation.WrappedMessageHandlingMember;
import org.axonframework.messaging.responsetypes.PublisherResponseType;
import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.queryhandling.SubscriptionQueryMessage;
import org.reactivestreams.Publisher;
import org.springframework.security.access.AccessDeniedException;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.axon.annotation.EnforceDropUpdatesWhileDenied;
import io.sapl.axon.annotation.EnforceRecoverableUpdatesIfDenied;
import io.sapl.axon.annotation.PostHandleEnforce;
import io.sapl.axon.annotation.PreHandleEnforce;
import io.sapl.axon.configuration.SaplAxonProperties;
import io.sapl.axon.constrainthandling.ConstraintHandlerService;
import io.sapl.axon.constrainthandling.QueryConstraintHandlerBundle;
import io.sapl.axon.subscription.AuthorizationSubscriptionBuilderService;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Wrapper for query handlers establishing a Policy Enforcement Point.
 *
 * @author Dominic Heutelbeck
 * @since 2.1.0
 *
 * @param <T> The type of the handing object.
 */
@Slf4j
public class QueryPolicyEnforcementPoint<T> extends WrappedMessageHandlingMember<T> {

    private static final String ACCESS_DENIED = "Access Denied";

    private static final Set<Class<?>> SAPL_AXON_ANNOTATIONS = Set.of(PreHandleEnforce.class, PostHandleEnforce.class,
            EnforceDropUpdatesWhileDenied.class, EnforceRecoverableUpdatesIfDenied.class);

    private static final Set<Class<?>> SUBSCRIPTION_ANNOTATIONS = Set.of(PreHandleEnforce.class,
            EnforceDropUpdatesWhileDenied.class, EnforceRecoverableUpdatesIfDenied.class);

    private static final Set<Class<?>> QUERY_ANNOTATIONS_IMPLYING_PREENFORCING = Set.of(PreHandleEnforce.class,
            EnforceDropUpdatesWhileDenied.class, EnforceRecoverableUpdatesIfDenied.class);

    private final SaplQueryUpdateEmitter                  emitter;
    private final ConstraintHandlerService                axonConstraintEnforcementService;
    private final AuthorizationSubscriptionBuilderService subscriptionBuilder;
    private final PolicyDecisionPoint                     pdp;
    private final Set<Annotation>                         saplAnnotations;
    private final MessageHandlingMember<T>                delegate;
    private final Executable                              handlerExecutable;
    private final SaplAxonProperties                      properties;

    /**
     * Instantiate a QueryPolicyEnforcementPoint.
     *
     * @param delegate                         The delegate handler.
     * @param pdp                              The Policy Decision Point.
     * @param emitter                          The SaplQueryUpdateEmitter.
     * @param axonConstraintEnforcementService The ConstraintHandlerService.
     * @param properties                       The configuration class for TTL
     *                                         settings.
     * @param subscriptionBuilder              The
     *                                         AuthorizationSubscriptionBuilderService.
     */
    public QueryPolicyEnforcementPoint(MessageHandlingMember<T> delegate, PolicyDecisionPoint pdp,
            ConstraintHandlerService axonConstraintEnforcementService, SaplQueryUpdateEmitter emitter,
            AuthorizationSubscriptionBuilderService subscriptionBuilder, SaplAxonProperties properties) {
        super(delegate);
        this.delegate                         = delegate;
        this.pdp                              = pdp;
        this.axonConstraintEnforcementService = axonConstraintEnforcementService;
        this.subscriptionBuilder              = subscriptionBuilder;
        this.handlerExecutable                = delegate.unwrap(Executable.class)
                .orElseThrow(() -> new IllegalStateException(
                        "No underlying method or constructor found while wrapping the CommandHandlingMember."));
        this.saplAnnotations                  = saplAnnotationsOnUnderlyingExecutable();
        this.emitter                          = emitter;
        this.properties                       = properties;
    }

    @Override
    public Object handle(@NonNull Message<?> message, T source) throws Exception {

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

        var preEnforceAnnotationsPresent = annotationsMatching(saplAnnotations,
                QUERY_ANNOTATIONS_IMPLYING_PREENFORCING);
        if (preEnforceAnnotationsPresent.size() > 1) {
            log.error("Only one of the following annotations is allowed on a query handler at the same time: {}",
                    QUERY_ANNOTATIONS_IMPLYING_PREENFORCING.stream().map(a -> "@" + a.getSimpleName())
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
            var preEnforceAuthzSubscription = subscriptionBuilder.constructAuthorizationSubscriptionForQuery(message,
                    preEnforcementAnnotation, handlerExecutable, Optional.empty());
            preEnforcedQueryResult = Optional
                    .of(pdp.decide(preEnforceAuthzSubscription).defaultIfEmpty(AuthorizationDecision.DENY).next()
                            .flatMap(enforcePreEnforceDecision(message, source, Optional.empty())));
        }

        var queryResult           = preEnforcedQueryResult.orElseGet(() -> callDelegate(message, source));
        var postEnforceAnnotation = saplAnnotations.stream()
                .filter(annotation -> annotation.annotationType().isAssignableFrom(PostHandleEnforce.class))
                .findFirst();

        if (postEnforceAnnotation.isPresent()) {
            queryResult = queryResult.onErrorResume(enforcePostEnforceOnErrorResult(message, postEnforceAnnotation))
                    .flatMap(enforcePostEnforceOnSuccessfulQueryResult(message, postEnforceAnnotation));
        }
        if (message.getResponseType() instanceof PublisherResponseType)
            return queryResult.flatMapMany(r -> (Publisher<?>) r);

        return queryResult.toFuture();
    }

    private Function<? super Object, ? extends Mono<?>> enforcePostEnforceOnSuccessfulQueryResult(
            QueryMessage<?, ?> message, Optional<Annotation> postEnforceAnnotation) {
        return actualQueryResultValue -> {
            var postEnforcementAnnotation = (PostHandleEnforce) postEnforceAnnotation.get();
            log.debug("Building a @PostHandlerEnforce PEP for the query handler of {}. ",
                    message.getPayloadType().getSimpleName());
            var postEnforceAuthzSubscription = subscriptionBuilder.constructAuthorizationSubscriptionForQuery(message,
                    postEnforcementAnnotation, handlerExecutable, Optional.of(actualQueryResultValue));
            return pdp.decide(postEnforceAuthzSubscription).defaultIfEmpty(AuthorizationDecision.DENY).next()
                    .flatMap(enforcePostEnforceDecision(message, actualQueryResultValue));
        };
    }

    private Function<? super Throwable, ? extends Mono<?>> enforcePostEnforceOnErrorResult(QueryMessage<?, ?> message,
            Optional<Annotation> postEnforceAnnotation) {
        return error -> {
            var postEnforcementAnnotation = (PostHandleEnforce) postEnforceAnnotation.get();
            log.debug("Building a @PostHandlerEnforce PEP (error value) for the query handler of {}. ",
                    message.getPayloadType().getSimpleName());
            var postEnforceAuthzSubscription = subscriptionBuilder.constructAuthorizationSubscriptionForQuery(message,
                    postEnforcementAnnotation, handlerExecutable, Optional.of(error));
            return pdp.decide(postEnforceAuthzSubscription).defaultIfEmpty(AuthorizationDecision.DENY).next()
                    .flatMap(enforcePostEnforceDecisionOnErrorResult(message, error));
        };
    }

    @SuppressWarnings("unchecked")
    private Function<AuthorizationDecision, Mono<Object>> enforcePreEnforceDecision(QueryMessage<?, ?> message,
            T source, Optional<ResponseType<?>> updateType) {
        return decision -> {
            @SuppressWarnings("rawtypes")
            QueryConstraintHandlerBundle constraintHandler;
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
                var error = new AccessDeniedException(ACCESS_DENIED);
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
            QueryConstraintHandlerBundle constraintHandler;
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
                var e = new AccessDeniedException(ACCESS_DENIED);
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
            QueryConstraintHandlerBundle constraintHandler;
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
                var error = new AccessDeniedException(ACCESS_DENIED);
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
        Object result;
        try {
            result = delegate.handle(message, source);
        } catch (Exception e) {
            return Mono.error(e);
        }

        if (result == null)
            return Mono.empty();

        if (CompletableFuture.class.isAssignableFrom(result.getClass())) {
            final var futureResult = (CompletableFuture<?>) result;
            return Mono.fromFuture(() -> futureResult);
        }

        return Mono.just(result);
    }

    private Object handleSubscriptionQuery(QueryMessage<?, ?> message, T source, Optional<ResponseType<?>> updateType)
            throws Exception {
        log.debug("Handling subscription query: {}", message.getPayload());

        if (saplAnnotations.isEmpty()) {
            log.debug("No SAPL annotations on handler. Delegate without policy enforcement");
            emitter.authorizeUpdatesForSubscriptionQueryWithId(message.getIdentifier());
            return delegate.handle(message, source);
        }

        if (!annotationsMatching(saplAnnotations, Set.of(PostHandleEnforce.class)).isEmpty()) {
            log.error("@PostHandleEnforce found while handling a subscription query. This is not allowed."
                    + " Immediately deny access. Consider making sure there are distinct queries and "
                    + "query handlers for normal queries and subscription queries, if your normal query requires "
                    + "@PostHandleEnforce.");
            emitter.immediatelyDenySubscriptionWithId(message.getIdentifier());
            throw new AccessDeniedException(ACCESS_DENIED);
        }

        var streamingAnnotation = uniqueStreamingEnforcementAnnotation();

        var authzSubscription = subscriptionBuilder.constructAuthorizationSubscriptionForQuery(message,
                streamingAnnotation, handlerExecutable, Optional.empty());
        var decisions         = pdp.decide(authzSubscription).defaultIfEmpty(AuthorizationDecision.DENY);
        var tap               = new FluxOneAndManyTap<>(decisions, properties.getSubscriptionQueryDecisionCacheTTL());
        var initialDecision   = tap.one();
        var tappedDecisions   = tap.many();

        log.debug("Set authorization mode of emitter {}", streamingAnnotation.annotationType().getSimpleName());
        emitter.authorizeUpdatesForSubscriptionQueryWithId(message.getIdentifier(), tappedDecisions,
                streamingAnnotation.annotationType());
        log.debug("Build pre-authorization-handler PDP for initial result of subscription query");

        return initialDecision.flatMap(enforcePreEnforceDecision(message, source, updateType)).toFuture();
    }

    private Annotation uniqueStreamingEnforcementAnnotation() {
        Set<Annotation> streamingEnforcementAnnotations = annotationsMatching(saplAnnotations,
                SUBSCRIPTION_ANNOTATIONS);
        if (streamingEnforcementAnnotations.size() != 1)
            throw new IllegalStateException(
                    "The query handler for a streaming query has more than one SAPL annotation which can be used for policy enforcement on subscription queries.");

        return streamingEnforcementAnnotations.stream().findFirst().get();
    }

    private Optional<ResponseType<?>> updateTypeIfSubscriptionQuery(Message<?> message) {
        return emitter.activeSubscriptions().stream().filter(sameAsHandledMessage(message)).findFirst()
                .map(SubscriptionQueryMessage::getUpdateResponseType);
    }

    private Predicate<? super SubscriptionQueryMessage<?, ?, ?>> sameAsHandledMessage(Message<?> message) {
        return sub -> sub.getIdentifier().equals(message.getIdentifier());
    }

    private Set<Annotation> saplAnnotationsOnUnderlyingExecutable() {
        var allAnnotationsOnExecutable = handlerExecutable.getDeclaredAnnotations();
        return Arrays.stream(allAnnotationsOnExecutable).filter(this::isSaplAnnotation)
                .collect(Collectors.toUnmodifiableSet());
    }

    private boolean isSaplAnnotation(Annotation annotation) {
        return SAPL_AXON_ANNOTATIONS.stream().anyMatch(matchesAnnotationType(annotation));
    }

    private Predicate<? super Class<?>> matchesAnnotationType(Annotation annotation) {
        return aSaplAnnotation -> annotation.annotationType().isAssignableFrom(aSaplAnnotation);
    }

    private static Set<Annotation> annotationsMatching(Collection<Annotation> annotations, Set<Class<?>> types) {
        return annotations.stream().filter(annotation -> annotationHasTypeIn(annotation, types))
                .collect(Collectors.toUnmodifiableSet());
    }

    private static boolean annotationHasTypeIn(Annotation annotation, Set<Class<?>> types) {
        return types.stream().anyMatch(type -> annotation.annotationType().isAssignableFrom(type));
    }
}
