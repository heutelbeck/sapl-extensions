package io.sapl.axon.commandhandling.model;

import static org.axonframework.modelling.command.AggregateLifecycle.apply;

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.spring.stereotype.Aggregate;

import io.sapl.axon.annotation.PreHandleEnforce;
import io.sapl.axon.commandhandling.model.TestAggregateAPI.AggregateCreated;
import io.sapl.axon.commandhandling.model.TestAggregateAPI.AggregateModified;
import io.sapl.axon.commandhandling.model.TestAggregateAPI.CreateAggregate;
import io.sapl.axon.commandhandling.model.TestAggregateAPI.ModifyAggregate;
import lombok.NoArgsConstructor;

@Aggregate
@NoArgsConstructor
public class TestAggregate {
	@AggregateIdentifier
	String id;

	@CommandHandler
	@PreHandleEnforce
	public TestAggregate(CreateAggregate command) {
		apply(new AggregateCreated(command.getId()));
	}

	@EventSourcingHandler
	public void on(AggregateCreated evt) {
		this.id = evt.getId();
		
	}

	@CommandHandler
	@PreHandleEnforce
	public void handle(ModifyAggregate command) {
		apply(new AggregateModified(id));
	}
}
