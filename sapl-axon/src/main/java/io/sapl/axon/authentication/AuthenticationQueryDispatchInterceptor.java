package io.sapl.axon.authentication;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.queryhandling.QueryMessage;

import lombok.RequiredArgsConstructor;

/**
 * The AuthenticationCommandDispatchInterceptor adds the authentication metadata
 * supplied by the AuthenticationSupplier to all dispatched Queries. This
 * identifies the subject for authorization.
 * 
 * @author Dominic Heutelbeck
 * @since 2.1.0
 */
@RequiredArgsConstructor
public class AuthenticationQueryDispatchInterceptor implements MessageDispatchInterceptor<QueryMessage<?, ?>> {

	private final AuthenticationSupplier authnProvider;
	/**
	 * Adds the subject's authentication data to the "subject" field in the metadata as a JSON String. 
	 */
	@Override
	public BiFunction<Integer, QueryMessage<?, ?>, QueryMessage<?, ?>> handle(
			List<? extends QueryMessage<?, ?>> messages) {
		return (index, query) -> query.andMetaData(Map.of("subject", authnProvider.get()));
	}
}
