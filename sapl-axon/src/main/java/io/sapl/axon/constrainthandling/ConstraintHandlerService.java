/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.sapl.axon.constrainthandling;

import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.axonframework.messaging.responsetypes.InstanceResponseType;
import org.axonframework.messaging.responsetypes.MultipleInstancesResponseType;
import org.axonframework.messaging.responsetypes.OptionalResponseType;
import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.queryhandling.QueryMessage;
import org.springframework.expression.spel.SpelEvaluationException;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.security.access.AccessDeniedException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.google.common.base.Functions;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.axon.annotation.ConstraintHandler;
import io.sapl.axon.constrainthandling.api.CommandConstraintHandlerProvider;
import io.sapl.axon.constrainthandling.api.OnDecisionConstraintHandlerProvider;
import io.sapl.axon.constrainthandling.api.QueryConstraintHandlerProvider;
import io.sapl.axon.constrainthandling.api.ResultConstraintHandlerProvider;
import io.sapl.axon.constrainthandling.api.UpdateFilterConstraintHandlerProvider;
import io.sapl.spring.constraints.api.ErrorMappingConstraintHandlerProvider;
import io.sapl.spring.constraints.api.MappingConstraintHandlerProvider;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

/**
 * This service collects all constraint handlers available in the application
 * context and context of command execution and assembles a constraint handler
 * bundle for each decision.
 * <p>
 * It checks if all obligations can be satisfied or raises an
 * AccessDeniedException.
 *
 * @author Dominic Heutelbeck
 * @since 2.1.0
 */
@Slf4j
public class ConstraintHandlerService {
    private static final SpelExpressionParser PARSER = new SpelExpressionParser();

    private final ObjectMapper                                mapper;
    private final ParameterResolverFactory                    parameterResolverFactory;
    private final List<OnDecisionConstraintHandlerProvider>   globalRunnableProviders;
    private final List<CommandConstraintHandlerProvider>      globalCommandMessageMappingProviders;
    private final List<QueryConstraintHandlerProvider>        globalQueryMessageMappingProviders;
    private final List<ErrorMappingConstraintHandlerProvider> globalErrorMappingHandlerProviders;
    private final List<MappingConstraintHandlerProvider<?>>   globalMappingProviders;
    private final List<UpdateFilterConstraintHandlerProvider> updatePredicateProviders;
    private final List<ResultConstraintHandlerProvider>       updateMappingProviders;

