package io.sapl.axon.commandhandling.model;

import org.axonframework.commandhandling.RoutingKey;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

import lombok.Value;

public class TestAggregateAPI {

	@Value
	public static class CreateAggregate {
		@TargetAggregateIdentifier
		final String id;
	}

	@Value
	public static class AggregateCreated {
		final String id;
	}

	@Value
	public static class ModifyAggregate {
		@TargetAggregateIdentifier
		final String id;
	}

	@Value
	public static class UpdateMember {
		@TargetAggregateIdentifier
		final String id;
		@RoutingKey
		final String memberId;
	}

	@Value
	public static class AggregateModified {
		final String id;
	}
}
