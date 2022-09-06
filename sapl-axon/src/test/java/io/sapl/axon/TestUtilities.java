package io.sapl.axon;

import java.util.function.Predicate;

import org.axonframework.messaging.GenericResultMessage;
import org.axonframework.messaging.ResultMessage;
import org.axonframework.queryhandling.SubscriptionQueryMessage;
import org.springframework.security.access.AccessDeniedException;

import io.sapl.axon.queryhandling.RecoverableResponse;
import lombok.experimental.UtilityClass;

@UtilityClass
public class TestUtilities {
	
	public static <U> Predicate<ResultMessage<RecoverableResponse<U>>> isAccessDeniedResponse() {
		return recoverableResponse -> {
			if (recoverableResponse == null)
				return false;
			else if (recoverableResponse.getPayload() == null)
				return false;
			else return recoverableResponse.getPayload().isAccessDenied();
		};
	}
	
	public static <U> Predicate<SubscriptionQueryMessage<?, ?, U>> alwaysTrue() {
		return val -> true;
	}
	
	public static <U> Predicate<ResultMessage<U>> matchesIgnoringIdentifier(ResultMessage<U> resultMessage) {
		return matchesIgnoringIdentifier(resultMessage.getPayloadType(), resultMessage);
	}
	
	@SuppressWarnings("unlikely-arg-type")
	public static <U1, U2> Predicate<ResultMessage<U2>> matchesIgnoringIdentifier(Class<U2> otherClazz, ResultMessage<U1> resultMessage) {
		return otherResultMessage -> {
			
			//message check
			if (resultMessage != null && otherResultMessage != null) {
				
				var isNotExceptional = true;
				//exception check
				if (resultMessage.isExceptional() && otherResultMessage.isExceptional()) {
					isNotExceptional = false;
					if (!resultMessage.exceptionResult().getClass().equals(otherResultMessage.exceptionResult().getClass()))
						return false;
				}
				
				//exception null-equality check
				else if (resultMessage.isExceptional() ^ otherResultMessage.isExceptional())
					return false;
				
				if (isNotExceptional) {
					
					//payload check
					if (resultMessage.getPayload() != null && otherResultMessage.getPayload() != null) {
						if (!resultMessage.getPayload().equals(otherResultMessage.getPayload()))
							return false;
					}
					
					//payload null-equality check
					else if (resultMessage.getPayload() == null ^ otherResultMessage.getPayload() == null)
						return false;
				}
				
				//metadata check
				if (resultMessage.getMetaData() != null && resultMessage.getMetaData() != null) {
					if (!resultMessage.getMetaData().equals(otherResultMessage.getMetaData()))
						return false;
				}
				
				//metadata null-equality check
				else if (resultMessage.isExceptional() ^ otherResultMessage.isExceptional())
					return false;
			}
			
			//message null-equality check
			else if (resultMessage == null ^ otherResultMessage == null)
				return false;
			
			return true;
		};
	}
	
	public static <U> Predicate<ResultMessage<RecoverableResponse<U>>> unwrappedMatchesIgnoringIdentifier(ResultMessage<U> resultMessage) {
		return recoverableResultMessage -> {
			if (TestUtilities.<U>isAccessDeniedResponse().test(recoverableResultMessage))
				return false;
			var unwrapped = new GenericResultMessage<>(recoverableResultMessage.getPayload().unwrap());
			return matchesIgnoringIdentifier(resultMessage).test(unwrapped);
		};
	}
	
	public static Predicate<Throwable> isAccessDenied() {
		return t -> {
			if (t instanceof AccessDeniedException)
				return true;

			if (t.getMessage().contains(AccessDeniedException.class.getName()))
				return true;

			if (t.getMessage().toUpperCase().contains("ACCESS DENIED"))
				return true;
			
			// in case the remote service closed the stream before it could be subscribed to
			if (t.getMessage().toUpperCase().contains("ALREADY BEEN COMPLETED"))
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
