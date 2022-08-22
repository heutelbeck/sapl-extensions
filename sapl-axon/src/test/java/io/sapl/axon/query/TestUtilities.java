package io.sapl.axon.query;

import java.util.function.Predicate;

import org.springframework.security.access.AccessDeniedException;

import lombok.experimental.UtilityClass;

@UtilityClass
public class TestUtilities {
	public static Predicate<Throwable> isAccessDenied() {
		return t -> {
			if (t instanceof AccessDeniedException)
				return true;

			if (t.getMessage().contains(AccessDeniedException.class.getName()))
				return true;

			if (t.getMessage().toUpperCase().contains("ACCESS DENIED"))
				return true;

			if (t.getCause() == null)
				return false;

			return isAccessDenied().test(t.getCause());
		};
	}
	
	public static Predicate<Throwable> isCausedBy(Class<? extends Throwable> cause) {
		return t -> {
			if (cause.isAssignableFrom(t.getClass()))
				return true;

			if (t.getMessage().contains(cause.getSimpleName()))
				return true;

			if (t.getCause() == null)
				return false;

			return isCausedBy(cause).test(t.getCause());
		};
	}
}
