/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.sapl.axon.commandhandling.model;

import static org.axonframework.modelling.command.AggregateLifecycle.apply;

import java.util.ArrayList;
import java.util.List;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.messaging.MetaData;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.AggregateMember;
import org.axonframework.modelling.command.EntityId;
import org.axonframework.spring.stereotype.Aggregate;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.axon.annotation.ConstraintHandler;
import io.sapl.axon.annotation.PreHandleEnforce;
import io.sapl.axon.commandhandling.model.TestAggregateAPI.AggregateCreated;
import io.sapl.axon.commandhandling.model.TestAggregateAPI.AggregateModified;
import io.sapl.axon.commandhandling.model.TestAggregateAPI.CreateAggregate;
import io.sapl.axon.commandhandling.model.TestAggregateAPI.ModifyAggregate;
import io.sapl.axon.commandhandling.model.TestAggregateAPI.UpdateMember;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Aggregate
@NoArgsConstructor
public class TestAggregate {
    @AggregateIdentifier
    String id;

    public String data = "some data";

    @AggregateMember
    private List<MemberEntity> members = new ArrayList<>();

    @CommandHandler
    @PreHandleEnforce
    public TestAggregate(CreateAggregate command) {
        apply(new AggregateCreated(command.getId()));
    }

    @EventSourcingHandler
    public void on(AggregateCreated evt) {
        this.id = evt.getId();
        members.add(new MemberEntity("A"));
        members.add(new MemberEntity("B"));
    }

    @CommandHandler
    @PreHandleEnforce
    public void handle(ModifyAggregate command) {
        apply(new AggregateModified(id));
    }

    @ConstraintHandler("#constraint.textValue() == 'something' && data == 'some data'")
    public void handle(ModifyAggregate command, JsonNode constraint, AuthorizationDecision decision,
            CommandBus commandBus, MetaData metaData) {
        log.trace("ConstraintHandler On Aggregate");
    }

    @RequiredArgsConstructor
    public static class MemberEntity {
        @EntityId
        public final String memberId;

        @CommandHandler
        @PreHandleEnforce
        public void handle(UpdateMember command) {
            log.trace("updated {}", memberId);
        }

        @ConstraintHandler("#constraint.textValue() == 'somethingWithMember' && memberId == 'A'")
        public void handle(UpdateMember command, JsonNode constraint, AuthorizationDecision decision,
                CommandBus commandBus, MetaData metaData) {
            log.trace("ConstraintHandler on entity A");
        }

        @ConstraintHandler("#constraint.textValue() == 'somethingWithMember' && memberId != 'A'")
        public void handle(JsonNode constraint) {
            log.trace("ConstraintHandler on entity B");
        }

    }
}
