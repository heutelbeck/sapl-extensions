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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation can be used inside classes (aggregates, entities, domain
 * services) containing {@code @CommandHandler} methods.
 * <p>
 * Whenever Axon is about to call a {@code @CommandHandler} method which is
 * secured by one of the SAPL enforcement annotations, and the PDP returned a
 * decision containing constraints, for each constraint all methods with this
 * annotation in the same object as the {@code @CommandHandler} are examined to
 * determine if they are responsible for handling the constraint.
 * <p>
 * If the method is responsible, it is invoked and access is granted if the
 * method returns without raising an Exception.
 * <p>
 * The responsibility of the Method is determined by evaluating the SpEL
 * expression contained in the annotation. The method is responsible to handle
 * the constraint, if the expression is empty or evaluates true.
 * <p>
 * To make a reasonable decision about its responsibility, the following data is
 * made available to the SpEL expression in its evaluation context:
 *
 * <ul>
 * <li>The object whose {@code @CommandHandler} method is to be invoked is the
 * root object of the evaluation context. I.e., all methods and members can be
 * invoked directly from within the expression, if they are publicly accessible.
 * This may have the consequence that some aggregate or entity fields or methods
 * must be made public to use them here. This does not break encapsulation, if
 * not used anywhere else but in the expressions residing in the class itself.
 * <li>The variable {@code #constraint} is set to the {@code JsonNode}
 * representing the constraint.
 * <li>The variable {@code #command} is set to the {@code CommandMessage} to be
 * handled.
 * </ul>
 *
 * Example:
 *
 * <pre>{@code
 *
 * @ConstraintHandler("#constraint.get('type').textValue() == 'documentSuspiciousManipulation'")
 * public void handleSuspiciousManipulation(JsonNode constraint) {
 *     apply(new SuspiciousManipulation(id, constraint.get("username").textValue()));
 * }
 * }</pre>
 *
 * @author Dominic Heutelbeck
 * @since 2.1.0
 */
@Target({ ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
public @interface ConstraintHandler {
    /**
     * @return SpEL expression that should evaluate to a Boolean value to determine
     *         responsibility.
     */
    String value() default "";
}
