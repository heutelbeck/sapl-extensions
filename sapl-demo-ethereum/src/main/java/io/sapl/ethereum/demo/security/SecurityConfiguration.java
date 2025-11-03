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
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

import com.vaadin.flow.spring.security.VaadinAwareSecurityContextHolderStrategyConfiguration;
import com.vaadin.flow.spring.security.VaadinSecurityConfigurer;

import io.sapl.ethereum.demo.views.login.LoginView;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

/**
 * Security configuration for SAPL Ethereum Demo application.
 * <p>
 * Configures Vaadin UI security with form-based login, permits access to static
 * image resources and line-awesome SVG icons, and integrates Vaadin's security
 * features for view-based access control.
 */
@Slf4j
@Configuration
@EnableWebSecurity
@Import(VaadinAwareSecurityContextHolderStrategyConfiguration.class)
public class SecurityConfiguration {

    /**
     * Configures password encoding using Argon2 with Spring Security 5.8 defaults.
     * <p>
     * Argon2 provides strong protection against brute-force attacks and is
     * recommended for production environments.
     *
     * @return the configured PasswordEncoder.
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }

    /**
     * Configures the security filter chain for the Vaadin UI.
     * <p>
     * Permits public access to static resources (PNG images and line-awesome SVG
     * icons), applies Vaadin's security integration for view-based access control,
     * and configures the login view.
     *
     * @param http the HttpSecurity to configure.
     * @return the configured SecurityFilterChain.
     * @throws Exception if configuration fails.
     */
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        val matcher = PathPatternRequestMatcher.withDefaults();

        log.info("Configuring Vaadin UI security with form-based login.");

        // Configure static resources with public access
        http.authorizeHttpRequests(auth -> auth.requestMatchers(matcher.matcher("/images/*.png")).permitAll()
                .requestMatchers(matcher.matcher("/line-awesome/**")).permitAll());

        // Apply Vaadin's security integration
        http.with(VaadinSecurityConfigurer.vaadin(), vaadin -> {
            vaadin.loginView(LoginView.class);
        });

        return http.build();
    }
}