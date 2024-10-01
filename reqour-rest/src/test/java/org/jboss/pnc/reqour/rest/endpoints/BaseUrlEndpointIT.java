/**
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.rest.endpoints;

import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.RestAssured;
import jakarta.ws.rs.core.MediaType;
import org.hamcrest.Matchers;
import org.jboss.pnc.reqour.common.profile.VersionProfile;
import org.junit.jupiter.api.Test;

import static org.jboss.pnc.api.constants.HttpHeaders.LOCATION_STRING;

@QuarkusTest
@TestHTTPEndpoint(BaseUrlEndpoint.class)
@TestProfile(VersionProfile.class)
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
