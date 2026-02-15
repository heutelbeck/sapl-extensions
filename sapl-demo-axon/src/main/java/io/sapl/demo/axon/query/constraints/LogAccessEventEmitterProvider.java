package io.sapl.demo.axon.query.constraints;

import java.util.function.BiConsumer;

import org.axonframework.extensions.reactor.eventhandling.gateway.ReactorEventGateway;
import org.axonframework.messaging.Message;
import org.springframework.stereotype.Service;

import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.axon.constrainthandling.api.OnDecisionConstraintHandlerProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class LogAccessEventEmitterProvider implements OnDecisionConstraintHandlerProvider {

	public record AccessAttempt(String message, AuthorizationDecision decision, Message<?> cause) {
	}

	private final ReactorEventGateway eventGateway;

	@Override
	public boolean isResponsible(Value constraint) {
		return constraint instanceof TextValue(String text)
				&& "dispatch access attempt event".equals(text);
	}

	@Override
	public BiConsumer<AuthorizationDecision, Message<?>> getHandler(Value constraint) {
		return (decision, cause) -> {
			var message = "Access to a protected resource was attempted/continued by ";
			var subject = cause.getMetaData().get("subject");
			if (subject != null)
				message += subject;
			else
				message += "an unknwon actor";

			message += ". Access was ";

			if (decision.decision() == Decision.PERMIT)
				message += " GRANTED. ";
			else
				message += " DENIED. ";

			message += "Means of access: "+cause.getPayloadType();

			eventGateway.publish(new AccessAttempt(message, decision, cause)).subscribe();
			
			log.debug("Published access log event to event bus: {}",message);

		};
	}

}
