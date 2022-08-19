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
package io.sapl.axon.query.updates;

import static java.util.function.Predicate.not;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.queryhandling.GenericSubscriptionQueryUpdateMessage;
import org.axonframework.queryhandling.SubscriptionQueryUpdateMessage;
import org.reactivestreams.Subscription;
import org.springframework.security.access.AccessDeniedException;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.spring.constraints.ConstraintEnforcementService;
import io.sapl.spring.constraints.ReactiveTypeConstraintHandlerBundle;
import lombok.extern.slf4j.Slf4j;
import reactor.core.CoreSubscriber;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

/**
 * The EnforceTillDeniedPolicyEnforcementPoint implements continuous policy
 * enforcement on a Flux resource access point.
 *
 * If the initial decision of the PDP is not PERMIT, an AccessDeniedException is
 * signaled downstream without subscribing to resource access point.
 *
 * After an initial PERMIT, the PEP subscribes to the resource access point and
 * forwards events downstream until a non-PERMIT decision from the PDP is
 * received. Then, an AccessDeniedException is signaled downstream and the PDP
 * and resource access point subscriptions are cancelled.
 *
 * Whenever a decision is received, the handling of obligations and advice are
 * updated accordingly.
 *
 * The PEP does not permit onErrorContinue() downstream.
 *
 * @param <T> type of the FLux contents
 */
@Slf4j
public class EnforceUpdatesTillDeniedPolicyEnforcementPoint<T> extends Flux<SubscriptionQueryUpdateMessage<T>> {

	private final Flux<AuthorizationDecision>                  decisions;
	private Flux<SubscriptionQueryUpdateMessage<T>>            resourceAccessPoint;
	private final ConstraintEnforcementService                 constraintsService;
	private EnforcementSink<SubscriptionQueryUpdateMessage<T>> sink;
	private final ResponseType<?>                              updateResponseType;

	final AtomicReference<Disposable>                             decisionsSubscription = new AtomicReference<>();
	final AtomicReference<Disposable>                             dataSubscription      = new AtomicReference<>();
	final AtomicReference<AuthorizationDecision>                  latestDecision        = new AtomicReference<>();
	final AtomicReference<ReactiveTypeConstraintHandlerBundle<SubscriptionQueryUpdateMessage<T>>> constraintHandler     = new AtomicReference<>();
	final AtomicBoolean                                           stopped               = new AtomicBoolean(false);

	private EnforceUpdatesTillDeniedPolicyEnforcementPoint(Flux<AuthorizationDecision> decisions,
			Flux<SubscriptionQueryUpdateMessage<T>> updateMessageFlux,
			ConstraintEnforcementService constraintHandlerService, ResponseType<?> updateResponseType) {
		this.decisions           = decisions;
		this.resourceAccessPoint = updateMessageFlux;
		this.constraintsService  = constraintHandlerService;
		this.updateResponseType  = updateResponseType;
	}

	public static <U> Flux<SubscriptionQueryUpdateMessage<U>> of(Flux<AuthorizationDecision> decisions,
			Flux<SubscriptionQueryUpdateMessage<U>> updateMessageFlux,
			ConstraintEnforcementService constraintEnforcementService, ResponseType<?> updateResponseType) {
		EnforceUpdatesTillDeniedPolicyEnforcementPoint<U> pep = new EnforceUpdatesTillDeniedPolicyEnforcementPoint<U>(
				decisions, updateMessageFlux, constraintEnforcementService, updateResponseType);
		return (Flux<SubscriptionQueryUpdateMessage<U>>) pep.doOnTerminate(pep::handleOnTerminateConstraints)
				.doAfterTerminate(pep::handleAfterTerminateConstraints)
				.onErrorMap(AccessDeniedException.class, pep::handleAccessDenied).doOnCancel(pep::handleCancel)
				.onErrorStop();
	}

	@Override
	public void subscribe(CoreSubscriber<? super SubscriptionQueryUpdateMessage<T>> subscriber) {
		if (sink != null)
			throw new IllegalStateException("Operator may only be subscribed once.");
		var context = subscriber.currentContext();
		sink                = new EnforcementSink<>();
		resourceAccessPoint = resourceAccessPoint.contextWrite(context);
		Flux.create(sink).subscribe(subscriber);
		decisionsSubscription.set(decisions.doOnNext(this::handleNextDecision).contextWrite(context).subscribe());
	}

