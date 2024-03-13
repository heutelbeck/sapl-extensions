package io.sapl.demo.axon.query.patients.api;

import java.time.Instant;
import java.util.function.UnaryOperator;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import io.sapl.demo.axon.command.patient.Ward;

@Document
@JsonInclude(Include.NON_NULL)
public record PatientDocument(
// @formatter:off
	@Id
	String  id,
	String  name,
	String  latestIcd11Code,
	String  latestDiagnosisText,
	Ward    ward,
	Instant updatedAt) {
	// @formatter:on

    public static UnaryOperator<PatientDocument> withIcd(String newIcd11Code, Instant timestamp) {
        return patient -> new PatientDocument(patient.id, patient.name, newIcd11Code, patient.latestDiagnosisText,
                patient.ward, timestamp);
    }

    public static UnaryOperator<PatientDocument> withIcdAndDisgnosis(String newIcd11Code, String newDiagnosis,
            Instant timestamp) {
        return patient -> new PatientDocument(patient.id, patient.name, newIcd11Code, newDiagnosis, patient.ward,
                timestamp);
    }

    public static UnaryOperator<PatientDocument> withDiagnosis(String newDiagnosis, Instant timestamp) {
        return patient -> new PatientDocument(patient.id, patient.name, patient.latestIcd11Code, newDiagnosis,
                patient.ward, timestamp);
    }

    public static UnaryOperator<PatientDocument> withWard(Ward newWard, Instant timestamp) {
        return patient -> new PatientDocument(patient.id, patient.name, patient.latestIcd11Code,
                patient.latestDiagnosisText, newWard, timestamp);
    }
}
