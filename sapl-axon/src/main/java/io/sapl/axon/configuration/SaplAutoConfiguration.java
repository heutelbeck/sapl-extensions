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
import io.sapl.axon.constraints.AxonConstraintHandlerService;
import io.sapl.axon.constraints.api.AxonRunnableConstraintHandlerProvider;
import io.sapl.axon.constraints.api.QueryMessageMappingConstraintHandlerProvider;
import io.sapl.axon.constraints.api.ResultMessageConsumerConstraintHandlerProvider;
import io.sapl.axon.constraints.api.ResultMessageFilterPredicateConstraintHandlerProvider;
import io.sapl.axon.constraints.api.ResultMessageMappingConstraintHandlerProvider;
import io.sapl.axon.interceptor.AuthenticationCommandDispatchInterceptor;
import io.sapl.axon.interceptor.AuthenticationMetadataProvider;
import io.sapl.axon.interceptor.AuthenticationQueryDispatchInterceptor;
import io.sapl.axon.interceptor.SpringSecurityAuthenticationMetadataProvider;
import io.sapl.axon.query.SaplHandlerEnhancer;
import io.sapl.axon.query.SaplQueryGateway;
import io.sapl.axon.query.SaplQueryUpdateEmitter;
import io.sapl.axon.subscriptions.AxonAuthorizationSubscriptionBuilderService;
import io.sapl.spring.constraints.api.ConsumerConstraintHandlerProvider;
import io.sapl.spring.constraints.api.ErrorHandlerProvider;
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
			List<AxonRunnableConstraintHandlerProvider> globalRunnableProviders,
			List<QueryMessageMappingConstraintHandlerProvider> globalQueryMappingProviders,
			List<ConsumerConstraintHandlerProvider<?>> globalConsumerProviders,
			List<ErrorMappingConstraintHandlerProvider> globalErrorMappingHandlerProviders,
			List<ErrorHandlerProvider> globalErrorHandlerProviders,
			List<MappingConstraintHandlerProvider<?>> globalMappingProviders,
			List<ResultMessageFilterPredicateConstraintHandlerProvider<?>> filterPredicateProviders,
			List<ResultMessageMappingConstraintHandlerProvider<?>> resulteMappingProviders,
			List<ResultMessageConsumerConstraintHandlerProvider<?>> resultConsumerProviders) {
		return new AxonConstraintHandlerService(mapper, globalRunnableProviders, globalQueryMappingProviders,
				globalConsumerProviders, globalErrorMappingHandlerProviders, globalErrorHandlerProviders,
				globalMappingProviders, filterPredicateProviders, resulteMappingProviders, resultConsumerProviders);
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
