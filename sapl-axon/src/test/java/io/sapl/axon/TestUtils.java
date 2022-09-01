package io.sapl.axon;

import java.util.function.Predicate;

import org.axonframework.messaging.GenericResultMessage;
import org.axonframework.messaging.ResultMessage;

import io.sapl.axon.queryhandling.RecoverableResponse;
import lombok.experimental.UtilityClass;

@UtilityClass
public class TestUtils {
	
	public static <U> Predicate<ResultMessage<RecoverableResponse<U>>> isAccessDeniedResponse() {
		return recoverableResponse -> {
			if (recoverableResponse == null)
				return false;
			else if (recoverableResponse.getPayload() == null)
				return false;
			else return recoverableResponse.getPayload().isAccessDenied();
		};
	}
	
	public static <U> Predicate<ResultMessage<U>> matchesIgnoringIdentifier(ResultMessage<U> resultMessage) {
		return otherResultMessage -> {
			
			//message check
			if (resultMessage != null && otherResultMessage != null) {
					
				//payload check
				if (resultMessage.getPayload() != null && otherResultMessage.getPayload() != null) {
					if (!resultMessage.getPayload().equals(otherResultMessage.getPayload()))
						return false;
				}
				
				//payload null-equality check
				else if (resultMessage.getPayload() == null ^ otherResultMessage.getPayload() == null)
					return false;
				
				//exception check
				if (resultMessage.isExceptional() && otherResultMessage.isExceptional()) {
					if (!resultMessage.exceptionResult().getClass().equals(otherResultMessage.exceptionResult().getClass()))
						return false;
				}
				
				//exception null-equality check
				else if (resultMessage.isExceptional() ^ otherResultMessage.isExceptional())
					return false;
				
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
			if (TestUtils.<U>isAccessDeniedResponse().test(recoverableResultMessage))
				return false;
			var unwrapped = new GenericResultMessage<>(recoverableResultMessage.getPayload().unwrap());
			return matchesIgnoringIdentifier(resultMessage).test(unwrapped);
		};
	}
}
