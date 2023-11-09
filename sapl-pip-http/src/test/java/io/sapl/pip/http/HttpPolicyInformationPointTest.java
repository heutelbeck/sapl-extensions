/*
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.springframework.http.HttpMethod.POST;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.google.common.net.HttpHeaders;

import io.sapl.api.interpreter.Val;
import io.sapl.interpreter.InitializationException;
import io.sapl.interpreter.pip.AnnotationAttributeContext;
import reactor.core.publisher.Flux;

@ExtendWith(MockitoExtension.class)
public class HttpPolicyInformationPointTest {

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    private Val actualRequestSpec;

    private JsonNode result;

    private RequestSpecification expectedRequestSpec;

    private WebClientRequestExecutor requestExecutor;

    @BeforeEach
    public void setUp() throws IOException {
        final String request = "{ " + "\"url\": \"http://jsonplaceholder.typicode.com/posts\", " + "\"headers\": { "
                + "\"" + HttpHeaders.ACCEPT + "\" : \"application/stream+json\", " + "\"" + HttpHeaders.ACCEPT_CHARSET
                + "\" : \"" + StandardCharsets.UTF_8 + "\" " + "}, " + "\"rawBody\" : \"hello world\" " + "}";

        actualRequestSpec = Val.ofJson(request);
        result            = JSON.textNode("result");
        final Map<String, String> headerProperties = new HashMap<>();
        headerProperties.put(HttpHeaders.ACCEPT, "application/stream+json");
        headerProperties.put(HttpHeaders.ACCEPT_CHARSET, StandardCharsets.UTF_8.toString());

        expectedRequestSpec = new RequestSpecification();
        expectedRequestSpec.setUrl(JSON.textNode("http://jsonplaceholder.typicode.com/posts"));
        expectedRequestSpec.setHeaders(headerProperties);
        expectedRequestSpec.setRawBody("hello world");

        requestExecutor = Mockito.spy(WebClientRequestExecutor.class);
        doReturn(Flux.just(result)).when(requestExecutor).executeReactiveRequest(any(RequestSpecification.class),
                any(HttpMethod.class));
    }

    @Test
    public void postRequest() throws IOException, InitializationException {
        var pip          = new HttpPolicyInformationPoint(requestExecutor);
        var attributeCtx = new AnnotationAttributeContext();
        attributeCtx.loadPolicyInformationPoint(pip);
        var returnedAttribute = attributeCtx.evaluateAttribute("http.post", actualRequestSpec, null, new HashMap<>())
                .blockFirst();
        assertEquals(Val.of(result), returnedAttribute);
        verify(requestExecutor).executeReactiveRequest(eq(expectedRequestSpec), eq(POST));
    }

}
