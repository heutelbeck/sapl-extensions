package io.sapl.axon.authentication;

import java.util.List;
import java.util.function.BiFunction;

import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.queryhandling.QueryMessage;

import lombok.RequiredArgsConstructor;
/**
 * The AuthenticationCommandDispatchInterceptor adds the authentication metadata
 * supplied by the AuthenticationMetadataProvider to all dispatched Queries.
 * This identifies the subject for authorization.
 * 
 * @author Dominic Heutelbeck
 *
 */
@RequiredArgsConstructor
public class AuthenticationQueryDispatchInterceptor implements MessageDispatchInterceptor<QueryMessage<?, ?>> {

	private final AuthenticationMetadataProvider authnProvider;

	@Override
	public BiFunction<Integer, QueryMessage<?, ?>, QueryMessage<?, ?>> handle(
			List<? extends QueryMessage<?, ?>> messages) {
		return (index, query) -> query.andMetaData(authnProvider.getSubjectMetadata());
	}
}
