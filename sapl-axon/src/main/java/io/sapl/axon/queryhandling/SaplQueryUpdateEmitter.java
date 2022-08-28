package io.sapl.axon.queryhandling;

import static java.lang.String.format;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

import org.axonframework.axonserver.connector.query.subscription.GrpcBackedSubscriptionQueryMessage;
import org.axonframework.common.Registration;
import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.monitoring.MessageMonitor;
import org.axonframework.monitoring.NoOpMessageMonitor;
import org.axonframework.queryhandling.GenericSubscriptionQueryMessage;
import org.axonframework.queryhandling.QueryUpdateEmitter;
import org.axonframework.queryhandling.SimpleQueryUpdateEmitter;
import org.axonframework.queryhandling.SubscriptionQueryBackpressure;
import org.axonframework.queryhandling.SubscriptionQueryMessage;
import org.axonframework.queryhandling.SubscriptionQueryUpdateMessage;
import org.axonframework.queryhandling.UpdateHandlerRegistration;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.axon.annotation.EnforceDropUpdatesWhileDenied;
import io.sapl.axon.annotation.EnforceRecoverableUpdatesIfDenied;
import io.sapl.axon.annotation.PreHandleEnforce;
import io.sapl.axon.constrainthandling.ConstraintHandlerService;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.Sinks.EmitFailureHandler;
import reactor.core.publisher.Sinks.Many;

/**
 * Implementation of {@link QueryUpdateEmitter} that uses Project Reactor to
 * implement Update Handlers.
 * 
 * @author Dominic Heutelbeck
 * @since 2.1.0
 */

@Slf4j
@SuppressWarnings("deprecation") // inherited from Axon
public class SaplQueryUpdateEmitter implements QueryUpdateEmitter {

	private static final String QUERY_UPDATE_TASKS_RESOURCE_KEY = "/update-tasks";

	private final MessageMonitor<? super SubscriptionQueryUpdateMessage<?>>                   updateMessageMonitor;
	private final ConstraintHandlerService                                                    constraintHandlerService;
	private final ConcurrentMap<SubscriptionQueryMessage<?, ?, ?>, QueryData<?>>              activeQueries        = new ConcurrentHashMap<>();
	private final List<MessageDispatchInterceptor<? super SubscriptionQueryUpdateMessage<?>>> dispatchInterceptors = new CopyOnWriteArrayList<>();

	/**
	 * Instantiate a {@link SimpleQueryUpdateEmitter} based on the fields contained
	 * in the {@link Builder}.
	 *
	 * @param builder the {@link Builder} used to instantiate a
	 *                {@link SimpleQueryUpdateEmitter} instance
	 */
	public SaplQueryUpdateEmitter(
			Optional<MessageMonitor<? super SubscriptionQueryUpdateMessage<?>>> updateMessageMonitor,
			ConstraintHandlerService constraintHandlerService) {
		this.updateMessageMonitor     = updateMessageMonitor.orElseGet(() -> NoOpMessageMonitor.INSTANCE);
		this.constraintHandlerService = constraintHandlerService;
	}

	/**
	 * {@inheritDoc}
	 *
	 * @deprecated in favour of using
	 *             {{@link #registerUpdateHandler(SubscriptionQueryMessage, int)}}
	 */
	@Override
	@Deprecated
	public <U> UpdateHandlerRegistration<U> registerUpdateHandler(SubscriptionQueryMessage<?, ?, ?> query,
			SubscriptionQueryBackpressure backpressure, int updateBufferSize) {
		log.trace("Deprecated subscription registration. Ignoring backpressure settings {}", backpressure);
		return registerUpdateHandler(query, updateBufferSize);
	}

	@Value
	private static class QueryEnforcementConfiguration {
		QueryAuthorizationMode      mode;
		Flux<AuthorizationDecision> decisions;
	}

	@Value
	private static class QueryData<U> {
		QueryAuthorizationMode                        mode;
		Sinks.One<QueryEnforcementConfiguration>      enforcementConfigurationSink;
		Sinks.Many<SubscriptionQueryUpdateMessage<U>> updateSink;

		public QueryData<U> withMode(QueryAuthorizationMode newMode) {
			return new QueryData<U>(newMode, enforcementConfigurationSink, updateSink);
		}
	}

	@Override
	public boolean queryUpdateHandlerRegistered(SubscriptionQueryMessage<?, ?, ?> query) {
		return activeQueries.keySet().stream().anyMatch(q -> q.getIdentifier().equals(query.getIdentifier()));
	}

