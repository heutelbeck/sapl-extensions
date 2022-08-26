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
package io.sapl.axon.queryhandling;

import static java.util.function.Predicate.not;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.axonframework.messaging.GenericResultMessage;
import org.axonframework.messaging.ResultMessage;
import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.queryhandling.GenericSubscriptionQueryUpdateMessage;
import org.axonframework.queryhandling.SubscriptionQueryMessage;
import org.axonframework.queryhandling.SubscriptionQueryUpdateMessage;
import org.springframework.security.access.AccessDeniedException;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.axon.constrainthandling.ConstraintHandlerService;
import io.sapl.axon.constrainthandling.QueryConstraintHandlerBundle;
import reactor.core.CoreSubscriber;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

/**
 * The EnforceDropWhileDeniedPolicyEnforcementPoint implements continuous policy
 * enforcement on a Flux resource access point.
 *
 * After an initial PERMIT, the PEP subscribes to the resource access point and
 * forwards events downstream until a non-PERMIT decision from the PDP is
 * received. Then, all events are dropped until a new PERMIT signal arrives.
 *
 * Whenever a decision is received, the handling of obligations and advice are
 * updated accordingly.
 *
 * The PEP does not permit onErrorContinue() downstream.
 *
 * @param <T> type of the payload
 */
public class EnforceRecoverableIfDeniedPolicyEnforcementPoint<T>
		extends Flux<SubscriptionQueryUpdateMessage<RecoverableResponse<T>>> {
	
	private final SubscriptionQueryMessage<?, ?, ?> query;
	private final Flux<AuthorizationDecision>       decisions;
	private Flux<SubscriptionQueryUpdateMessage<T>> resourceAccessPoint;
	private final ConstraintHandlerService          constraintHandlerService;
	private final ResponseType<?>                   resultResponseType;
	private final ResponseType<T>                   updateResponseType;

	private EnforcementSink<SubscriptionQueryUpdateMessage<RecoverableResponse<T>>> sink;

	final AtomicReference<Disposable>                         decisionsSubscription = new AtomicReference<>();
	final AtomicReference<Disposable>                         dataSubscription      = new AtomicReference<>();
	final AtomicReference<AuthorizationDecision>              latestDecision        = new AtomicReference<>();
	final AtomicReference<QueryConstraintHandlerBundle<?, ?>> constraintHandler     = new AtomicReference<>();
	final AtomicBoolean                                       stopped               = new AtomicBoolean(false);

	private EnforceRecoverableIfDeniedPolicyEnforcementPoint(SubscriptionQueryMessage<?, ?, ?> query,Flux<AuthorizationDecision> decisions,
			Flux<SubscriptionQueryUpdateMessage<T>> resourceAccessPoint,
			ConstraintHandlerService constraintHandlerService, ResponseType<?> resultResponseType,
			ResponseType<T> updateResponseType) {
		this.query                    = query;
		this.decisions                = decisions;
		this.resourceAccessPoint      = resourceAccessPoint;
		this.constraintHandlerService = constraintHandlerService;
		this.updateResponseType       = updateResponseType;
		this.resultResponseType       = resultResponseType;
	}

	public static <V> Flux<SubscriptionQueryUpdateMessage<RecoverableResponse<V>>> of(SubscriptionQueryMessage<?, ?, ?> query,
			Flux<AuthorizationDecision> decisions, Flux<SubscriptionQueryUpdateMessage<V>> resourceAccessPoint,
			ConstraintHandlerService constraintHandlerService, ResponseType<?> resultResponseType,
			ResponseType<V> originalUpdateResponseType) {
		var pep = new EnforceRecoverableIfDeniedPolicyEnforcementPoint<V>(query,decisions, resourceAccessPoint,
				constraintHandlerService, resultResponseType, originalUpdateResponseType);
		return pep.doOnCancel(pep::handleCancel).onErrorStop();
	}

	@Override
	public void subscribe(CoreSubscriber<? super SubscriptionQueryUpdateMessage<RecoverableResponse<T>>> actual) {
		if (sink != null)
			throw new IllegalStateException("Operator may only be subscribed once.");
		var context = actual.currentContext();
		sink                = new EnforcementSink<>();
		resourceAccessPoint = resourceAccessPoint.contextWrite(context);
		Flux.create(sink).subscribe(actual);
		decisionsSubscription.set(decisions.doOnNext(this::handleNextDecision).contextWrite(context).subscribe());
	}

	private SubscriptionQueryUpdateMessage<RecoverableResponse<T>> newAccessDeniedUpdate() {
		return new GenericSubscriptionQueryUpdateMessage<>(RecoverableResponse.accessDenied(updateResponseType));
	}

	private SubscriptionQueryUpdateMessage<RecoverableResponse<T>> wrapPayloadInRecoverableResponse(
			ResultMessage<T> msg) {
		return GenericSubscriptionQueryUpdateMessage.asUpdateMessage(new GenericResultMessage<>(
				new RecoverableResponse<>(updateResponseType, msg.getPayload()), msg.getMetaData()));
	}

	private void handleNextDecision(AuthorizationDecision decision) {

		var                                implicitDecision = decision;
		QueryConstraintHandlerBundle<?, ?> newBundle        = QueryConstraintHandlerBundle.NOOP_BUNDLE;

		try {
			newBundle = constraintHandlerService.buildQueryPreHandlerBundle(decision, resultResponseType,
					Optional.of(updateResponseType));
		} catch (AccessDeniedException e) {
			sink.next(newAccessDeniedUpdate());
			// INDETERMINATE -> as long as we cannot handle the obligations of the current
			// decision, drop data
			implicitDecision = AuthorizationDecision.INDETERMINATE;
		}

		constraintHandler.set(newBundle);

		if (implicitDecision.getDecision() != Decision.PERMIT)
			sink.next(newAccessDeniedUpdate());

		try {
			newBundle.executeOnDecisionHandlers(decision,query);
		} catch (AccessDeniedException e) {
			sink.next(newAccessDeniedUpdate());
			implicitDecision = AuthorizationDecision.INDETERMINATE;
		}

		latestDecision.set(implicitDecision);

		if (implicitDecision.getResource().isPresent()) {
			try {
				T newResponse = constraintHandlerService.deserializeResource(implicitDecision.getResource().get(),
						updateResponseType);
				sink.next(new GenericSubscriptionQueryUpdateMessage<RecoverableResponse<T>>(
						new RecoverableResponse<>(updateResponseType, newResponse)));
				disposeDecisionsAndResourceAccessPoint();
			} catch (AccessDeniedException e) {
				sink.next(newAccessDeniedUpdate());
				implicitDecision = AuthorizationDecision.INDETERMINATE;
			}
		}

		if (implicitDecision.getDecision() == Decision.PERMIT && dataSubscription.get() == null)
			dataSubscription.set(wrapResourceAccessPointAndSubscribe());
	}

	private Disposable wrapResourceAccessPointAndSubscribe() {
		return resourceAccessPoint.doOnError(this::handleError).doOnNext(this::handleNext)
				.doOnComplete(this::handleComplete).subscribe();
	}

	@SuppressWarnings("unchecked")
	private void handleNext(SubscriptionQueryUpdateMessage<T> value) {
		// the following guard clause makes sure that the constraint handlers do not get
		// called after downstream consumers cancelled. If the RAP is not consisting of
		// delayed elements, but something like Flux.just(1,2,3) the handler would be
		// called for 2 and 3, even if there was a take(1) applied downstream.
		if (stopped.get())
			return;

		var decision = latestDecision.get();

		if (decision.getDecision() != Decision.PERMIT)
			return;

		// drop elements while the last decision replaced data with resource
		if (decision.getResource().isPresent())
			return;

		try {
			var bundle = constraintHandler.get();
			bundle.executeOnNextHandlers(value)
					.ifPresent(val -> sink.next(wrapPayloadInRecoverableResponse((ResultMessage<T>) val)));
		} catch (Throwable t) {
			// Signal error but drop only the element with the failed obligation
			// doing handleNextDecision(AuthorizationDecision.DENY); would drop all
			// subsequent messages, even if the constraint handler would succeed on then.
			// Do not signal original error, as this may leak information in message.
			sink.error(new AccessDeniedException("Failed to handle onNext obligation."));
		}
	}

	private void handleComplete() {
		if (stopped.get())
			return;
		sink.complete();
		disposeDecisionsAndResourceAccessPoint();
	}

	private void handleCancel() {

		disposeDecisionsAndResourceAccessPoint();
	}

	private void handleError(Throwable error) {
		try {
			sink.error(constraintHandler.get().executeOnErrorHandlers(error));
		} catch (Throwable t) {
			sink.error(t);
			handleNextDecision(AuthorizationDecision.INDETERMINATE);
			disposeDecisionsAndResourceAccessPoint();
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