	private void handleNextDecision(AuthorizationDecision decision) {
		var                                    previousDecision = latestDecision.getAndSet(decision);
		ReactiveTypeConstraintHandlerBundle<T> newBundle;
		try {
		//	newBundle = constraintsService.reactiveTypeBundleFor(decision, updateResponseType.responseMessagePayloadType()t);
			//constraintHandler.set(newBundle);
		} catch (AccessDeniedException e) {
			constraintHandler.set(new ReactiveTypeConstraintHandlerBundle<>());
			sink.error(e);
			disposeDecisionsAndResourceAccessPoint();
			return;
		}
	//	constraintHandler.get().handleOnDecisionConstraints();

		if (decision.getDecision() != Decision.PERMIT) {
			sink.next((SubscriptionQueryUpdateMessage<T>) GenericSubscriptionQueryUpdateMessage.asUpdateMessage(updateResponseType.getExpectedResponseType(), new AccessDeniedException("Access Denied by PDP")));
			sink.complete();
			disposeDecisionsAndResourceAccessPoint();
			return;
		}

		if (decision.getResource().isPresent()) {
			try {
				sink.next(new GenericSubscriptionQueryUpdateMessage<T>((T) constraintsService.unmarshallResource(decision.getResource().get(), updateResponseType.getExpectedResponseType())));
				
			} catch (JsonProcessingException | IllegalArgumentException e) {
				sink.error(new AccessDeniedException("Error replacing stream with resource. Ending Stream.", e));
			}
			sink.complete();
			disposeDecisionsAndResourceAccessPoint();
		}

		if (previousDecision == null)
			dataSubscription.set(wrapResourceAccessPointAndSubscribe());
	}

	private Disposable wrapResourceAccessPointAndSubscribe() {
		return resourceAccessPoint.doOnError(this::handleError).doOnRequest(this::handleRequest)
				.doOnSubscribe(this::handleSubscribe).doOnNext(this::handleNext).doOnComplete(this::handleComplete)
				.subscribe();
	}

	private void handleSubscribe(Subscription s) {
		try {
		//	constraintHandler.get().handleOnSubscribeConstraints(s);
		} catch (Throwable t) {
			sink.error(t);
			disposeDecisionsAndResourceAccessPoint();
		}
	}

	private void handleOnTerminateConstraints() {
	//	constraintHandler.get().handleOnTerminateConstraints();
	}

	private void handleAfterTerminateConstraints() {
	//	constraintHandler.get().handleAfterTerminateConstraints();
	}

	private void handleNext(SubscriptionQueryUpdateMessage<T> value) {
		// the following guard clause makes sure that the constraint handlers do not get
		// called after downstream consumers cancelled. If the RAP is not consisting of
		// delayed elements, but something like Flux.just(1,2,3) the handler would be
		// called for 2 and 3, even if there was a take(1) applied downstream.
		if (stopped.get())
			return;
		try {
			var transformedValue = value; // constraintHandler.get().handleAllOnNextConstraints(value);
			if (transformedValue != null)
				sink.next(transformedValue);
		} catch (Throwable t) {
			sink.error(t);
			disposeDecisionsAndResourceAccessPoint();
		}
	}

	private void handleRequest(Long value) {
		try {
//			constraintHandler.get().handleOnRequestConstraints(value);
		} catch (Throwable t) {
			sink.error(t);
			disposeDecisionsAndResourceAccessPoint();
		}
	}

	private void handleComplete() {
		if (stopped.get())
			return;
		try {
	//		constraintHandler.get().handleOnCompleteConstraints();
			sink.complete();
		} catch (Throwable t) {
			sink.error(t);
			sink.complete();
		}
		disposeDecisionsAndResourceAccessPoint();
	}

	private void handleCancel() {
		try {
//			constraintHandler.get().handleOnCancelConstraints();
		} catch (Throwable t) {
			log.warn("Failed to handle obligation during onCancel. Error is dropped and Flux is canceled. "
					+ "No information is leaked, however take actions to mitigate error.", t);
		}
		disposeDecisionsAndResourceAccessPoint();
	}

	private void handleError(Throwable error) {
		try {
	//		sink.error(constraintHandler.get().handleAllOnErrorConstraints(error));
			sink.error(error);
		} catch (Throwable t) {
			sink.error(t);
			disposeDecisionsAndResourceAccessPoint();
		}
	}

	private Throwable handleAccessDenied(Throwable error) {
		try {
			return error;
//			return constraintHandler.get().handleAllOnErrorConstraints(error);
		} catch (Throwable t) {
			disposeDecisionsAndResourceAccessPoint();
			return t;
		}
	}

	private void disposeDecisionsAndResourceAccessPoint() {
		stopped.set(true);
		disposeActiveIfPresent(decisionsSubscription);
		disposeActiveIfPresent(dataSubscription);
	}

	private void disposeActiveIfPresent(AtomicReference<Disposable> atomicDisposable) {
		Optional.ofNullable(atomicDisposable.get()).filter(not(Disposable::isDisposed)).ifPresent(Disposable::dispose);
	}

}