    /**
     * Instantiate the ConstraintHandlerService.
     *
     * @param mapper                         The systems ObjectMapper
     * @param parameterResolverFactory       Axon parameter resolver factory.
     * @param onDecisionProviders            All OnDecisionConstraintHandlerProvider
     *                                       instances.
     * @param globalCommandProviders         All CommandConstraintHandlerProvider
     *                                       instances.
     * @param globalQueryProviders           All QueryConstraintHandlerProvider
     *                                       instances.
     * @param globalErrorHandlerProviders    All
     *                                       ErrorMappingConstraintHandlerProvider
     *                                       instances.
     * @param globalMappingProviders         All MappingConstraintHandlerProvider
     *                                       instances.
     * @param globalUpdatePredicateProviders All
     *                                       UpdateFilterConstraintHandlerProvider
     *                                       instances.
     * @param updateMappingProviders         All ResultConstraintHandlerProvider
     *                                       instances.
     */
    public ConstraintHandlerService(ObjectMapper mapper, ParameterResolverFactory parameterResolverFactory,
            List<OnDecisionConstraintHandlerProvider> onDecisionProviders,
            List<CommandConstraintHandlerProvider> globalCommandProviders,
            List<QueryConstraintHandlerProvider> globalQueryProviders,
            List<ErrorMappingConstraintHandlerProvider> globalErrorHandlerProviders,
            List<MappingConstraintHandlerProvider<?>> globalMappingProviders,
            List<UpdateFilterConstraintHandlerProvider> globalUpdatePredicateProviders,
            List<ResultConstraintHandlerProvider> updateMappingProviders) {

        this.mapper                   = mapper;
        this.parameterResolverFactory = parameterResolverFactory;

        log.debug("Loading constraint handler providers...");

        this.updatePredicateProviders = new ArrayList<>(globalUpdatePredicateProviders);
        logDeployedHandlers("Update Predicate Providers:", this.updatePredicateProviders);
        // sort according to priority
        this.globalRunnableProviders = new ArrayList<>(onDecisionProviders);
        Collections.sort(this.globalRunnableProviders);
        logDeployedHandlers("Update Runnable Providers:", this.globalRunnableProviders);
        this.globalQueryMessageMappingProviders = new ArrayList<>(globalQueryProviders);
        Collections.sort(this.globalQueryMessageMappingProviders);
        logDeployedHandlers("Query Mappers:", new ArrayList<>(this.globalQueryMessageMappingProviders));
        this.globalErrorMappingHandlerProviders = new ArrayList<>(globalErrorHandlerProviders);
        Collections.sort(this.globalErrorMappingHandlerProviders);
        logDeployedHandlers("Error Mappers:", this.globalErrorMappingHandlerProviders);
        this.globalMappingProviders = new ArrayList<>(globalMappingProviders);
        Collections.sort(this.globalMappingProviders);
        logDeployedHandlers("Mapping Mappers:", this.globalMappingProviders);
        this.updateMappingProviders = new ArrayList<>(updateMappingProviders);
        Collections.sort(this.updateMappingProviders);
        logDeployedHandlers("Update Mappers:", this.updateMappingProviders);
        this.globalCommandMessageMappingProviders = new ArrayList<>(globalCommandProviders);
        Collections.sort(this.globalCommandMessageMappingProviders);
        logDeployedHandlers("Command Mappers:", this.globalCommandMessageMappingProviders);
    }

    private void logDeployedHandlers(String description, Collection<?> handlers) {
        if (handlers.isEmpty())
            return;
        log.debug(description);
        for (var handler : handlers) {
            log.debug(" - {}", handler.getClass().getSimpleName());
        }
    }

    /**
     * Attempts to deserialize a JSON Object to the provided class.
     *
     * @param <T>      Expected Type.
     * @param resource JSON representation of resource
     * @param type     Expected type.
     * @return The deserialized resource, or AccessDeniedException if
     *         deserialization fails.
     */
    @SuppressWarnings("unchecked")
    public <T> Object deserializeResource(JsonNode resource, ResponseType<T> type) {

        if (InstanceResponseType.class.isAssignableFrom(type.getClass())) {
            try {
                return mapper.treeToValue(resource, (Class<T>) type.getExpectedResponseType());
            } catch (JsonProcessingException | IllegalArgumentException e) {
                log.error("Failed to deserialize resource object from decision: {}", e.getLocalizedMessage());
                throw new AccessDeniedException("Access Denied");
            }
        }

        else if (MultipleInstancesResponseType.class.isAssignableFrom(type.getClass())) {

            if (!ArrayNode.class.isAssignableFrom(resource.getClass())) {
                log.error("resource is no array, however a MultipleInstancesResponseType was expected!");
                throw new AccessDeniedException("Access Denied");
            }

            var deserialized = List.of();
            try {
                deserialized = mapper.treeToValue(resource, List.class);
            } catch (JsonProcessingException | IllegalArgumentException e) {
                log.error("Failed to deserialize resource object from decision: {}", e.getLocalizedMessage());
                throw new AccessDeniedException("Access Denied");
            }

            if (!deserialized.isEmpty()
                    && (!type.getExpectedResponseType().isAssignableFrom(deserialized.get(0).getClass()))) {
                log.error("Unsupported entry in resource: " + deserialized.get(0).getClass() + ", expected: "
                        + type.getExpectedResponseType());
                throw new AccessDeniedException("Access Denied");
            }

            return deserialized;
        }

        else if (OptionalResponseType.class.isAssignableFrom(type.getClass())) {
            try {
                return Optional.ofNullable(mapper.treeToValue(resource, (Class<T>) type.getExpectedResponseType()));
            } catch (JsonProcessingException | IllegalArgumentException e) {
                log.error("Failed to deserialize resource object from decision: {}", e.getLocalizedMessage());
                throw new AccessDeniedException("Access Denied");
            }
        }

        else {
            log.error("Unsupported ResponseType: {}", type.getClass());
            throw new AccessDeniedException("Access Denied");
        }
    }

