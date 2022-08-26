package io.sapl.axon.configuration;

import java.util.List;
import java.util.Optional;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thoughtworks.xstream.XStream;

import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.axon.authentication.AuthenticationCommandDispatchInterceptor;
import io.sapl.axon.authentication.AuthenticationMetadataProvider;
import io.sapl.axon.authentication.AuthenticationQueryDispatchInterceptor;
import io.sapl.axon.authentication.SpringSecurityAuthenticationMetadataProvider;
import io.sapl.axon.constrainthandling.ConstraintHandlerService;
import io.sapl.axon.constrainthandling.api.CommandConstraintHandlerProvider;
import io.sapl.axon.constrainthandling.api.OnDecisionConstraintHandlerProvider;
import io.sapl.axon.constrainthandling.api.QueryConstraintHandlerProvider;
import io.sapl.axon.constrainthandling.api.ResultConstraintHandlerProvider;
import io.sapl.axon.constrainthandling.api.UpdateFilterConstraintHandlerProvider;
import io.sapl.axon.queryhandling.SaplQueryGateway;
import io.sapl.axon.queryhandling.SaplQueryUpdateEmitter;
import io.sapl.axon.subscription.AuthorizationSubscriptionBuilderService;
import io.sapl.spring.constraints.api.ErrorMappingConstraintHandlerProvider;
import io.sapl.spring.constraints.api.MappingConstraintHandlerProvider;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class SaplAutoConfiguration {

	@Autowired
	void whitelistSaplObjectsInXStream(Optional<XStream> xStream) {
		xStream.ifPresent(xStr -> log.trace("Allow 'io.sapl.**' classes in XStream "));
		xStream.ifPresent(xStr -> xStr.allowTypesByWildcard(new String[] { "io.sapl.**" }));
	}

	@Bean
	@ConditionalOnMissingBean
	AuthenticationMetadataProvider authenticationMetadataProvider(ObjectMapper mapper) {
		log.trace("Deploy Spring AuthenticationMetadataProvider");
		return new SpringSecurityAuthenticationMetadataProvider(mapper);
	}

	@Bean
	AuthenticationCommandDispatchInterceptor authenticationCommandDispatchInterceptor(
			AuthenticationMetadataProvider authnProvider, CommandBus commandBus) {
		log.trace("Deploy AuthenticationCommandDispatchInterceptor");
		var interceptor = new AuthenticationCommandDispatchInterceptor(authnProvider);
		commandBus.registerDispatchInterceptor(interceptor);
		return interceptor;
	}

	@Bean
	AuthenticationQueryDispatchInterceptor authenticationQueryDispatchInterceptor(
			AuthenticationMetadataProvider authnProvider, QueryBus queryBus) {
		log.trace("Deploy AuthenticationQueryDispatchInterceptor");
		var interceptor = new AuthenticationQueryDispatchInterceptor(authnProvider);
		queryBus.registerDispatchInterceptor(interceptor);
		return interceptor;
	}

	@Bean
	public ConstraintHandlerService axonConstraintHandlerService(ObjectMapper mapper,
			ParameterResolverFactory parameterResolver,
			List<OnDecisionConstraintHandlerProvider> globalRunnableProviders,
			List<CommandConstraintHandlerProvider> globalCommandMessageMappingProviders,
			List<QueryConstraintHandlerProvider> globalQueryMappingProviders,
			List<ErrorMappingConstraintHandlerProvider> globalErrorMappingHandlerProviders,
			List<MappingConstraintHandlerProvider<?>> globalMappingProviders,
			List<UpdateFilterConstraintHandlerProvider<?>> filterPredicateProviders,
			List<ResultConstraintHandlerProvider<?>> resulteMappingProviders) {
		log.trace("Deploy ConstraintHandlerService");
		return new ConstraintHandlerService(mapper, parameterResolver, globalRunnableProviders,
				globalCommandMessageMappingProviders, globalQueryMappingProviders, globalErrorMappingHandlerProviders,
				globalMappingProviders, filterPredicateProviders, resulteMappingProviders);
	}

	@Bean
	public SaplQueryGateway queryGateway(QueryBus queryBus,
			List<MessageDispatchInterceptor<? super QueryMessage<?, ?>>> dispatchInterceptors) {
		log.trace("Deploy SaplQueryGateway");
		return new SaplQueryGateway(queryBus, dispatchInterceptors);
	}

	@Bean
	SaplQueryUpdateEmitter updateEmitter(ConstraintHandlerService axonConstraintEnforcementService) {
		log.trace("Deploy SaplQueryUpdateEmitter");
		return new SaplQueryUpdateEmitter(Optional.empty(), axonConstraintEnforcementService);
	}

	@Bean
	SaplHandlerEnhancer saplEnhancer(PolicyDecisionPoint pdp, ConstraintHandlerService axonConstraintEnforcementService,
			SaplQueryUpdateEmitter emitter, AuthorizationSubscriptionBuilderService subscriptionBuilder,
			ObjectMapper mapper) {
		log.trace("Deploy SaplHandlerEnhancer");
		return new SaplHandlerEnhancer(pdp, axonConstraintEnforcementService, emitter, subscriptionBuilder);
	}

	@Bean
	@ConditionalOnMissingBean
	AuthorizationSubscriptionBuilderService subscriptionBuilder(ObjectMapper mapper) {
		log.trace("Deploy AuthorizationSubscriptionBuilderService");
		return new AuthorizationSubscriptionBuilderService(mapper);
	}

}