	/**
	 * @param messageIdentifier
	 */
	public void authozrizeUpdatesForSubscriptionQueryWithId(String messageIdentifier) {
		var enforcementConfiguration = new QueryEnforcementConfiguration(QueryAuthorizationMode.NO_AUTHORIZATION, null);
		emitEnforcementConfiguration(messageIdentifier, enforcementConfiguration);
	}

	/**
	 * @param messageIdentifier
	 * @param decisions
	 * @param clazz
	 */
	public void authozrizeUpdatesForSubscriptionQueryWithId(String messageIdentifier,
			Flux<AuthorizationDecision> decisions, Class<? extends Annotation> clazz) {
		var enforcementConfiguration = new QueryEnforcementConfiguration(QueryAuthorizationMode.of(clazz), decisions);
		emitEnforcementConfiguration(messageIdentifier, enforcementConfiguration);
	}

	private void emitEnforcementConfiguration(String messageIdentifier,
			QueryEnforcementConfiguration enforcementConfiguration) {
		activeQueries.keySet().stream().filter(m -> m.getIdentifier().equals(messageIdentifier))
				.forEach(query -> Optional.ofNullable(activeQueries.get(query)).ifPresent(queryData -> {
					try {
						queryData.getEnforcementConfigurationSink().emitValue(enforcementConfiguration,
								EmitFailureHandler.FAIL_FAST);
					} catch (Exception e) {
						emitError(query, e, queryData.getUpdateSink());
					}
				}));
	}

	@Override
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public <U> UpdateHandlerRegistration<U> registerUpdateHandler(SubscriptionQueryMessage<?, ?, ?> registeredQuery,
			int updateBufferSize) {

		var query = reconstructOriginalQuery(registeredQuery);

		Sinks.One<QueryEnforcementConfiguration> enforcementConfigurationSink = Sinks.one();

		Many<SubscriptionQueryUpdateMessage<U>> updateSink = Sinks.many().replay()
				.<SubscriptionQueryUpdateMessage<U>>limit(updateBufferSize);

		Runnable     removeHandler = () -> activeQueries.remove(query);
		Registration registration  = () -> {
										removeHandler.run();
										return true;
									};

		activeQueries.put(query,
				new QueryData<U>(QueryAuthorizationMode.UNDEFINED, enforcementConfigurationSink, updateSink));

		final Flux<SubscriptionQueryUpdateMessage<U>> updateMessageFlux = updateSink.asFlux().doOnCancel(removeHandler)
				.doOnTerminate(removeHandler);

		var securedUpdates = enforcementConfigurationSink.asMono().flatMapMany(authzConfig -> {
			activeQueries.computeIfPresent(query,
					(__, originalQueryData) -> originalQueryData.withMode(authzConfig.getMode()));

			if (authzConfig.getMode() == QueryAuthorizationMode.TILL_DENIED) {
				return EnforceUpdatesTillDeniedPolicyEnforcementPoint.of(registeredQuery, authzConfig.getDecisions(),
						updateMessageFlux, constraintHandlerService, query.getResponseType(),
						query.getUpdateResponseType());
			}
			if (authzConfig.getMode() == QueryAuthorizationMode.DROP_WHILE_DENIED) {
				return EnforceDropUpdatesWhileDeniedPolicyEnforcementPoint.of(registeredQuery,
						authzConfig.getDecisions(), updateMessageFlux, constraintHandlerService,
						query.getResponseType(), query.getUpdateResponseType());
			}
			if (authzConfig.getMode() == QueryAuthorizationMode.RECOVERABLE_IF_DENIED) {
				var originalUpdateResponseType = (ResponseType<U>) query.getMetaData()
						.get(RecoverableResponse.RECOVERABLE_UPDATE_TYPE_KEY);
				if (originalUpdateResponseType != null) {
					log.debug("Client requested access denied recoverability.");

					return EnforceRecoverableIfDeniedPolicyEnforcementPoint.of(registeredQuery,
							authzConfig.getDecisions(), updateMessageFlux, constraintHandlerService,
							query.getResponseType(), originalUpdateResponseType);
				}
				log.debug(
						"While handler supports recoverability, client did not request it. Fall back to TILL_DENIED enforcement. Requested: {}",
						query.getUpdateResponseType().getExpectedResponseType().getSimpleName());

				return EnforceUpdatesTillDeniedPolicyEnforcementPoint.of(registeredQuery, authzConfig.getDecisions(),
						updateMessageFlux, constraintHandlerService, query.getResponseType(),
						query.getUpdateResponseType());
			}
			return updateMessageFlux;
		});

		return (UpdateHandlerRegistration<U>) new UpdateHandlerRegistration(registration, securedUpdates,
				() -> updateSink.emitComplete(EmitFailureHandler.FAIL_FAST));
	}

