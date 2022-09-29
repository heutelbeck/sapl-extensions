package io.sapl.axon.configuration;

import java.util.List;
import java.util.Optional;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.extensions.reactor.commandhandling.gateway.DefaultReactorCommandGateway;
import org.axonframework.extensions.reactor.commandhandling.gateway.ReactorCommandGateway;
import org.axonframework.extensions.reactor.messaging.ReactorMessageDispatchInterceptor;
import org.axonframework.extensions.reactor.queryhandling.gateway.DefaultReactorQueryGateway;
import org.axonframework.extensions.reactor.queryhandling.gateway.ReactorQueryGateway;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.queryhandling.QueryUpdateEmitter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thoughtworks.xstream.XStream;

import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.axon.authentication.reactive.ReactiveAuthenticationCommandDispatchInterceptor;
import io.sapl.axon.authentication.reactive.ReactiveAuthenticationQueryDispatchInterceptor;
import io.sapl.axon.authentication.reactive.ReactiveAuthenticationSupplier;
import io.sapl.axon.authentication.reactive.ReactorAuthenticationSupplier;
import io.sapl.axon.authentication.servlet.AuthenticationCommandDispatchInterceptor;
import io.sapl.axon.authentication.servlet.AuthenticationQueryDispatchInterceptor;
import io.sapl.axon.authentication.servlet.AuthenticationSupplier;
import io.sapl.axon.authentication.servlet.ServletAuthenticationSupplier;
import io.sapl.axon.constrainthandling.ConstraintHandlerService;
import io.sapl.axon.constrainthandling.api.CommandConstraintHandlerProvider;
import io.sapl.axon.constrainthandling.api.OnDecisionConstraintHandlerProvider;
import io.sapl.axon.constrainthandling.api.QueryConstraintHandlerProvider;
import io.sapl.axon.constrainthandling.api.ResultConstraintHandlerProvider;
import io.sapl.axon.constrainthandling.api.UpdateFilterConstraintHandlerProvider;
import io.sapl.axon.constrainthandling.provider.ResponseMessagePayloadFilterPredicateProvider;
import io.sapl.axon.constrainthandling.provider.ResponseMessagePayloadFilterProvider;
import io.sapl.axon.queryhandling.SaplQueryGateway;
import io.sapl.axon.queryhandling.SaplQueryUpdateEmitter;
import io.sapl.axon.subscription.AuthorizationSubscriptionBuilderService;
import io.sapl.spring.constraints.api.ErrorMappingConstraintHandlerProvider;
import io.sapl.spring.constraints.api.MappingConstraintHandlerProvider;
import lombok.extern.slf4j.Slf4j;

/**
 * 
 * This AutoConfiguration class is registered in META-INF/spring.factories of
 * this project, which ensures, that all Spring Boot applications using this
 * module as a dependency will load this configuration class.
 * 
 * @author Dominic Heutelbeck
 * @since 2.1.0
 */
@Slf4j
@Configuration
@Import(SaplAxonProperties.class)
public class SaplAutoConfiguration {

	/**
	 * For PEPs based on @EnforceRecoverableUpdatesIfDenied, the class @see
	 * io.sapl.axon.queryhandlingRecoverableResponse must be serializable. XStream
	 * uses a whitelist for determining which classes may be serialized. This method
	 * registers the RecoverableResponse.
	 * 
	 * @param xStream XStream serialization if present.
	 */
	@Autowired
	void whitelistSaplObjectsInXStream(Optional<XStream> xStream) {
		xStream.ifPresent(xStr -> log.trace("Allow 'io.sapl.**' classes in XStream "));
		xStream.ifPresent(xStr -> xStr.allowTypesByWildcard(new String[] { "io.sapl.**" }));
	}

	/**
	 * 
	 * The @see io.sapl.axon.authentication.AuthenticationMetadataProvider is
	 * responsible for identifying the user triggering a Command or Query and to
	 * provide matching metadata to be added by dispatch interceptors.
	 * 
	 * @param mapper The applications ObjectMapper.
	 * @return An AuthenticationMetadataProvider.
	 */
	@Bean
	AuthenticationSupplier authenticationMetadataProvider(ObjectMapper mapper) {
		log.trace("Deploy Spring ServletAuthenticationSupplier");
		return new ServletAuthenticationSupplier(mapper);
	}

