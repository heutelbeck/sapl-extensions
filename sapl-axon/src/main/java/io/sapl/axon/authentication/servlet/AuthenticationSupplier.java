package io.sapl.axon.authentication.servlet;

import java.util.function.Supplier;

/**
 * 
 * Supplies a JSON String representing the currently authenticated user.
 * 
 * @author Dominic Heutelbeck
 * @since 2.1.0
 */
@FunctionalInterface
public interface AuthenticationSupplier extends Supplier<String> {

}