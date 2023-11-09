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
package io.sapl.axon.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The {@code @EnforceDropUpdatesWhileDenied} annotation establishes a policy
 * enforcement point (PEP) for Handlers of subscription queries.
 * <p>
 * If the {@code @QueryHandler} is invoked using a non-subscription query, the
 * PEP falls back to the behavior of
 * {@link io.sapl.axon.annotation.PreHandleEnforce}.
 * <p>
 * The policy enforcement strategy implemented by this PEP is to wait for the
 * PDP's first decision.
 * <p>
 * If the initial decision is PERMIT, then send the initial response and
 * subscribe to the query updates.
 * <p>
 * If the initial decision is DENY, deny access to the initial response, but
 * stay subscribed to the query updates, but drop all events until a new
 * decision is sent by the PDP.
 * <p>
 * If the new decision is a PERMIT, updates start up back to be propagated to
 * the client.
 * <p>
 * On each decision, constraints will be respected and updated.
 * <p>
 * If a decision contains a resource, the PEP assumes that this is a final
 * update message to be sent. It is sent and the query subscription is
 * terminated.
 * <p>
 * The parameters of the annotation can be used to customize the
 * {@code AuthorizationSubscription} sent to the PDP. If a field is left empty,
 * the PEP attempts to construct a reasonable subscription element from the
 * security context, inspecting messages, and using reflection of the involved
 * objects.
 * <p>
 * By default, the subject is determined by serializing the 'subject' field of
 * the message metadata into a JsonNode using the default {@code ObjectMapper}.
 * <p>
 * To be able to construct reasonable {@code AuthorizationSubscription} objects,
 * the following data is made available to the SpEL expression in its evaluation
 * context:
 *
 * <ul>
 * <li>The variable {@code #message} is set to the {@code QueryMessage}.
 * <li>The variable {@code #query} is set to the payload of the
 * {@code QueryMessage} to be handled.
 * <li>The variable {@code #metadata} is set to the metadata of the
 * {@code QueryMessage} to be handled.
 * <li>The variable {@code #executable} is set to the
 * {@link java.lang.reflect.Executable} representing the method to be invoked to
 * generate the initial query response.
 * </ul>
 *
 * Example:
 *
 * <pre>
 * {@code
 * &#64;QueryHandler
 * @EnforceDropUpdatesWhileDenied(action = "'Monitor'", resource = "{ 'type':'measurement', 'id':#query.patientId(), 'monitorType':#query.type() }")
 * Optional<VitalSignMeasurement> handle(MonitorVitalSignOfPatient query) {
 *     return repository.findById(query.patientId()).map(v -> v.lastKnownMeasurements().get(query.type()));
 * }
 * }
 * </pre>
 *
 * @author Dominic Heutelbeck
 * @since 2.1.0
 */
@Inherited
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.METHOD, ElementType.TYPE })
public @interface EnforceDropUpdatesWhileDenied {

    /**
     * @return the Spring-EL expression to whose evaluation result is to be used as
     *         the subject in the authorization subscription to the PDP. If empty,
     *         the PEP attempts to derive a guess to describe the subject based on
     *         the current Principal.
     */
    String subject() default "";

    /**
     * @return the Spring-EL expression to whose evaluation result is to be used as
     *         the action in the authorization subscription to the PDP. If empty,
     *         the PEP attempts to derive a guess to describe the action based on
     *         reflection.
     */
    String action() default "";

    /**
     * @return the Spring-EL expression to whose evaluation result is to be used as
     *         the action in the authorization subscription to the PDP. If empty,
     *         the PEP attempts to derive a guess to describe the resource based on
     *         reflection.
     */
    String resource() default "";

    /**
     * @return the Spring-EL expression to whose evaluation result is to be used as
     *         the action in the authorization subscription to the PDP. If empty, no
     *         environment is set in the subscription.
     */
    String environment() default "";

}
