package io.sapl.axon.util;

import org.axonframework.queryhandling.SinkWrapper;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import reactor.core.publisher.Sinks;

/**
 * Wrapper around {@link reactor.core.publisher.Sinks.Many} for busy looping.
 *
 * @param <T> The value type
 * @author Dominic Heutelbeck
 * @since 2.1.0
 */
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SinkManyWrapper<T> implements SinkWrapper<T> {

	Sinks.Many<T> fluxSink;

	/**
	 *
	 */
	@Override
	public void complete() {
		Sinks.EmitResult result;
		// noinspection StatementWithEmptyBody
		while ((result = fluxSink.tryEmitComplete()) == Sinks.EmitResult.FAIL_NON_SERIALIZED) {
			// busy spin
		}
		result.orThrow();
	}

	/**
	 * Wrapper around {@link reactor.core.publisher.Sinks.Many#tryEmitNext(Object)}. Throws exception on
	 * failure cases.
	 *
	 * @param value to be passed to the delegate sink
	 */
	@Override
	public void next(T value) {
		Sinks.EmitResult result;
		// noinspection StatementWithEmptyBody
		while ((result = fluxSink.tryEmitNext(value)) == Sinks.EmitResult.FAIL_NON_SERIALIZED) {
			// busy spin
		}
		result.orThrow();
	}

	/**
	 * Wrapper around {@link reactor.core.publisher.Sinks.Many#tryEmitError(Throwable)}. Throws exception
	 * on failure cases.
	 *
	 * @param t to be passed to the delegate sink
	 */
	@Override
	public void error(Throwable t) {
		Sinks.EmitResult result;
		// no inspection StatementWithEmptyBody
		while ((result = fluxSink.tryEmitError(t)) == Sinks.EmitResult.FAIL_NON_SERIALIZED) {
			// busy spin
		}
		result.orThrow();

	}
}
