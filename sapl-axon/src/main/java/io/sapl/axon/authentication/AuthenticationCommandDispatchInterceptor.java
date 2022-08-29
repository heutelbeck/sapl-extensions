package io.sapl.axon.authentication;

import java.util.List;
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
 *
 */
@RequiredArgsConstructor
public class AuthenticationCommandDispatchInterceptor implements MessageDispatchInterceptor<CommandMessage<?>> {

	private final AuthenticationMetadataProvider authnProvider;

	@Override
	public BiFunction<Integer, CommandMessage<?>, CommandMessage<?>> handle(
			List<? extends CommandMessage<?>> messages) {
		return (index, command) -> command.andMetaData(authnProvider.getSubjectMetadata());
	}

}
