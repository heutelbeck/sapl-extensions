package io.sapl.demo.axon.query.constraints;

import java.util.Set;

import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.axon.constrainthandling.api.ResultConstraintHandlerProvider;
import io.sapl.demo.axon.query.vitals.api.VitalSignMeasurement;

@Service
public class BloodPressureClassificationProvider implements ResultConstraintHandlerProvider {

	private static final String BLOOD_PRESSURE_CATEGORY = "Blood Pressure Category";

    @Override
	public boolean isResponsible(JsonNode constraint) {
		return constraint.isTextual() && "catrgorise blood pressure".equals(constraint.textValue());
	}

	@Override
	public Set<ResponseType<?>> getSupportedResponseTypes() {
		return Set.of(ResponseTypes.instanceOf(VitalSignMeasurement.class));
	}

	@Override
	public Object mapPayload(Object payload, Class<?> clazz, JsonNode constraint) {
		var measurement = (VitalSignMeasurement) payload;
		var split       = measurement.value().split("/");
		var systolic    = Double.valueOf(split[0]);
		var diastolic   = Double.valueOf(split[1]);

		if (systolic < 100 || diastolic < 60)
			return new VitalSignMeasurement(measurement.monitorDeviceId(), measurement.type(), "Hypotension",
					BLOOD_PRESSURE_CATEGORY, measurement.timestamp());

		if (systolic < 120 || diastolic < 80)
			return new VitalSignMeasurement(measurement.monitorDeviceId(), measurement.type(), "Normal",
					BLOOD_PRESSURE_CATEGORY, measurement.timestamp());

		if (systolic < 140 || diastolic < 90)
			return new VitalSignMeasurement(measurement.monitorDeviceId(), measurement.type(), "Prehypertension",
					BLOOD_PRESSURE_CATEGORY, measurement.timestamp());

		if (systolic < 160 || diastolic < 100)
			return new VitalSignMeasurement(measurement.monitorDeviceId(), measurement.type(), "Stage 1 Hypertension",
					BLOOD_PRESSURE_CATEGORY, measurement.timestamp());

		if (systolic < 180 || diastolic < 110)
			return new VitalSignMeasurement(measurement.monitorDeviceId(), measurement.type(), "Stage 2 Hypertension",
					BLOOD_PRESSURE_CATEGORY, measurement.timestamp());

		return new VitalSignMeasurement(measurement.monitorDeviceId(), measurement.type(),
				"Hypertension Crisis EMERGENCY", BLOOD_PRESSURE_CATEGORY, measurement.timestamp());
	}

}