	/**
	 * 
	 * The @see io.sapl.axon.authentication.AuthenticationMetadataProvider is
	 * responsible for identifying the user triggering a Command or Query and to
	 * provide matching metadata to be added by dispatch interceptors.
	 * 
	 * @param mapper The applications ObjectMapper.
	 * @return An AuthenticationMetadataProvider.
	 */
	@Bean
	@ConditionalOnClass(ReactorMessageDispatchInterceptor.class)
	ReactiveAuthenticationSupplier reactiveAuthenticationMetadataProvider(ObjectMapper mapper) {
		log.trace("Deploy Spring ReactiveAuthenticationSupplier");
		return new ReactorAuthenticationSupplier(mapper);
	}

	/**
	 * The default AutoConfiguration of the reactor extension does not automatically
	 * inject interceptors.
	 * 
	 * @param commandBus  the CommandBus
	 * @param interceptor the authn interceptor
	 * @return ReactorCommandGateway with Authn interceptor
	 */
	@Bean
	@ConditionalOnClass(ReactorMessageDispatchInterceptor.class)
	ReactorCommandGateway reactiveCommandGateway(CommandBus commandBus, ReactiveAuthenticationSupplier authnSupplier) {
		return DefaultReactorCommandGateway.builder().commandBus(commandBus)
				.dispatchInterceptors(new ReactiveAuthenticationCommandDispatchInterceptor(authnSupplier)).build();
	}

	/**
	 * The default AutoConfiguration of the reactor extension does not automatically
	 * inject interceptors.
	 * 
	 * @param queryBus    the QueryBus
	 * @param interceptor the authn interceptor
	 * @return ReactorQueryGateway with Authn interceptor
	 */
	@Bean
	@ConditionalOnClass(ReactorMessageDispatchInterceptor.class)
	ReactorQueryGateway reactiveQueryGateway(QueryBus queryBus, ReactiveAuthenticationSupplier authnSupplier) {
		return DefaultReactorQueryGateway.builder().queryBus(queryBus)
				.dispatchInterceptors(new ReactiveAuthenticationQueryDispatchInterceptor(authnSupplier)).build();
	}

	/**
	 * 
	 * MessageDispatchInterceptor for authenticating command messages.
	 * 
	 * @param authnSupplier The applications AuthenticationSupplier.
	 * @param commandBus    The Axon Command Bus.
	 * @return A MessageDispatchInterceptor for adding authentication metadata to
	 *         commands.
	 */
	@Bean
	AuthenticationCommandDispatchInterceptor authenticationCommandDispatchInterceptor(
			AuthenticationSupplier authnSupplier, CommandBus commandBus) {
		log.trace("Deploy AuthenticationCommandDispatchInterceptor");
		var interceptor = new AuthenticationCommandDispatchInterceptor(authnSupplier);
		commandBus.registerDispatchInterceptor(interceptor);
		return interceptor;
	}

	/**
	 * 
	 * MessageDispatchInterceptor for authenticating query messages.
	 * 
	 * @param authnSupplier The applications AuthenticationSupplier.
	 * @param queryBus      The Axon Query Bus.
	 * @return A MessageDispatchInterceptor for adding authentication metadata to
	 *         queries.
	 */
	@Bean
	AuthenticationQueryDispatchInterceptor authenticationQueryDispatchInterceptor(AuthenticationSupplier authnSupplier,
			QueryBus queryBus) {
		log.trace("Deploy AuthenticationQueryDispatchInterceptor");
		var interceptor = new AuthenticationQueryDispatchInterceptor(authnSupplier);
		queryBus.registerDispatchInterceptor(interceptor);
		return interceptor;
	}

