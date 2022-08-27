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

package io.sapl.axon;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

import io.sapl.axon.annotation.EnforceDropUpdatesWhileDenied;
import io.sapl.axon.annotation.EnforceRecoverableUpdatesIfDenied;
import io.sapl.axon.annotation.PostHandleEnforce;
import io.sapl.axon.annotation.PreHandleEnforce;
import lombok.experimental.UtilityClass;

/**
 * The Annotations Object provides public static final Lists with possible
 * Annotations. It is used in the {@link SAPLQueryHandlingMember}.
 */
@UtilityClass
public class Annotations {
	public static final Set<Class<?>> SAPL_AXON_ANNOTATIONS = Set.of(PreHandleEnforce.class, PostHandleEnforce.class,
			EnforceDropUpdatesWhileDenied.class, EnforceRecoverableUpdatesIfDenied.class);

	public static final Set<Class<?>> SUBSCRIPTION_ANNOTATIONS = Set.of(PreHandleEnforce.class,
			EnforceDropUpdatesWhileDenied.class, EnforceRecoverableUpdatesIfDenied.class);

	public static final Set<Class<?>> SINGLE_ANNOTATIONS = Set.of(PreHandleEnforce.class, PostHandleEnforce.class);

	public static final Set<Class<?>> QUERY_ANNOTATIONS_IMPLYING_PREENFORCING = Set.of(PreHandleEnforce.class,
			EnforceDropUpdatesWhileDenied.class, EnforceRecoverableUpdatesIfDenied.class);

	public static Set<Annotation> annotationsMatching(Collection<Annotation> annotations, Set<Class<?>> types) {
		return annotations.stream().filter(annotation -> annotationHasTypeIn(annotation, types))
				.collect(Collectors.toUnmodifiableSet());
	}

	private static boolean annotationHasTypeIn(Annotation annotation, Set<Class<?>> types) {
		return types.stream().anyMatch(type -> annotation.annotationType().isAssignableFrom(type));
	}
}