    /**
     * Build the bundle for command handling.
     *
     * @param <T>           Type of the handler Object.
     * @param decision      The decision.
     * @param handlerObject The handlerObject.
     * @param executable    The Executable.
     * @param command       The command.
     * @return A CommandConstraintHandlerBundle.
     */
    public <T> CommandConstraintHandlerBundle<?> buildPreEnforceCommandConstraintHandlerBundle(
            AuthorizationDecision decision, T handlerObject, Optional<Executable> executable,
            CommandMessage<?> command) {

        if (decision.getResource().isPresent()) {
            log.warn("PDP decision contained resource object for command handler. Access Denied. {}", decision);
            throw new AccessDeniedException("Access Denied");
        }

        var obligationsWithoutHandler = new HashSet<JsonNode>();
        decision.getObligations().ifPresent(obligations -> obligations.forEach(obligationsWithoutHandler::add));

        Optional<Class<?>> returnType = executable.map(Executable::getAnnotatedReturnType).map(AnnotatedType::getType)
                .map(TypeFactory::rawClass);

        Class<?> type = returnType.orElse(Object.class);

        var onDecisionHandlers     = constructOnDecisionHandlers(decision, obligationsWithoutHandler);
        var commandMappingHandlers = constructCommandMessageMappingHandlers(decision, obligationsWithoutHandler);
        var errorMappingHandlers   = constructErrorMappingHandlers(decision, obligationsWithoutHandler);
        var resultMappingHandlers  = constructResultMappingHandlers(decision, obligationsWithoutHandler, type);
        var handlersOnObject       = constructObjectConstraintHandlers(handlerObject, command, decision,
                obligationsWithoutHandler);

        if (!obligationsWithoutHandler.isEmpty()) {
            log.error("Could not find handlers for all obligations. Missing handlers for: {}",
                    obligationsWithoutHandler);
            throw new AccessDeniedException("Access Denied");
        }
        return new CommandConstraintHandlerBundle<>(onDecisionHandlers, errorMappingHandlers, commandMappingHandlers,
                resultMappingHandlers, handlersOnObject);
    }

