package io.sapl.axon.constraints;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import org.axonframework.messaging.ResultMessage;
import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.queryhandling.QueryMessage;
import org.springframework.security.access.AccessDeniedException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.base.Functions;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.axon.constraints.api.AxonRunnableConstraintHandlerProvider;
import io.sapl.axon.constraints.api.AxonRunnableConstraintHandlerProvider.Signal;
import io.sapl.axon.constraints.api.QueryMessageMappingConstraintHandlerProvider;
import io.sapl.axon.constraints.api.ResultMessageConsumerConstraintHandlerProvider;
import io.sapl.axon.constraints.api.ResultMessageFilterPredicateConstraintHandlerProvider;
import io.sapl.axon.constraints.api.ResultMessageMappingConstraintHandlerProvider;
import io.sapl.spring.constraints.api.ConsumerConstraintHandlerProvider;
import io.sapl.spring.constraints.api.ErrorHandlerProvider;
import io.sapl.spring.constraints.api.ErrorMappingConstraintHandlerProvider;
import io.sapl.spring.constraints.api.MappingConstraintHandlerProvider;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AxonConstraintHandlerService {

	private final ObjectMapper                                                   mapper;
	private final List<AxonRunnableConstraintHandlerProvider>                    globalRunnableProviders;
	private final List<QueryMessageMappingConstraintHandlerProvider>             globalQueryMessageMappingProviders;
	private final List<ConsumerConstraintHandlerProvider<?>>                     globalConsumerProviders;
	private final List<ErrorMappingConstraintHandlerProvider>                    globalErrorMappingHandlerProviders;
	private final List<ErrorHandlerProvider>                                     globalErrorHandlerProviders;
	private final List<MappingConstraintHandlerProvider<?>>                      globalMappingProviders;
	private final List<ResultMessageFilterPredicateConstraintHandlerProvider<?>> updatePredicateProviders;
	private final List<ResultMessageMappingConstraintHandlerProvider<?>>         updateMappingProviders;
	private final List<ResultMessageConsumerConstraintHandlerProvider<?>>        updateMessageConsumerProviders;

	public AxonConstraintHandlerService(ObjectMapper mapper,
			List<AxonRunnableConstraintHandlerProvider> globalRunnableProviders,
			List<QueryMessageMappingConstraintHandlerProvider> globalQueryMessageMappingProviders,
			List<ConsumerConstraintHandlerProvider<?>> globalConsumerProviders,
			List<ErrorMappingConstraintHandlerProvider> globalErrorMappingHandlerProviders,
			List<ErrorHandlerProvider> globalErrorHandlerProviders,
			List<MappingConstraintHandlerProvider<?>> globalMappingProviders,
			List<ResultMessageFilterPredicateConstraintHandlerProvider<?>> updatePredicateProviders,
			List<ResultMessageMappingConstraintHandlerProvider<?>> updateMappingProviders,
			List<ResultMessageConsumerConstraintHandlerProvider<?>> updateConsumerProviders) {

		this.mapper = mapper;
		// sort according to priority
		this.globalRunnableProviders = globalRunnableProviders;
		Collections.sort(this.globalRunnableProviders);
		this.globalQueryMessageMappingProviders = globalQueryMessageMappingProviders;
		Collections.sort(this.globalQueryMessageMappingProviders);
		this.globalConsumerProviders = globalConsumerProviders;
		Collections.sort(this.globalConsumerProviders);
		this.globalErrorMappingHandlerProviders = globalErrorMappingHandlerProviders;
		Collections.sort(this.globalErrorMappingHandlerProviders);
		this.globalErrorHandlerProviders = globalErrorHandlerProviders;
		Collections.sort(this.globalErrorHandlerProviders);
		this.globalMappingProviders = globalMappingProviders;
		Collections.sort(this.globalMappingProviders);
		this.updateMappingProviders = updateMappingProviders;
		Collections.sort(this.updateMappingProviders);
		this.updatePredicateProviders       = updatePredicateProviders;
		this.updateMessageConsumerProviders = updateConsumerProviders;
	}

	@SuppressWarnings("unchecked")
	public <T> T deserializeResource(JsonNode resource, ResponseType<T> type) {
		try {
			return mapper.treeToValue(resource, (Class<T>) type.getExpectedResponseType());
		} catch (JsonProcessingException | IllegalArgumentException e) {
			log.error("Failed to deserialize resource object from decision", e);
			throw new AccessDeniedException("Access Denied");
		}
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public AxonQueryConstraintHandlerBundle<?, ?> buildQueryPreHandlerBundle(AuthorizationDecision decision,
			ResponseType<?> responseType, Optional<ResponseType<?>> updateType) {

		var obligationsWithoutHandler = new HashSet<JsonNode>();
		decision.getObligations()
				.ifPresent(obligations -> obligations.forEach(obligation -> obligationsWithoutHandler.add(obligation)));

		var onDecisionHandlers     = constructOnDecisionHandlers(decision, obligationsWithoutHandler);
		var queryMappingHandlers   = constructQueryMessageMappingHandlers(decision, obligationsWithoutHandler);
		var queryConsumerHandlers  = constructQueryConsumerHandlers(decision, obligationsWithoutHandler);
		var errorMappingHandlers   = constructErrorMappingHandlers(decision, obligationsWithoutHandler);
		var errorConsumerHandlers  = constructErrorConsumerHandlers(decision, obligationsWithoutHandler);
		var resultMappingHandlers  = constructResultMappingHandlers(decision, obligationsWithoutHandler, responseType);
		var resultConsumerHandlers = constructResultConsumerHandlers(decision, obligationsWithoutHandler, responseType);

		Function<?, ?> updateMappingHandlers  = Functions.identity();
		Consumer<?>    updateConsumerHandlers = __ -> {
												};
		Predicate<?>   updateFilterPredicate  = __ -> true;

		if (updateType.isPresent()) {
			updateMappingHandlers  = constructUpdateMessageMappingHandlers(decision, obligationsWithoutHandler,
					updateType.get());
			updateFilterPredicate  = constructFilterPredicateHandlers(decision, obligationsWithoutHandler,
					updateType.get());
			updateConsumerHandlers = constructResultMessageConsumerHandlers(decision, obligationsWithoutHandler,
					updateType.get());
		}

		if (!obligationsWithoutHandler.isEmpty()) {
			log.error("Could not find handlers for all obligations. Missing handlers for: {}",
					obligationsWithoutHandler);
			throw new AccessDeniedException("Access Denied");
		}

		return new AxonQueryConstraintHandlerBundle(onDecisionHandlers, queryMappingHandlers, queryConsumerHandlers,
				errorMappingHandlers, errorConsumerHandlers, resultMappingHandlers, resultConsumerHandlers,
				updateMappingHandlers, updateConsumerHandlers, updateFilterPredicate);
	}

	private <T> Consumer<ResultMessage<T>> constructResultMessageConsumerHandlers(AuthorizationDecision decision,
			HashSet<JsonNode> obligationsWithoutHandler, ResponseType<T> responseType) {
		var obligationConsumers = constructMessageResultConsumerHandlers(decision.getObligations(),
				obligationsWithoutHandler::remove, responseType);
		var adviceConsumers     = constructMessageResultConsumerHandlers(decision.getAdvice(), __ -> {
								}, responseType);
		return result -> {
			obligationConsumers.ifPresent(handler -> BundleUtil.obligation(handler).accept(result));
			adviceConsumers.ifPresent(handler -> BundleUtil.advice(handler).accept(result));
		};
	}

	private <T> Optional<Consumer<ResultMessage<?>>> constructMessageResultConsumerHandlers(
			Optional<ArrayNode> constraints, Consumer<JsonNode> onHandlerFound, ResponseType<T> responseType) {
		return constraints.map(constraintsArray -> {
			var handlers = new ArrayList<Consumer<ResultMessage<?>>>(constraintsArray.size());
			for (var constraint : constraintsArray) {
				for (var provider : updateMessageConsumerProviders) {
					if (responseType.getExpectedResponseType().isAssignableFrom(provider.getSupportedType())
							&& provider.isResponsible(constraint)) {
						onHandlerFound.accept(constraint);
						handlers.add((Consumer<ResultMessage<?>>) provider.getHandler(constraint));
					}
				}
			}
			return BundleUtil.consumeAll(handlers);
		});
	}

	private <T> Function<ResultMessage<?>, ResultMessage<?>> constructUpdateMessageMappingHandlers(
			AuthorizationDecision decision, HashSet<JsonNode> obligationsWithoutHandler, ResponseType<T> responseType) {
		var obligationFun = constructUpdateMesaageMappingHandlers(decision.getObligations(),
				obligationsWithoutHandler::remove, responseType);
		var adviceFun     = constructUpdateMesaageMappingHandlers(decision.getAdvice(), __ -> {
							}, responseType);

		return result -> {
			var newResult = result;
			try {
				newResult = obligationFun.orElse(Functions.identity()).apply(result);
			} catch (Throwable t) {
				log.error("Failed to execute obligation handlers.", t);
				throw new AccessDeniedException("Access Denied");
			}
			try {
				newResult = adviceFun.orElse(Functions.identity()).apply(newResult);
			} catch (Throwable t) {
				log.error("Failed to execute advice handlers.", t);
			}
			return newResult;
		};
	}

	private <T> Optional<Function<ResultMessage<?>, ResultMessage<?>>> constructUpdateMesaageMappingHandlers(
			Optional<ArrayNode> constraints, Consumer<JsonNode> onHandlerFound, ResponseType<T> responseType) {
		return constraints.map(constraintsArray -> {
			var handlers = new ArrayList<Function<ResultMessage<?>, ResultMessage<?>>>(constraintsArray.size());
			for (var constraint : constraintsArray) {
				for (var provider : updateMappingProviders) {
					if (responseType.getExpectedResponseType().isAssignableFrom(provider.getSupportedType())
							&& provider.isResponsible(constraint)) {
						onHandlerFound.accept(constraint);
						handlers.add(provider.getHandler(constraint));
					}
				}
			}
			return BundleUtil.mapAll(handlers);
		});
	}

	private <T> Predicate<T> constructFilterPredicateHandlers(AuthorizationDecision decision,
			HashSet<JsonNode> obligationsWithoutHandler, ResponseType<T> responseType) {
		Predicate<T> obligationFilterPredicate = constructPredicate(decision.getObligations(), responseType,
				obligationsWithoutHandler::remove);
		Predicate<T> adviceFilterPredicate     = constructPredicate(decision.getAdvice(), responseType,
				obligationsWithoutHandler::remove);
		return t -> onErrorFallbackTo(obligationFilterPredicate, false).test(t)
				&& onErrorFallbackTo(adviceFilterPredicate, true).test(t);
	}

	@SuppressWarnings("unchecked")
	private <T> Predicate<T> constructPredicate(Optional<ArrayNode> constraints, ResponseType<T> responseType,
			Consumer<JsonNode> onHandlerFound) {
		return constraints.map(constraintsArray -> {
			var handlers = new ArrayList<Predicate<T>>(constraintsArray.size());
			for (var constraint : constraintsArray) {
				for (var provider : updatePredicateProviders) {
					if (responseType.getExpectedResponseType().isAssignableFrom(provider.getSupportedType())
							&& provider.isResponsible(constraint)) {
						onHandlerFound.accept(constraint);
						handlers.add((Predicate<T>) provider.getHandler(constraint));
					}
				}
			}
			return andAll(handlers);
		}).orElse(__ -> true);
	}

	private <T> Predicate<T> andAll(List<Predicate<T>> predicates) {
		return t -> {
			for (var p : predicates)
				if (!p.test(t))
					return false;
			return true;
		};
	}

	private <T> Predicate<T> onErrorFallbackTo(Predicate<T> p, boolean fallback) {
		return t -> {
			try {
				return p.test(t);
			} catch (Throwable e) {
				log.error("Failed to evaluate predicate.", e);
				return fallback;
			}
		};
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public AxonQueryConstraintHandlerBundle<?, ?> buildQueryPostHandlerBundle(AuthorizationDecision decision,
			ResponseType<?> responseType) {

		var obligationsWithoutHandler = new HashSet<JsonNode>();
		decision.getObligations()
				.ifPresent(obligations -> obligations.forEach(obligation -> obligationsWithoutHandler.add(obligation)));

		var onDecisionHandlers     = constructOnDecisionHandlers(decision, obligationsWithoutHandler);
		var errorMappingHandlers   = constructErrorMappingHandlers(decision, obligationsWithoutHandler);
		var errorConsumerHandlers  = constructErrorConsumerHandlers(decision, obligationsWithoutHandler);
		var resultMappingHandlers  = constructResultMappingHandlers(decision, obligationsWithoutHandler, responseType);
		var resultConsumerHandlers = constructResultConsumerHandlers(decision, obligationsWithoutHandler, responseType);

		if (!obligationsWithoutHandler.isEmpty()) {
			log.error("Could not find handlers for all obligations. Missing handlers for: {}",
					obligationsWithoutHandler);
			throw new AccessDeniedException("Access Denied");
		}

		return new AxonQueryConstraintHandlerBundle(onDecisionHandlers, Functions.identity(), __ -> {
		}, errorMappingHandlers, errorConsumerHandlers, resultMappingHandlers, resultConsumerHandlers,
				Functions.identity(), __ -> {
				}, __ -> true);
	}

	private Consumer<QueryMessage<?, ?>> constructQueryConsumerHandlers(AuthorizationDecision decision,
			HashSet<JsonNode> obligationsWithoutHandler) {
		var obligationConsumers = constructQueryConsumerHandlers(decision.getObligations(),
				obligationsWithoutHandler::remove);
		var adviceConsumers     = constructQueryConsumerHandlers(decision.getAdvice(), __ -> {
								});
		return query -> {
			obligationConsumers.ifPresent(handler -> BundleUtil.obligation(handler).accept(query));
			adviceConsumers.ifPresent(handler -> BundleUtil.advice(handler).accept(query));
		};
	}

	@SuppressWarnings("unchecked")
	private Optional<Consumer<QueryMessage<?, ?>>> constructQueryConsumerHandlers(Optional<ArrayNode> constraints,
			Consumer<JsonNode> onHandlerFound) {
		return constraints.map(constraintsArray -> {
			var handlers = new ArrayList<Consumer<QueryMessage<?, ?>>>(constraintsArray.size());
			for (var constraint : constraintsArray) {
				for (var provider : globalConsumerProviders) {
					if (QueryMessage.class.isAssignableFrom(provider.getSupportedType())
							&& provider.isResponsible(constraint)) {
						onHandlerFound.accept(constraint);
						handlers.add((Consumer<QueryMessage<?, ?>>) provider.getHandler(constraint));
					}
				}
			}
			return BundleUtil.consumeAll(handlers);
		});
	}

	private <T> Consumer<T> constructResultConsumerHandlers(AuthorizationDecision decision,
			HashSet<JsonNode> obligationsWithoutHandler, ResponseType<T> responseType) {
		var obligationConsumers = constructResultConsumerHandlers(decision.getObligations(),
				obligationsWithoutHandler::remove, responseType);
		var adviceConsumers     = constructResultConsumerHandlers(decision.getAdvice(), __ -> {
								}, responseType);
		return result -> {
			obligationConsumers.ifPresent(handler -> BundleUtil.obligation(handler).accept(result));
			adviceConsumers.ifPresent(handler -> BundleUtil.advice(handler).accept(result));
		};
	}

	@SuppressWarnings("unchecked")
	private <T> Optional<Consumer<T>> constructResultConsumerHandlers(Optional<ArrayNode> constraints,
			Consumer<JsonNode> onHandlerFound, ResponseType<T> responseType) {
		return constraints.map(constraintsArray -> {
			var handlers = new ArrayList<Consumer<T>>(constraintsArray.size());
			for (var constraint : constraintsArray) {
				for (var provider : globalConsumerProviders) {
					if (responseType.getExpectedResponseType().isAssignableFrom(provider.getSupportedType())
							&& provider.isResponsible(constraint)) {
						onHandlerFound.accept(constraint);
						handlers.add((Consumer<T>) provider.getHandler(constraint));
					}
				}
			}
			return BundleUtil.consumeAll(handlers);
		});
	}

	private Function<Throwable, Throwable> constructErrorMappingHandlers(AuthorizationDecision decision,
			HashSet<JsonNode> obligationsWithoutHandler) {
		var obligationFun = constructErrorMappingHandlers(decision.getObligations(), obligationsWithoutHandler::remove);
		var adviceFun     = constructErrorMappingHandlers(decision.getAdvice(), __ -> {
							});

		return error -> {
			var newError = error;
			try {
				newError = obligationFun.orElse(Functions.identity()).apply(newError);
			} catch (Throwable t) {
				log.error("Failed to execute obligation handlers.", t);
				throw new AccessDeniedException("Access Denied");
			}
			try {
				newError = adviceFun.orElse(Functions.identity()).apply(newError);
			} catch (Throwable t) {
				log.error("Failed to execute advice handlers.", t);
			}
			return newError;
		};
	}

	private Optional<Function<Throwable, Throwable>> constructErrorMappingHandlers(Optional<ArrayNode> constraints,
			Consumer<JsonNode> onHandlerFound) {
		return constraints.map(constraintsArray -> {
			var handlers = new ArrayList<Function<Throwable, Throwable>>(constraintsArray.size());
			for (var constraint : constraintsArray) {
				for (var provider : globalErrorMappingHandlerProviders) {
					if (provider.isResponsible(constraint)) {
						onHandlerFound.accept(constraint);
						handlers.add(provider.getHandler(constraint));
					}
				}
			}
			return BundleUtil.mapAll(handlers);
		});
	}

	private Consumer<Throwable> constructErrorConsumerHandlers(AuthorizationDecision decision,
			HashSet<JsonNode> obligationsWithoutHandler) {
		var obligationConsumers = constructErrorConsumerHandlers(decision.getObligations(),
				obligationsWithoutHandler::remove);
		var adviceConsumers     = constructErrorConsumerHandlers(decision.getAdvice(), __ -> {
								});
		return error -> {
			obligationConsumers.ifPresent(handler -> BundleUtil.obligation(handler).accept(error));
			adviceConsumers.ifPresent(handler -> BundleUtil.advice(handler).accept(error));
		};
	}

	private Optional<Consumer<Throwable>> constructErrorConsumerHandlers(Optional<ArrayNode> constraints,
			Consumer<JsonNode> onHandlerFound) {
		return constraints.map(constraintsArray -> {
			var handlers = new ArrayList<Consumer<Throwable>>(constraintsArray.size());
			for (var constraint : constraintsArray) {
				for (var provider : globalErrorHandlerProviders) {
					if (provider.isResponsible(constraint)) {
						onHandlerFound.accept(constraint);
						handlers.add(provider.getHandler(constraint));
					}
				}
			}
			return BundleUtil.consumeAll(handlers);
		});
	}

	private Function<QueryMessage<?, ?>, QueryMessage<?, ?>> constructQueryMessageMappingHandlers(
			AuthorizationDecision decision, HashSet<JsonNode> obligationsWithoutHandler) {
		var obligationFun = constructQueryMessageMappingHandlers(decision.getObligations(),
				obligationsWithoutHandler::remove);
		var adviceFun     = constructQueryMessageMappingHandlers(decision.getAdvice(), __ -> {
							});

		return query -> {
			var newQuery = query;
			try {
				newQuery = obligationFun.orElse(Functions.identity()).apply(newQuery);
			} catch (Throwable t) {
				log.error("Failed to execute obligation handlers.", t);
				throw new AccessDeniedException("Access Denied");
			}
			try {
				newQuery = adviceFun.orElse(Functions.identity()).apply(newQuery);
			} catch (Throwable t) {
				log.error("Failed to execute advice handlers.", t);
			}
			return newQuery;
		};
	}

	private Optional<Function<QueryMessage<?, ?>, QueryMessage<?, ?>>> constructQueryMessageMappingHandlers(
			Optional<ArrayNode> constraints, Consumer<JsonNode> onHandlerFound) {
		return constraints.map(constraintsArray -> {
			var handlers = new ArrayList<Function<QueryMessage<?, ?>, QueryMessage<?, ?>>>(constraintsArray.size());
			for (var constraint : constraintsArray) {
				for (var provider : globalQueryMessageMappingProviders) {
					if (provider.isResponsible(constraint)) {
						onHandlerFound.accept(constraint);
						handlers.add(provider.getHandler(constraint));
					}
				}
			}
			return BundleUtil.mapAll(handlers);
		});
	}

	private <T> Function<T, T> constructResultMappingHandlers(AuthorizationDecision decision,
			HashSet<JsonNode> obligationsWithoutHandler, ResponseType<T> responseType) {
		var obligationFun = constructResultMappingHandlers(decision.getObligations(), obligationsWithoutHandler::remove,
				responseType);
		var adviceFun     = constructResultMappingHandlers(decision.getAdvice(), __ -> {
							}, responseType);

		return result -> {
			var newResult = result;
			try {
				newResult = obligationFun.orElse(Functions.identity()).apply(result);
			} catch (Throwable t) {
				log.error("Failed to execute obligation handlers.", t);
				throw new AccessDeniedException("Access Denied");
			}
			try {
				newResult = adviceFun.orElse(Functions.identity()).apply(newResult);
			} catch (Throwable t) {
				log.error("Failed to execute advice handlers.", t);
			}
			return newResult;
		};
	}

	@SuppressWarnings("unchecked")
	private <T> Optional<Function<T, T>> constructResultMappingHandlers(Optional<ArrayNode> constraints,
			Consumer<JsonNode> onHandlerFound, ResponseType<T> responseType) {
		return constraints.map(constraintsArray -> {
			var handlers = new ArrayList<Function<T, T>>(constraintsArray.size());
			for (var constraint : constraintsArray) {
				for (var provider : globalMappingProviders) {
					if (responseType.getExpectedResponseType().isAssignableFrom(provider.getSupportedType())
							&& provider.isResponsible(constraint)) {
						onHandlerFound.accept(constraint);
						handlers.add((Function<T, T>) provider.getHandler(constraint));
					}
				}
			}
			return BundleUtil.mapAll(handlers);
		});
	}

	private Runnable constructOnDecisionHandlers(AuthorizationDecision decision,
			Set<JsonNode> obligationsWithoutHandler) {
		var onDecisionObligationHandlers = runnableHandlersForSignal(Signal.ON_DECISION, decision.getObligations(),
				obligationsWithoutHandler::remove);
		var onDecisionAdviceHandlers     = runnableHandlersForSignal(Signal.ON_DECISION, decision.getAdvice(), __ -> {
											});

		return () -> {
			onDecisionObligationHandlers.map(BundleUtil::obligation).ifPresent(Runnable::run);
			onDecisionAdviceHandlers.map(BundleUtil::advice).ifPresent(Runnable::run);
		};
	}

	private Optional<Runnable> runnableHandlersForSignal(Signal signal, Optional<ArrayNode> constraints,
			Consumer<JsonNode> onHandlerFound) {
		return constraints.map(obligations -> {
			var handlers = new ArrayList<Runnable>(obligations.size());
			for (var constraint : obligations) {
				for (var provider : globalRunnableProviders) {
					if (provider.getSignals().contains(signal) && provider.isResponsible(constraint)) {
						onHandlerFound.accept(constraint);
						handlers.add(provider.getHandler(constraint));
					}
				}
			}
			return BundleUtil.runAll(handlers);
		});
	}

}
