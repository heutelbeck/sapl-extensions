package io.sapl.axon.query;

import static org.axonframework.messaging.responsetypes.ResponseTypes.instanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static reactor.test.StepVerifier.create;

import java.util.Collection;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import org.axonframework.queryhandling.GenericQueryMessage;
import org.axonframework.queryhandling.QueryHandler;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.queryhandling.QueryUpdateEmitter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.axon.annotations.PostHandleEnforce;
import io.sapl.axon.annotations.PreHandleEnforce;
import io.sapl.axon.configuration.SaplAutoConfiguration;
import io.sapl.axon.constraints.api.AxonQueryMessageMappingConstraintHandlerProvider;
import io.sapl.axon.constraints.api.AxonRunnableConstraintHandlerProvider;
import io.sapl.axon.query.ConstraintHandlingTest.TestScenarioConfiguration;
import io.sapl.axon.temp.util.LogUtil;
import io.sapl.spring.constraints.api.ConsumerConstraintHandlerProvider;
import io.sapl.spring.constraints.api.ErrorHandlerProvider;
import io.sapl.spring.constraints.api.ErrorMappingConstraintHandlerProvider;
import io.sapl.spring.constraints.api.MappingConstraintHandlerProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@SpringBootTest
@Import(TestScenarioConfiguration.class)
public class ConstraintHandlingTest {
	private static final String CONSUME_ERROR      = "consume error";
	private static final String MODIFY_ERROR       = "modify error";
	private static final String CONSUME_RESULT     = "consume result";
	private static final String MODIFY_RESULT      = "modify result";
	private static final String MODIFIED_RESULT    = "this is a modified result";
	private static final String CONSUME_QUERY      = "consume query";
	private static final String MODIFIED_QUERY     = "modifiedQuery";
	private static final String MODIFY_QUERY       = "modifyQuery";
	private static final String ON_DECISION_DO     = "onDecisionDo";
	private static final String POST_HANDLE_QUERY  = "PostHandleEnforceQuery";
	private static final String PRE_HANDLE_QUERY   = "PreHandleEnforceQuery";
	private static final String QUERY              = "Query Content";
	private static final String RESOURCE           = "Resource Description";
	private static final String RESOURCE_EXPR      = "'" + RESOURCE + "'";
	private static final String FAILING_PRE_QUERY  = "failingPreQuery";
	private static final String FAILING_POST_QUERY = "failingPostQuery";

	private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

	@MockBean
	PolicyDecisionPoint pdp;

	@Autowired
	SaplQueryGateway queryGateway;

	@Autowired
	QueryUpdateEmitter emitter;

	@SpyBean
	OnDecisionProvider onDecisionProvider;

	@SpyBean
	QuerMappingProvider querMappingProvider;

	@SpyBean
	QueryMessageConsumerProvider queryMessageConsumerProvider;

	@SpyBean
	ResultMappingProvider resultMappingProvider;

	@SpyBean
	ResultConsumerProvider resultConsumerProvider;

	@SpyBean
	ErrorMappingProvider errorMappingProvider;

	@SpyBean
	ErrorConsumerProvider errorConsumerProvider;

	@Test
	void when_preHandlerSecuredQueryAndPermitWithUnknownAdvice_then_accessGranted() {
		var constraints = JSON.arrayNode();
		constraints.add(JSON.textNode("unknown constraint"));
		when(pdp.decide(any(AuthorizationSubscription.class)))
				.thenReturn(Flux.just(AuthorizationDecision.PERMIT.withAdvice(constraints)));

		var result = Mono.fromFuture(queryGateway.query(PRE_HANDLE_QUERY, QUERY, instanceOf(String.class)));

		create(result).expectNext(QUERY).verifyComplete();

		verify(pdp, times(1)).decide(any(AuthorizationSubscription.class));
	}

	@Test
	void when_preHandlerSecuredQueryAndPermitWithUnknownObligation_then_accessDenied() {
		var obligations = JSON.arrayNode();
		obligations.add(JSON.textNode("unknown obligation"));
		when(pdp.decide(any(AuthorizationSubscription.class)))
				.thenReturn(Flux.just(AuthorizationDecision.PERMIT.withObligations(obligations)));

		var result = Mono.fromFuture(queryGateway.query(PRE_HANDLE_QUERY, QUERY, instanceOf(String.class)));

		create(result).expectError(AccessDeniedException.class).verify();

		verify(pdp, times(1)).decide(any(AuthorizationSubscription.class));
	}

