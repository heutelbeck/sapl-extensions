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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpMethod.GET;

import java.io.IOException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.pip.http.WebClientRequestExecutor;
import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import reactor.core.publisher.Flux;

class TraccarTest {

    private static String positionsJson = "[{\"id\":16,\"attributes\":{\"batteryLevel\":66.0,\"distance\":1.0,"
            + "\"totalDistance\":2,\"ip\":\"192.168.2.1\",\"motion\":false},\"deviceId\":1,"
            + "\"type\":null,\"protocol\":\"osmand\",\"serverTime\":\"2017-09-16T11:00:00.000+0000\","
            + "\"deviceTime\":\"2017-09-16T11:00:00.000+0000\",\"fixTime\":\"2017-09-16T11:00:00.000+0000\","
            + "\"outdated\":false,\"valid\":true,\"latitude\":50.0,\"longitude\":4.4,"
            + "\"altitude\":70.0,\"speed\":0.0,\"course\":0.0,\"address\":\"Sample Adress\",\"accuracy\":0.0,"
            + "\"network\":null}]";

    private static String devicesJson = "[{\"id\":1,\"attributes\":{},\"name\":\"TestDevice\",\"uniqueId\":\"123456\","
            + "\"status\":\"offline\",\"lastUpdate\":\"\",\"positionId\":0,\"groupId\":0,"
            + "\"geofenceIds\":[],\"phone\":\"\",\"model\":\"\",\"contact\":\"\",\"category\":null}]";

    private static String geofencesJson = "[{\"id\":1,\"attributes\":{},\"name\":\"Kastel\",\"description\":\"\","
            + "\"area\":\"POLYGON((50.0 8.2, 50.0 8.2, 50.0 8.3, 50.0 8.3, 50.0 8.2))\",\"calendarId\":0},"
            + "{\"id\":3,\"attributes\":{},\"name\":\"Mainz\",\"description\":\"\",\"area\":"
            + "\"POLYGON((50.03 8.23, 50.0 8.18, 50.0 8.23, 50.0 8.3, 50.0 8.4, 50.03 8.4, 50.05 8.3, 50.03 8.23))\","
            + "\"calendarId\":0}]";

    private static String configJson = "{\"deviceID\": \"123456\", \"url\": \"http://lcl:00/api/\","
            + "\"credentials\": \"YWRtaW46YWRtaW4=\", \"posValidityTimespan\": 10}";

    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private static final String DEVICE_ID = "123456";

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    private TraccarDevice trDevice;

    private TraccarConnection trConn;

    private TraccarGeofence[] trFences;

    private JsonNode jsonDumpOne;

    private JsonNode jsonDumpTwo;

    private WebClientRequestExecutor requestExecutor;

    @BeforeEach
    void setUp() throws IOException {
        requestExecutor = mock(WebClientRequestExecutor.class);
        trConn          = new TraccarConnection(MAPPER.readValue(configJson, TraccarConfig.class), requestExecutor);
        trDevice        = MAPPER.readValue(devicesJson, TraccarDevice[].class)[0];
        trFences        = MAPPER.readValue(geofencesJson, TraccarGeofence[].class);
        jsonDumpOne     = JSON.textNode("1");
        jsonDumpTwo     = JSON.textNode("2");
    }

    @Test
    void jsonConstructor() throws IOException {
        JsonNode jsonConfig = MAPPER.readValue(configJson, JsonNode.class);
        assertEquals(new TraccarConnection(jsonConfig).getConfig(), trConn.getConfig());
    }

    @Test
	void getDeviceTest() throws IOException {
		when(requestExecutor.executeReactiveRequest(any(), eq(GET)))
				.thenReturn(Flux.just(MAPPER.readTree(devicesJson)));

		assertEquals("TestDevice", trConn.getTraccarDevice(DEVICE_ID).block().getName());
	}

    @Test
	void getPositionTest() throws IOException {
		when(requestExecutor.executeReactiveRequest(any(), eq(GET)))
				.thenReturn(Flux.just(MAPPER.readTree(positionsJson)));

		TraccarPosition expectedPosition = MAPPER.readValue(positionsJson, TraccarPosition[].class)[0];
		assertEquals(expectedPosition, trConn.getTraccarPosition(trDevice).block());
	}

    @Test
	void getGeofencesTest() throws IOException {
		when(requestExecutor.executeReactiveRequest(any(), eq(GET)))
				.thenReturn(Flux.just(MAPPER.convertValue(trFences, JsonNode.class)));

		assertArrayEquals(trFences, trConn.getTraccarGeofences(trDevice).block());
	}

    @Test
    void httpHeaderGenerationTest() {
        assertEquals("{Authorization=Basic YWRtaW46YWRtaW4=, Accept=application/json}",
                trConn.getTraccarHTTPHeader().toString());
    }

    @Test
    void createCredentialsTest() throws IOException {
        String            config = "{\"deviceID\": \"123456\", \"url\": \"http://lcl:00/api/\","
                + "\"username\": \"admin\", \"password\": \"admin\"}";
        TraccarConnection conn   = new TraccarConnection(MAPPER.readValue(config, TraccarConfig.class));
        assertEquals("{Authorization=Basic YWRtaW46YWRtaW4=, Accept=application/json}",
                conn.getTraccarHTTPHeader().toString());
    }

    @Test
    void positionEqualsTest() {
        EqualsVerifier.forClass(TraccarPosition.class).suppress(Warning.STRICT_INHERITANCE, Warning.NONFINAL_FIELDS)
                .withPrefabValues(JsonNode.class, jsonDumpOne, jsonDumpTwo).verify();
    }

    @Test
    void geofenceEqualsTest() {
        EqualsVerifier.forClass(TraccarGeofence.class).suppress(Warning.STRICT_INHERITANCE, Warning.NONFINAL_FIELDS)
                .withPrefabValues(JsonNode.class, jsonDumpOne, jsonDumpTwo).verify();
    }

    @Test
    void deviceEqualsTest() {
        EqualsVerifier.forClass(TraccarDevice.class).suppress(Warning.STRICT_INHERITANCE, Warning.NONFINAL_FIELDS)
                .withPrefabValues(JsonNode.class, jsonDumpOne, jsonDumpTwo).verify();
    }

    @Test
    void configEqualsTest() {
        EqualsVerifier.forClass(TraccarConfig.class).suppress(Warning.STRICT_INHERITANCE, Warning.NONFINAL_FIELDS)
                .verify();
    }

    @Test
    void positionCompareAscendingTest() {
        TraccarPosition a = mock(TraccarPosition.class);
        TraccarPosition b = mock(TraccarPosition.class);

        when(a.getId()).thenReturn(0);
        when(b.getId()).thenReturn(1);

        assertTrue(TraccarPosition.compareAscending(a, b) == -1 && TraccarPosition.compareAscending(b, a) == 1
                && TraccarPosition.compareAscending(a, a) == 0);
    }

    @Test
    void positionCompareDescendingTest() {
        TraccarPosition a = mock(TraccarPosition.class);
        TraccarPosition b = mock(TraccarPosition.class);

        when(a.getId()).thenReturn(0);
        when(b.getId()).thenReturn(1);

        assertTrue(TraccarPosition.compareDescending(a, b) == 1 && TraccarPosition.compareDescending(b, a) == -1
                && TraccarPosition.compareDescending(a, a) == 0);
    }

}
