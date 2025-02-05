/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.rest.endpoints;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.quarkiverse.wiremock.devservice.ConnectWireMock;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.MediaType;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.jboss.pnc.api.dto.ErrorResponse;
import org.jboss.pnc.api.enums.ResultStatus;
import org.jboss.pnc.api.reqour.dto.RepositoryCloneRequest;
import org.jboss.pnc.api.reqour.rest.CloneEndpoint;
import org.jboss.pnc.reqour.common.CloneTestUtils;
import org.jboss.pnc.reqour.common.GitCommands;
import org.jboss.pnc.reqour.common.TestDataSupplier;
import org.jboss.pnc.reqour.common.TestUtils;
import org.jboss.pnc.reqour.common.profile.CloningProfile;
import org.jboss.pnc.reqour.model.ProcessContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jboss.pnc.reqour.common.TestDataSupplier.CALLBACK_PATH;

@QuarkusTest
@TestHTTPEndpoint(CloneEndpoint.class)
@TestProfile(CloningProfile.class)
@ConnectWireMock
class CloningEndpointIT {

    WireMock invokerWireMock;

    @Inject
    GitCommands gitCommands;

    @Inject
    ObjectMapper objectMapper;

    @BeforeAll
    static void setUpCloneRepo() throws IOException, GitAPIException {
        Files.createDirectory(CloneTestUtils.SOURCE_REPO_PATH);
        CloneTestUtils.cloneSourceRepoFromGithub();
    }

    @BeforeEach
    void setUp() throws IOException {
        setUpEmptyDestRepo();
        configureWireMockStubbing();
    }

    @AfterEach
    void tearDown() throws IOException {
        FileUtils.deleteDirectory(CloneTestUtils.EMPTY_DEST_REPO_PATH.toFile());
        invokerWireMock.resetRequests();
    }

    @AfterAll
    static void removeCloneRepo() throws IOException {
        FileUtils.deleteDirectory(CloneTestUtils.SOURCE_REPO_PATH.toFile());
    }

    private void configureWireMockStubbing() {
        invokerWireMock.register(WireMock.post(CALLBACK_PATH).willReturn(WireMock.ok()));
    }

    private void setUpEmptyDestRepo() throws IOException {
        Files.createDirectory(CloneTestUtils.EMPTY_DEST_REPO_PATH);
        gitCommands.init(true, ProcessContext.defaultBuilderWithWorkdir(CloneTestUtils.EMPTY_DEST_REPO_PATH));
    }

    @Test
    void clone_validCloneRequest_sendsCallback() throws InterruptedException, JsonProcessingException {
        String expectedBody = objectMapper.writeValueAsString(
                TestUtils.createRepositoryCloneResponse(
                        CloneTestUtils.SOURCE_REPO_URL,
                        CloneTestUtils.EMPTY_DEST_REPO_URL,
                        ResultStatus.SUCCESS,
                        TestDataSupplier.TASK_ID));

        RestAssured.given()
                .when()
                .contentType(MediaType.APPLICATION_JSON)
                .body(
                        TestUtils.createRepositoryCloneRequest(
                                CloneTestUtils.SOURCE_REPO_URL,
                                CloneTestUtils.EMPTY_DEST_REPO_URL,
                                getCallbackUrl(),
                                TestDataSupplier.TASK_ID))
                .when()
                .post()
                .then()
                .statusCode(202);

        Thread.sleep(2_000);
        WireMockUtils.verifyThatCallbackWasSent(invokerWireMock, CALLBACK_PATH, expectedBody);
    }

    @Test
    void clone_nonExistentRepoUrl_sendsCallbackWithConflictStatus()
            throws InterruptedException, JsonProcessingException {
        String nonExistentRepoUrl = "git@github.com:user/non-existent.git";
        String expectedBody = objectMapper.writeValueAsString(
                TestUtils.createRepositoryCloneResponse(
                        nonExistentRepoUrl,
                        CloneTestUtils.EMPTY_DEST_REPO_URL,
                        ResultStatus.FAILED,
                        TestDataSupplier.TASK_ID));

        RestAssured.given()
                .when()
                .contentType(MediaType.APPLICATION_JSON)
                .body(
                        TestUtils.createRepositoryCloneRequest(
                                nonExistentRepoUrl,
                                CloneTestUtils.EMPTY_DEST_REPO_URL,
                                getCallbackUrl(),
                                TestDataSupplier.TASK_ID))
                .when()
                .post()
                .then()
                .statusCode(202);

        Thread.sleep(2_000);
        WireMockUtils.verifyThatCallbackWasSent(invokerWireMock, CALLBACK_PATH, expectedBody);
    }

    @Test
    void clone_invalidRequest_returnsErrorDTO() {
        RepositoryCloneRequest request = TestDataSupplier.Cloning.withMissingTargetUrl();
        ErrorResponse expectedResponse = new ErrorResponse(
                "ResteasyReactiveViolationException",
                "clone.arg0.targetRepoUrl: Invalid URL of the git repository");

        Response response = RestAssured.given().contentType(MediaType.APPLICATION_JSON).body(request).when().post();

        assertThat(response.statusCode()).isEqualTo(jakarta.ws.rs.core.Response.Status.BAD_REQUEST.getStatusCode());
        assertThat(response.body().as(ErrorResponse.class)).isEqualTo(expectedResponse);
    }

    private String getCallbackUrl() {
        return TestUtils.getWiremockBaseUrl() + CALLBACK_PATH;
    }
}
