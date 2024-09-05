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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.quarkiverse.wiremock.devservice.ConnectWireMock;
import io.quarkiverse.wiremock.devservice.WireMockConfigKey;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.MediaType;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.pnc.api.dto.ErrorResponse;
import org.jboss.pnc.api.enums.ResultStatus;
import org.jboss.pnc.api.reqour.dto.RepositoryCloneRequest;
import org.jboss.pnc.api.reqour.dto.rest.CloneEndpoint;
import org.jboss.pnc.reqour.common.GitCommands;
import org.jboss.pnc.reqour.common.TestData;
import org.jboss.pnc.reqour.common.TestUtils;
import org.jboss.pnc.reqour.model.ProcessContext;
import org.jboss.pnc.reqour.profile.CloningProfile;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;

import static io.restassured.RestAssured.given;
import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.jboss.pnc.reqour.common.TestData.TASK_ID;
import static org.jboss.pnc.reqour.common.TestUtils.EMPTY_DEST_REPO_ABSOLUTE_PATH;
import static org.jboss.pnc.reqour.common.TestUtils.EMPTY_DEST_REPO_URL;
import static org.jboss.pnc.reqour.common.TestUtils.SOURCE_REPO_ABSOLUTE_PATH;
import static org.jboss.pnc.reqour.common.TestUtils.SOURCE_REPO_URL;

@QuarkusTest
@TestHTTPEndpoint(CloneEndpoint.class)
@TestProfile(CloningProfile.class)
@ConnectWireMock
class CloningEndpointIT {

    private static final String CALLBACK_PATH = "/callback";

    @ConfigProperty(name = WireMockConfigKey.PORT)
    Integer port;

    WireMock invokerWireMock;

    @Inject
    GitCommands gitCommands;

    @Inject
    ObjectMapper objectMapper;

    @BeforeAll
    static void setUpCloneRepo() throws IOException, GitAPIException {
        Files.createDirectory(SOURCE_REPO_ABSOLUTE_PATH);
        TestUtils.cloneSourceRepoFromGithub();
    }

    @AfterAll
    static void removeCloneRepo() throws IOException {
        FileUtils.deleteDirectory(SOURCE_REPO_ABSOLUTE_PATH.toFile());
    }

    @BeforeEach
    void setUp() throws IOException {
        setUpEmptyDestRepo();
        configureWireMockStubbing();
    }

    private void configureWireMockStubbing() {
        invokerWireMock.register(WireMock.post(CALLBACK_PATH).willReturn(WireMock.ok()));
    }

    private void setUpEmptyDestRepo() throws IOException {
        Files.createDirectory(EMPTY_DEST_REPO_ABSOLUTE_PATH);
        gitCommands.init(
                true,
                ProcessContext.builder()
                        .workingDirectory(EMPTY_DEST_REPO_ABSOLUTE_PATH)
                        .extraEnvVariables(Collections.emptyMap())
                        .stdoutConsumer(System.out::println)
                        .stderrConsumer(System.err::println));
    }

    @AfterEach
    void tearDown() throws IOException {
        FileUtils.deleteDirectory(EMPTY_DEST_REPO_ABSOLUTE_PATH.toFile());
    }

    @Test
    void clone_validCloneRequest_sendsCallback() throws InterruptedException, JsonProcessingException {
        String expectedBody = objectMapper.writeValueAsString(
                TestUtils.createRepositoryCloneResponseCallback(
                        SOURCE_REPO_URL,
                        EMPTY_DEST_REPO_URL,
                        ResultStatus.SUCCESS,
                        TASK_ID));

        given().when()
                .contentType(MediaType.APPLICATION_JSON)
                .body(
                        TestUtils.createRepositoryCloneRequest(
                                SOURCE_REPO_URL,
                                EMPTY_DEST_REPO_URL,
                                getCallbackUrl(),
                                TASK_ID))
                .when()
                .post()
                .then()
                .statusCode(202);

        Thread.sleep(1_000);
        invokerWireMock.verifyThat(
                1,
                WireMock.postRequestedFor(WireMock.urlEqualTo(CALLBACK_PATH))
                        .withHeader("Content-Type", WireMock.equalTo(MediaType.APPLICATION_JSON))
                        .withRequestBody(WireMock.equalTo(expectedBody)));
    }

    @Test
    void clone_nonExistentRepoUrl_sendsCallbackWithConflictStatus()
            throws InterruptedException, JsonProcessingException {
        String nonExistentRepoUrl = "git@github.com:user/non-existent.git";
        String expectedBody = objectMapper.writeValueAsString(
                TestUtils.createRepositoryCloneResponseCallback(
                        nonExistentRepoUrl,
                        EMPTY_DEST_REPO_URL,
                        ResultStatus.FAILED,
                        TASK_ID));

        given().when()
                .contentType(MediaType.APPLICATION_JSON)
                .body(
                        TestUtils.createRepositoryCloneRequest(
                                nonExistentRepoUrl,
                                EMPTY_DEST_REPO_URL,
                                getCallbackUrl(),
                                TASK_ID))
                .when()
                .post()
                .then()
                .statusCode(202);

        Thread.sleep(2_000);
        invokerWireMock.verifyThat(
                1,
                WireMock.postRequestedFor(WireMock.urlEqualTo(CALLBACK_PATH))
                        .withHeader("Content-Type", WireMock.equalTo(MediaType.APPLICATION_JSON))
                        .withRequestBody(WireMock.equalTo(expectedBody)));
    }

    @Test
    void clone_invalidRequest_returnsErrorDTO() {
        RepositoryCloneRequest request = TestData.Cloning.withMissingTargetUrl();
        ErrorResponse expectedResponse = new ErrorResponse(
                "ResteasyReactiveViolationException",
                "clone.arg0.targetRepoUrl: Invalid URL of the git repository");

        Response response = given().contentType(MediaType.APPLICATION_JSON).body(request).when().post();

        assertThat(response.statusCode()).isEqualTo(BAD_REQUEST.getStatusCode());
        assertThat(response.body().as(ErrorResponse.class)).isEqualTo(expectedResponse);
    }

    private String getCallbackUrl() {
        return "http://localhost:" + port + CALLBACK_PATH;
    }
}
