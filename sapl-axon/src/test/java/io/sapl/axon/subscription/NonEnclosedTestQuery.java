package io.sapl.axon.subscription;

import lombok.Value;

@Value
class NonEnclosedTestQuery {
	Object targetDocumentIdentifier;
	Object someOtherField = "someOtherValue";
}