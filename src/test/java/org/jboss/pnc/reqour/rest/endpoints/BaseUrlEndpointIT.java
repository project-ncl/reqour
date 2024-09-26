/**
 * JBoss, Home of Professional Open Source.
 * Copyright 2024-2024 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.pnc.reqour.rest.endpoints;

import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.ws.rs.core.MediaType;
import org.hamcrest.Matchers;
import org.jboss.pnc.reqour.profile.VersionProfile;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.jboss.pnc.api.constants.HttpHeaders.LOCATION_STRING;

@QuarkusTest
@TestHTTPEndpoint(BaseUrlEndpoint.class)
@TestProfile(VersionProfile.class)
class BaseUrlEndpointIT {

    @Test
    void redirectToVersion_requestToBaseURL_redirectsToVersionEndpoint() {
        given().when()
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
