package io.sapl.axon.constraints;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
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
	protected final Consumer<QueryMessage<?, ?>>                     queryConsumer;
	protected final Function<Throwable, Throwable>                   errorMapper;
	protected final Consumer<Throwable>                              errorConsumer;
	protected final Function<I, I>                                   resultMapper;
	protected final Consumer<I>                                      resultConsumer;
	protected final Function<ResultMessage<?>, ResultMessage<?>>     updateMapper;
	protected final Consumer<ResultMessage<?>>                       updateConsumer;
	protected final Predicate<ResultMessage<?>>                      filterPredicate;

	// @formatter:off
	private QueryConstraintHandlerBundle() {
		this.onDecision = ()->{};
		this.queryMapper = Function.identity();
		this.queryConsumer = __ -> {};
		this.errorMapper = Function.identity();
		this.errorConsumer = __ -> {};
		this.resultMapper = Function.identity();
		this.resultConsumer = __ -> {};
		this.updateMapper = Function.identity();
		this.updateConsumer = __ -> {};
		this.filterPredicate = __ -> true;
	}
	// @formatter:on

	public void executeOnDecisionHandlers() {
		onDecision.run();
	}

	public Throwable executeOnErrorHandlers(Throwable t) {
		errorConsumer.accept(t);
		return errorMapper.apply(t);
	}

	public QueryMessage<?, ?> executePreHandlingHandlers(QueryMessage<?, ?> message) {
		queryConsumer.accept(message);
		return queryMapper.apply(message);
	}

	@SuppressWarnings("unchecked") // The handlers have been validated to support the returnType
	public Object executePostHandlingHandlers(Object o) {
		if (o instanceof CompletableFuture) {
			return ((CompletableFuture<?>) o).thenApply(this::executePostHandlingHandlers);
		}

		resultConsumer.accept((I) o);
		return resultMapper.apply((I) o);
	}

	public Optional<ResultMessage<?>> executeOnNextHandlers(ResultMessage<?> message) {
		var updated = updateMapper.apply(message);

		if (!filterPredicate.test(message))
			return Optional.empty();

		updateConsumer.accept(updated);
		return Optional.of(updated);
	}
}
