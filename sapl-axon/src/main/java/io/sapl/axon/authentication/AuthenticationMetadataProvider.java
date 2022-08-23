package io.sapl.axon.authentication;

import java.util.Map;

@FunctionalInterface
public interface AuthenticationMetadataProvider {

	Map<String, Object> getSubjectMetadata();

}