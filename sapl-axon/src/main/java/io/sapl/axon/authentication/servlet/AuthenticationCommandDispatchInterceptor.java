package io.sapl.axon.authentication.servlet;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.messaging.MessageDispatchInterceptor;

import lombok.RequiredArgsConstructor;

/**
 * The AuthenticationCommandDispatchInterceptor adds the authentication metadata
 * supplied by the AuthenticationMetadataProvider to all dispatched Commands.
 * This identifies the subject for authorization.
 * 
 * @author Dominic Heutelbeck
 * @since 2.1.0
 */
@RequiredArgsConstructor
public class AuthenticationCommandDispatchInterceptor implements MessageDispatchInterceptor<CommandMessage<?>> {

	private final AuthenticationSupplier authnProvider;

	/**
	 * Adds the subject's authentication data to the "subject" field in the metadata
	 * as a JSON String.
	 */
	@Override
	public BiFunction<Integer, CommandMessage<?>, CommandMessage<?>> handle(
			List<? extends CommandMessage<?>> messages) {
		return (index, command) -> {
			if (command.getMetaData().get("subject") != null)
				return command;
			return command.andMetaData(Map.of("subject", authnProvider.get()));
		};
	}

}
