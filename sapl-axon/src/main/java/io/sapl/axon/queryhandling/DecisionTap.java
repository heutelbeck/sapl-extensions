package io.sapl.axon.queryhandling;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import io.sapl.api.pdp.AuthorizationDecision;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.Sinks.Many;
import reactor.core.publisher.Sinks.One;

public class DecisionTap {

	private final Flux<AuthorizationDecision> source;
	private final Duration                    connectionTtl;
	boolean                                   oneDecisionCalled = false;
	boolean                                   decisionsCalled   = false;

	AtomicReference<AuthorizationDecision>       cache                 = new AtomicReference<>();
	AtomicReference<Disposable>                  sourceDisposable      = new AtomicReference<>();
	AtomicReference<One<AuthorizationDecision>>  oneDecisionSink       = new AtomicReference<>();
	AtomicReference<Many<AuthorizationDecision>> manyDecisionsSink     = new AtomicReference<>();
	AtomicBoolean                                oneDecisionSubscribed = new AtomicBoolean(false);
	AtomicBoolean                                decisionsSubscribed   = new AtomicBoolean(false);

	public DecisionTap(Flux<AuthorizationDecision> source, Duration connectionTtl) {
		this.source        = source;
		this.connectionTtl = connectionTtl;
	}

	public Mono<AuthorizationDecision> oneDecision() {
		if (oneDecisionCalled)
			throw new IllegalStateException("oneDecision() may only be called once!");

		oneDecisionCalled = true;

		var sink = Sinks.<AuthorizationDecision>one();

		return sink.asMono().doOnSubscribe(sub -> {
			/*
			 * Ensure the mono is only consumed once.
			 */
			if (oneDecisionSubscribed.getAndSet(true)) {
				throw new IllegalStateException("the oneDecision Mono may only be subscribed to once!");
			}
			/*
			 * Check of there has been a cached value in the meantime.
			 */
			var cachedDecision = cache.get();
			if (cachedDecision != null) {
				sink.tryEmitValue(cachedDecision);
				return;
			}
			/*
			 * No cache so far. Publish the sink.
			 */
			oneDecisionSink.set(sink);

			subscribeIfNoActiveSubscriptionToSourcePresent();
		}).doFinally(signal -> {
			oneDecisionSink.set(null); // remove the sink as a listener for updates to the source

			if (decisionsSubscribed.get())
				return;

			scheduleConnectionAndCacheTimeoutForOneDecision();
		});
	}

	private void scheduleConnectionAndCacheTimeoutForOneDecision() {
		/*
		 * if the Flux has not been subscribed to yet, keep the connection alive and
		 * cache valid for the given ttl
		 */
		Mono.just(0).delayElement(connectionTtl).doOnNext(__ -> {
			if (manyDecisionsSink.get() == null)
				dropConnectionAndClearCache();
		}).subscribe();
	}

	public Flux<AuthorizationDecision> decisions() {
		if (decisionsCalled)
			throw new IllegalStateException("decisions() may only be called once!");

		decisionsCalled = true;

		/*
		 * Unicast ensured, that this can only be subscribed to once. No additional
		 * check necessary
		 */
		Many<AuthorizationDecision> sink = Sinks.many().unicast().onBackpressureBuffer();

		return sink.asFlux().doOnSubscribe(sub -> {
			/*
			 * Check of there has been a cached value.
			 */
			var cachedDecision = cache.get();
			if (cachedDecision != null) {
				sink.tryEmitNext(cachedDecision);
			}

			manyDecisionsSink.set(sink);

			subscribeIfNoActiveSubscriptionToSourcePresent();
		}).doFinally(signal -> {
			// remove the sink as a listener for updates to the source
			manyDecisionsSink.set(null);

			if (oneDecisionSubscribed.get())
				return;

			scheduleConnectionAndCacheTimeoutForManyDecisions();
		});
	}

	private void scheduleConnectionAndCacheTimeoutForManyDecisions() {
		/*
		 * if the Mono has not been subscribed to yet, keep the connection alive and
		 * cache valid for the given ttl
		 */
		Mono.just(0).delayElement(connectionTtl).doOnNext(__ -> {
			if (oneDecisionSink.get() == null)
				dropConnectionAndClearCache();
		}).subscribe();
	}

	private void dropConnectionAndClearCache() {
		/*
		 * If after the timeout, the Mono is not subscribed, dispose of the PDP
		 * connection.
		 */
		sourceDisposable.getAndUpdate(disposable -> {
			if (disposable != null)
				disposable.dispose();
			cache.set(null);
			return null;
		});
	}

	private Disposable subscribeToSource() {
		return source.doOnNext(decision -> cache.set(decision)).doOnNext(decision -> {
			cache.set(decision);
			updateOne(sink -> sink.tryEmitValue(decision));
			updateMany(sink -> sink.tryEmitNext(decision));
		}).doOnError(error -> {
			updateOne(sink -> sink.tryEmitError(error));
			updateMany(sink -> sink.tryEmitError(error));
		}).doFinally(signal -> {
			if (signal == SignalType.ON_ERROR) // Has been taken care of by doOnError
				return;

			updateOne(sink -> sink.tryEmitEmpty());
			updateMany(sink -> sink.tryEmitComplete());
		}).subscribe();
	}

	private void updateOne(Consumer<One<AuthorizationDecision>> update) {
		oneDecisionSink.getAndUpdate(sink -> {
			/*
			 * If present the oneDecisionSink is a one-time-use object. Emit the update and
			 * remove it.
			 */
			if (sink != null)
				update.accept(sink);
			return null;
		});
	}

	private void updateMany(Consumer<Many<AuthorizationDecision>> update) {
		manyDecisionsSink.getAndUpdate(sink -> {
			if (sink != null)
				update.accept(sink);
			return sink;
		});
	}

	private void subscribeIfNoActiveSubscriptionToSourcePresent() {
		sourceDisposable.getAndUpdate(d -> {
			/*
			 * If the sourceDisposable is already set, the source is currently already
			 * subscribed to and waiting for updates.
			 */
			if (d != null)
				return d;

			var newDisposable = subscribeToSource();
			return newDisposable;
		});
	}

}
