package io.sapl.axon.configuration;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.queryhandling.QueryBus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.axon.interceptor.AuthenticationCommandDispatchInterceptor;
import io.sapl.axon.interceptor.AuthenticationMetadataProvider;
import io.sapl.axon.interceptor.AuthenticationQueryDispatchInterceptor;

@Configuration
public class CommandAndQueryAuthenticationConfiguration {
	
	@Bean
	AuthenticationMetadataProvider AuthenticationMetadataProvider(ObjectMapper mapper) {
		return new AuthenticationMetadataProvider(mapper);
	}
	
	@Bean
	AuthenticationCommandDispatchInterceptor authenticationCommandDispatchInterceptor(AuthenticationMetadataProvider authnProvider,
			CommandBus commandBus) {
		var interceptor = new AuthenticationCommandDispatchInterceptor(authnProvider);
		commandBus.registerDispatchInterceptor(interceptor);
		return interceptor;
	}

	@Bean
	AuthenticationQueryDispatchInterceptor authenticationQueryDispatchInterceptor(AuthenticationMetadataProvider authnProvider,
			QueryBus queryBus) {
		var interceptor = new AuthenticationQueryDispatchInterceptor(authnProvider);
		queryBus.registerDispatchInterceptor(interceptor);
		return interceptor;
	}
}
