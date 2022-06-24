/*
 * Copyright © 2017-2022 Dominic Heutelbeck (dominic@heutelbeck.com)
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

package io.sapl.axon.client.metadata;

import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.queryhandling.SubscriptionQueryMessage;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * Abstract implementation of a {@link MessageDispatchInterceptor} that
 * delegates Queries to a SaplCommandInterceptor. Inside this abstract
 * implementation the metadata regarding the subject are merged to the Query
 * Message, before the Query is dispatched.
 */

public abstract class AbstractSaplQueryInterceptor extends AbstractSaplMessageInterceptor<QueryMessage<?, ?>> {

    @Override
    public QueryMessage<?, ?> handle(QueryMessage<?, ?> message) {
        var metadata = getSubjectMetadata();
        if (metadata != null && !metadata.isEmpty())
            message = message.andMetaData(metadata);
        message = addUpdateResponseType(message);

        return super.handle(message);
    }

    protected QueryMessage<?, ?> addUpdateResponseType(QueryMessage<?, ?> message) {
        if (message instanceof SubscriptionQueryMessage<?, ?, ?>) {
            var updateResponseType = ((SubscriptionQueryMessage<?, ?, ?>) message).getUpdateResponseType();
            message = message.andMetaData(Map.of("updateResponseType", updateResponseType));
        }
        return message;
    }

    @Override
    public BiFunction<Integer, QueryMessage<?, ?>, QueryMessage<?, ?>> handle(
            List<? extends QueryMessage<?, ?>> messages) {
        return (i, m) -> m;
    }
}