	private SubscriptionQueryMessage<?, ?, ?> reconstructOriginalQuery(SubscriptionQueryMessage<?, ?, ?> query) {
		var originalUpdateResponseType = (ResponseType<?>) query.getMetaData()
				.get(RecoverableResponse.RECOVERABLE_UPDATE_TYPE_KEY);

		if (originalUpdateResponseType != null)
			return new GenericSubscriptionQueryMessage<>(
					new GenericMessage<>(query.getIdentifier(), query.getPayload(), query.getMetaData()),
					query.getQueryName(), query.getResponseType(), originalUpdateResponseType);

		if (query instanceof GrpcBackedSubscriptionQueryMessage) {
			log.error(
					"ATTENTION: Due to a bug in Axon Framework, the update response type of the query was overwritten");
			log.error("           with the initial response type. If these types were different, queries may break.");
			log.error("           See: https://github.com/AxonFramework/AxonFramework/issues/2331");
		}

		return query;
	}

	@Override
	public <U> void emit(Predicate<SubscriptionQueryMessage<?, ?, U>> filter,
			SubscriptionQueryUpdateMessage<U> update) {
		runOnAfterCommitOrNow(() -> doEmit(filter, intercept(update)));
	}

	@SuppressWarnings("unchecked")
	private <U> SubscriptionQueryUpdateMessage<U> intercept(SubscriptionQueryUpdateMessage<U> message) {
		SubscriptionQueryUpdateMessage<U> intercepted = message;
		for (MessageDispatchInterceptor<? super SubscriptionQueryUpdateMessage<?>> interceptor : dispatchInterceptors) {
			// noinspection unchecked
			intercepted = (SubscriptionQueryUpdateMessage<U>) interceptor.handle(intercepted);
		}
		return intercepted;
	}

	@Override
	public void complete(Predicate<SubscriptionQueryMessage<?, ?, ?>> filter) {
		runOnAfterCommitOrNow(() -> doComplete(filter));
	}

	@Override
	public void completeExceptionally(Predicate<SubscriptionQueryMessage<?, ?, ?>> filter, Throwable cause) {
		runOnAfterCommitOrNow(() -> doCompleteExceptionally(filter, cause));
	}

	@Override
	public Registration registerDispatchInterceptor(
			MessageDispatchInterceptor<? super SubscriptionQueryUpdateMessage<?>> interceptor) {
		dispatchInterceptors.add(interceptor);
		return () -> dispatchInterceptors.remove(interceptor);
	}

	@Override
	public Set<SubscriptionQueryMessage<?, ?, ?>> activeSubscriptions() {
		return activeQueries.keySet();
	}

	@SuppressWarnings("unchecked")
	private <U> void doEmit(Predicate<SubscriptionQueryMessage<?, ?, U>> filter,
			SubscriptionQueryUpdateMessage<U> update) {
		activeQueries.keySet().stream().filter(sqm -> filter.test((SubscriptionQueryMessage<?, ?, U>) sqm))
				.forEach(query -> Optional.ofNullable(activeQueries.get(query))
						.ifPresent(uh -> doEmit(query, uh.getUpdateSink(), update)));
	}

	@SuppressWarnings("unchecked")
	private <U> void doEmit(SubscriptionQueryMessage<?, ?, ?> query, Sinks.Many<?> updateHandler,
			SubscriptionQueryUpdateMessage<U> update) {
		MessageMonitor.MonitorCallback monitorCallback = updateMessageMonitor.onMessageIngested(update);
		try {
			((Many<SubscriptionQueryUpdateMessage<U>>) updateHandler).emitNext(update, EmitFailureHandler.FAIL_FAST);
			monitorCallback.reportSuccess();
		} catch (Exception e) {
			log.info(
					"An error occurred while trying to emit an update to a query '{}'. "
							+ "The subscription will be cancelled. Exception summary: {}",
					query.getQueryName(), e.toString());
			monitorCallback.reportFailure(e);
			activeQueries.remove(query);
			emitError(query, e, updateHandler);
		}
	}

