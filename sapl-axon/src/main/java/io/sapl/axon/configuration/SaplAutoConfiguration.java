package io.sapl.axon.configuration;

import java.util.List;
import java.util.Optional;

import javax.annotation.PostConstruct;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thoughtworks.xstream.XStream;

import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.axon.authentication.AuthenticationCommandDispatchInterceptor;
import io.sapl.axon.authentication.AuthenticationMetadataProvider;
import io.sapl.axon.authentication.AuthenticationQueryDispatchInterceptor;
import io.sapl.axon.authentication.SpringSecurityAuthenticationMetadataProvider;
import io.sapl.axon.constrainthandling.AxonConstraintHandlerService;
import io.sapl.axon.constrainthandling.api.CommandMessageMappingConstraintHandlerProvider;
import io.sapl.axon.constrainthandling.api.OnDecisionConstraintHandlerProvider;
import io.sapl.axon.constrainthandling.api.QueryMessageMappingConstraintHandlerProvider;
import io.sapl.axon.constrainthandling.api.ResultMessageFilterPredicateConstraintHandlerProvider;
import io.sapl.axon.constrainthandling.api.ResultMessageMappingConstraintHandlerProvider;
import io.sapl.axon.queryhandling.SaplQueryGateway;
import io.sapl.axon.queryhandling.SaplQueryUpdateEmitter;
import io.sapl.axon.subscriptions.AxonAuthorizationSubscriptionBuilderService;
import io.sapl.spring.constraints.api.ErrorMappingConstraintHandlerProvider;
import io.sapl.spring.constraints.api.MappingConstraintHandlerProvider;

@Configuration
public class SaplAutoConfiguration {

	@Autowired
	Optional<XStream> xStream;

	@PostConstruct
	void whitelistSaplObjectsInXStream() {
		xStream.ifPresent(xStream -> xStream.allowTypesByWildcard(new String[] { "io.sapl.**" }));
	}

	@Bean
	AuthenticationMetadataProvider authenticationMetadataProvider(ObjectMapper mapper) {
		return new SpringSecurityAuthenticationMetadataProvider(mapper);
	}

	@Bean
	AuthenticationCommandDispatchInterceptor authenticationCommandDispatchInterceptor(
			AuthenticationMetadataProvider authnProvider, CommandBus commandBus) {
		var interceptor = new AuthenticationCommandDispatchInterceptor(authnProvider);
		commandBus.registerDispatchInterceptor(interceptor);
		return interceptor;
	}

	@Bean
	AuthenticationQueryDispatchInterceptor authenticationQueryDispatchInterceptor(
			AuthenticationMetadataProvider authnProvider, QueryBus queryBus) {
		var interceptor = new AuthenticationQueryDispatchInterceptor(authnProvider);
		queryBus.registerDispatchInterceptor(interceptor);
		return interceptor;
	}

	@Bean
	public AxonConstraintHandlerService axonConstraintHandlerService(ObjectMapper mapper,
			List<OnDecisionConstraintHandlerProvider> globalRunnableProviders,
			List<CommandMessageMappingConstraintHandlerProvider> globalCommandMessageMappingProviders,
			List<QueryMessageMappingConstraintHandlerProvider> globalQueryMappingProviders,
			List<ErrorMappingConstraintHandlerProvider> globalErrorMappingHandlerProviders,
			List<MappingConstraintHandlerProvider<?>> globalMappingProviders,
			List<ResultMessageFilterPredicateConstraintHandlerProvider<?>> filterPredicateProviders,
			List<ResultMessageMappingConstraintHandlerProvider<?>> resulteMappingProviders) {
		return new AxonConstraintHandlerService(mapper, globalRunnableProviders, globalCommandMessageMappingProviders,
				globalQueryMappingProviders, globalErrorMappingHandlerProviders, globalMappingProviders,
				filterPredicateProviders, resulteMappingProviders);
	}

	@Bean
	public SaplQueryGateway queryGateway(QueryBus queryBus,
			List<MessageDispatchInterceptor<? super QueryMessage<?, ?>>> dispatchInterceptors) {
		return new SaplQueryGateway(queryBus, dispatchInterceptors);
	}

	@Bean
	SaplQueryUpdateEmitter updateEmitter(AxonConstraintHandlerService axonConstraintEnforcementService) {
		return new SaplQueryUpdateEmitter(Optional.empty(), axonConstraintEnforcementService);
	}

	@Bean
	SaplHandlerEnhancer saplEnhancer(PolicyDecisionPoint pdp,
			AxonConstraintHandlerService axonConstraintEnforcementService, SaplQueryUpdateEmitter emitter,
			AxonAuthorizationSubscriptionBuilderService subscriptionBuilder, ObjectMapper mapper) {
		return new SaplHandlerEnhancer(pdp, axonConstraintEnforcementService, emitter, subscriptionBuilder);
	}

	@Bean
	AxonAuthorizationSubscriptionBuilderService subscriptionBuilder() {
		return new AxonAuthorizationSubscriptionBuilderService(new ObjectMapper());
	}

}
