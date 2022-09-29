package io.sapl.axon.authentication.servlet;

import org.springframework.security.core.context.SecurityContextHolder;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.axon.authentication.AuthnUtil;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

/**
 * 
 * Default implementation of an AuthenticationSupplier. The subject is read from
 * the Spring Security SecurityContextHolder and serialized as Jackson
 * JsonObject. The service removes to remove credentials and passwords from the
 * created authentication data.
 * 
 * @author Dominic Heutelbeck
 * @since 2.1.0
 */
@RequiredArgsConstructor
public class ServletAuthenticationSupplier implements AuthenticationSupplier {

	private final ObjectMapper mapper;

	/**
	 * Extracts the authentication information from the Spring SecurityContext.
	 * Defaults to "anonymous" if no authentication is present.
	 */
	@Override
	@SneakyThrows
	public String get() {
		return AuthnUtil.authenticationToJsonString(SecurityContextHolder.getContext().getAuthentication(), mapper);
	}

}
