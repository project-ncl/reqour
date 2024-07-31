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
package org.jboss.pnc.reqour.api;

import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.response.Response;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response.Status;
import org.jboss.pnc.api.dto.ErrorResponse;
import org.jboss.pnc.api.reqour.dto.TranslateRequest;
import org.jboss.pnc.api.reqour.dto.TranslateResponse;
import org.jboss.pnc.api.reqour.dto.rest.TranslateEndpoint;
import org.jboss.pnc.reqour.common.TestData;
import org.jboss.pnc.reqour.common.TestUtils;
import org.jboss.pnc.reqour.profile.TranslationProfile;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@TestHTTPEndpoint(TranslateEndpoint.class)
@TestProfile(TranslationProfile.class)
public class TranslationEndpointIT {

    @Test
    void externalToInternal_validURL_returnsResponse() {
        TranslateResponse expectedResponse = TestData.Translation.httpsWithOrganizationAndGitSuffix();
        TranslateRequest request = TestUtils.createTranslateRequestFromExternalUrl(expectedResponse.getExternalUrl());

        Response response = given().contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(request)
                .when()
                .post();

        assertThat(response.statusCode()).isEqualTo(Status.OK.getStatusCode());
        assertThat(response.getBody().as(TranslateResponse.class)).isEqualTo(expectedResponse);
    }

    @Test
    void externalToInternal_invalidURL_returnsErrorDTO() {
        TranslateRequest request = TestData.Translation.withoutRepository();
        ErrorResponse expectedResponse = new ErrorResponse(
                "ResteasyReactiveViolationException",
                "externalToInternal.arg0.externalUrl: Invalid URL of the git repository");

        Response response = given().contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(request)
                .when()
                .post();

        assertThat(response.statusCode()).isEqualTo(Status.BAD_REQUEST.getStatusCode());
        assertThat(response.getBody().as(ErrorResponse.class)).isEqualTo(expectedResponse);
    }
}
