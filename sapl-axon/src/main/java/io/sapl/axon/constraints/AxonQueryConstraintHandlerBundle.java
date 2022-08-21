package io.sapl.axon.constraints;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

import org.axonframework.queryhandling.QueryMessage;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class AxonQueryConstraintHandlerBundle<T> {
	protected final Runnable                                         onDecisionHandlers;
	protected final Function<QueryMessage<?, ?>, QueryMessage<?, ?>> queryMappingHandlers;
	protected final Consumer<QueryMessage<?, ?>>                     messageConsumerHandlers;
	protected final Function<Throwable, Throwable>                   errorMappingHandlers;
	protected final Consumer<Throwable>                              errorConsumerHandlers;
	protected final Function<T, T>                                   resultMappingHandlers;
	protected final Consumer<T>                                      resultConsumerHandlers;

	public void executeOnDecisionHandlers() {
		onDecisionHandlers.run();
	}

	public Throwable executeOnErrorHandlers(Throwable t) {
		errorConsumerHandlers.accept(t);
		return errorMappingHandlers.apply(t);
	}

	public QueryMessage<?, ?> executePreHandlingHandlers(QueryMessage<?, ?> message) {
		messageConsumerHandlers.accept(message);
		return queryMappingHandlers.apply(message);
	}

	@SuppressWarnings("unchecked") // The handlers have been validated to support the returnType
	public Object executePostHandlingHandlers(Object o) {
		if (o instanceof CompletableFuture) {
			return ((CompletableFuture<T>) o).thenApply(this::executePostHandlingHandlers);
		}
		resultConsumerHandlers.accept((T) o);
		return resultMappingHandlers.apply((T) o);
	}
}
