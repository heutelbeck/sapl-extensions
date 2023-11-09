/*
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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
