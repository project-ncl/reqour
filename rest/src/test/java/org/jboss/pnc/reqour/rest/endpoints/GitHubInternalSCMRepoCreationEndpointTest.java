/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.rest.endpoints;

import static org.jboss.pnc.reqour.common.TestDataSupplier.CALLBACK_PATH;
import static org.jboss.pnc.reqour.rest.endpoints.TestConstants.TEST_USER;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import jakarta.inject.Inject;
import jakarta.ws.rs.core.MediaType;

import org.jboss.pnc.api.reqour.rest.InternalSCMRepositoryCreationEndpoint;
import org.jboss.pnc.reqour.common.TestDataSupplier;
import org.jboss.pnc.reqour.common.TestUtils;
import org.jboss.pnc.reqour.common.profile.WithGitHubProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;

import io.quarkiverse.wiremock.devservice.ConnectWireMock;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.security.TestSecurity;
import io.restassured.RestAssured;

@QuarkusTest
@TestHTTPEndpoint(InternalSCMRepositoryCreationEndpoint.class)
@TestProfile(WithGitHubProvider.class)
@ConnectWireMock
@TestSecurity(user = TEST_USER, roles = { OidcRoleConstants.PNC_APP_REPOUR_USER })
public class GitHubInternalSCMRepoCreationEndpointTest {

    private static final Path GITHUB_OBJECTS_DIR = Path.of("src", "test", "resources", "github-objects");

    @Inject
    ObjectMapper objectMapper;

    WireMock wireMock;

    @BeforeEach
    void setUp() throws IOException {
        wireMock.register(WireMock.post(CALLBACK_PATH).willReturn(WireMock.ok()));
    }

    @AfterEach
    void tearDown() {
        wireMock.resetMappings();
        wireMock.resetRequests();
    }

    @Test
    void createInternalSCMRepository_repositoryDoesNotExist_newRepositoryCreated()
            throws InterruptedException, IOException {
        String projectPath = "organization/newrepo";
        WireMockUtils.registerGet(
                wireMock,
                "/orgs/test-prefix",
                Files.readString(GITHUB_OBJECTS_DIR.resolve("organization.json")));
        WireMockUtils.registerGet(
                wireMock,
                "/orgs/test-prefix/repos",
                Files.readString(GITHUB_OBJECTS_DIR.resolve("repositories.json")));
        WireMockUtils.registerGet(
                wireMock,
                "/repos/test-prefix/organization-existingrepo",
                Files.readString(GITHUB_OBJECTS_DIR.resolve("repository.json")));
        wireMock.register(
                WireMock.post("/orgs/test-prefix/repos")
                        .willReturn(WireMock.ok(Files.readString(GITHUB_OBJECTS_DIR.resolve("new-repo.json")))));

        RestAssured.given()
                .when()
                .contentType(MediaType.APPLICATION_JSON)
                .body(
                        TestUtils.createInternalSCMRepoCreationRequest(
                                projectPath,
                                TestDataSupplier.TASK_ID,
                                CALLBACK_PATH))
                .when()
                .post()
                .then()
                .statusCode(202);

        Thread.sleep(2_000);
        WireMockUtils.verifyThatCallbackWasSent(
                wireMock,
                CALLBACK_PATH,
                objectMapper.writeValueAsString(
                        TestUtils.newlyCreatedSuccess("test-prefix/organization-newrepo", TestDataSupplier.TASK_ID)));
    }

    @Test
    void createInternalSCMRepository_repositoryAlreadyExists_successWithAlreadyExists()
            throws InterruptedException, IOException {
        String projectPath = "organization/existingrepo";
        WireMockUtils.registerGet(
                wireMock,
                "/orgs/test-prefix",
                Files.readString(GITHUB_OBJECTS_DIR.resolve("organization.json")));
        WireMockUtils.registerGet(
                wireMock,
                "/orgs/test-prefix/repos",
                Files.readString(GITHUB_OBJECTS_DIR.resolve("repositories.json")));
        WireMockUtils.registerGet(
                wireMock,
                "/repos/test-prefix/organization-existingrepo",
                Files.readString(GITHUB_OBJECTS_DIR.resolve("repository.json")));

        RestAssured.given()
                .when()
                .contentType(MediaType.APPLICATION_JSON)
                .body(
                        TestUtils.createInternalSCMRepoCreationRequest(
                                projectPath,
                                TestDataSupplier.TASK_ID,
                                CALLBACK_PATH))
                .when()
                .post()
                .then()
                .statusCode(202);

        Thread.sleep(2_000);
        WireMockUtils.verifyThatCallbackWasSent(
                wireMock,
                CALLBACK_PATH,
                objectMapper.writeValueAsString(
                        TestUtils.alreadyExistsSuccess(
                                "test-prefix/organization-existingrepo",
                                TestDataSupplier.TASK_ID)));
    }

    @Test
    void createInternalSCMRepository_githubUnavailable_failsToCreateRepository()
            throws InterruptedException, IOException {
        String projectPath = "organization/repository";
        WireMockUtils.registerGet(
                wireMock,
                "/orgs/test-prefix/repos",
                Files.readString(GITHUB_OBJECTS_DIR.resolve("repositories.json")));
        WireMockUtils.registerFailures(wireMock, "/orgs/test-prefix", WireMock.serviceUnavailable(), 3);

        RestAssured.given()
                .when()
                .contentType(MediaType.APPLICATION_JSON)
                .body(
                        TestUtils.createInternalSCMRepoCreationRequest(
                                projectPath,
                                TestDataSupplier.TASK_ID,
                                CALLBACK_PATH))
                .when()
                .post()
                .then()
                .statusCode(202);

        Thread.sleep(6_000); // will be retrying 1s after first fail, and 2s after second fail
        WireMockUtils.verifyThatCallbackWasSent(
                wireMock,
                CALLBACK_PATH,
                objectMapper.writeValueAsString(
                        TestUtils.failed(
                                "test-prefix/organization-repository",
                                TestDataSupplier.TASK_ID)));
    }
}
