package io.sapl.demo.axon.query.vitals.api;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import io.sapl.demo.axon.command.MonitorType;

@Document
@JsonInclude(Include.NON_NULL)
public record VitalSignsDocument (
    //@formatter:off
	@Id
	String      patientId,	
	Map<MonitorType,VitalSignMeasurement> lastKnownMeasurements,
	Set<String> connectedSensors,
	Instant updatedAt)
    //@formatter:on
{
	public static UnaryOperator<VitalSignsDocument> withSensor(String sensorId, Instant timestamp) {
		return vitals -> {
			var sensors = new HashSet<>(vitals.connectedSensors);
			sensors.add(sensorId);
			return new VitalSignsDocument(vitals.patientId, vitals.lastKnownMeasurements, sensors, timestamp);
		};
	}

	public static UnaryOperator<VitalSignsDocument> withoutSensor(String sensorId, Instant timestamp) {
		return vitals -> {
			var sensors = new HashSet<>(vitals.connectedSensors);
			sensors.remove(sensorId);
			return new VitalSignsDocument(vitals.patientId, vitals.lastKnownMeasurements, sensors, timestamp);
		};
	}

	public static UnaryOperator<VitalSignsDocument> withMeasurement(VitalSignMeasurement measurement,
			Instant timestamp) {
		return vitals -> {
		    // EnumMap breaks the code. Is this a serialization issue ?
			var measurements = new HashMap<>(vitals.lastKnownMeasurements);
			measurements.put(measurement.type(), measurement);
			return new VitalSignsDocument(vitals.patientId, measurements, vitals.connectedSensors, timestamp);
		};
	}

}
