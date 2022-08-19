package io.sapl.axon.query;

import static com.spotify.hamcrest.jackson.JsonMatchers.*;
import static com.spotify.hamcrest.pojo.IsPojo.pojo;
import static io.sapl.axon.query.TestUtilities.isAccessDenied;
import static org.axonframework.messaging.responsetypes.ResponseTypes.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static reactor.test.StepVerifier.create;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryHandler;
import org.axonframework.queryhandling.QueryUpdateEmitter;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.thoughtworks.xstream.XStream;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.axon.annotations.EnforceDropUpdatesWhileDenied;
import io.sapl.axon.annotations.EnforceRecoverableUpdatesIfDenied;
import io.sapl.axon.annotations.PostHandleEnforce;
import io.sapl.axon.annotations.PreHandleEnforce;
import io.sapl.axon.configuration.CommandAndQueryAuthenticationConfiguration;
import io.sapl.axon.constraints.AxonConstraintHandlerService;
import io.sapl.axon.query.QuerySideTestsuite.TestScenarioConfiguration;
import io.sapl.axon.subscriptions.AxonAuthorizationSubscriptionBuilderService;
import io.sapl.spring.constraints.ConstraintEnforcementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@SpringBootTest
@Import(TestScenarioConfiguration.class)
public abstract class QuerySideTestsuite {
	private static final String ANONYMOUS                        = "anonymous";
	private static final String BAD_ANNOTATIONS1                 = "BadAnnotations1";
	private static final String BAD_ANNOTATIONS2                 = "BadAnnotations2";
	private static final String BAD_ANNOTATIONS3                 = "BadAnnotations3";
	private static final String BAD_RESOURCE_SERIALIZATION_QUERY = "BadResourceSerializationQuery";
	private static final String DROP_QUERY                       = "DropQuery";
	private static final String I_WAS_REPLACED                   = "I was replaced";
	private static final String POST_HANDLE_NO_RESOURCE_QUERY    = "PostHandleNoResourceQuery";
	private static final String POST_HANDLE_QUERY                = "PostHandleEnforceQuery";
	private static final String PRE_HANDLE_QUERY                 = "PreHandleEnforceQuery";
	private static final String QUERY                            = "Query Content";
	private static final String RECOVERABLE_QUERY                = "RecoverableQuery";
	private static final String RESOURCE                         = "Resource Description";
	private static final String RESOURCE_EXPR                    = "'" + RESOURCE + "'";
	private static final String UNSECURED_QUERY                  = "UnsecuredQuery";

	private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

	@MockBean
	PolicyDecisionPoint pdp;

	@Autowired
	SaplQueryGateway queryGateway;

	@Autowired
	QueryUpdateEmitter emitter;

	@Test
	void when_unsecuredQuery_then_resultReturnsAndPdpNotCalled() {
		var result = Mono.fromFuture(queryGateway.query(UNSECURED_QUERY, QUERY, instanceOf(String.class)));
		create(result).expectNext(QUERY).verifyComplete();
		verifyNoInteractions(pdp);
	}

