package io.sapl.axon.authentication.reactive;

import java.util.Map;

import org.axonframework.extensions.reactor.messaging.ReactorMessageDispatchInterceptor;
import org.axonframework.queryhandling.QueryMessage;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

/**
 * The ReactiveAuthenticationQueryDispatchInterceptor adds the authentication
 * metadata supplied by the AuthenticationMetadataProvider to all dispatched
 * Commands. This identifies the subject for authorization.
 * 
 * @author Dominic Heutelbeck
 * @since 2.1.0
 */
@RequiredArgsConstructor
public class ReactiveAuthenticationQueryDispatchInterceptor
		implements ReactorMessageDispatchInterceptor<QueryMessage<?, ?>> {

	private final ReactiveAuthenticationSupplier authnProvider;

	@Override
	public Mono<QueryMessage<?, ?>> intercept(Mono<QueryMessage<?, ?>> message) {
		return message.flatMap(msg -> {
			if (msg.getMetaData().get("subject") != null)
				return Mono.just(msg);

			return authnProvider.get().map(authn -> msg.andMetaData(Map.of("subject", authn)));
		});
	}

}
