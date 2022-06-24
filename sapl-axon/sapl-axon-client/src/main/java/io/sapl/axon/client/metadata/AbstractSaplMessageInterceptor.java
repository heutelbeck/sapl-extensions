package io.sapl.axon.client.metadata;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;


public abstract class AbstractSaplMessageInterceptor<U extends Message<?>> implements MessageDispatchInterceptor<U> {

    protected abstract ObjectMapper getMapper();

    @Override
    public U handle(U message) {
        return MessageDispatchInterceptor.super.handle(message);
    }

    @Override
    public BiFunction<Integer, U, U> handle(List<? extends U> messages) {
        return (i, m) -> m;
    }

    protected Map<String, Object> getSubjectMetadata() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        Map<String, Object> metadata = Map.of();
        if (authentication != null) {
            ObjectNode subject = getMapper().valueToTree(authentication);
            subject.remove("credentials");
            var principal = subject.get("principal");
            if (principal instanceof ObjectNode)
                ((ObjectNode) principal).remove("password");

            metadata = Map.of("subject", subject);

        }
        return metadata;
    }

}
