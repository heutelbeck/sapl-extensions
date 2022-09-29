package io.sapl.axon.authentication;

import org.springframework.security.core.Authentication;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

/**
 * Tool to map Authentication to JSON String.
 * 
 * @author Dominic Heutelbeck
 * @since 2.1.0
 */
@UtilityClass
public class AuthnUtil {

	/**
	 * @param authentication the Authentication
	 * @param mapper         the ObjectMapper
	 * @return the Authentication as a JSON String
	 */
	@SneakyThrows
	public static String authenticationToJsonString(Authentication authentication, ObjectMapper mapper) {
		if (authentication == null)
			return "\"anonymous\"";

		JsonNode subject = mapper.valueToTree(authentication.getPrincipal());
		if (subject.isObject()) {
			((ObjectNode) subject).remove("credentials");
			((ObjectNode) subject).remove("password");
			var principal = subject.get("principal");
			if (principal != null)
				if (principal.isObject())
					((ObjectNode) principal).remove("password");
		}
		return mapper.writeValueAsString(subject);
	}

}