	/**
	 * The ConstraintHandlerService provides functionality to check the application
	 * for capabilities to enforce constraints (obligations/advice) specified by the
	 * PDP and to create customized bundles of handlers for individual authorization
	 * decisions.
	 * 
	 * @param mapper                               The applications ObjectMapper.
	 * @param parameterResolver                    The Axon ParameterResolverFactory
	 *                                             for injecting arguments in
	 *                                             command handler methods..
	 * @param globalRunnableProviders              All
	 *                                             OnDecisionConstraintHandlerProvider
	 *                                             implementation in the
	 *                                             ApplicationContext.
	 * @param globalCommandMessageMappingProviders All
	 *                                             CommandConstraintHandlerProvider
	 *                                             implementation in the
	 *                                             ApplicationContext.
	 * @param globalQueryMappingProviders          All
	 *                                             QueryConstraintHandlerProvider
	 *                                             implementation in the
	 *                                             ApplicationContext.
	 * @param globalErrorMappingHandlerProviders   All
	 *                                             ErrorMappingConstraintHandlerProvider
	 *                                             implementation in the
	 *                                             ApplicationContext.
	 * @param globalMappingProviders               All
	 *                                             MappingConstraintHandlerProvider
	 *                                             implementation in the
	 *                                             ApplicationContext.
	 * @param filterPredicateProviders             All
	 *                                             UpdateFilterConstraintHandlerProvider
	 *                                             implementation in the
	 *                                             ApplicationContext.
	 * @param resulteMappingProviders              All
	 *                                             ResultConstraintHandlerProvider
	 *                                             implementation in the
	 *                                             ApplicationContext.
	 * @return The ConstraintHandlerService.
	 */
	@Bean
	ConstraintHandlerService axonConstraintHandlerService(ObjectMapper mapper,
			ParameterResolverFactory parameterResolver,
			List<OnDecisionConstraintHandlerProvider> globalRunnableProviders,
			List<CommandConstraintHandlerProvider> globalCommandMessageMappingProviders,
			List<QueryConstraintHandlerProvider> globalQueryMappingProviders,
			List<ErrorMappingConstraintHandlerProvider> globalErrorMappingHandlerProviders,
			List<MappingConstraintHandlerProvider<?>> globalMappingProviders,
			List<UpdateFilterConstraintHandlerProvider> filterPredicateProviders,
			List<ResultConstraintHandlerProvider> resulteMappingProviders) {
		log.trace("Deploy ConstraintHandlerService");
		return new ConstraintHandlerService(mapper, parameterResolver, globalRunnableProviders,
				globalCommandMessageMappingProviders, globalQueryMappingProviders, globalErrorMappingHandlerProviders,
				globalMappingProviders, filterPredicateProviders, resulteMappingProviders);
	}

	/**
	 * A specialized QueryGateway offering explicit methods for recoverable
	 * subscription queries. I.e., subscription queries which can recover from an
	 * AccessDeniedException and continue to consume updates one access is granted
	 * again.
	 * 
	 * @param queryBus             The Axon QueryBus
	 * @param dispatchInterceptors All
	 *                             {@code MessageDispatchInterceptor<QueryMessage>}
	 *                             in the application context.
	 * @return A query gateway supporting recoverable subscription queries.
	 */
	@Bean
	SaplQueryGateway queryGateway(QueryBus queryBus,
			List<MessageDispatchInterceptor<? super QueryMessage<?, ?>>> dispatchInterceptors) {
		log.trace("Deploy SaplQueryGateway");
		return new SaplQueryGateway(queryBus, dispatchInterceptors);
	}

	@Bean
	@Primary
	QueryUpdateEmitter updateEmitter(ConstraintHandlerService axonConstraintEnforcementService) {
		log.trace("Deploy SaplQueryUpdateEmitter");
		return new SaplQueryUpdateEmitter(Optional.empty(), axonConstraintEnforcementService);
	}

	@Bean
	SaplHandlerEnhancer saplEnhancer(PolicyDecisionPoint pdp, ConstraintHandlerService axonConstraintEnforcementService,
			SaplQueryUpdateEmitter emitter, AuthorizationSubscriptionBuilderService subscriptionBuilder,
			ObjectMapper mapper, SaplAxonProperties properties) {
		log.trace("Deploy SaplHandlerEnhancer");
		return new SaplHandlerEnhancer(pdp, axonConstraintEnforcementService, emitter, subscriptionBuilder, properties);
	}

	@Bean
	@ConditionalOnMissingBean
	AuthorizationSubscriptionBuilderService subscriptionBuilder(ObjectMapper mapper) {
		log.trace("Deploy AuthorizationSubscriptionBuilderService");
		return new AuthorizationSubscriptionBuilderService(mapper);
	}

	/**
	 * @param mapper The application's ObjectMapper.
	 * @return ResponseMessagePayloadFilterProvider for filtering obligations.
	 */
	@Bean
	ResponseMessagePayloadFilterProvider responsePayloadFilterProvider(ObjectMapper mapper) {
		return new ResponseMessagePayloadFilterProvider(mapper);
	}

	/**
	 * @param mapper The application's ObjectMapper.
	 * @return ResponseMessagePayloadFilterProvider for filtering obligations.
	 */
	@Bean
	ResponseMessagePayloadFilterPredicateProvider responseMessagePayloadFilterPredicateProvider(ObjectMapper mapper) {
		return new ResponseMessagePayloadFilterPredicateProvider(mapper);
	}

}
