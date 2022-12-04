/*
 * Copyright © 2017-2021 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.interpreter.pip;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.interpreter.Val;
import io.sapl.api.pip.Attribute;
import io.sapl.api.pip.PolicyInformationPoint;
import io.sapl.interpreter.pip.geo.KMLImport;
import io.sapl.interpreter.pip.geo.PostGISConnection;
import io.sapl.interpreter.pip.geo.TraccarConnection;
import reactor.core.publisher.Flux;

@PolicyInformationPoint(name = GeoPolicyInformationPoint.NAME, description = GeoPolicyInformationPoint.DESCRIPTION)
public class GeoPolicyInformationPoint {

	public static final String NAME = "io.sapl.pip.geo";

	public static final String DESCRIPTION = "PIP for geographical data.";

	@Attribute
	public Flux<Val> traccar(Val leftHandValue, Map<String, JsonNode> variables) {
		return Flux.from(new TraccarConnection(leftHandValue.get()).toGeoPIPResponse().map(Val::of));
	}

	@Attribute
	public Flux<Val> postgis(Val leftHandValue, Map<String, JsonNode> variables) {
		return Flux.just(Val.of(new PostGISConnection(leftHandValue.get()).toGeoPIPResponse()));
	}

	@Attribute
	public Flux<Val> kml(Val leftHandValue, Map<String, JsonNode> variables) {
		return Flux.just(Val.of(new KMLImport(leftHandValue.get()).toGeoPIPResponse()));
	}

}
