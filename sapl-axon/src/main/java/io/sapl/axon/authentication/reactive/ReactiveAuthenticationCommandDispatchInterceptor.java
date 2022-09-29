package io.sapl.axon.authentication.reactive;

import java.util.Map;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.extensions.reactor.messaging.ReactorMessageDispatchInterceptor;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

/**
 * The ReactiveAuthenticationCommandDispatchInterceptor adds the authentication
 * metadata supplied by the AuthenticationMetadataProvider to all dispatched
 * Commands. This identifies the subject for authorization.
 * 
 * @author Dominic Heutelbeck
 * @since 2.1.0
 */
@RequiredArgsConstructor
public class ReactiveAuthenticationCommandDispatchInterceptor
		implements ReactorMessageDispatchInterceptor<CommandMessage<?>> {

	private final ReactiveAuthenticationSupplier authnProvider;

	@Override
	public Mono<CommandMessage<?>> intercept(Mono<CommandMessage<?>> message) {
		return message.flatMap(msg -> {
			if (msg.getMetaData().get("subject") != null)
				return Mono.just(msg);

			return authnProvider.get().map(authn -> msg.andMetaData(Map.of("subject", authn)));
		});
	}

}
