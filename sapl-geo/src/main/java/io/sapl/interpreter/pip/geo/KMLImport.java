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
package io.sapl.interpreter.pip.geo;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collection;

import javax.xml.parsers.ParserConfigurationException;

import org.geotools.kml.v22.KMLConfiguration;
import org.geotools.xml.Parser;
import org.locationtech.jts.geom.Geometry;
import org.opengis.feature.simple.SimpleFeature;
import org.xml.sax.SAXException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.functions.GeometryBuilder;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode
public class KMLImport {

    private static final String ATT_FEATURE = "Feature";

    private static final String ATT_NAME = "name";

    private static final String ATT_GEOM = "Geometry";

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    protected static final String UNABLE_TO_PARSE_KML = "Provided KML-file cannot be found or is not compliant. Unable to parse geometry.";

    protected static final String NO_VALID_FILENAME = "Provided filename is not valid or nested in an Json-Object.";

    protected static final String TEST_OKAY = "ok";

    private final String kmlSource;

    public KMLImport(String source) {
        kmlSource = source;
    }

    public KMLImport(JsonNode source) {
        if (source.isTextual()) {
            kmlSource = source.asText();
        } else {
            throw new PolicyEvaluationException(NO_VALID_FILENAME);
        }
    }

    public JsonNode toGeoPIPResponse() {
        if (kmlSource.isEmpty()) {
            return JSON.textNode(TEST_OKAY);
        } else {
            return GeoPIPResponse.builder().geofences(retrieveGeometries()).build().toJsonNode();
        }
    }

    private ObjectNode retrieveGeometries() {
        if (kmlSource.contains("http://") || kmlSource.contains("https://") || kmlSource.contains("file://")) {
            return formatCollection((Collection<?>) getKmlFromWeb().getAttribute(ATT_FEATURE));
        } else {
            return formatCollection((Collection<?>) getKmlFromFile().getAttribute(ATT_FEATURE));
        }
    }

    private SimpleFeature getKmlFromWeb() {
        try (InputStream inputStream = new URL(kmlSource).openStream()) {

            return parse(inputStream);

        } catch (IllegalArgumentException | IOException | SAXException | ParserConfigurationException e) {
            throw new PolicyEvaluationException(UNABLE_TO_PARSE_KML, e);
        }
    }

    private SimpleFeature getKmlFromFile() {
        try (InputStream inputStream = getClass().getResourceAsStream(kmlSource)) {

            return parse(inputStream);

        } catch (IOException | ParserConfigurationException | SAXException e) {
            throw new PolicyEvaluationException(UNABLE_TO_PARSE_KML, e);
        }
    }

    private static SimpleFeature parse(InputStream inputStream)
            throws IOException, SAXException, ParserConfigurationException {
        Parser parser = new Parser(new KMLConfiguration());
        return (SimpleFeature) parser.parse(inputStream);
    }

    protected static ObjectNode formatCollection(Collection<?> placeMarks) {
        ObjectNode geometries = JSON.objectNode();
        for (Object obj : placeMarks) {

            if (!(obj instanceof SimpleFeature feature)) {
                throw new PolicyEvaluationException(UNABLE_TO_PARSE_KML);
            } else {
                Geometry geom = (Geometry) feature.getAttribute(ATT_GEOM);
                geometries.set((String) feature.getAttribute(ATT_NAME), GeometryBuilder.toJsonNode(geom));
            }
        }
        return geometries;
    }

}
