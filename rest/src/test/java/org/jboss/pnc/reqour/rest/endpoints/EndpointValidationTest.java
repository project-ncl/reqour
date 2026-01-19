/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.rest.endpoints;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jboss.pnc.reqour.rest.endpoints.TestConstants.TEST_USER;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response.Status;

import org.jboss.pnc.api.constants.MDCHeaderKeys;
import org.jboss.pnc.api.dto.Request;
import org.jboss.pnc.api.enums.BuildType;
import org.jboss.pnc.api.reqour.dto.AdjustRequest;
import org.jboss.pnc.api.reqour.dto.InternalGitRepositoryUrl;
import org.jboss.pnc.api.reqour.dto.RepositoryCloneRequest;
import org.jboss.pnc.reqour.common.TestDataSupplier;
import org.jboss.pnc.reqour.common.profile.WithInternalUrlValidation;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.security.TestSecurity;
import io.restassured.RestAssured;
import io.restassured.http.Header;
import io.restassured.response.Response;

@QuarkusTest
@TestProfile(WithInternalUrlValidation.class)
@TestSecurity(user = TEST_USER, roles = { OidcRoleConstants.PNC_APP_REPOUR_USER })
public class EndpointValidationTest {

    @Test
    void adjust_invalidInternalUrl_conflictStatusReturned() {
        Response response = RestAssured.given()
                .basePath("/adjust")
                .contentType(MediaType.APPLICATION_JSON)
                .header(new Header(MDCHeaderKeys.PROCESS_CONTEXT.getHeaderName(), TestDataSupplier.PROCESS_CONTEXT))
                .body(
                        AdjustRequest.builder()
                                .taskId(TestDataSupplier.TASK_ID)
                                .callback(Request.builder().build())
                                .buildType(BuildType.MVN)
                                .internalUrl(
                                        InternalGitRepositoryUrl.builder()
                                                .readonlyUrl("https://github.invalid.internal.url.com/foo/bar.git")
                                                .readwriteUrl("git@github.invalid.internal.url.com:foo/bar.git")
                                                .build())
                                .build())
                .when()
                .post();

        assertThat(response.statusCode()).isEqualTo(Status.CONFLICT.getStatusCode());
    }

    @Test
    void clone_invalidInternalUrl_conflictStatusReturned() {
        Response response = RestAssured.given()
                .basePath("/clone")
                .contentType(MediaType.APPLICATION_JSON)
                .header(new Header(MDCHeaderKeys.PROCESS_CONTEXT.getHeaderName(), TestDataSupplier.PROCESS_CONTEXT))
                .body(
                        RepositoryCloneRequest.builder()
                                .taskId(TestDataSupplier.TASK_ID)
                                .callback(Request.builder().build())
                                .originRepoUrl("https://github.com/foo/bar.git")
                                .targetRepoUrl("git@github.invalid.internal.url.com:foo/bar.git")
                                .build())
                .when()
                .post();

        assertThat(response.statusCode()).isEqualTo(Status.CONFLICT.getStatusCode());
    }
}
