package io.sapl.axon.authentication;

import java.util.function.Supplier;

/**
 * 
 * Supplies a JSON String representing the currently authenticated user.
 * 
 * @author Dominic Heutelbeck
 *
 */
@FunctionalInterface
public interface AuthenticationSupplier extends Supplier<String> {

}