	private void doComplete(Predicate<SubscriptionQueryMessage<?, ?, ?>> filter) {
		activeQueries.keySet().stream().filter(filter)
				.forEach(query -> Optional.ofNullable(activeQueries.get(query)).ifPresent(queryData -> {
					try {
						if (queryData.getMode() != QueryAuthorizationMode.UNDEFINED)
							queryData.getUpdateSink().emitComplete(EmitFailureHandler.FAIL_FAST);
					} catch (Exception e) {
						emitError(query, e, queryData.getUpdateSink());
					}
				}));
	}

	private void emitError(SubscriptionQueryMessage<?, ?, ?> query, Throwable cause, Sinks.Many<?> updateHandler) {
		try {
			updateHandler.emitError(cause, EmitFailureHandler.FAIL_FAST);
		} catch (Exception e) {
			log.error(format("An error happened while trying to inform update handler about the error. Query: %s",
					query));
		}
	}

	private void doCompleteExceptionally(Predicate<SubscriptionQueryMessage<?, ?, ?>> filter, Throwable cause) {
		activeQueries.keySet().stream().filter(filter)
				.forEach(query -> Optional.ofNullable(activeQueries.get(query)).ifPresent(queryData -> {
					if (queryData.getMode() != QueryAuthorizationMode.UNDEFINED) {
						emitError(query, cause, queryData.getUpdateSink());
					} else {
						queryData.getUpdateSink().emitComplete(EmitFailureHandler.FAIL_FAST);
					}
				}));
	}

	/**
	 * Either runs the provided {@link Runnable} immediately or adds it to a
	 * {@link List} as a resource to the current {@link UnitOfWork} if
	 * {@link SimpleQueryUpdateEmitter#inStartedPhaseOfUnitOfWork} returns
	 * {@code true}. This is done to ensure any emitter calls made from a message
	 * handling function are executed in the {@link UnitOfWork.Phase#AFTER_COMMIT}
	 * phase.
	 * <p>
	 * The latter check requires the current UnitOfWork's phase to be
	 * {@link UnitOfWork.Phase#STARTED}. This is done to allow users to circumvent
	 * their {@code queryUpdateTask} being handled in the AFTER_COMMIT phase. They
	 * can do this by retrieving the current UnitOfWork and performing any of the
	 * {@link QueryUpdateEmitter} calls in a different phase.
	 *
	 * @param queryUpdateTask a {@link Runnable} to be ran immediately or as a
	 *                        resource if
	 *                        {@link SimpleQueryUpdateEmitter#inStartedPhaseOfUnitOfWork}
	 *                        returns {@code true}
	 */
	private void runOnAfterCommitOrNow(Runnable queryUpdateTask) {
		if (inStartedPhaseOfUnitOfWork()) {
			UnitOfWork<?> unitOfWork = CurrentUnitOfWork.get();
			unitOfWork.getOrComputeResource(this.toString() + QUERY_UPDATE_TASKS_RESOURCE_KEY, resourceKey -> {
				List<Runnable> queryUpdateTasks = new ArrayList<>();
				unitOfWork.afterCommit(uow -> queryUpdateTasks.forEach(Runnable::run));
				return queryUpdateTasks;
			}).add(queryUpdateTask);
		} else {
			queryUpdateTask.run();
		}
	}

	/**
	 * Return {@code true} if the {@link CurrentUnitOfWork#isStarted()} returns
	 * {@code true} and in if the phase is {@link UnitOfWork.Phase#STARTED},
	 * otherwise {@code false}.
	 *
	 * @return {@code true} if the {@link CurrentUnitOfWork#isStarted()} returns
	 *         {@code true} and in if the phase is {@link UnitOfWork.Phase#STARTED},
	 *         otherwise {@code false}.
	 */
	private boolean inStartedPhaseOfUnitOfWork() {
		return CurrentUnitOfWork.isStarted() && UnitOfWork.Phase.STARTED.equals(CurrentUnitOfWork.get().phase());
	}

	private enum QueryAuthorizationMode {
		NO_AUTHORIZATION, TILL_DENIED, DROP_WHILE_DENIED, RECOVERABLE_IF_DENIED, UNDEFINED;

		public static QueryAuthorizationMode of(Class<? extends Annotation> clazz) {
			if (clazz.isAssignableFrom(PreHandleEnforce.class))
				return TILL_DENIED;
			if (clazz.isAssignableFrom(EnforceDropUpdatesWhileDenied.class))
				return DROP_WHILE_DENIED;
			if (clazz.isAssignableFrom(EnforceRecoverableUpdatesIfDenied.class))
				return RECOVERABLE_IF_DENIED;

			throw new IllegalArgumentException(
					"Not a legal authorization mode for subscription queries: " + clazz.getName());
		}
	}

}
