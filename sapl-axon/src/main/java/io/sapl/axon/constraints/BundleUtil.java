package io.sapl.axon.constraints;

import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import org.springframework.security.access.AccessDeniedException;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@UtilityClass
public class BundleUtil {
	public static <V> Consumer<V> consumeAll(List<Consumer<V>> handlers) {
		return value -> handlers.forEach(handler -> handler.accept(value));
	}

	public static Runnable runAll(List<Runnable> handlers) {
		return () -> handlers.forEach(Runnable::run);

	}
	
	public static Runnable runAllObligations(List<Runnable> handlers) {
		return () -> {
			try {
				handlers.forEach(Runnable::run);
			} catch (Throwable t) {
				log.error("Failed to execute obligation handlers.", t);
				throw new AccessDeniedException("Access Denied");
			}
		};
	}

	public static Runnable runAllAdvice(List<Runnable> handlers) {
		return () -> {
			try {
				handlers.forEach(Runnable::run);
			} catch (Throwable t) {
				log.error("Failed to execute advice handlers.", t);
			}
		};
	}

	public Runnable obligation(Runnable handler) {
		return () -> {
			try {
				handler.run();
			} catch (Throwable t) {
				log.error("Failed to execute obligation handlers.", t);
				throw new AccessDeniedException("Access Denied");
			}
		};
	}

	public Runnable advice(Runnable handler) {
		return () -> {
			try {
				handler.run();
			} catch (Throwable t) {
				log.error("Failed to execute adivce handlers.", t);
			}
		};
	}

	public <V> Consumer<V> advice(Consumer<V> handler) {
		return v -> {
			try {
				handler.accept(v);
			} catch (Throwable t) {
				log.error("Failed to execute adivce handlers.", t);
			}
		};
	}
	
	public <V> Consumer<V> obligation(Consumer<V> handler) {
		return v -> {
			try {
				handler.accept(v);
			} catch (Throwable t) {
				log.error("Failed to execute obligation handlers.", t);
				throw new AccessDeniedException("Access Denied");
			}
		};
	}
	
	public static <V> Function<V, V> mapAll(Collection<Function<V, V>> handlers) {
		return handlers.stream().reduce(Function.identity(),
				(merged, newFunction) -> x -> newFunction.apply(merged.apply(x)));
	}
}
