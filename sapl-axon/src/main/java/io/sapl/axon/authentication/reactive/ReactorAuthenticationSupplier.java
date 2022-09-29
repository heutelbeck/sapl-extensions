package io.sapl.axon.authentication.reactive;

import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.axon.authentication.AuthnUtil;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import reactor.core.publisher.Mono;

/**
 * 
 * Default implementation of an AuthenticationSupplier. The subject is read from
 * the Webflux ReactiveSecurityContextHolder and serialized as Jackson
 * JsonObject. The service removes to remove credentials and passwords from the
 * created authentication data.
 * 
 * @author Dominic Heutelbeck
 * @since 2.1.0
 */
@RequiredArgsConstructor
public class ReactorAuthenticationSupplier implements ReactiveAuthenticationSupplier {

	private final static String ANONYMOUS = "\"anonymous\"";

	private final ObjectMapper mapper;

	/**
	 * Extracts the authentication information from the Spring SecurityContext.
	 * Defaults to "anonymous" if no authentication is present.
	 */
	@Override
	@SneakyThrows
	public Mono<String> get() {
		return ReactiveSecurityContextHolder.getContext().map(SecurityContext::getAuthentication)
				.map(authn -> AuthnUtil.authenticationToJsonString(authn, mapper)).onErrorResume(e -> Mono.empty())
				.defaultIfEmpty(ANONYMOUS);
	}

}
