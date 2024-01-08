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
package io.sapl.functions;

import static io.sapl.functions.GeometryBuilder.geoOf;
import static io.sapl.functions.GeometryBuilder.geodesicDistance;
import static io.sapl.functions.GeometryBuilder.toVal;

import java.util.BitSet;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.Val;
import io.sapl.api.validation.Array;
import io.sapl.api.validation.JsonObject;
import io.sapl.api.validation.Number;
import io.sapl.api.validation.Text;
import lombok.NoArgsConstructor;
/*
 * Format always [Lat(y), Long(x)]
 */

@NoArgsConstructor
@FunctionLibrary(name = GeoFunctionLibrary.NAME, description = GeoFunctionLibrary.DESCRIPTION)
public class GeoFunctionLibrary {

    public static final String  NAME                                                                                                                          = "geo";
    public static final String  DESCRIPTION                                                                                                                   = "Functions enabling location based authorisation and geofencing.";
    private static final String EQUALS_DOC                                                                                                                    = "equals(GEOMETRY1, GEOMETRY2): Tests if two geometries are exactly (!) equal. GEOMETRY can also be a GEOMETRYCOLLECTION.";
    private static final String DISJOINT_DOC                                                                                                                  = "disjoint(GEOMETRY1, GEOMETRY2): Tests if two geometries are disjoint from each other (not intersecting each other). ";
    private static final String TOUCHES_DOC                                                                                                                   = "touches(GEOMETRY1, GEOMETRY2): Tests if two geometries are touching each other.";
    private static final String CROSSES_DOC                                                                                                                   = "crosses(GEOMETRY1, GEOMETRY2): Tests if two geometries are crossing each other (having a intersecting area).";
    private static final String WITHIN_DOC                                                                                                                    = "within(GEOMETRY1, GEOMETRY2): Tests if the GEOMETRY1 is fully within GEOMETRY2 (converse of contains-function). GEOMETRY2 can also be of type GeometryCollection.";
    private static final String CONTAINS_DOC                                                                                                                  = "contains(GEOMETRY1, GEOMETRY2): Tests if the GEOMETRY1 fully contains GEOMETRY2 (converse of within-function). GEOMETRY1 can also be of type GeometryCollection.";
    private static final String OVERLAPS_DOC                                                                                                                  = "overlaps(GEOMETRY1, GEOMETRY2): Tests if two geometries are overlapping.";
    private static final String INTERSECTS_DOC                                                                                                                = "intersects(GEOMETRY1, GEOMETRY2): Tests if two geometries have at least one common intersection point.";
    private static final String BUFFER_DOC                                                                                                                    = "buffer(GEOMETRY, BUFFER_WIDTH): Adds a buffer area of BUFFER_WIDTH around GEOMETRY and returns the new geometry."
            + " BUFFE_RWIDTH is in the units of the coordinates or of the projection (if projection applied)";
    private static final String BOUNDARY_DOC                                                                                                                  = "boundary(GEOMETRY): Returns the boundary of a geometry.";
    private static final String CENTROID_DOC                                                                                                                  = "centroid(GEOMETRY): Returns a point that is the geometric center of gravity of the geometry.";
    private static final String CONVEX_HULL_GEOMETRY_RETURNS_THE_CONVEX_HULL_SMALLEST_CONVEX_POLYGON_THAT_CONTAINS_ALL_POINTS_OF_THE_GEOMETRY_OF_THE_GEOMETRY = "convexHull(GEOMETRY): Returns the convex hull (smallest convex polygon, that contains all points of the geometry) of the geometry.";
    private static final String UNION_DOC                                                                                                                     = "union(GEOMETRY1, GEOMETRY2): Returns the union of two geometries. GEOMETRY can also be a GEOMETRYCOLLECTION.";
    private static final String INTERSECTION_DOC                                                                                                              = "intersection(GEOMETRY1, GEOMETRY2): Returns the point set intersection of the geometries. GEOMETRY can also be a GEOMETRYCOLLECTION.";
    private static final String DIFFERENCE_DOC                                                                                                                = "difference(GEOMETRY1, GEOMETRY2): Returns the closure of the set difference between two geometries.";
    private static final String BETWEEN_TWO_GEOMETRIES                                                                                                        = "symDifference(GEOMETRY1, GEOMETRY2): Returns the closure of the symmetric difference between two geometries.";
    private static final String DISTANCE_DOC                                                                                                                  = "distance(GEOMETRY1, GEOMETRY2): Returns the (shortest) geometric (planar) distance between two geometries. Does return the value of the unit of the coordinates (or projection if used).";
    private static final String GEO_DISTANCE_DOC                                                                                                              = "geoDistance(GEOMETRY1, GEOMETRY2): Returns the (shortest) geodetic distance of two geometries in [m]. Coordinate Reference System is the un-projected (source) system (WGS84 recommended).";
    private static final String IS_WITHIN_DISTANCE_DOC                                                                                                        = "isWithinDistance(GEOMETRY1, GEOMETRY2, DISTANCE): Tests if two geometries are within the given geometric (planar) distance of each other. "
            + "Uses the unit of the coordinates (or projection if used).";
    private static final String IS_WITHIN_GEO_DISTANCE_DOC                                                                                                    = "isWithinGeoDistance(GEOMETRY1, GEOMETRY2, DISTANCE): Tests if two geometries are within the given geodetic distance of each other. Uses [m] as unit."
            + " Coordinate Reference System is the unprojected (source) system (WGS84 recommended).";
    private static final String LENGTH_DOC                                                                                                                    = "length(GEOMETRY): Returns the length of the geometry (perimeter in case of areal geometries). The returned value is in the units of the coordinates or of the projection (if projection applied).";
    private static final String AREA_DOC                                                                                                                      = "area(GEOMETRY): Returns the area of the geometry. The returned value is in the units (squared) of the coordinates or of the projection (if projection applied).";
    private static final String IS_SIMPLE_DOC                                                                                                                 = "isSimple(GEOMETRY): Returns true if the geometry has no anomalous geometric points (e.g. self intersection, self tangency,...).";
    private static final String IS_VALID_DOC                                                                                                                  = "isValid(GEOMETRY): Returns true if the geometry is topologically valid according to OGC specifications.";
    private static final String IS_CLOSED_DOC                                                                                                                 = "isClosed(GEOMETRY): Returns true if the geometry is either empty or from type (Multi)Point or a closed (Multi)LineString.";

