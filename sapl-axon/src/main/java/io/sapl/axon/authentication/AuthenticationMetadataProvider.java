package io.sapl.axon.authentication;

import java.util.Map;

/**
 * 
 * Supplies the subject for authorization in the metadata under the key 'subject'.
 * 
 * @author Dominic Heutelbeck
 *
 */
@FunctionalInterface
public interface AuthenticationMetadataProvider {

	Map<String, Object> getSubjectMetadata();

}