	@Test
	void when_preHandlerSecuredQueryAndPermitWithOnDecisionObligation_then_accessGranted() {
		var obligations = JSON.arrayNode();
		obligations.add(JSON.textNode(ON_DECISION_DO));
		when(pdp.decide(any(AuthorizationSubscription.class)))
				.thenReturn(Flux.just(AuthorizationDecision.PERMIT.withObligations(obligations)));

		var result = Mono.fromFuture(queryGateway.query(PRE_HANDLE_QUERY, QUERY, instanceOf(String.class)));

		create(result).expectNext(QUERY).verifyComplete();

		verify(pdp, times(1)).decide(any(AuthorizationSubscription.class));
		verify(onDecisionProvider, times(1)).run();
	}

	@Test
	void when_preHandlerSecuredQueryAndPermitWithQueryMapperObligation_then_accessGrantedAndHandlerEnforced() {
		var obligations = JSON.arrayNode();
		obligations.add(JSON.textNode(MODIFY_QUERY));
		obligations.add(JSON.textNode(CONSUME_QUERY));
		when(pdp.decide(any(AuthorizationSubscription.class)))
				.thenReturn(Flux.just(AuthorizationDecision.PERMIT.withObligations(obligations)));

		var result = Mono.fromFuture(queryGateway.query(PRE_HANDLE_QUERY, QUERY, instanceOf(String.class)));

		create(result).expectNext(MODIFIED_QUERY).verifyComplete();

		verify(pdp, times(1)).decide(any(AuthorizationSubscription.class));
		verify(querMappingProvider, times(1)).map(any());
		verify(queryMessageConsumerProvider, times(1)).accept(any());
	}

	@Test
	void when_preHandlerSecuredQueryAndPermitWithResultMapperObligation_then_accessGrantedAndHandlerEnforced() {
		var obligations = JSON.arrayNode();
		obligations.add(JSON.textNode(MODIFY_RESULT));
		obligations.add(JSON.textNode(CONSUME_RESULT));
		when(pdp.decide(any(AuthorizationSubscription.class)))
				.thenReturn(Flux.just(AuthorizationDecision.PERMIT.withObligations(obligations)));

		var result = Mono.fromFuture(queryGateway.query(PRE_HANDLE_QUERY, QUERY, instanceOf(String.class)));

		create(result).expectNext(MODIFIED_RESULT).verifyComplete();

		verify(pdp, times(1)).decide(any(AuthorizationSubscription.class));
		verify(resultMappingProvider, times(1)).map(any());
		verify(resultConsumerProvider, times(1)).accept(any());
	}

	@Test
	void when_preHandlerSecuredQueryAndPermitWithErrorObligation_then_failsWithModifiedError() {
		var obligations = JSON.arrayNode();
		obligations.add(JSON.textNode(MODIFY_ERROR));
		obligations.add(JSON.textNode(CONSUME_ERROR));
		when(pdp.decide(any(AuthorizationSubscription.class)))
				.thenReturn(Flux.just(AuthorizationDecision.PERMIT.withObligations(obligations)));

		var result = Mono.fromFuture(queryGateway.query(FAILING_PRE_QUERY, QUERY, instanceOf(String.class)));

		create(result).expectError(IllegalArgumentException.class).verify();

		verify(pdp, times(1)).decide(any(AuthorizationSubscription.class));
		verify(errorMappingProvider, times(1)).map(any());
		verify(errorConsumerProvider, times(1)).accept(any());
	}

	////////////////////////////////////////////////
	
	@Test
	void when_postHandlerSecuredQueryAndPermitWithUnknownAdvice_then_accessGranted() {
		var advice = JSON.arrayNode();
		advice.add(JSON.textNode("unknown constraint"));
		when(pdp.decide(any(AuthorizationSubscription.class)))
				.thenReturn(Flux.just(AuthorizationDecision.PERMIT.withAdvice(advice)));

		var result = Mono.fromFuture(queryGateway.query(POST_HANDLE_QUERY, QUERY, instanceOf(String.class)));

		create(result).expectNext(QUERY).verifyComplete();

		verify(pdp, times(1)).decide(any(AuthorizationSubscription.class));
	}

	@Test
	void when_postHandlerSecuredQueryAndPermitWithUnknownObligation_then_accessDenied() {
		var obligations = JSON.arrayNode();
		obligations.add(JSON.textNode("unknown constraint"));
		when(pdp.decide(any(AuthorizationSubscription.class)))
				.thenReturn(Flux.just(AuthorizationDecision.PERMIT.withObligations(obligations)));

		var result = Mono.fromFuture(queryGateway.query(POST_HANDLE_QUERY, QUERY, instanceOf(String.class)));

		create(result).expectError(AccessDeniedException.class).verify();

		verify(pdp, times(1)).decide(any(AuthorizationSubscription.class));
	}