    // private static final String TOMETER_DOC = "toMeter(VALUE, UNIT): Converts the
    // given
    // VALUE from [UNIT] to [m].";
    // private static final String TOSQUAREMETER_DOC = "toSquareMeter(VALUE, UNIT):
    // Converts the given VALUE from [UNIT] to [m].";
    private static final String ONE_AND_ONLY_DOC                            = "oneAndOnly(GEOMETRYCOLLECTION): If GEOMETRYCOLLECTION only contains one element, this element will be returned. In all other cases an error will be thrown.";
    private static final String BAG_SIZE_DOC                                = "bagSize(GOEMETRYCOLLECTION): Returns the number of elements in the GEOMETRYCOLLECTION.";
    private static final String GEOMETRY_IS_IN_DOC                          = "geometryIsIn(GEOMETRY, GEOMETRYCOLLECTION): Tests if GEOMETRY is included in GEOMETRYCOLLECTION.";
    private static final String GEOMETRY_BAG_DOC                            = "geometryBag(GEOMETRY,...): Takes any number of GEOMETRY and returns a GEOMETRYCOLLECTION containing all of them.";
    private static final String RES_TO_GEOMETRY_BAG_DOC                     = "resToGeometryBag(RESOURCE_ARRAY): Takes multiple Geometries from RESOURCE_ARRAY and turns them into a GeometryCollection (e.g. geofences from a third party system).";
    private static final String AT_LEAST_ONE_MEMBER_OF_DOC                  = "atLeastOneMemberOf(GEOMETRYCOLLECTION1, GEOMETRYCOLLECTION2): Returns TRUE if at least one member of GEOMETRYCOLLECTION1 is contained in GEOMETRYCOLLECTION2.";
    private static final String SUBSET_DOC                                  = "subset(GEOMETRYCOLLECTION1, GEOMETRYCOLLECTION2): Returns true, if GEOMETRYCOLLECTION1 is a subset of GEOMETRYCOLLECTION2.";
    private static final String GET_PROJECTION_DOC                          = "getProjection(SRCSYSTEM, DESTSYSTEM): Returns the projection parameters between the given set of coordinate systems (given as EPSG id).";
    private static final String PROJECT_DOC                                 = "project(GEOMETRY): Returns the projected geometry (or the geometry itself in case no projection is defined).";
    private static final String INPUT_NOT_GEO_COLLECTION_WITH_ONLY_ONE_GEOM = "Input must be a GeometryCollection containing only one Geometry.";

