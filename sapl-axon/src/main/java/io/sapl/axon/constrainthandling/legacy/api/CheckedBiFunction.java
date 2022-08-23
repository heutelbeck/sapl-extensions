package io.sapl.axon.constrainthandling.legacy.api;

@FunctionalInterface
public interface CheckedBiFunction<C, T, R> {
	R apply(C c, T t) throws Exception;
}