	@Test
	void when_postHandlerSecuredQueryAndPermitWithOnDecisionObligation_then_accessGranted() {
		var obligations = JSON.arrayNode();
		obligations.add(JSON.textNode(ON_DECISION_DO));
		when(pdp.decide(any(AuthorizationSubscription.class)))
				.thenReturn(Flux.just(AuthorizationDecision.PERMIT.withObligations(obligations)));

		var result = Mono.fromFuture(queryGateway.query(PRE_HANDLE_QUERY, QUERY, instanceOf(String.class)));

		create(result).expectNext(QUERY).verifyComplete();

		verify(pdp, times(1)).decide(any(AuthorizationSubscription.class));
		verify(onDecisionProvider, times(1)).run();
	}

	@Test
	void when_postHandlerSecuredQueryAndPermitWithQueryMapperObligation_then_accessDeniedCauseOfNotAbleToHandle() {
		var obligations = JSON.arrayNode();
		obligations.add(JSON.textNode(MODIFY_QUERY));
		obligations.add(JSON.textNode(CONSUME_QUERY));
		when(pdp.decide(any(AuthorizationSubscription.class)))
				.thenReturn(Flux.just(AuthorizationDecision.PERMIT.withObligations(obligations)));

		var result = Mono.fromFuture(queryGateway.query(POST_HANDLE_QUERY, QUERY, instanceOf(String.class)));

		create(result).expectError(AccessDeniedException.class).verify();

		verify(pdp, times(1)).decide(any(AuthorizationSubscription.class));
		verify(querMappingProvider, times(0)).map(any());
		verify(queryMessageConsumerProvider, times(0)).accept(any());
	}

	@Test
	void when_postHandlerSecuredQueryAndPermitWithResultMapperObligation_then_accessGrantedAndHandlerEnforced() {
		var obligations = JSON.arrayNode();
		obligations.add(JSON.textNode(MODIFY_RESULT));
		obligations.add(JSON.textNode(CONSUME_RESULT));
		when(pdp.decide(any(AuthorizationSubscription.class)))
				.thenReturn(Flux.just(AuthorizationDecision.PERMIT.withObligations(obligations)));

		var result = Mono.fromFuture(queryGateway.query(POST_HANDLE_QUERY, QUERY, instanceOf(String.class)));

		create(result).expectNext(MODIFIED_RESULT).verifyComplete();

		verify(pdp, times(1)).decide(any(AuthorizationSubscription.class));
		verify(resultMappingProvider, times(1)).map(any());
		verify(resultConsumerProvider, times(1)).accept(any());
	}

	@Test
	void when_postHandlerSecuredQueryAndPermitWithErrorObligation_then_failsWithModifiedError() {
		var obligations = JSON.arrayNode();
		obligations.add(JSON.textNode(MODIFY_ERROR));
		obligations.add(JSON.textNode(CONSUME_ERROR));
		when(pdp.decide(any(AuthorizationSubscription.class)))
				.thenReturn(Flux.just(AuthorizationDecision.PERMIT.withObligations(obligations)));

		var result = Mono.fromFuture(queryGateway.query(FAILING_POST_QUERY, QUERY, instanceOf(String.class)));

		create(result).expectError(IllegalArgumentException.class).verify();

		verify(pdp, times(1)).decide(any(AuthorizationSubscription.class));
		verify(errorMappingProvider, times(1)).map(any());
		verify(errorConsumerProvider, times(1)).accept(any());
	}

	static class OnDecisionProvider implements AxonRunnableConstraintHandlerProvider {

		@Override
		public boolean isResponsible(JsonNode constraint) {
			return constraint.isTextual() && ON_DECISION_DO.equals(constraint.textValue());
		}

		@Override
		public Collection<Signal> getSignals() {
			return Set.of(Signal.ON_DECISION);
		}

		@Override
		public Runnable getHandler(JsonNode constraint) {
			return this::run;
		}

		public void run() {
			// NOOP
		}

	}

	static class QuerMappingProvider implements AxonQueryMessageMappingConstraintHandlerProvider {

		@Override
		public boolean isResponsible(JsonNode constraint) {
			return constraint.isTextual() && MODIFY_QUERY.equals(constraint.textValue());
		}

		@Override
		public Function<QueryMessage<?, ?>, QueryMessage<?, ?>> getHandler(JsonNode constraint) {
			return this::map;
		}

		public QueryMessage<?, ?> map(QueryMessage<?, ?> query) {
			LogUtil.dumpQueryMessage("Query before modification", query);
			var updatedQuery = new GenericQueryMessage<>(MODIFIED_QUERY, query.getQueryName(), query.getResponseType())
					.andMetaData(query.getMetaData());
			return updatedQuery;
		}

	}

	@SuppressWarnings("rawtypes")
	static class QueryMessageConsumerProvider implements ConsumerConstraintHandlerProvider<QueryMessage> {

		@Override
		public boolean isResponsible(JsonNode constraint) {
			return constraint.isTextual() && CONSUME_QUERY.equals(constraint.textValue());
		}

