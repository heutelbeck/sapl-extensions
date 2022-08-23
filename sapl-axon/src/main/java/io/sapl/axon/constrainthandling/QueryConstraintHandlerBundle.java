package io.sapl.axon.constrainthandling;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Predicate;

import org.axonframework.messaging.ResultMessage;
import org.axonframework.queryhandling.QueryMessage;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class QueryConstraintHandlerBundle<I, U> {
	public static final QueryConstraintHandlerBundle<?, ?> NOOP_BUNDLE = new QueryConstraintHandlerBundle<>();

	protected final Runnable                                         onDecision;
	protected final Function<QueryMessage<?, ?>, QueryMessage<?, ?>> queryMapper;
	protected final Function<Throwable, Throwable>                   errorMapper;
	protected final Function<I, I>                                   resultMapper;
	protected final Function<ResultMessage<?>, ResultMessage<?>>     updateMapper;
	protected final Predicate<ResultMessage<?>>                      filterPredicate;

	// @formatter:off
	private QueryConstraintHandlerBundle() {
		this.onDecision = ()->{};
		this.queryMapper = Function.identity();
		this.errorMapper = Function.identity();
		this.resultMapper = Function.identity();
		this.updateMapper = Function.identity();
		this.filterPredicate = __ -> true;
	}
	// @formatter:on

	public void executeOnDecisionHandlers() {
		onDecision.run();
	}

	public Throwable executeOnErrorHandlers(Throwable t) {
		return errorMapper.apply(t);
	}

	public QueryMessage<?, ?> executePreHandlingHandlers(QueryMessage<?, ?> message) {
		return queryMapper.apply(message);
	}

	@SuppressWarnings("unchecked") // The handlers have been validated to support the returnType
	public Object executePostHandlingHandlers(Object o) {
		if (o instanceof CompletableFuture) {
			return ((CompletableFuture<?>) o).thenApply(this::executePostHandlingHandlers);
		}

		return resultMapper.apply((I) o);
	}

	public Optional<ResultMessage<?>> executeOnNextHandlers(ResultMessage<?> message) {
		var updated = updateMapper.apply(message);

		if (!filterPredicate.test(message))
			return Optional.empty();

		return Optional.of(updated);
	}
}
