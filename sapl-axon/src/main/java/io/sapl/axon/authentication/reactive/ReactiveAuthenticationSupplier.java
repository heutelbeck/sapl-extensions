package io.sapl.axon.authentication.reactive;

import java.util.function.Supplier;

import reactor.core.publisher.Mono;

/**
 * 
 * Supplies a JSON String representing the currently authenticated user.
 * 
 * @author Dominic Heutelbeck
 * @Since 2.1.0
 */
@FunctionalInterface
public interface ReactiveAuthenticationSupplier extends Supplier<Mono<String>> {

}