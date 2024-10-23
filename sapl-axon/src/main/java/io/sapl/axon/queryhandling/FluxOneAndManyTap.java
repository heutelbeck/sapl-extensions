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
import reactor.util.concurrent.Queues;

/**
 * This class wraps a source {@code Flux<T>} and provides two reactive accessors
 * to the Flux.
 * <p>
 * The first accessor is a {@code Mono<T>} and the second accessor is a
 * {@code Flux<T>}.
 * <p>
 * The purpose is to avoid the creation of two independent subscriptions to the
 * source when these are subscribed to in a short period of time.
 * <p>
 * The primary use-case is the sharing of a {@code Flux<AuthorizationDecision>}
 * between the PEP in the {@code @QueryHandler} producing the initial result and
 * the PEP in the UpdateEmitter.
 * <p>
 * Axon does not enforce that the initial result and the updates are subscribed
 * both or in a specific sequence.
 * <p>
 * The FluxOneAndManyTap keeps the connection to the source {@code Flux} alive
 * and caches the last event of the source for the provided TTL.
 * <p>
 * If one of the accessors is subscribed to after the other within the set TTL,
 * the source is only subscribed to once, and a cached value of propagated
 * first, if available.
 * <p>
 * IF the second subscription occurs later than the TTL after the end of the
 * first accessor subscription, the connection is dropped after TTL and the
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

    private final AtomicReference<T>          cache            = new AtomicReference<>();
    private final AtomicReference<Disposable> sourceDisposable = new AtomicReference<>();
    private final AtomicReference<One<T>>     oneSink          = new AtomicReference<>();
    private final AtomicReference<Many<T>>    manySink         = new AtomicReference<>();
    private boolean                           oneCalled        = false;
    private final AtomicBoolean               oneSubscribed    = new AtomicBoolean(false);
    private boolean                           manyCalled       = false;
    private final AtomicBoolean               manySubscribed   = new AtomicBoolean(false);

    /**
     * Create a FluxOneAndManyTap
     *
     * @param source The source Flux.
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
             * Check if there has been a cached value in the meantime.
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
            oneSubscribed.set(false);

            if (manySubscribed.get())
                return;

            scheduleConnectionAndCacheTimeoutForOneDecision();
        });
    }

    private void scheduleConnectionAndCacheTimeoutForOneDecision() {
        /*
         * if the Flux has not been subscribed to yet, keep the connection alive and
         * cache valid for the given TTL
         */
        Mono.just(0).delayElement(connectionTtl).doOnNext(i -> {
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
         * Unicast ensured, that this can only be subscribed once. No additional check
         * necessary
         */
        Many<T> sink = Sinks.many().unicast().onBackpressureBuffer(Queues.<T>one().get());

        return sink.asFlux().doOnSubscribe(sub -> {
            /*
             * Check if there has been a cached value.
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
            manySubscribed.set(false);

            if (oneSubscribed.get())
                return;

            scheduleConnectionAndCacheTimeoutForManyDecisions();
        });
    }

    private void scheduleConnectionAndCacheTimeoutForManyDecisions() {
        /*
         * if the Mono has not been subscribed to yet, keep the connection alive and
         * cache valid for the given TTL
         */
        Mono.just(0).delayElement(connectionTtl).doOnNext(i -> {
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
        return source.doOnNext(cache::set).doOnNext(v -> {
            cache.set(v);
            updateOne(sink -> sink.tryEmitValue(v));
            updateMany(sink -> sink.tryEmitNext(v));
        }).doOnError(error -> {
            updateOne(sink -> sink.tryEmitError(error));
            updateMany(sink -> sink.tryEmitError(error));
        }).doFinally(signal -> {
            if (signal == SignalType.ON_ERROR) // Has been taken care of by doOnError
                return;

            updateOne(Sinks.Empty::tryEmitEmpty);
            updateMany(Many::tryEmitComplete);
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

            return subscribeToSource();
        });
    }

}
