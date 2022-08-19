package io.sapl.axon.interceptor;

import java.util.Map;

import org.springframework.security.core.context.SecurityContextHolder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

@RequiredArgsConstructor
public class AuthenticationMetadataProvider {
	private final ObjectMapper mapper;
	
	@SneakyThrows
	public Map<String, Object> getSubjectMetadata() {
		var authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null)
			return Map.of("subject", "\"anonymous\"");

		ObjectNode subject = mapper.valueToTree(authentication.getPrincipal());
		subject.remove("credentials");
		subject.remove("password");
		var principal = subject.get("principal");
		if (principal instanceof ObjectNode)
			((ObjectNode) principal).remove("password");
		return Map.of("subject",mapper.writeValueAsString(subject));
	}
}
