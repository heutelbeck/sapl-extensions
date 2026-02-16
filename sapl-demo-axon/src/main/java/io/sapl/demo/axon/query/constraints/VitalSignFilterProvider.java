package io.sapl.demo.axon.query.constraints;

import java.util.Set;
import java.util.function.Predicate;

import org.axonframework.messaging.ResultMessage;
import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.springframework.stereotype.Service;

import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import io.sapl.axon.constrainthandling.api.UpdateFilterConstraintHandlerProvider;
import io.sapl.demo.axon.query.vitals.api.VitalSignMeasurement;

@Service
public class VitalSignFilterProvider implements UpdateFilterConstraintHandlerProvider {

    private static final String CONSTRAINT_TYPE = "constraintType";
    private static final String BLOCK_TYPE      = "blockType";

    @Override
    public boolean isResponsible(Value constraint) {
        return constraint instanceof ObjectValue obj
                && obj.get(CONSTRAINT_TYPE) instanceof TextValue(String type)
                && "filter vital sign type".equals(type)
                && obj.get(BLOCK_TYPE) instanceof TextValue;
    }

    @Override
    public Set<ResponseType<?>> getSupportedResponseTypes() {
        return Set.of(ResponseTypes.instanceOf(VitalSignMeasurement.class));
    }

    @Override
    public Predicate<ResultMessage<?>> getHandler(Value constraint) {
        if (constraint instanceof ObjectValue obj && obj.get(BLOCK_TYPE) instanceof TextValue(var blockedType)) {
            return measurement -> !blockedType
                    .equals(((VitalSignMeasurement) measurement.getPayload()).type().toString());
        }
        return measurement -> true;
    }

}
