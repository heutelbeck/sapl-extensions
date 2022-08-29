package io.sapl.axon.authentication;

import java.util.Map;

import org.springframework.security.core.context.SecurityContextHolder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

/**
 * 
 * Default implementation of an AuthenticationMetadataProvider. The subject is
 * read from the Spring Security SecurityContextHolder and serialized as Jackson
 * JsonObject. The service removes to remove credentials and passwords from the
 * created authentication data.
 * 
 * @author Dominic Heutelbeck
 *
 */
@RequiredArgsConstructor
public class SpringSecurityAuthenticationMetadataProvider implements AuthenticationMetadataProvider {
	private final ObjectMapper mapper;

	@Override
	@SneakyThrows
	public Map<String, Object> getSubjectMetadata() {
		var authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null)
			return Map.of("subject", "\"anonymous\"");

		JsonNode subject = mapper.valueToTree(authentication.getPrincipal());
		if (subject.isObject()) {
			((ObjectNode) subject).remove("credentials");
			((ObjectNode) subject).remove("password");
			var principal = subject.get("principal");
			if (principal != null && principal.isObject())
				((ObjectNode) principal).remove("password");
		}

		return Map.of("subject", mapper.writeValueAsString(subject));
	}
}