    // private static final String UNIT_NOT_CONVERTIBLE = "Given unit '%s' is not
    // convertible to '%s'.";

    @Function(name = "equals", docs = EQUALS_DOC)
    public Val geometryEquals(@JsonObject Val jsonGeometryOne, @JsonObject Val jsonGeometryTwo) {
        return Val.of(geoOf(jsonGeometryOne).equals(geoOf(jsonGeometryTwo)));
    }

    @Function(docs = DISJOINT_DOC)
    public Val disjoint(@JsonObject Val jsonGeometryOne, @JsonObject Val jsonGeometryTwo) {
        return Val.of(geoOf(jsonGeometryOne).disjoint(geoOf(jsonGeometryTwo)));
    }

    @Function(docs = TOUCHES_DOC)
    public Val touches(@JsonObject Val jsonGeometryOne, @JsonObject Val jsonGeometryTwo) {
        return Val.of(geoOf(jsonGeometryOne).touches(geoOf(jsonGeometryTwo)));
    }

    @Function(docs = CROSSES_DOC)
    public Val crosses(@JsonObject Val jsonGeometryOne, @JsonObject Val jsonGeometryTwo) {
        return Val.of(geoOf(jsonGeometryOne).crosses(geoOf(jsonGeometryTwo)));
    }

    @Function(docs = WITHIN_DOC)
    public Val within(@JsonObject Val jsonGeometryOne, @JsonObject Val jsonGeometryTwo) {
        Geometry geometryOne = geoOf(jsonGeometryOne);
        Geometry geometryTwo = geoOf(jsonGeometryTwo);
        if (geometryTwo instanceof GeometryCollection) {
            return Val.of(geometryOne.within(geometryTwo.union()));
        } else {
            return Val.of(geometryOne.within(geometryTwo));
        }
    }

    @Function(docs = CONTAINS_DOC)
    public Val contains(@JsonObject Val jsonGeometryOne, @JsonObject Val jsonGeometryTwo) {
        Geometry geometryOne = geoOf(jsonGeometryOne);
        Geometry geometryTwo = geoOf(jsonGeometryTwo);
        if (geometryOne instanceof GeometryCollection) {
            return Val.of(geometryOne.union().contains(geometryTwo));
        } else {
            return Val.of(geometryOne.contains(geometryTwo));
        }
    }

    @Function(docs = OVERLAPS_DOC)
    public Val overlaps(@JsonObject Val jsonGeometryOne, @JsonObject Val jsonGeometryTwo) {
        return Val.of(geoOf(jsonGeometryOne).overlaps(geoOf(jsonGeometryTwo)));
    }

    @Function(docs = INTERSECTS_DOC)
    public Val intersects(@JsonObject Val jsonGeometryOne, @JsonObject Val jsonGeometryTwo) {
        return Val.of(geoOf(jsonGeometryOne).intersects(geoOf(jsonGeometryTwo)));
    }

    @Function(docs = BUFFER_DOC)
    public Val buffer(@JsonObject Val jsonGeometry, @Number Val buffer) {
        return toVal(geoOf(jsonGeometry).buffer(buffer.get().asDouble()));
    }

    @Function(docs = BOUNDARY_DOC)
    public Val boundary(@JsonObject Val jsonGeometry) {
        return toVal(geoOf(jsonGeometry).getBoundary());
    }

    @Function(docs = CENTROID_DOC)
    public Val centroid(@JsonObject Val jsonGeometry) {
        return toVal(geoOf(jsonGeometry).getCentroid());
    }

    @Function(docs = CONVEX_HULL_GEOMETRY_RETURNS_THE_CONVEX_HULL_SMALLEST_CONVEX_POLYGON_THAT_CONTAINS_ALL_POINTS_OF_THE_GEOMETRY_OF_THE_GEOMETRY)
    public Val convexHull(@JsonObject Val jsonGeometry) {
        return toVal(geoOf(jsonGeometry).convexHull());
    }

    @Function(docs = UNION_DOC)
    public Val union(@JsonObject Val... jsonGeometries) {
        if (jsonGeometries.length == 1) {
            return jsonGeometries[0];
        }
        Geometry geomUnion = geoOf(jsonGeometries[0]);
        for (int i = 1; i < jsonGeometries.length; i++) {
            Geometry additionalGeom = geoOf(jsonGeometries[i]);
            geomUnion = geomUnion.union(additionalGeom);
        }
        return toVal(geomUnion);
    }

