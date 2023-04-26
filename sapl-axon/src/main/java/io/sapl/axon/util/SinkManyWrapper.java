package io.sapl.axon.util;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import io.grpc.internal.AtomicBackoff;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.Sinks.EmitResult;

@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SinkManyWrapper<T> {

	private static record EmitAction<T> (T data, Throwable error) {
		static <T> EmitAction<T> emitNextAction(T data) {
			return new EmitAction<T>(data, null);
		}

		static <T> EmitAction<T> emitCompleteAction() {
			return new EmitAction<T>(null, null);
		}

		static <T> EmitAction<T> emitErrorAction(Throwable error) {
			return new EmitAction<T>(null, error);
		}

		boolean isNextAction() {
			return data != null && error == null;
		}

		boolean isCompleteAction() {
			return data == null && error == null;
		}

		boolean isErrorAction() {
			return data == null && error != null;
		}
	}

	final Sinks.Many<T> manySink;
	final Queue<EmitAction<T>> actionQueue = new ConcurrentLinkedQueue<>();
	AtomicBackoff backoffValueMs = resetBackoff();
	AtomicBoolean actionSuccess = new AtomicBoolean(true);

	public void emitNext(T data) {
		actionQueue.add(EmitAction.emitNextAction(data));
	}

	public void emitComplete() {
		actionQueue.add(EmitAction.emitCompleteAction());
	}

	public void emitError(Throwable error) {
		actionQueue.add(EmitAction.emitErrorAction(error));
	}

	private boolean handleNextAction() {
		var action = actionQueue.peek();
		if (action == null)
			return false;
		var success = tryPerformEmit(action);
		actionSuccess.set(success);
		if (success) {
			actionQueue.poll();
			resetBackoff();
		} else
			backoff();
		return true;
	}

	private boolean tryPerformEmit(EmitAction<T> action) {
		var result = EmitResult.OK;
		if (action.isNextAction())
			result = manySink.tryEmitNext(action.data());
		if (action.isCompleteAction())
			result = manySink.tryEmitComplete();
		if (action.isErrorAction())
			result = manySink.tryEmitError(action.error());
		return result != EmitResult.FAIL_NON_SERIALIZED;
	}

	private void backoff() {
		backoffValueMs.getState().backoff();
	}

	private AtomicBackoff resetBackoff() {
		return backoffValueMs = new AtomicBackoff("sinkManyBackoff", 1);
	}
}
