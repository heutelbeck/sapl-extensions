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

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import com.vaadin.flow.spring.security.VaadinWebSecurity;

import io.sapl.ethereum.demo.views.login.LoginView;

@Configuration
@EnableWebSecurity
public class SecurityConfiguration extends VaadinWebSecurity {

	@Bean
	PasswordEncoder passwordEncoder() {
		return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
	}

	@Override
	protected void configure(HttpSecurity http) throws Exception {

		http.authorizeHttpRequests(
				requests -> requests.requestMatchers(new AntPathRequestMatcher("/images/*.png")).permitAll());

		// Icons from the line-awesome addon
		http.authorizeHttpRequests(
				requests -> requests.requestMatchers(new AntPathRequestMatcher("/line-awesome/**/*.svg")).permitAll());
		super.configure(http);
		setLoginView(http, LoginView.class);
	}

}
