package io.sapl.axon.interceptor;

import java.util.Map;

@FunctionalInterface
public interface AuthenticationMetadataProvider {

	Map<String, Object> getSubjectMetadata();

}