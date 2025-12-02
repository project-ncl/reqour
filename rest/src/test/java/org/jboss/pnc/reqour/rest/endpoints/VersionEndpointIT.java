/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.rest.endpoints;

import jakarta.ws.rs.core.MediaType;

import org.junit.jupiter.api.Test;

import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;

@QuarkusTest
@TestHTTPEndpoint(VersionEndpointImpl.class)
class VersionEndpointIT {

    @Test
    void getVersion_returns200Response() {
        RestAssured.given().when().accept(MediaType.APPLICATION_JSON).when().get().then().statusCode(200);
    }
}