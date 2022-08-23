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

package io.sapl.axon.queryhandling;

import org.axonframework.messaging.responsetypes.ResponseType;
import org.springframework.security.access.AccessDeniedException;

import lombok.RequiredArgsConstructor;
import lombok.Value;

@Value
@RequiredArgsConstructor
public class RecoverableResponse<U> {

	public static final String RECOVERABLE_UPDATE_TYPE_KEY = "recoverableUpdateType";

	ResponseType<U> reponseType;
	U               payload;

	public U unwrap() {
		if (payload == null)
			throw new AccessDeniedException("Access Denied");

		return payload;
	}

	public boolean isAccessDenied() {
		return payload == null;
	}

	public static <T> RecoverableResponse<T> accessDenied(ResponseType<T> reponseType) {
		return new RecoverableResponse<>(reponseType,null);
	}
}
