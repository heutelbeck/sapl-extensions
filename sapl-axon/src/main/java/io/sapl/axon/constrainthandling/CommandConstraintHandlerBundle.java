package io.sapl.axon.constrainthandling;

import java.util.function.Function;

import org.axonframework.commandhandling.CommandMessage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class CommandConstraintHandlerBundle<R, T> {
	public static final CommandConstraintHandlerBundle<?, ?> NOOP_BUNDLE = new CommandConstraintHandlerBundle<>();

	protected final Runnable                                       onDecision;
	protected final Function<Throwable, Throwable>                 errorMapper;
	protected final Function<CommandMessage<?>, CommandMessage<?>> commandMapper;
	protected final Function<R, R>                                 resultMapper;

	// @formatter:off
	private CommandConstraintHandlerBundle() {
		this.onDecision = ()->{};
		this.commandMapper = Function.identity();
		this.errorMapper = Function.identity();
		this.resultMapper = Function.identity();
	}
	// @formatter:on

	public void executeOnDecisionHandlers() {
		onDecision.run();
	}

	public Exception executeOnErrorHandlers(Exception t) {
		log.debug("original error: {}", t.getClass().getSimpleName());
		var mapped = errorMapper.apply(t);
		log.debug("mapped   error: {}", mapped.getMessage());
		if (mapped instanceof Exception)
			return (Exception) mapped;
		return new RuntimeException("Error: " + t.getMessage(), t);
	}

	public void executeAggregateConstraintHandlerMethods() {
		log.warn("UNIMPLEMENTED AGGREGATE HANDLERS");
	}

	public CommandMessage<?> executeCommandMappingHandlers(CommandMessage<?> message) {
		return commandMapper.apply(message);
	}

	@SuppressWarnings("unchecked")
	public Object executePostHandlingHandlers(Object value) {
		return resultMapper.apply((R) value);
	}

}