		@Override
		public Class<QueryMessage> getSupportedType() {
			return QueryMessage.class;
		}

		@Override
		public Consumer<QueryMessage> getHandler(JsonNode constraint) {
			return this::accept;
		}

		public void accept(QueryMessage message) {
			// NOOP
		}
	}

	public static class ResultMappingProvider implements MappingConstraintHandlerProvider<String> {

		@Override
		public boolean isResponsible(JsonNode constraint) {
			return constraint.isTextual() && MODIFY_RESULT.equals(constraint.textValue());
		}

		@Override
		public Class<String> getSupportedType() {
			return String.class;
		}

		@Override
		public Function<String, String> getHandler(JsonNode constraint) {
			return this::map;
		}

		public String map(String original) {
			log.info("MOD THE RESULT NOW");
			return MODIFIED_RESULT;
		}

	}

	static class ResultConsumerProvider implements ConsumerConstraintHandlerProvider<String> {

		@Override
		public boolean isResponsible(JsonNode constraint) {
			return constraint.isTextual() && CONSUME_RESULT.equals(constraint.textValue());
		}

		@Override
		public Class<String> getSupportedType() {
			return String.class;
		}

		@Override
		public Consumer<String> getHandler(JsonNode constraint) {
			return this::accept;
		}

		public void accept(String result) {
			// NOOP
		}
	}

	public static class ErrorMappingProvider implements ErrorMappingConstraintHandlerProvider {

		@Override
		public boolean isResponsible(JsonNode constraint) {
			return constraint.isTextual() && MODIFY_ERROR.equals(constraint.textValue());
		}

		@Override
		public Function<Throwable, Throwable> getHandler(JsonNode constraint) {
			return this::map;
		}

		public Throwable map(Throwable original) {
			return new IllegalArgumentException(original.getMessage(), original.getCause());
		}

	}

	static class ErrorConsumerProvider implements ErrorHandlerProvider {

		@Override
		public boolean isResponsible(JsonNode constraint) {
			return constraint.isTextual() && CONSUME_ERROR.equals(constraint.textValue());
		}

		@Override
		public Consumer<Throwable> getHandler(JsonNode constraint) {
			return this::accept;
		}

		public void accept(Throwable error) {
			// NOOP
		}
	}

	// @formatter:off
	@RequiredArgsConstructor
	static class Projection {
		@QueryHandler(queryName = PRE_HANDLE_QUERY)
		@PreHandleEnforce(action="'"+PRE_HANDLE_QUERY+"'", resource=RESOURCE_EXPR)
		public String handlePreEnforce(String query) { return query; }
	
		@QueryHandler(queryName = FAILING_PRE_QUERY)
		@PreHandleEnforce(action="'"+FAILING_PRE_QUERY+"'", resource=RESOURCE_EXPR)
		public String handlePreEnforceFail(String query) { throw new RuntimeException("PANIC"); }
			
		@QueryHandler(queryName = POST_HANDLE_QUERY)
		@PostHandleEnforce(action="'"+POST_HANDLE_QUERY+"'", resource=RESOURCE_EXPR)
		public String handlePostEnforce(String query) { return query; }

		@QueryHandler(queryName = FAILING_POST_QUERY)
		@PostHandleEnforce(action="'"+FAILING_POST_QUERY+"'", resource=RESOURCE_EXPR)
		public String handlePostEnforceFail(String query) { throw new RuntimeException("PANIC"); }
	}
	// @formatter:on

	@DynamicPropertySource
	static void registerAxonProperties(DynamicPropertyRegistry registry) {
		registry.add("axon.axonserver.enabled", () -> "false");
	}

	@Configuration
	@Import({ SaplAutoConfiguration.class })
	static class TestScenarioConfiguration {

		@Bean
		Projection projection() {
			return new Projection();
		}

		@Bean
		OnDecisionProvider onDecisionProvider() {
			return new OnDecisionProvider();
		}

		@Bean
		QuerMappingProvider querMappingProvider() {
			return new QuerMappingProvider();
		}

		@Bean
		QueryMessageConsumerProvider queryMessageConsumerProvider() {
			return new QueryMessageConsumerProvider();
		}

		@Bean
		ResultMappingProvider resultMappingProvider() {
			return new ResultMappingProvider();
		}

		@Bean
		ResultConsumerProvider ResultConsumerProvider() {
			return new ResultConsumerProvider();
		}

		@Bean
		ErrorMappingProvider errorMappingProvider() {
			return new ErrorMappingProvider();
		}

		@Bean
		ErrorConsumerProvider errorConsumerProvider() {
			return new ErrorConsumerProvider();
		}

	}

	@SpringBootApplication
	static class TestApplication {

		public static void main(String[] args) {
			SpringApplication.run(TestApplication.class, args);
		}
	}

}
