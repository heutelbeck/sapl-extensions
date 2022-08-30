package io.sapl.axon.constrainthandling;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;

import org.axonframework.messaging.Message;
import org.axonframework.messaging.ResultMessage;
import org.axonframework.queryhandling.QueryMessage;

import io.sapl.api.pdp.AuthorizationDecision;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class QueryConstraintHandlerBundle<I, U> {
	public static final QueryConstraintHandlerBundle<?, ?> NOOP_BUNDLE = new QueryConstraintHandlerBundle<>();

	private final BiConsumer<AuthorizationDecision, Message<?>>    onDecisionHandlers;
	private final Function<QueryMessage<?, ?>, QueryMessage<?, ?>> queryMappers;
	private final Function<Throwable, Throwable>                   errorMappers;
	private final Function<I, I>                                   initialResultMappers;
	private final Function<ResultMessage<?>, ResultMessage<?>>     updateMappers;
	private final Predicate<ResultMessage<?>>                      filterPredicates;

	// @formatter:off
	private QueryConstraintHandlerBundle() {
		this.onDecisionHandlers = (__,___)->{};
		this.queryMappers       = Function.identity();
		this.errorMappers       = Function.identity();
		this.initialResultMappers      = Function.identity();
		this.updateMappers      = Function.identity();
		this.filterPredicates   = __ -> true;
	}
	// @formatter:on

	public void executeOnDecisionHandlers(AuthorizationDecision decision, Message<?> message) {
		onDecisionHandlers.accept(decision, message);
	}

	public Throwable executeOnErrorHandlers(Throwable t) {
		return errorMappers.apply(t);
	}

	public QueryMessage<?, ?> executePreHandlingHandlers(QueryMessage<?, ?> message) {
		return queryMappers.apply(message);
	}

	@SuppressWarnings("unchecked") // The handlers have been validated to support the returnType
	public Object executePostHandlingHandlers(Object o) {
		if (o instanceof CompletableFuture) {
			return ((CompletableFuture<?>) o).thenApply(this::executePostHandlingHandlers);
		}

		return initialResultMappers.apply((I) o);
	}

	public Optional<ResultMessage<?>> executeOnNextHandlers(ResultMessage<?> message) {
		var updated = updateMappers.apply(message);

		if (!filterPredicates.test(message))
			return Optional.empty();

		return Optional.of(updated);
	}
}
