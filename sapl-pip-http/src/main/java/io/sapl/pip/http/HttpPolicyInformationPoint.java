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
package io.sapl.pip.http;

import static org.springframework.http.HttpMethod.DELETE;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.PATCH;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.PUT;

import java.io.IOException;
import java.util.Map;

import org.springframework.http.HttpMethod;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.Val;
import io.sapl.api.pip.Attribute;
import io.sapl.api.pip.PolicyInformationPoint;
import io.sapl.api.validation.JsonObject;
import io.sapl.api.validation.Text;
import reactor.core.publisher.Flux;

/**
 * Uses the {@link WebClientRequestExecutor} the send reactive HTTP requests to
 * a remote policy information point providing the according REST endpoints.
 */
@PolicyInformationPoint(name = HttpPolicyInformationPoint.NAME, description = HttpPolicyInformationPoint.DESCRIPTION)
public class HttpPolicyInformationPoint {

    static final String         NAME        = "http";
    static final String         DESCRIPTION = "Policy Information Point and attributes for consuming HTTP services";
    private static final String GET_DOCS    = "Sends an HTTP GET request to the url provided in the value parameter and returns a flux of responses.";
    private static final String POST_DOCS   = "Sends an HTTP POST request to the url provided in the value parameter and returns a flux of responses.";
    private static final String PUT_DOCS    = "Sends an HTTP PUT request to the url provided in the value parameter and returns a flux of responses.";
    private static final String PATCH_DOCS  = "Sends an HTTP PATCH request to the url provided in the value parameter and returns a flux of responses.";
    private static final String DELETE_DOCS = "Sends an HTTP DELETE request to the url provided in the value parameter and returns a flux of responses.";

    private final WebClientRequestExecutor requestExecutor;

    public HttpPolicyInformationPoint(WebClientRequestExecutor requestExecutor) {
        this.requestExecutor = requestExecutor;
    }

    @Attribute(docs = GET_DOCS)
    public Flux<Val> get(@Text @JsonObject Val leftHandValue, Map<String, JsonNode> variables) {
        return executeReactiveRequest(leftHandValue, GET);
    }

    @Attribute(docs = POST_DOCS)
    public Flux<Val> post(@Text @JsonObject Val leftHandValue, Map<String, JsonNode> variables) {
        return executeReactiveRequest(leftHandValue, POST);
    }

    @Attribute(docs = PUT_DOCS)
    public Flux<Val> put(@Text @JsonObject Val leftHandValue, Map<String, JsonNode> variables) {
        return executeReactiveRequest(leftHandValue, PUT);
    }

    @Attribute(docs = PATCH_DOCS)
    public Flux<Val> patch(@Text @JsonObject Val leftHandValue, Map<String, JsonNode> variables) {
        return executeReactiveRequest(leftHandValue, PATCH);
    }

    @Attribute(docs = DELETE_DOCS)
    public Flux<Val> delete(@Text @JsonObject Val leftHandValue, Map<String, JsonNode> variables) {
        return executeReactiveRequest(leftHandValue, DELETE);
    }

    private Flux<Val> executeReactiveRequest(Val value, HttpMethod httpMethod) {
        try {
            final RequestSpecification saplRequest = getRequestSpecification(value.get());
            return getRequestExecutor().executeReactiveRequest(saplRequest, httpMethod)
                    .onErrorMap(IOException.class, PolicyEvaluationException::new).map(Val::of);
        } catch (JsonProcessingException e) {
            return Flux.error(e);
        }
    }

    private RequestSpecification getRequestSpecification(JsonNode value) throws JsonProcessingException {
        if (value.isTextual()) {
            final RequestSpecification saplRequest = new RequestSpecification();
            saplRequest.setUrl(value);
            return saplRequest;
        } else {
            return RequestSpecification.from(value);
        }
    }

    private WebClientRequestExecutor getRequestExecutor() {
        return requestExecutor != null ? requestExecutor : new WebClientRequestExecutor();
    }

}
