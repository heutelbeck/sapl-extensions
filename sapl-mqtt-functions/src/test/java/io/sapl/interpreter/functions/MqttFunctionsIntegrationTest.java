/*
 * Copyright © 2019-2022 Dominic Heutelbeck (dominic@heutelbeck.com)
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

package io.sapl.interpreter.functions;

import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.interpreter.InitializationException;
import io.sapl.pdp.EmbeddedPolicyDecisionPoint;
import io.sapl.pdp.PolicyDecisionPointFactory;

import org.junit.jupiter.api.*;
import reactor.test.StepVerifier;

import java.util.List;

class MqttFunctionsIntegrationTest {

    private static final String ACTION = "actionName";
    private static EmbeddedPolicyDecisionPoint pdp;

    @BeforeAll
    static void beforeAll() throws InitializationException {
        pdp = buildPdp();
    }

    @AfterAll
    static void afterAll() {
        pdp.dispose();
    }

    @Test
    void when_allTopicsShouldMatchWithMultiLevelWildcardAndSingleTopicMatchesWildcard_then_returnTrue() {
        // GIVEN
        AuthorizationSubscription authzSubscription = AuthorizationSubscription.of("firstSubject", ACTION,
                "first/second/#");

        // WHEN
        var pdpDecisionFlux = pdp.decide(authzSubscription);

        // THEN
        StepVerifier.create(pdpDecisionFlux)
                .expectNextMatches(authzDecision -> authzDecision.getDecision() == Decision.PERMIT)
                .thenCancel()
                .verify();
    }

    @Test
    void when_atLeastOneTopicShouldMatchWithSingleLevelWildcardAndSingleTopicDoesNotMatchWildcard_then_returnTrue() {
        // GIVEN
        AuthorizationSubscription authzSubscription = AuthorizationSubscription.of("secondSubject", ACTION,
                "first/+/third");

        // WHEN
        var pdpDecisionFlux = pdp.decide(authzSubscription);

        // THEN
        StepVerifier.create(pdpDecisionFlux)
                .expectNextMatches(authzDecision -> authzDecision.getDecision() == Decision.PERMIT)
                .thenCancel()
                .verify();
    }

    private static EmbeddedPolicyDecisionPoint buildPdp() throws InitializationException {
        return PolicyDecisionPointFactory
                .filesystemPolicyDecisionPoint("src/test/resources/policies",
                        List.of(), List.of(new MqttFunctions()));
    }
}
