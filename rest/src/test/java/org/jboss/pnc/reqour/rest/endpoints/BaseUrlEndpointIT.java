/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.rest.endpoints;

import static org.jboss.pnc.api.constants.HttpHeaders.LOCATION_STRING;

import jakarta.ws.rs.core.MediaType;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;

@QuarkusTest
@TestHTTPEndpoint(BaseUrlEndpoint.class)
class BaseUrlEndpointIT {

    @Test
    void redirectToVersion_requestToBaseURL_redirectsToVersionEndpoint() {
        RestAssured.given()
                .when()
                .accept(MediaType.APPLICATION_JSON)
                .when()
                .redirects()
                .follow(false)
                .get()
                .then()
                .statusCode(303)
                .and()
                .header(LOCATION_STRING, Matchers.endsWith("/version"));
    }
}
