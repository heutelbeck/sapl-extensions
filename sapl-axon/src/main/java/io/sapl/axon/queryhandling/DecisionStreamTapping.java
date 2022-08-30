package io.sapl.axon.queryhandling;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.Sinks.Many;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

/**
 * Utility class for splitting off a decision stream into two for the initial
 * result and the updates of a subscription query.
 * 
 * @author Dominic Heutelbeck
 * @since 2.1.0
 */
@Slf4j
@UtilityClass
public class DecisionStreamTapping {

	private enum SubcriptionState {
		NONE, SUBSCRIBED, CANCELLED
	}

	@Data
	@AllArgsConstructor
	private static class State {
		SubcriptionState initial;
		SubcriptionState updates;
	}

	/**
	 * Splits off a decision stream into two for the initial result and the updates
	 * of a subscription query.
	 * 
	 * @param <V>     Type of the Flux contents.
	 * @param source  The original Flux.
	 * @param timeout A timeout which terminates the stream if the two streams are
	 *                not subscribed to.
	 * @return A Tuple2 containing the split Mono and Flux.
	 */
	public static <V> Tuple2<Mono<V>, Flux<V>> tapForInitialValue(Flux<V> source, Duration timeout) {
		var multicastSink = Sinks.many().replay().<V>limit(1);
		var tappedSource  = source.doOnNext(v -> multicastSink.tryEmitNext(v)).subscribe();
		var state         = new State(SubcriptionState.NONE, SubcriptionState.NONE);
		var stateRef      = new AtomicReference<State>(state);
		var multicastFlux = multicastSink.asFlux();

		var initialMono = multicastFlux.next()
				.doOnSubscribe(__ -> stateRef.getAndUpdate(s -> new State(SubcriptionState.SUBSCRIBED, s.updates)))
				.doAfterTerminate(checkInitialTermination(multicastSink, tappedSource, stateRef));

		var updatesFlux = multicastFlux
				.doOnSubscribe(__ -> stateRef.getAndUpdate(s -> new State(s.initial, SubcriptionState.SUBSCRIBED)))
				.doOnCancel(checkUpdateTermination(multicastSink, tappedSource, stateRef))
				.doAfterTerminate(checkUpdateTermination(multicastSink, tappedSource, stateRef));

		Flux.interval(timeout).next().doOnNext(__ -> {
			var s = stateRef.get();
			if (s.initial == SubcriptionState.NONE || s.updates == SubcriptionState.NONE) {
				log.warn("Timeout! Decisions were not subscribed to cancel PDP subscription.");
				tappedSource.dispose();
			}
		}).subscribe();
		return Tuples.of(initialMono, updatesFlux.log());
	}

	private static <V> Runnable checkUpdateTermination(Many<V> multicastSink, Disposable tappedSource,
			AtomicReference<State> stateRef) {
		return () -> stateRef.getAndUpdate(s -> {
			if (s.initial == SubcriptionState.CANCELLED) {
				tappedSource.dispose();
				multicastSink.tryEmitComplete();
			}
			return new State(s.initial, SubcriptionState.CANCELLED);
		});
	}

	private static <V> Runnable checkInitialTermination(Many<V> multicastSink, Disposable tappedSource,
			AtomicReference<State> stateRef) {
		return () -> stateRef.getAndUpdate(s -> {
			if (s.updates == SubcriptionState.CANCELLED) {
				tappedSource.dispose();
				multicastSink.tryEmitComplete();
			}
			return new State(SubcriptionState.CANCELLED, s.updates);
		});
	}
}
