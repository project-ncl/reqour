/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.rest.endpoints;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response.Status;

import org.jboss.pnc.api.dto.ErrorResponse;
import org.jboss.pnc.api.reqour.dto.TranslateRequest;
import org.jboss.pnc.api.reqour.dto.TranslateResponse;
import org.jboss.pnc.api.reqour.rest.TranslateEndpoint;
import org.jboss.pnc.reqour.common.TestDataSupplier;
import org.jboss.pnc.reqour.common.TestUtils;
import org.jboss.pnc.reqour.common.profile.TranslationProfile;
import org.jboss.pnc.reqour.service.api.TranslationService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import io.quarkus.test.InjectMock;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.RestAssured;
import io.restassured.response.Response;

@QuarkusTest
@TestHTTPEndpoint(TranslateEndpoint.class)
@TestProfile(TranslationProfile.class)
class TranslationEndpointTest {

    @InjectMock
    TranslationService service;

    @Test
    void externalToInternal_validURL_returnsResponse() {
        TranslateResponse expectedResponse = TestDataSupplier.Translation.httpsWithOrganizationAndGitSuffix();
        TranslateRequest request = TestUtils.createTranslateRequestFromExternalUrl(expectedResponse.getExternalUrl());
        Mockito.when(service.externalToInternal(ArgumentMatchers.any())).thenReturn(expectedResponse.getInternalUrl());

        Response response = RestAssured.given()
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(request)
                .when()
                .post();

        assertThat(response.statusCode()).isEqualTo(Status.OK.getStatusCode());
        assertThat(response.getBody().as(TranslateResponse.class)).isEqualTo(expectedResponse);
    }

    @Test
    void externalToInternal_invalidURL_returnsErrorDTO() {
        TranslateRequest request = TestDataSupplier.Translation.withoutRepository();
        ErrorResponse expectedResponse = new ErrorResponse(
                "ResteasyReactiveViolationException",
                "externalToInternal.arg0.externalUrl: Invalid URL of the git repository");

        Response response = RestAssured.given()
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(request)
                .when()
                .post();

        assertThat(response.statusCode()).isEqualTo(Status.BAD_REQUEST.getStatusCode());
        assertThat(response.getBody().as(ErrorResponse.class)).isEqualTo(expectedResponse);
    }
}