    /**
     * Build the QueryConstraintHandlerBundle for pre-query handling.
     *
     * @param decision     The decision.
     * @param responseType The response type.
     * @param updateType   The update type. Optional. Non-empty for subscription
     *                     queries.
     * @return A QueryConstraintHandlerBundle.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public QueryConstraintHandlerBundle<?> buildQueryPreHandlerBundle(AuthorizationDecision decision,
            ResponseType<?> responseType, Optional<ResponseType<?>> updateType) {

        var obligationsWithoutHandler = new HashSet<JsonNode>();
        decision.getObligations().ifPresent(obligations -> obligations.forEach(obligationsWithoutHandler::add));

        var onDecisionHandlers    = constructOnDecisionHandlers(decision, obligationsWithoutHandler);
        var queryMappingHandlers  = constructQueryMessageMappingHandlers(decision, obligationsWithoutHandler);
        var errorMappingHandlers  = constructErrorMappingHandlers(decision, obligationsWithoutHandler);
        var resultMappingHandlers = constructResultMessageMappingHandlers(decision, obligationsWithoutHandler,
                responseType);

        Function<?, ?> updateMappingHandlers = Functions.identity();
        Predicate<?>   updateFilterPredicate = __ -> true;

        if (updateType.isPresent()) {
            updateMappingHandlers = constructResultMessageMappingHandlers(decision, obligationsWithoutHandler,
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

    private <T> UnaryOperator<Object> constructResultMessageMappingHandlers(AuthorizationDecision decision,
            HashSet<JsonNode> obligationsWithoutHandler, ResponseType<T> responseType) {
        var obligationFun = constructResultMessageMappingHandlers(decision.getObligations(),
                obligationsWithoutHandler::remove, responseType);
        var adviceFun     = constructResultMessageMappingHandlers(decision.getAdvice(), __ -> {
                          }, responseType);

        return result -> {
            var newResult = result;
            try {
                newResult = obligationFun.orElseGet(Functions::identity).apply(result);
            } catch (Throwable t) {
                log.error("Failed to execute obligation handlers. {}", t.getLocalizedMessage());
                throw new AccessDeniedException("Access Denied", t);
            }
            try {
                newResult = adviceFun.orElseGet(Functions::identity).apply(newResult);
            } catch (Throwable t) {
                log.error("Failed to execute advice handlers. {}", t.getLocalizedMessage());
            }
            return newResult;
        };
    }

    private <T> Optional<Function<Object, Object>> constructResultMessageMappingHandlers(
            Optional<ArrayNode> constraints, Consumer<JsonNode> onHandlerFound, ResponseType<T> responseType) {
        return constraints.map(constraintsArray -> {
            var handlersWithPriority = new ArrayList<HandlerWithPriority<Function<Object, Object>>>(
                    constraintsArray.size());

            for (var constraint : constraintsArray) {
                for (var provider : updateMappingProviders) {
                    if (provider.supports(responseType) && provider.isResponsible(constraint)) {
                        onHandlerFound.accept(constraint);
                        handlersWithPriority.add(
                                new HandlerWithPriority<>(provider.getHandler(constraint), provider.getPriority()));
                    }
                }
            }
            Collections.sort(handlersWithPriority);

            return mapAll(handlersWithPriority);
        });
    }

    @Data
    @AllArgsConstructor
    static class HandlerWithPriority<T> implements Comparable<HandlerWithPriority<T>> {
        T   handler;
        int priority;

        @Override
        public int compareTo(HandlerWithPriority<T> o) {
            return Integer.compare(o.getPriority(), getPriority());
        }
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
                    if (provider.supports(responseType) && provider.isResponsible(constraint)) {
                        onHandlerFound.accept(constraint);
                        handlers.add((Predicate<T>) provider.getHandler(constraint));
                    }
                }
            }
            return andAll(handlers);
        }).orElse(__ -> true);
    }

    /**
     * Build the QueryConstraintHandlerBundle for post-query handling.
     *
     * @param decision     The decision.
     * @param responseType The response type.
     * @return A QueryConstraintHandlerBundle.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public QueryConstraintHandlerBundle<?> buildQueryPostHandlerBundle(AuthorizationDecision decision,
            ResponseType<?> responseType) {

        var obligationsWithoutHandler = new HashSet<JsonNode>();
        decision.getObligations().ifPresent(obligations -> obligations.forEach(obligationsWithoutHandler::add));

        var onDecisionHandlers    = constructOnDecisionHandlers(decision, obligationsWithoutHandler);
        var errorMappingHandlers  = constructErrorMappingHandlers(decision, obligationsWithoutHandler);
        var resultMappingHandlers = constructResultMessageMappingHandlers(decision, obligationsWithoutHandler,
                responseType);

        if (!obligationsWithoutHandler.isEmpty()) {
            log.error("Could not find handlers for all obligations. Missing handlers for: {}",
                    obligationsWithoutHandler);
            throw new AccessDeniedException("Access Denied");
        }

        return new QueryConstraintHandlerBundle(onDecisionHandlers, Functions.identity(), errorMappingHandlers,
                resultMappingHandlers, Functions.identity(), __ -> true);
    }

    private UnaryOperator<Throwable> constructErrorMappingHandlers(AuthorizationDecision decision,
            HashSet<JsonNode> obligationsWithoutHandler) {
        var obligationFun = constructErrorMappingHandlers(decision.getObligations(), obligationsWithoutHandler::remove);
        var adviceFun     = constructErrorMappingHandlers(decision.getAdvice(), __ -> {
                          });

        return error -> {
            var newError = error;
            try {
                newError = obligationFun.orElseGet(Functions::identity).apply(newError);
            } catch (Throwable t) {
                log.error("Failed to execute obligation handlers. {}", t.getLocalizedMessage());
                throw new AccessDeniedException("Access Denied");
            }
            try {
                newError = adviceFun.orElseGet(Functions::identity).apply(newError);
            } catch (Throwable t) {
                log.error("Failed to execute advice handlers. {}", t.getLocalizedMessage());
            }
            return newError;
        };
    }

    private Optional<Function<Throwable, Throwable>> constructErrorMappingHandlers(Optional<ArrayNode> constraints,
            Consumer<JsonNode> onHandlerFound) {
        return constraints.map(constraintsArray -> {
            var handlersWithPriority = new ArrayList<HandlerWithPriority<Function<Throwable, Throwable>>>(
                    constraintsArray.size());

            for (var constraint : constraintsArray) {
                for (var provider : globalErrorMappingHandlerProviders) {
                    if (provider.isResponsible(constraint)) {
                        onHandlerFound.accept(constraint);
                        handlersWithPriority.add(
                                new HandlerWithPriority<>(provider.getHandler(constraint), provider.getPriority()));
                    }
                }
            }
            Collections.sort(handlersWithPriority);
            return mapAll(handlersWithPriority);
        });
    }

    private UnaryOperator<CommandMessage<?>> constructCommandMessageMappingHandlers(AuthorizationDecision decision,
            HashSet<JsonNode> obligationsWithoutHandler) {
        var obligationFun = constructCommandMessageMappingHandlers(decision.getObligations(),
                obligationsWithoutHandler::remove);
        var adviceFun     = constructCommandMessageMappingHandlers(decision.getAdvice(), __ -> {
                          });

        return command -> {
            var newCommand = command;
            try {
                newCommand = obligationFun.orElseGet(Functions::identity).apply(newCommand);
            } catch (Throwable t) {
                log.error("Failed to execute obligation handlers. {}", t.getLocalizedMessage());
                throw new AccessDeniedException("Access Denied");
            }
            try {
                newCommand = adviceFun.orElseGet(Functions::identity).apply(newCommand);
            } catch (Throwable t) {
                log.error("Failed to execute advice handlers. {}", t.getLocalizedMessage());
            }
            return newCommand;
        };
    }

    private Optional<Function<CommandMessage<?>, CommandMessage<?>>> constructCommandMessageMappingHandlers(
            Optional<ArrayNode> constraints, Consumer<JsonNode> onHandlerFound) {
        return constraints.map(constraintsArray -> {
            var handlersWithPriority = new ArrayList<HandlerWithPriority<Function<CommandMessage<?>, CommandMessage<?>>>>(
                    constraintsArray.size());

            for (var constraint : constraintsArray) {
                for (var provider : globalCommandMessageMappingProviders) {
                    if (provider.isResponsible(constraint)) {
                        onHandlerFound.accept(constraint);
                        handlersWithPriority.add(
                                new HandlerWithPriority<>(provider.getHandler(constraint), provider.getPriority()));
                    }
                }
            }
            Collections.sort(handlersWithPriority);
            return mapAll(handlersWithPriority);
        });
    }

    private UnaryOperator<QueryMessage<?, ?>> constructQueryMessageMappingHandlers(AuthorizationDecision decision,
            HashSet<JsonNode> obligationsWithoutHandler) {
        var obligationFun = constructQueryMessageMappingHandlers(decision.getObligations(),
                obligationsWithoutHandler::remove);
        var adviceFun     = constructQueryMessageMappingHandlers(decision.getAdvice(), __ -> {
                          });

        return query -> {
            var newQuery = query;
            try {
                newQuery = obligationFun.orElseGet(Functions::identity).apply(newQuery);
            } catch (Throwable t) {
                log.error("Failed to execute obligation handlers. {}", t.getLocalizedMessage());
                throw new AccessDeniedException("Access Denied");
            }
            try {
                newQuery = adviceFun.orElseGet(Functions::identity).apply(newQuery);
            } catch (Throwable t) {
                log.error("Failed to execute advice handlers. {}", t.getLocalizedMessage());
            }
            return newQuery;
        };
    }

    private Optional<Function<QueryMessage<?, ?>, QueryMessage<?, ?>>> constructQueryMessageMappingHandlers(
            Optional<ArrayNode> constraints, Consumer<JsonNode> onHandlerFound) {
        return constraints.map(constraintsArray -> {
            var handlersWithPriority = new ArrayList<HandlerWithPriority<Function<QueryMessage<?, ?>, QueryMessage<?, ?>>>>(
                    constraintsArray.size());

            for (var constraint : constraintsArray) {
                for (var provider : globalQueryMessageMappingProviders) {
                    if (provider.isResponsible(constraint)) {
                        onHandlerFound.accept(constraint);
                        handlersWithPriority.add(
                                new HandlerWithPriority<>(provider.getHandler(constraint), provider.getPriority()));
                    }
                }
            }
            Collections.sort(handlersWithPriority);
            return mapAll(handlersWithPriority);
        });
    }

    @SuppressWarnings("unchecked")
    private <T> UnaryOperator<T> constructResultMappingHandlers(AuthorizationDecision decision,
            HashSet<JsonNode> obligationsWithoutHandler, Class<?> type) {
        var obligationFun = constructResultMappingHandlers(decision.getObligations(), obligationsWithoutHandler::remove,
                type);
        var adviceFun     = constructResultMappingHandlers(decision.getAdvice(), __ -> {
                          }, type);

        return result -> {
            var newResult = result;
            try {
                newResult = (T) obligationFun.orElseGet(Functions::identity).apply(result);
            } catch (Throwable t) {
                log.error("Failed to execute obligation handlers. {}", t.getLocalizedMessage());
                throw new AccessDeniedException("Access Denied");
            }
            try {
                newResult = (T) adviceFun.orElseGet(Functions::identity).apply(newResult);
            } catch (Throwable t) {
                log.error("Failed to execute advice handlers. {}", t.getLocalizedMessage());
            }
            return newResult;
        };
    }

    @SuppressWarnings("unchecked")
    private <T> Optional<Function<T, T>> constructResultMappingHandlers(Optional<ArrayNode> constraints,
            Consumer<JsonNode> onHandlerFound, Class<?> responseType) {
        return constraints.map(constraintsArray -> {
            var handlersWithPriority = new ArrayList<HandlerWithPriority<Function<T, T>>>(constraintsArray.size());

            for (var constraint : constraintsArray) {
                for (var provider : globalMappingProviders) {
                    if (provider.getSupportedType().isAssignableFrom(responseType)
                            && provider.isResponsible(constraint)) {
                        onHandlerFound.accept(constraint);
                        handlersWithPriority.add(new HandlerWithPriority<>(
                                (Function<T, T>) provider.getHandler(constraint), provider.getPriority()));
                    }
                }
            }
            Collections.sort(handlersWithPriority);
            return mapAll(handlersWithPriority);
        });
    }

    private BiConsumer<AuthorizationDecision, Message<?>> constructOnDecisionHandlers(AuthorizationDecision decision,
            Set<JsonNode> obligationsWithoutHandler) {
        var onDecisionObligationHandlers = onDecisionHandlers(decision.getObligations(),
                obligationsWithoutHandler::remove);
        var onDecisionAdviceHandlers     = onDecisionHandlers(decision.getAdvice(), __ -> {
                                         });

        return (authzDecision, message) -> {
            onDecisionObligationHandlers.map(biConsumer -> (Runnable) (() -> biConsumer.accept(authzDecision, message)))
                    .map(this::obligation).ifPresent(Runnable::run);
            onDecisionAdviceHandlers.map(biConsumer -> (Runnable) (() -> biConsumer.accept(authzDecision, message)))
                    .map(this::advice).ifPresent(Runnable::run);
        };
    }

    private Optional<BiConsumer<AuthorizationDecision, Message<?>>> onDecisionHandlers(Optional<ArrayNode> constraints,
            Consumer<JsonNode> onHandlerFound) {
        return constraints.map(obligations -> {
            var handlers = new ArrayList<BiConsumer<AuthorizationDecision, Message<?>>>(obligations.size());
            for (var constraint : obligations) {
                for (var provider : globalRunnableProviders) {
                    if (provider.isResponsible(constraint)) {
                        onHandlerFound.accept(constraint);
                        handlers.add(provider.getHandler(constraint));
                    }
                }
            }
            return (decision, message) -> handlers.forEach(handler -> handler.accept(decision, message));
        });
    }

    private <T> Runnable constructObjectConstraintHandlers(T aggregate, CommandMessage<?> command,
            AuthorizationDecision decision, Set<JsonNode> obligationsWithoutHandler) {
        var obligationHandlers = collectConstraintHandlerMethods(decision.getObligations(), aggregate, command,
                decision, obligationsWithoutHandler::remove);
        var adviceHandlers     = collectConstraintHandlerMethods(decision.getAdvice(), aggregate, command, decision,
                __ -> {
                                       });

        return () -> {
            obligationHandlers.map(this::obligation).ifPresent(Runnable::run);
            adviceHandlers.map(this::advice).ifPresent(Runnable::run);
        };
    }

    private Optional<Runnable> collectConstraintHandlerMethods(Optional<ArrayNode> constraints, Object handlerObject,
            CommandMessage<?> command, AuthorizationDecision decision, Consumer<JsonNode> onHandlerFound) {
        if (constraints.isEmpty())
            return Optional.empty();

        var handlers = new ArrayList<Runnable>();
        for (var constraint : constraints.get()) {
            var methods = responsibleConstraintHandlerMethods(handlerObject, command, constraint);

            if (methods.isEmpty())
                break;

            onHandlerFound.accept(constraint);

            for (var method : methods) {
                var task = runMethod(method, handlerObject, command, decision, constraint);
                handlers.add(task);
            }
        }
        return Optional.of(runAll(handlers));
    }

    private static List<Method> responsibleConstraintHandlerMethods(Object handlerObject, CommandMessage<?> command,
            JsonNode constraint) {
        log.debug("Examining object for constraint handlers: {}", handlerObject);
        if (handlerObject == null) // constructor command
            return List.of();

        return Arrays.stream(handlerObject.getClass().getMethods())
                .filter(method -> isMethodResponsible(method, constraint, command, handlerObject)).toList();
    }

    private static <U extends CommandMessage<?>, T> boolean isMethodResponsible(Method method, JsonNode constraint,
            U command, T handlerObject) {
        var annotation = (ConstraintHandler) method.getAnnotation(ConstraintHandler.class);

        if (annotation == null)
            return false;

        var annotationValue = annotation.value();
        if (annotationValue.isBlank()) {
            return true;
        }

        var context = new StandardEvaluationContext(handlerObject);
        context.setVariable("constraint", constraint);
        context.setVariable("command", command);
        var expression = PARSER.parseExpression(annotationValue);

        Object value;
        try {
            value = expression.getValue(context);
            log.debug("Expression evaluated to: {}", value);
        } catch (SpelEvaluationException | NullPointerException e) {
            log.warn("Failed to evaluate \"{}\" on Class {} Method {} Error {}", annotationValue,
                    handlerObject.getClass().getName(), method.getName(), e.getMessage());
            return false;
        }

        if (value instanceof Boolean bool)
            return bool;

        log.warn("Expression returned non Boolean ({}). Expression \"{}\" on Class {} Method {}", value,
                annotationValue, handlerObject.getClass().getName(), method.getName());
        return false;
    }

    private Runnable runMethod(Method method, Object contextObject, Message<?> message, AuthorizationDecision decision,
            JsonNode constraint) {
        return () -> invokeMethod(method, contextObject, message, decision, constraint);
    }

    @SneakyThrows
    private void invokeMethod(Method method, Object contextObject, Message<?> message, AuthorizationDecision decision,
            JsonNode constraint) {
        var arguments = resolveArgumentsForMethodParameters(method, message, decision, constraint);
        method.invoke(contextObject, arguments);
    }

    private Object[] resolveArgumentsForMethodParameters(Method method, Message<?> message,
            AuthorizationDecision decision, JsonNode constraint) {
        var parameters     = method.getParameters();
        var arguments      = new Object[parameters.length];
        var parameterIndex = 0;
        for (var parameter : parameters) {
            if (JsonNode.class.isAssignableFrom(parameter.getType())) {
                arguments[parameterIndex++] = constraint;
            } else if (AuthorizationDecision.class.isAssignableFrom(parameter.getType())) {
                arguments[parameterIndex++] = decision;
            } else if (message.getPayloadType().isAssignableFrom(parameter.getType())) {
                arguments[parameterIndex++] = message.getPayload();
            } else {
                arguments[parameterIndex] = revolveAxonAndSpringParameters(method, message, parameters, parameterIndex,
                        parameter);
                parameterIndex++;
            }
        }
        return arguments;
    }

    private Object revolveAxonAndSpringParameters(Method method, Message<?> message, Parameter[] parameters,
            int parameterIndex, Parameter parameter) {

        var resolver = parameterResolverFactory.createInstance(method, parameters, parameterIndex);

        if (resolver == null)
            throw new IllegalStateException(String.format(
                    "Could not resolve parameter of @ConstraintHandler. method='%s' parameterName='%s'. No matching parameter resolver found.",
                    method, parameter.getName()));

        @SuppressWarnings("unchecked")
        Object argument = resolver.resolveParameterValue(message);

        if (argument == null)
            throw new IllegalStateException(
                    String.format("Could not resolve parameter of @ConstraintHandler %s %s. No value found.", method,
                            parameter.getName()));
        return argument;
    }

    private static Runnable runAll(List<Runnable> handlers) {
        return () -> handlers.forEach(Runnable::run);

    }

    private Runnable obligation(Runnable handler) {
        return () -> {
            try {
                handler.run();
            } catch (Throwable t) {
                log.error("Failed to execute obligation handlers. {}", t.getLocalizedMessage());
                throw new AccessDeniedException("Access Denied");
            }
        };
    }

    private Runnable advice(Runnable handler) {
        return () -> {
            try {
                handler.run();
            } catch (Throwable t) {
                log.error("Failed to execute advice handlers. {}", t.getLocalizedMessage());
            }
        };
    }

    private static <V> Function<V, V> mapAll(Collection<HandlerWithPriority<Function<V, V>>> handlers) {
        return handlers.stream().map(HandlerWithPriority::getHandler).reduce(Function.identity(),
                (merged, newFunction) -> x -> newFunction.apply(merged.apply(x)));
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
                log.error("Failed to evaluate predicate. {}", e.getLocalizedMessage());
                return fallback;
            }
        };
    }
}
