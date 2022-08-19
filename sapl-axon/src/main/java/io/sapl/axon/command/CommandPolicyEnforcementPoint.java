package io.sapl.axon.command;

import org.axonframework.messaging.annotation.MessageHandlingMember;
import org.axonframework.messaging.annotation.WrappedMessageHandlingMember;

public class CommandPolicyEnforcementPoint<T> extends WrappedMessageHandlingMember<T> {

	public CommandPolicyEnforcementPoint(MessageHandlingMember<T> delegate) {
		super(delegate);
		// TODO Auto-generated constructor stub
	}

}
