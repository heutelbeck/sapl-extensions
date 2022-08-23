package io.sapl.axon.constrainthandling;

import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Executable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.messaging.ResultMessage;
import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.queryhandling.QueryMessage;
import org.springframework.security.access.AccessDeniedException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.google.common.base.Functions;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.axon.constrainthandling.api.CommandConstraintHandlerProvider;
import io.sapl.axon.constrainthandling.api.OnDecisionConstraintHandlerProvider;
import io.sapl.axon.constrainthandling.api.QueryConstraintHandlerProvider;
import io.sapl.axon.constrainthandling.api.UpdateFilterConstraintHandlerProvider;
import io.sapl.axon.constrainthandling.api.ResultConstraintHandlerProvider;
import io.sapl.spring.constraints.api.ErrorMappingConstraintHandlerProvider;
import io.sapl.spring.constraints.api.MappingConstraintHandlerProvider;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AxonConstraintHandlerService {

	private final ObjectMapper                                   mapper;
	private final List<OnDecisionConstraintHandlerProvider>      globalRunnableProviders;
	private final List<CommandConstraintHandlerProvider>         globalCommandMessageMappingProviders;
	private final List<QueryConstraintHandlerProvider>           globalQueryMessageMappingProviders;
	private final List<ErrorMappingConstraintHandlerProvider>    globalErrorMappingHandlerProviders;
	private final List<MappingConstraintHandlerProvider<?>>      globalMappingProviders;
	private final List<UpdateFilterConstraintHandlerProvider<?>> updatePredicateProviders;
	private final List<ResultConstraintHandlerProvider<?>>       updateMappingProviders;

	public AxonConstraintHandlerService(ObjectMapper mapper,
			List<OnDecisionConstraintHandlerProvider> globalRunnableProviders,
			List<CommandConstraintHandlerProvider> globalCommandMessageMappingProviders,
			List<QueryConstraintHandlerProvider> globalQueryMessageMappingProviders,
			List<ErrorMappingConstraintHandlerProvider> globalErrorMappingHandlerProviders,
			List<MappingConstraintHandlerProvider<?>> globalMappingProviders,
			List<UpdateFilterConstraintHandlerProvider<?>> updatePredicateProviders,
			List<ResultConstraintHandlerProvider<?>> updateMappingProviders) {

		this.mapper = mapper;
		// sort according to priority
		this.globalRunnableProviders  = globalRunnableProviders;
		this.updatePredicateProviders = updatePredicateProviders;

		Collections.sort(this.globalRunnableProviders);
		this.globalQueryMessageMappingProviders = globalQueryMessageMappingProviders;
		Collections.sort(this.globalQueryMessageMappingProviders);
		this.globalErrorMappingHandlerProviders = globalErrorMappingHandlerProviders;
		Collections.sort(this.globalErrorMappingHandlerProviders);
		this.globalMappingProviders = globalMappingProviders;
		Collections.sort(this.globalMappingProviders);
		this.updateMappingProviders = updateMappingProviders;
		Collections.sort(this.updateMappingProviders);
		this.globalCommandMessageMappingProviders = globalCommandMessageMappingProviders;
		Collections.sort(this.globalCommandMessageMappingProviders);
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

	public <T> CommandConstraintHandlerBundle<?, ?> buildPreEnforceCommandConstraintHandlerBundle(
			AuthorizationDecision decision, T aggregate, Optional<Executable> executable) {

		if (decision.getResource().isPresent()) {
			log.warn("PDP decision contained resource object for command handler. Access Denied. {}", decision);
			throw new AccessDeniedException("Access Denied");
		}

		var obligationsWithoutHandler = new HashSet<JsonNode>();
		decision.getObligations()
				.ifPresent(obligations -> obligations.forEach(obligation -> obligationsWithoutHandler.add(obligation)));

		Optional<Class<?>> returnType = executable.map(Executable::getAnnotatedReturnType).map(AnnotatedType::getType)
				.map(TypeFactory::rawClass);

		Class<?> type = returnType.orElse(Object.class);

		var onDecisionHandlers     = constructOnDecisionHandlers(decision, obligationsWithoutHandler);
		var commandMappingHandlers = constructCommandMessageMappingHandlers(decision, obligationsWithoutHandler);
		var errorMappingHandlers   = constructErrorMappingHandlers(decision, obligationsWithoutHandler);
		var resultMappingHandlers  = constructResultMappingHandlers(decision, obligationsWithoutHandler, type);
		var handlersOnObject       = (Runnable) () -> {
									};
		if (!obligationsWithoutHandler.isEmpty()) {
			log.error("Could not find handlers for all obligations. Missing handlers for: {}",
					obligationsWithoutHandler);
			throw new AccessDeniedException("Access Denied");
		}
		return new CommandConstraintHandlerBundle<>(onDecisionHandlers, errorMappingHandlers, commandMappingHandlers,
				resultMappingHandlers, handlersOnObject);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public QueryConstraintHandlerBundle<?, ?> buildQueryPreHandlerBundle(AuthorizationDecision decision,
			ResponseType<?> responseType, Optional<ResponseType<?>> updateType) {

		var obligationsWithoutHandler = new HashSet<JsonNode>();
		decision.getObligations()
				.ifPresent(obligations -> obligations.forEach(obligation -> obligationsWithoutHandler.add(obligation)));

		var onDecisionHandlers    = constructOnDecisionHandlers(decision, obligationsWithoutHandler);
		var queryMappingHandlers  = constructQueryMessageMappingHandlers(decision, obligationsWithoutHandler);
		var errorMappingHandlers  = constructErrorMappingHandlers(decision, obligationsWithoutHandler);
		var resultMappingHandlers = constructResultMappingHandlers(decision, obligationsWithoutHandler, responseType);

		Function<?, ?> updateMappingHandlers = Functions.identity();
		Predicate<?>   updateFilterPredicate = __ -> true;

		if (updateType.isPresent()) {
			updateMappingHandlers = constructUpdateMessageMappingHandlers(decision, obligationsWithoutHandler,
					updateType.get());
			updateFilterPredicate = constructFilterPredicateHandlers(decision, obligationsWithoutHandler,
					updateType.get());
		}

		if (!obligationsWithoutHandler.isEmpty()) {
			log.error("Could not find handlers for all obligations. Missing handlers for: {}",
					obligationsWithoutHandler);
			throw new AccessDeniedException("Access Denied");
		}

		return new QueryConstraintHandlerBundle(onDecisionHandlers, queryMappingHandlers, errorMappingHandlers,
				resultMappingHandlers, updateMappingHandlers, updateFilterPredicate);
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
	public QueryConstraintHandlerBundle<?, ?> buildQueryPostHandlerBundle(AuthorizationDecision decision,
			ResponseType<?> responseType) {

		var obligationsWithoutHandler = new HashSet<JsonNode>();
		decision.getObligations()
				.ifPresent(obligations -> obligations.forEach(obligation -> obligationsWithoutHandler.add(obligation)));

		var onDecisionHandlers    = constructOnDecisionHandlers(decision, obligationsWithoutHandler);
		var errorMappingHandlers  = constructErrorMappingHandlers(decision, obligationsWithoutHandler);
		var resultMappingHandlers = constructResultMappingHandlers(decision, obligationsWithoutHandler, responseType);

		if (!obligationsWithoutHandler.isEmpty()) {
			log.error("Could not find handlers for all obligations. Missing handlers for: {}",
					obligationsWithoutHandler);
			throw new AccessDeniedException("Access Denied");
		}

		return new QueryConstraintHandlerBundle(onDecisionHandlers, Functions.identity(), errorMappingHandlers,
				resultMappingHandlers, Functions.identity(), __ -> true);
	}

	private Function<Throwable, Throwable> constructErrorMappingHandlers(AuthorizationDecision decision,
			HashSet<JsonNode> obligationsWithoutHandler) {
		var obligationFun = constructErrorMappingHandlers(decision.getObligations(), obligationsWithoutHandler::remove);
		var adviceFun     = constructErrorMappingHandlers(decision.getAdvice(), __ -> {
							});

		return error -> {
			log.debug("*** original {}", error);
			var newError = error;
			try {
				newError = obligationFun.orElse(Functions.identity()).apply(newError);
				log.debug("*** after o {}", newError);
			} catch (Throwable t) {
				log.error("Failed to execute obligation handlers.", t);
				throw new AccessDeniedException("Access Denied");
			}
			try {
				newError = adviceFun.orElse(Functions.identity()).apply(newError);
				log.debug("*** after a {}", newError);
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

	private Function<CommandMessage<?>, CommandMessage<?>> constructCommandMessageMappingHandlers(
			AuthorizationDecision decision, HashSet<JsonNode> obligationsWithoutHandler) {
		var obligationFun = constructCommandMessageMappingHandlers(decision.getObligations(),
				obligationsWithoutHandler::remove);
		var adviceFun     = constructCommandMessageMappingHandlers(decision.getAdvice(), __ -> {
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

	private Optional<Function<CommandMessage<?>, CommandMessage<?>>> constructCommandMessageMappingHandlers(
			Optional<ArrayNode> constraints, Consumer<JsonNode> onHandlerFound) {
		return constraints.map(constraintsArray -> {
			var handlers = new ArrayList<Function<CommandMessage<?>, CommandMessage<?>>>(constraintsArray.size());
			for (var constraint : constraintsArray) {
				for (var provider : globalCommandMessageMappingProviders) {
					if (provider.isResponsible(constraint)) {
						onHandlerFound.accept(constraint);
						handlers.add(provider.getHandler(constraint));
					}
				}
			}
			return BundleUtil.mapAll(handlers);
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

	@SuppressWarnings("unchecked")
	private <T> Function<T, T> constructResultMappingHandlers(AuthorizationDecision decision,
			HashSet<JsonNode> obligationsWithoutHandler, ResponseType<T> responseType) {
		return (Function<T, T>) constructResultMappingHandlers(decision, obligationsWithoutHandler,
				responseType.getExpectedResponseType());
	}

	@SuppressWarnings("unchecked")
	private <T> Function<T, T> constructResultMappingHandlers(AuthorizationDecision decision,
			HashSet<JsonNode> obligationsWithoutHandler, Class<?> type) {
		var obligationFun = constructResultMappingHandlers(decision.getObligations(), obligationsWithoutHandler::remove,
				type);
		var adviceFun     = constructResultMappingHandlers(decision.getAdvice(), __ -> {
							}, type);

		return result -> {
			var newResult = result;
			try {
				newResult = (T) obligationFun.orElse(Functions.identity()).apply(result);
			} catch (Throwable t) {
				log.error("Failed to execute obligation handlers.", t);
				throw new AccessDeniedException("Access Denied");
			}
			try {
				newResult = (T) adviceFun.orElse(Functions.identity()).apply(newResult);
			} catch (Throwable t) {
				log.error("Failed to execute advice handlers.", t);
			}
			return newResult;
		};
	}

	@SuppressWarnings("unchecked")
	private <T> Optional<Function<T, T>> constructResultMappingHandlers(Optional<ArrayNode> constraints,
			Consumer<JsonNode> onHandlerFound, Class<?> responseType) {
		return constraints.map(constraintsArray -> {
			var handlers = new ArrayList<Function<T, T>>(constraintsArray.size());
			for (var constraint : constraintsArray) {
				for (var provider : globalMappingProviders) {
					if (responseType.isAssignableFrom(provider.getSupportedType())
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
		var onDecisionObligationHandlers = ondecisionHandlersForSignal(decision.getObligations(),
				obligationsWithoutHandler::remove);
		var onDecisionAdviceHandlers     = ondecisionHandlersForSignal(decision.getAdvice(), __ -> {
											});

		return () -> {
			onDecisionObligationHandlers.map(BundleUtil::obligation).ifPresent(Runnable::run);
			onDecisionAdviceHandlers.map(BundleUtil::advice).ifPresent(Runnable::run);
		};
	}

	private Optional<Runnable> ondecisionHandlersForSignal(Optional<ArrayNode> constraints,
			Consumer<JsonNode> onHandlerFound) {
		return constraints.map(obligations -> {
			var handlers = new ArrayList<Runnable>(obligations.size());
			for (var constraint : obligations) {
				for (var provider : globalRunnableProviders) {
					if (provider.isResponsible(constraint)) {
						onHandlerFound.accept(constraint);
						handlers.add(provider.getHandler(constraint));
					}
				}
			}
			return BundleUtil.runAll(handlers);
		});
	}

}
