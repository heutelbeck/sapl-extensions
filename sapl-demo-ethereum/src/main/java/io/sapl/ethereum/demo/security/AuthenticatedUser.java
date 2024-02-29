/*
 * Copyright © 2020-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.ethereum.demo.security;

import java.util.Optional;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import com.vaadin.flow.spring.security.AuthenticationContext;

@Component
public class AuthenticatedUser {

	private final PrinterUserService    userRepository;
	private final AuthenticationContext authenticationContext;

	public AuthenticatedUser(AuthenticationContext authenticationContext, PrinterUserService userRepository) {
		this.userRepository        = userRepository;
		this.authenticationContext = authenticationContext;
	}

	public Optional<PrinterUser> get() {
		return authenticationContext.getAuthenticatedUser(UserDetails.class)
				.map(userDetails -> userRepository.loadUser(userDetails.getUsername()));
	}

	public void logout() {
		authenticationContext.logout();
	}

}