    @Function(docs = INTERSECTION_DOC)
    public Val intersection(@JsonObject Val jsonGeometryOne, @JsonObject Val jsonGeometryTwo) {
        return toVal(geoOf(jsonGeometryOne).intersection(geoOf(jsonGeometryTwo)));
    }

    @Function(docs = DIFFERENCE_DOC)
    public Val difference(@JsonObject Val jsonGeometryOne, @JsonObject Val jsonGeometryTwo) {
        return toVal(geoOf(jsonGeometryOne).difference(geoOf(jsonGeometryTwo)));
    }

    @Function(docs = BETWEEN_TWO_GEOMETRIES)
    public Val symDifference(@JsonObject Val jsonGeometryOne, @JsonObject Val jsonGeometryTwo) {
        return toVal(geoOf(jsonGeometryOne).symDifference(geoOf(jsonGeometryTwo)));
    }

    @Function(docs = DISTANCE_DOC)
    public Val distance(@JsonObject Val jsonGeometryOne, @JsonObject Val jsonGeometryTwo) {
        return Val.of(geoOf(jsonGeometryOne).distance(geoOf(jsonGeometryTwo)));
    }

    @Function(docs = GEO_DISTANCE_DOC)
    public Val geoDistance(@JsonObject Val jsonGeometryOne, @JsonObject Val jsonGeometryTwo) {
        return Val.of(geodesicDistance(geoOf(jsonGeometryOne), geoOf(jsonGeometryTwo)));
    }

    @Function(docs = IS_WITHIN_DISTANCE_DOC)
    public Val isWithinDistance(@JsonObject Val jsonGeometryOne, @JsonObject Val jsonGeometryTwo,
            @Number Val distInput) {
        return Val.of(geoOf(jsonGeometryOne).isWithinDistance(geoOf(jsonGeometryTwo), distInput.get().asDouble()));
    }

    @Function(docs = IS_WITHIN_GEO_DISTANCE_DOC)
    public Val isWithinGeoDistance(@JsonObject Val jsonGeometryOne, @JsonObject Val jsonGeometryTwo,
            @Number Val distInput) {
        Geometry geometryOne = geoOf(jsonGeometryOne);
        Geometry geometryTwo = geoOf(jsonGeometryTwo);
        double   distance    = distInput.get().asDouble();
        return Val.of(geodesicDistance(geometryOne, geometryTwo) <= distance);
    }

    @Function(docs = LENGTH_DOC)
    public Val length(@JsonObject Val jsonGeometry) {
        return Val.of(geoOf(jsonGeometry).getLength());
    }

    @Function(docs = AREA_DOC)
    public Val area(@JsonObject Val jsonGeometry) {
        return Val.of(geoOf(jsonGeometry).getArea());
    }

    @Function(docs = IS_SIMPLE_DOC)
    public Val isSimple(@JsonObject Val jsonGeometry) {
        return Val.of(geoOf(jsonGeometry).isSimple());
    }

    @Function(docs = IS_VALID_DOC)
    public Val isValid(@JsonObject Val jsonGeometry) {
        return Val.of(geoOf(jsonGeometry).isValid());
    }

    @Function(docs = IS_CLOSED_DOC)
    public Val isClosed(@JsonObject Val jsonGeometry) {
        Geometry geometry = geoOf(jsonGeometry);
        boolean  result   = false;
        if (geometry.isEmpty() || ("Point".equals(geometry.getGeometryType()))
                || ("MultiPoint".equals(geometry.getGeometryType()))) {
            result = true;
        } else if ("LineString".equals(geometry.getGeometryType())) {
            result = ((LineString) geometry).isClosed();
        } else if ("MultiLineString".equals(geometry.getGeometryType())) {
            result = ((MultiLineString) geometry).isClosed();
        }
        return Val.of(result);
    }

    @Function(docs = BAG_SIZE_DOC)
    public Val bagSize(@JsonObject Val jsonGeometry) {
        return Val.of(geoOf(jsonGeometry).getNumGeometries());
    }

    @Function(docs = ONE_AND_ONLY_DOC)
    public Val oneAndOnly(@JsonObject Val jsonGeometryCollection) {
        GeometryCollection geometryCollection = (GeometryCollection) geoOf(jsonGeometryCollection);
        if (geometryCollection.getNumGeometries() == 1) {
            return toVal(geometryCollection.getGeometryN(0));
        } else {
            throw new PolicyEvaluationException(INPUT_NOT_GEO_COLLECTION_WITH_ONLY_ONE_GEOM);
        }
    }