	@Test
	@WithMockUser(username = "user1", roles = "MANAGER")
	void when_preHandlerSecuredQueryAndPermit_then_resultReturnsAndPdpIsCalledWithSubscription() {
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));

		var result = Mono.fromFuture(queryGateway.query(PRE_HANDLE_QUERY, QUERY, instanceOf(String.class)));

		create(result).expectNext(QUERY).verifyComplete();

		verify(pdp, times(1)).decide(any(AuthorizationSubscription.class));
		assertThatSubject(is(jsonObject().where("username", is(jsonText("user1")))));
		assertThatAction(is(jsonText(PRE_HANDLE_QUERY)));
		assertThatResource(is(jsonText(RESOURCE)));
		assertThatEnvironmentNotPresent();
	}

	@Test
	void when_dropSecuredQueryAndPermit_then_resultReturnsAndPdpIsCalledWithSubscription() {
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));

		var result = Mono.fromFuture(queryGateway.query(DROP_QUERY, QUERY, instanceOf(String.class)));

		create(result).expectNext(QUERY).verifyComplete();

		verify(pdp, times(1)).decide(any(AuthorizationSubscription.class));
		assertThatSubject(is(jsonText(ANONYMOUS)));
		assertThatAction(is(jsonText(DROP_QUERY)));
		assertThatResource(is(jsonText(RESOURCE)));
		assertThatEnvironmentNotPresent();
	}

	@Test
	void when_recoverableSecuredQueryAndPermit_then_resultReturnsAndPdpIsCalledWithSubscription() {
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));

		var result = Mono.fromFuture(queryGateway.query(RECOVERABLE_QUERY, QUERY, instanceOf(String.class)));

		create(result).expectNext(QUERY).verifyComplete();

		verify(pdp, times(1)).decide(any(AuthorizationSubscription.class));
		assertThatSubject(is(jsonText(ANONYMOUS)));
		assertThatAction(is(jsonText(RECOVERABLE_QUERY)));
		assertThatResource(is(jsonText(RESOURCE)));
		assertThatEnvironmentNotPresent();
	}

	@Test
	void when_preHandlerSecuredQueryAndDeny_then_accessDenied() {
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.DENY));

		var result = Mono.fromFuture(queryGateway.query(PRE_HANDLE_QUERY, QUERY, instanceOf(String.class)));

		create(result).expectErrorMatches(isAccessDenied()).verify();
	}

	@Test
	void when_dropSecuredHandlerAndDeny_then_accessDenied() {
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.DENY));

		var result = Mono.fromFuture(queryGateway.query(DROP_QUERY, QUERY, instanceOf(String.class)));

		create(result).expectErrorMatches(isAccessDenied()).verify();
	}

	@Test
	void when_recoverableSecuredQueryAndDeny_then_accessDenied() {
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.DENY));

		var result = Mono.fromFuture(queryGateway.query(RECOVERABLE_QUERY, QUERY, instanceOf(String.class)));

		create(result).expectErrorMatches(isAccessDenied()).verify();
	}

	@Test
	void when_postHandlerSecuredQueryAndPermitWithResource_then_resultReturnsAndPdpIsCalledWithSubscriptionAndReplacementIsReturned() {
		when(pdp.decide(any(AuthorizationSubscription.class)))
				.thenReturn(Flux.just(AuthorizationDecision.PERMIT.withResource(JSON.textNode(I_WAS_REPLACED))));

		var result = Mono.fromFuture(queryGateway.query(POST_HANDLE_QUERY, QUERY, instanceOf(String.class)));

		create(result).expectNext(I_WAS_REPLACED).verifyComplete();

		verify(pdp, times(1)).decide(any(AuthorizationSubscription.class));
		assertThatSubject(is(jsonText(ANONYMOUS)));
		assertThatAction(is(jsonText(POST_HANDLE_QUERY)));
		assertThatResource(is(jsonText(RESOURCE)));
		assertThatEnvironmentNotPresent();
	}

	@Test
	void when_postHandlerSecuredQueryAndPermit_then_resultReturns() {
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));

		var result = Mono
				.fromFuture(queryGateway.query(POST_HANDLE_NO_RESOURCE_QUERY, QUERY, instanceOf(String.class)));

		create(result).expectNext(QUERY).verifyComplete();

		verify(pdp, times(1)).decide(any(AuthorizationSubscription.class));
		assertThatSubject(is(jsonText(ANONYMOUS)));
		assertThatAction(is(jsonText(POST_HANDLE_NO_RESOURCE_QUERY)));
		assertThatResource(is(jsonText(RESOURCE)));
		assertThatEnvironmentNotPresent();
	}

	@Test
	void when_postHandlerSecuredQueryAndPermitWithResourceAndResourceMarshallingPails_then_accessDenied() {
		when(pdp.decide(any(AuthorizationSubscription.class)))
				.thenReturn(Flux.just(AuthorizationDecision.PERMIT.withResource(JSON.textNode(I_WAS_REPLACED))));

		var result = Mono
				.fromFuture(queryGateway.query(BAD_RESOURCE_SERIALIZATION_QUERY, QUERY, instanceOf(Integer.class)));

		create(result).expectErrorMatches(isAccessDenied()).verify();

		verify(pdp, times(1)).decide(any(AuthorizationSubscription.class));
		assertThatSubject(is(jsonText(ANONYMOUS)));
		assertThatAction(is(jsonText(BAD_RESOURCE_SERIALIZATION_QUERY)));
		assertThatResource(is(jsonText(RESOURCE)));
		assertThatEnvironmentNotPresent();
	}

	@Test
	void when_postHandlerSecuredQueryAndDeny_then_accessDenied() {
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.DENY));

		var result = Mono.fromFuture(queryGateway.query(POST_HANDLE_QUERY, QUERY, instanceOf(String.class)));

		create(result).expectErrorMatches(isAccessDenied()).verify();
	}

	@Test
	void when_handlerIsAnnotatedWithAnIllegalCombinationOfSaplAnnotations_then_accessDenied_case1() {
		var result = Mono.fromFuture(queryGateway.query(BAD_ANNOTATIONS1, QUERY, instanceOf(String.class)));
		create(result).expectErrorMatches(isAccessDenied()).verify();
	}

	@Test
	void when_handlerIsAnnotatedWithAnIllegalCombinationOfSaplAnnotations_then_accessDenied_case2() {
		var result = Mono.fromFuture(queryGateway.query(BAD_ANNOTATIONS2, QUERY, instanceOf(String.class)));
		create(result).expectErrorMatches(isAccessDenied()).verify();
	}

	@Test
	void when_handlerIsAnnotatedWithAnIllegalCombinationOfSaplAnnotations_then_accessDenied_case3() {
		var result = Mono.fromFuture(queryGateway.query(BAD_ANNOTATIONS3, QUERY, instanceOf(String.class)));
		create(result).expectErrorMatches(isAccessDenied()).verify();
	}

	@Test
	void when_preHandlerSecuredSubscriptionQueryAndPermit_then_initialReturnAndUpdatesAreEmitted() {
		var emitIntervallMs = 100L;
		var queryPayload    = "case1";
		var numberOfUpdates = 5L;

		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));

		var result = queryGateway.subscriptionQuery(PRE_HANDLE_QUERY, queryPayload, instanceOf(String.class),
				instanceOf(String.class));

		emitUpdates(queryPayload, emitIntervallMs, numberOfUpdates);

		create(result.initialResult().timeout(Duration.ofMillis(emitIntervallMs * (numberOfUpdates + 10L))))
				.expectNext(queryPayload).verifyComplete();
		create(result.updates().timeout(Duration.ofMillis(emitIntervallMs * (numberOfUpdates + 10L))).take(5))
				.expectNextCount(5L).verifyComplete();
		verify(pdp, times(1)).decide(any(AuthorizationSubscription.class));
		result.close();
	}

	@Test
	void when_preHandlerSecuredSubscriptionQueryAndDeny_then_bothStreamsAccessDenied() {
		var emitIntervallMs = 100L;
		var queryPayload    = "case2";
		var numberOfUpdates = 5L;

		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.DENY));

		var result = queryGateway.subscriptionQuery(PRE_HANDLE_QUERY, queryPayload, instanceOf(String.class),
				instanceOf(String.class));

		emitUpdates(queryPayload, emitIntervallMs, numberOfUpdates);

		create(result.initialResult().timeout(Duration.ofMillis(emitIntervallMs * (numberOfUpdates + 3L))))
				.expectErrorMatches(isAccessDenied()).verify();
		create(result.updates().timeout(Duration.ofMillis(emitIntervallMs * (numberOfUpdates + 3L))))
				.expectErrorMatches(isAccessDenied()).verify();
		verify(pdp, times(1)).decide(any(AuthorizationSubscription.class));

		result.close();
	}

	@Test
	void when_preHandlerSecuredSubscriptionQueryAndPermitPermitDeny_then_initialReturnAndUpdatesAreEmittedAndDenyForUpdatesLater() {
		var emitIntervallMs = 100L;
		var queryPayload    = "case3";
		var numberOfUpdates = 5L;

		when(pdp.decide(any(AuthorizationSubscription.class)))
				.thenReturn(Flux.concat(Flux.just(AuthorizationDecision.PERMIT),
						Flux.just(AuthorizationDecision.PERMIT, AuthorizationDecision.DENY)
								.delayElements(Duration.ofMillis(emitIntervallMs * 5L + emitIntervallMs / 4L))));

		var result = queryGateway.subscriptionQuery(PRE_HANDLE_QUERY, queryPayload, instanceOf(String.class),
				instanceOf(String.class));

		emitUpdates(queryPayload, emitIntervallMs, numberOfUpdates);

		create(result.initialResult().timeout(Duration.ofMillis(emitIntervallMs * (numberOfUpdates + 3L))))
				.expectNext(queryPayload).verifyComplete();
		create(result.updates().timeout(Duration.ofMillis(emitIntervallMs * (numberOfUpdates + 3L)))).expectNextCount(5)
				.expectErrorMatches(isAccessDenied()).verify();
		verify(pdp, times(1)).decide(any(AuthorizationSubscription.class));

		result.close();
	}

	@Test
	void when_dropHandlerSecuredSubscriptionQueryAndPermitDenyPermit_then_initialReturnAndUpdatesAreEmittedAndDroppedWhileDenied()
			throws InterruptedException {
		var emitIntervallMs = 100L;
		var queryPayload    = "case4";
		var numberOfUpdates = 14L;

		// @formatter:off
		when(pdp.decide(any(AuthorizationSubscription.class)))
				.thenReturn(Flux.concat(
						Flux.just(AuthorizationDecision.PERMIT),
						// next half time between 5th and 6th
						Flux.just(AuthorizationDecision.DENY).delayElements(Duration.ofMillis(emitIntervallMs * 5L + emitIntervallMs / 2L)), 						
						// next half time between 10th and 11th
						Flux.just(AuthorizationDecision.PERMIT).delayElements(Duration.ofMillis(emitIntervallMs * 5L))
						));
		// @formatter:on

		var result = queryGateway.subscriptionQuery(DROP_QUERY, queryPayload, instanceOf(String.class),
				instanceOf(String.class));

		emitUpdates(queryPayload, emitIntervallMs, numberOfUpdates);

		create(result.initialResult().timeout(Duration.ofMillis(emitIntervallMs * (numberOfUpdates + 3L))))
				.expectNext(queryPayload).verifyComplete();
		create(result.updates().take(6).timeout(Duration.ofMillis(emitIntervallMs * (numberOfUpdates + 3L))))
				.expectNext(queryPayload + "-0", queryPayload + "-1", queryPayload + "-2", queryPayload + "-3",
						queryPayload + "-4", queryPayload + "-10")
				.verifyComplete();
		verify(pdp, times(1)).decide(any(AuthorizationSubscription.class));

		result.close();
	}

	@Test
	void when_recoverableHandlerSecuredSubscriptionQueryAndPermitDenyPermitNoContiniue_then_initialReturnAndUpdatesAreEmittedAndAccessDeniedTerminatesUpdates()
			throws InterruptedException {
		var emitIntervallMs = 250L;
		var queryPayload    = "case5";
		var numberOfUpdates = 14L;

		// @formatter:off
		when(pdp.decide(any(AuthorizationSubscription.class)))
				.thenReturn(Flux.concat(
						Flux.just(AuthorizationDecision.PERMIT),
						// next half time between 5th and 6th
						Flux.just(AuthorizationDecision.DENY).delayElements(Duration.ofMillis(emitIntervallMs * 5L + emitIntervallMs / 2L)), 						
						// next half time between 10th and 11th
						Flux.just(AuthorizationDecision.PERMIT).delayElements(Duration.ofMillis(emitIntervallMs * 5L))
						));
		// @formatter:on

		var accessDeniedHandler = spy(new Runnable() {
			@Override
			public void run() {
				// NOOP
			}
		});

		var result = queryGateway.recoverableSubscriptionQuery(RECOVERABLE_QUERY, queryPayload,
				instanceOf(String.class), instanceOf(String.class), accessDeniedHandler);
		emitUpdates(queryPayload, emitIntervallMs, numberOfUpdates);

		create(result.initialResult().timeout(Duration.ofMillis(emitIntervallMs * (numberOfUpdates + 3L))))
				.expectNext(queryPayload).verifyComplete();
		create(result.updates().take(6))
				.expectNext(queryPayload + "-0", queryPayload + "-1", queryPayload + "-2", queryPayload + "-3",
						queryPayload + "-4")
				.expectError(AccessDeniedException.class)
				.verify(Duration.ofMillis(emitIntervallMs * (numberOfUpdates + 3000000L)));

		verify(pdp, times(1)).decide(any(AuthorizationSubscription.class));
		verify(accessDeniedHandler, times(1)).run();

		result.close();
	}

	@Test
	void when_recoverableHandlerSecuredSubscriptionQueryAndPermitDenyPermitWithContiniue_then_initialReturnAndUpdatesAreEmittedAndAccessDeniedThenResumesOnPermit()
			throws InterruptedException {
		var emitIntervallMs = 250L;
		var queryPayload    = "case6";
		var numberOfUpdates = 14L;

		// @formatter:off
		when(pdp.decide(any(AuthorizationSubscription.class)))
				.thenReturn(Flux.concat(
						Flux.just(AuthorizationDecision.PERMIT),
						// next half time between 5th and 6th
						Flux.just(AuthorizationDecision.DENY).delayElements(Duration.ofMillis(emitIntervallMs * 5L + emitIntervallMs / 2L)), 						
						// next half time between 10th and 11th
						Flux.just(AuthorizationDecision.PERMIT).delayElements(Duration.ofMillis(emitIntervallMs * 5L))
						));
		// @formatter:on

		var accessDeniedHandler = spy(new Runnable() {
									@Override
									public void run() {
										// NOOP
									}
								});
		var result              = queryGateway.recoverableSubscriptionQuery(RECOVERABLE_QUERY, queryPayload,
				instanceOf(String.class), instanceOf(String.class), accessDeniedHandler);
		emitUpdates(queryPayload, emitIntervallMs, numberOfUpdates);

		create(result.initialResult().timeout(Duration.ofMillis(emitIntervallMs * (numberOfUpdates + 3L))))
				.expectNext(queryPayload).verifyComplete();
		create(result.updates().onErrorContinue((t, o) -> accessDeniedHandler.run()).take(6)
				.timeout(Duration.ofMillis(emitIntervallMs * (numberOfUpdates + 3L))))
				.expectNext(queryPayload + "-0", queryPayload + "-1", queryPayload + "-2", queryPayload + "-3",
						queryPayload + "-4", queryPayload + "-10")
				.verifyComplete();

		verify(pdp, times(1)).decide(any(AuthorizationSubscription.class));
		verify(accessDeniedHandler, times(1)).run();

		result.close();
	}

	private void emitUpdates(String queryPayload, long emitIntervallMs, long numberOfEmittedUpdates) {
		Flux.interval(Duration.ofMillis(emitIntervallMs)).doOnNext(
				i -> emitter.emit(query -> query.getPayload().toString().equals(queryPayload), queryPayload + "-" + i))
				.take(Duration.ofMillis(emitIntervallMs * numberOfEmittedUpdates + emitIntervallMs / 2L)).subscribe();
	}

	// @formatter:off
	@RequiredArgsConstructor
	static class Projection {
		
		@QueryHandler(queryName = UNSECURED_QUERY)
		public String handleUnsecured(String query) { return query; }

		@QueryHandler(queryName = PRE_HANDLE_QUERY)
		@PreHandleEnforce(action="'"+PRE_HANDLE_QUERY+"'", resource=RESOURCE_EXPR)
		public String handlePreEnforce(String query) { return query; }
	
		@QueryHandler(queryName = DROP_QUERY)
		@EnforceDropUpdatesWhileDenied(action="'"+DROP_QUERY+"'", resource=RESOURCE_EXPR)
		public String handleDrop(String query) { return query; }

		@QueryHandler(queryName = RECOVERABLE_QUERY)
		@EnforceRecoverableUpdatesIfDenied(action="'"+RECOVERABLE_QUERY+"'", resource=RESOURCE_EXPR)
		public String handleRecoverable(String query) { return query; }
		
		@QueryHandler(queryName = POST_HANDLE_QUERY)
		@PostHandleEnforce(action="'"+POST_HANDLE_QUERY+"'", resource=RESOURCE_EXPR)
		public String handlePostEnforce(String query) { return query; }

		@QueryHandler(queryName = POST_HANDLE_NO_RESOURCE_QUERY)
		@PostHandleEnforce(action="'"+POST_HANDLE_NO_RESOURCE_QUERY+"'", resource=RESOURCE_EXPR)
		public String handlePostEnforceNoResource(String query) { return query; }

		@QueryHandler(queryName = BAD_RESOURCE_SERIALIZATION_QUERY)
		@PostHandleEnforce(action="'"+BAD_RESOURCE_SERIALIZATION_QUERY+"'", resource=RESOURCE_EXPR, genericsType=Integer.class)
		public Integer handlePostEnforceFailing(String query) { return 0; }

		@PreHandleEnforce
		@EnforceDropUpdatesWhileDenied
		@QueryHandler(queryName = BAD_ANNOTATIONS1)
		public String handleBadAnnotations1(String query) { return query; }

		@PreHandleEnforce
		@EnforceRecoverableUpdatesIfDenied
		@QueryHandler(queryName = BAD_ANNOTATIONS2)
		public String handleBadAnnotations2(String query) { return query; }

		@EnforceDropUpdatesWhileDenied
		@EnforceRecoverableUpdatesIfDenied
		@QueryHandler(queryName = BAD_ANNOTATIONS3)
		public String handleBadAnnotations3(String query) { return query; }		
		
		@QueryHandler(queryName = RECOVERABLE_QUERY)
		@EnforceRecoverableUpdatesIfDenied(action="'"+RECOVERABLE_QUERY+"'", resource=RESOURCE_EXPR)
		public String handleRecoverableQuery(String query) { return query; }

	}
	// @formatter:on

	private void assertThatSubject(Matcher<JsonNode> matcher) {
		assertThatAuthzSubscriptionProperty("subject", matcher);
	}

	private void assertThatAction(Matcher<JsonNode> matcher) {
		assertThatAuthzSubscriptionProperty("action", matcher);
	}

	private void assertThatResource(Matcher<JsonNode> matcher) {
		assertThatAuthzSubscriptionProperty("resource", matcher);
	}

	private void assertThatEnvironmentNotPresent() {
		assertThat(captureAuthzSubscription().getEnvironment(), is(nullValue()));
	}

	private void assertThatAuthzSubscriptionProperty(String authzSubscriptionProperty, Matcher<JsonNode> matcher) {
		assertThat(captureAuthzSubscription(),
				is(pojo(AuthorizationSubscription.class).withProperty(authzSubscriptionProperty, matcher)));
	}

	private AuthorizationSubscription captureAuthzSubscription() {
		var argumentCaptor = ArgumentCaptor.forClass(AuthorizationSubscription.class);
		verify(pdp).decide(argumentCaptor.capture());
		var capturedArgument = argumentCaptor.getValue();
		return capturedArgument;
	}

	@Configuration
	@Import({ CommandAndQueryAuthenticationConfiguration.class, AxonConstraintHandlerService.class })
	static class TestScenarioConfiguration {

		@Bean
		Projection projection() {
			return new Projection();
		}

		@Bean
		public SaplQueryGateway registerQueryGateway(QueryBus queryBus) {
			return new SaplQueryGateway(queryBus, List.of());
		}

		@Bean
		SaplQueryUpdateEmitter updateEmitter() {
			return new SaplQueryUpdateEmitter(Optional.empty(), null, null);
		}

		@Bean
		SaplHandlerEnhancer saplEnhancer(PolicyDecisionPoint pdp,
				ConstraintEnforcementService constraintEnforcementService,
				AxonConstraintHandlerService axonConstraintEnforcementService, SaplQueryUpdateEmitter emitter,
				AxonAuthorizationSubscriptionBuilderService subscriptionBuilder, ObjectMapper mapper) {
			return new SaplHandlerEnhancer(pdp, constraintEnforcementService, axonConstraintEnforcementService, emitter,
					subscriptionBuilder, mapper);
		}

		@Bean
		AxonAuthorizationSubscriptionBuilderService subscriptionBuilder() {
			return new AxonAuthorizationSubscriptionBuilderService(new ObjectMapper());
		}

		@Bean
		public XStream xStream() {
			XStream xStream = new XStream();

			xStream.allowTypesByWildcard(new String[] { "io.sapl.**" });
			return xStream;
		}
	}

}
