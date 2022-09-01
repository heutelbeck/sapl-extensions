package io.sapl.axon.queryhandling;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.Sinks.Many;
import reactor.core.publisher.Sinks.One;

/**
 * This class wraps a source {@code Flux<T>} and provides two reactive accessors to the
 * Flux.
 * 
 * The first accessor is a {@code Mono<T>} and the second accessor is a {@code Flux<T>}.
 * 
 * The purpose is to avoid the creation of two independent subscriptions to the
 * source when these are subscribed to in a short period of time.
 * 
 * The primary use-case is the sharing of a {@code Flux<AuthorizationDecision>} between
 * the PEP in the {@code @QueryHandler} producing the initial result and the PEP
 * in the UpdateEmitter.
 * 
 * Axon does not enforce that the initial result and the updates are subscribed
 * both or in a specific sequence.
 * 
 * The FluxOneAndManyTap keeps the connection to the source {@code Flux} alive and
 * caches the last event of the source for the provided TTL.
 * 
 * If one of the accessors is subscribed to after the other within the set TTL,
 * the source is only subscribed to once, and a cached value of propagated
 * first, if available.
 * 
 * IF the second subscription does occurs later than the TTL after the end of
 * the first accessor subscription, the connection is dropped after TTL and the
 * source is newly subscribed to.
 * 
 * @author Dominic Heutelbeck
 * @since 2.1.0
 * 
 * @param <T> The type of the Flux payload.
 */
public class FluxOneAndManyTap<T> {

	private final Flux<T>  source;
	private final Duration connectionTtl;

	AtomicReference<T>          cache            = new AtomicReference<>();
	AtomicReference<Disposable> sourceDisposable = new AtomicReference<>();
	AtomicReference<One<T>>     oneSink          = new AtomicReference<>();
	AtomicReference<Many<T>>    manySink         = new AtomicReference<>();
	boolean                     oneCalled        = false;
	AtomicBoolean               oneSubscribed    = new AtomicBoolean(false);
	boolean                     manyCalled       = false;
	AtomicBoolean               manySubscribed   = new AtomicBoolean(false);

	/**
	 * Create a FluxOneAndManyTap
	 * 
	 * @param source        The source Flux.
	 * @param connectionTtl Time-to-live for connection to source and cache value.
	 */
	public FluxOneAndManyTap(Flux<T> source, Duration connectionTtl) {
		this.source        = source;
		this.connectionTtl = connectionTtl;
	}

	/**
	 * @return A single value of the source.
	 */
	public Mono<T> one() {
		if (oneCalled)
			throw new IllegalStateException("one() may only be called once!");

		oneCalled = true;

		var sink = Sinks.<T>one();

		return sink.asMono().doOnSubscribe(sub -> {
			/*
			 * Ensure the mono is only consumed once.
			 */
			if (oneSubscribed.getAndSet(true)) {
				throw new IllegalStateException("the one Mono may only be subscribed to once!");
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
			oneSink.set(sink);

			subscribeIfNoActiveSubscriptionToSourcePresent();
		}).doFinally(signal -> {
			oneSink.set(null); // remove the sink as a listener for updates to the source

			if (manySubscribed.get())
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
			if (manySink.get() == null)
				dropConnectionAndClearCache();
		}).subscribe();
	}

	/**
	 * @return Full subscription to the source.
	 */
	public Flux<T> many() {
		if (manyCalled)
			throw new IllegalStateException("many() may only be called once!");

		manyCalled = true;

		/*
		 * Unicast ensured, that this can only be subscribed to once. No additional
		 * check necessary
		 */
		Many<T> sink = Sinks.many().unicast().onBackpressureBuffer();

		return sink.asFlux().doOnSubscribe(sub -> {
			/*
			 * Check of there has been a cached value.
			 */
			var cachedDecision = cache.get();
			if (cachedDecision != null) {
				sink.tryEmitNext(cachedDecision);
			}

			manySink.set(sink);

			subscribeIfNoActiveSubscriptionToSourcePresent();
		}).doFinally(signal -> {
			// remove the sink as a listener for updates to the source
			manySink.set(null);

			if (oneSubscribed.get())
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
			if (oneSink.get() == null)
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
		return source.doOnNext(v -> cache.set(v)).doOnNext(v -> {
			cache.set(v);
			updateOne(sink -> sink.tryEmitValue(v));
			updateMany(sink -> sink.tryEmitNext(v));
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

	private void updateOne(Consumer<One<T>> update) {
		oneSink.getAndUpdate(sink -> {
			/*
			 * If present the oneDecisionSink is a one-time-use object. Emit the update and
			 * remove it.
			 */
			if (sink != null)
				update.accept(sink);
			return null;
		});
	}

	private void updateMany(Consumer<Many<T>> update) {
		manySink.getAndUpdate(sink -> {
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