    @Function(docs = GEOMETRY_IS_IN_DOC)
    public Val geometryIsIn(@JsonObject Val jsonGeometry, @JsonObject Val jsonGeometryCollection) {
        Geometry           geometry           = geoOf(jsonGeometry);
        GeometryCollection geometryCollection = (GeometryCollection) geoOf(jsonGeometryCollection);
        boolean            result             = false;

        for (int i = 0; i < geometryCollection.getNumGeometries(); i++) {
            if (geometry.equals(geometryCollection.getGeometryN(i))) {
                result = true;
            }
        }
        return Val.of(result);
    }

    @Function(docs = GEOMETRY_BAG_DOC)
    public Val geometryBag(@JsonObject Val... geometryJsonInput) {
        Geometry[] geometries = new Geometry[geometryJsonInput.length];
        for (int i = 0; i < geometryJsonInput.length; i++) {
            geometries[i] = geoOf(geometryJsonInput[i]);
        }

        GeometryFactory geomFactory = new GeometryFactory();
        return toVal(geomFactory.createGeometryCollection(geometries));
    }

    @Function(docs = RES_TO_GEOMETRY_BAG_DOC)
    public Val resToGeometryBag(@Array Val resourceArray) {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode[]   nodes  = mapper.convertValue(resourceArray.get(), JsonNode[].class);
        Val[]        values = new Val[nodes.length];
        for (int i = 0; i < nodes.length; i++) {
            values[i] = Val.of(nodes[i]);
        }
        return geometryBag(values);
    }

    @Function(docs = AT_LEAST_ONE_MEMBER_OF_DOC)
    public Val atLeastOneMemberOf(@JsonObject Val jsonGeometryCollectionOne,
            @JsonObject Val jsonGeometryCollectionTwo) {
        GeometryCollection geometryCollectionOne = (GeometryCollection) geoOf(jsonGeometryCollectionOne);
        GeometryCollection geometryCollectionTwo = (GeometryCollection) geoOf(jsonGeometryCollectionTwo);
        boolean            result                = false;
        for (int i = 0; i < geometryCollectionOne.getNumGeometries(); i++) {
            for (int j = 0; j < geometryCollectionTwo.getNumGeometries(); j++) {
                if (geometryCollectionOne.getGeometryN(i).equals(geometryCollectionTwo.getGeometryN(j))) {
                    result = true;
                }
            }
        }
        return Val.of(result);
    }

    @Function(docs = SUBSET_DOC)
    public Val subset(@JsonObject Val jsonGeometryCollectionOne, @JsonObject Val jsonGeometryCollectionTwo) {
        GeometryCollection geometryCollectionOne = (GeometryCollection) geoOf(jsonGeometryCollectionOne);
        GeometryCollection geometryCollectionTwo = (GeometryCollection) geoOf(jsonGeometryCollectionTwo);
        if (geometryCollectionOne.getNumGeometries() > geometryCollectionTwo.getNumGeometries()) {
            return Val.FALSE;
        }

        // Use BitSet as more efficient replacement for boolean array
        BitSet resultSet = new BitSet(geometryCollectionOne.getNumGeometries());

        for (int i = 0; i < geometryCollectionOne.getNumGeometries(); i++) {
            for (int j = 0; j < geometryCollectionTwo.getNumGeometries(); j++) {
                if (geometryCollectionOne.getGeometryN(i).equals(geometryCollectionTwo.getGeometryN(j))) {
                    resultSet.set(i);
                }
            }
        }

        return Val.of(resultSet.cardinality() == geometryCollectionOne.getNumGeometries());
    }

    @Function(docs = GET_PROJECTION_DOC)
    public Val getProjection(@Text Val srcSystem, @Text Val destSystem) {
        return Val.of(new GeoProjection(srcSystem.get().asText(), destSystem.get().asText()).toWkt());
    }

    @Function(docs = PROJECT_DOC)
    public Val project(@JsonObject Val jsonGeometry, @Text Val mathTransform) {
        GeoProjection projection = new GeoProjection(mathTransform.get().asText());
        Geometry      geometry   = geoOf(jsonGeometry);
        return toVal(projection.project(geometry));
    }

}
