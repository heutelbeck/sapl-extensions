package io.sapl.vaadindemo.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vaadin.flow.spring.security.VaadinAwareSecurityContextHolderStrategyConfiguration;
import com.vaadin.flow.spring.security.VaadinSecurityConfigurer;

import io.sapl.spring.config.EnableSaplMethodSecurity;
import io.sapl.vaadin.base.VaadinAuthorizationSubscriptionBuilderService;
import io.sapl.vaadindemo.views.LoginView;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

/**
 * Security configuration for SAPL Vaadin Demo application.
 * <p>
 * Configures Vaadin UI security with form-based login, integrates SAPL policy
 * engine for reactive authorization, and provides demo users with hard-coded
 * credentials for testing purposes.
 * <p>
 * This configuration:
 * <ul>
 * <li>Enables SAPL method security for policy-based access control</li>
 * <li>Permits public access to static resources (images and icons)</li>
 * <li>Configures Argon2 password encoding</li>
 * <li>Provides in-memory user details service with admin and user roles</li>
 * </ul>
 * 
 * @see EnableSaplMethodSecurity
 * @see VaadinAuthorizationSubscriptionBuilderService
 */
@Slf4j
@Configuration
@EnableWebSecurity
@EnableSaplMethodSecurity
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
     * icons), applies Vaadin's security integration for view-based access control
     * with SAPL policy engine integration, and configures the login view.
     *
     * @param http the HttpSecurity to configure.
     * @return the configured SecurityFilterChain.
     * @throws Exception if configuration fails.
     */
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        val matcher = PathPatternRequestMatcher.withDefaults();

        log.info("Configuring Vaadin UI security with SAPL integration and form-based login.");

        // Configure static resources with public access
        http.authorizeHttpRequests(auth -> auth.requestMatchers(matcher.matcher("/images/*.png")).permitAll()
                .requestMatchers(matcher.matcher("/line-awesome/**")).permitAll());

        // Apply Vaadin's security integration
        http.with(VaadinSecurityConfigurer.vaadin(), vaadin -> {
            vaadin.loginView(LoginView.class);
        });

        return http.build();
    }

    /**
     * Demo UserDetailsService providing two hard-coded in-memory users and their
     * roles.
     * <p>
     * This configuration is for demonstration purposes only and should NOT be used
     * in production applications. In production, integrate with a proper user store
     * such as LDAP, database, or external identity provider.
     * <p>
     * Credentials:
     * <ul>
     * <li>Username: admin, Password: admin, Role: Admin</li>
     * <li>Username: user, Password: user, Role: USER</li>
     * </ul>
     *
     * @return the configured UserDetailsService with demo users.
     */
    @Bean
    UserDetailsService userDetailsService() {
        UserDetails admin = User.withUsername("admin")
                .password("$2a$12$wuM1Cmdn4e0eTZfWrqSk0.Q82N3S6ehvj7/jzdxUH5xuthcvvlKCW").roles("Admin").build();
        UserDetails user = User.withUsername("user")
                .password("$2a$12$itBzi/0MWsalfjnrftIO9eQ6lifIn61K77A3/UbNMAC9IVEtVmnvW").roles("USER").build();
        return new InMemoryUserDetailsManager(admin, user);
    }

    /**
     * Configures the Vaadin authorization subscription builder service for SAPL
     * policy engine integration.
     * <p>
     * This service enables reactive, streaming-based authorization decisions in
     * Vaadin components by integrating with the SAPL policy engine. It uses the
     * Spring Security method security expression handler to evaluate access control
     * expressions within SAPL policies.
     *
     * @param mapper the ObjectMapper for JSON serialization of authorization
     *               requests.
     * @return the configured VaadinAuthorizationSubscriptionBuilderService.
     */
    @Bean
    VaadinAuthorizationSubscriptionBuilderService vaadinAuthorizationSubscriptionBuilderService(
            ObjectMapper mapper) {
        val expressionHandler = new DefaultMethodSecurityExpressionHandler();
        return new VaadinAuthorizationSubscriptionBuilderService(expressionHandler, mapper);
    }
}