package io.sapl.vaadindemo.security;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import io.sapl.spring.constraints.api.RunnableConstraintHandlerProvider;

/**
 * This Constraint Handler Provider can be used to log messages based on SAPL Obligations.
 *
 * This provider manages constrains with id "log", here an example:
 * ...
 * obligation
 *     {
 *         "type": "log",
 *         "message"  : "test message"
 *     }
 * ...
 *
 */
@Slf4j
@Service
public class LoggingConstraintHandlerProvider implements RunnableConstraintHandlerProvider {

    @Override
    public boolean isResponsible(Value constraint) {
        return constraint instanceof ObjectValue obj
                && obj.get("type") instanceof TextValue(String type)
                && "log".equals(type);
    }

    @Override
    public Signal getSignal() {
        return Signal.ON_COMPLETE;
    }

    /**
     * The handle method actually acts on the given constraint and logs the policy-defined message to console.
     */
    @Override
    public Runnable getHandler(Value constraint) {
        return () -> {
            if (constraint instanceof ObjectValue obj
                    && obj.get("message") instanceof TextValue(String message)) {
                log.info(message);
            }
        };
    }
}
