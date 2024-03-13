/*
 * Copyright © 2020-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.ethereum.demo.pep;

import java.util.function.BiConsumer;

import com.vaadin.flow.component.UI;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.PolicyDecisionPoint;
import reactor.core.Disposable;

public class VaadinPEP<C> {

	private final PolicyDecisionPoint pdp;

	private AuthorizationSubscription subscription;

	private final C component;

	private final UI ui;

	private BiConsumer<C, AuthorizationDecision> permitListener;

	private BiConsumer<C, AuthorizationDecision> denyListener;

	private BiConsumer<C, AuthorizationDecision> notApplicableListener;

	private BiConsumer<C, AuthorizationDecision> indeterminateListener;

	private Disposable decisionFlux;

	public VaadinPEP(C component, AuthorizationSubscription subscription, PolicyDecisionPoint pdp, UI ui) {
		this.component    = component;
		this.pdp          = pdp;
		this.subscription = subscription;
		this.ui           = ui;
	}

	public void newSub(AuthorizationSubscription subscription) {
		this.subscription = subscription;
	}

	public void enforce() {
		if (decisionFlux != null)
			dispose();
		this.decisionFlux = pdp.decide(subscription).subscribe(this::onDecision);
	}

	private void onDecision(AuthorizationDecision decision) {
		if (decision.getDecision() == Decision.PERMIT) {
			ui.access(() -> permitListener.accept(component, decision));
		} else if (decision.getDecision() == Decision.DENY) {
			ui.access(() -> denyListener.accept(component, decision));
		} else if (decision.getDecision() == Decision.INDETERMINATE) {
			if (indeterminateListener != null) {
				ui.access(() -> indeterminateListener.accept(component, decision));
			} else {
				ui.access(() -> denyListener.accept(component, decision));
			}
		} else if (decision.getDecision() == Decision.NOT_APPLICABLE) {
			if (notApplicableListener != null) {
				ui.access(() -> notApplicableListener.accept(component, decision));
			} else {
				ui.access(() -> denyListener.accept(component, decision));
			}
		}
	}

	public void dispose() {
		this.decisionFlux.dispose();
	}

	public void onPermit(BiConsumer<C, AuthorizationDecision> permitListener) {
		this.permitListener = permitListener;
	}

	public void onDeny(BiConsumer<C, AuthorizationDecision> denyListener) {
		this.denyListener = denyListener;
	}

	public void onIndeterminate(BiConsumer<C, AuthorizationDecision> indeterminateListener) {
		this.indeterminateListener = indeterminateListener;
	}

	public void onNotApplicable(BiConsumer<C, AuthorizationDecision> notApplicableListener) {
		this.notApplicableListener = notApplicableListener;
	}

}
