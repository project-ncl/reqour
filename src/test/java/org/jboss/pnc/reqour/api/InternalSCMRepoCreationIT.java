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
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.MediaType;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.models.Group;
import org.gitlab4j.api.models.Project;
import org.gitlab4j.api.utils.UrlEncoder;
import org.jboss.pnc.api.reqour.rest.InternalSCMRepositoryCreationEndpoint;
import org.jboss.pnc.reqour.common.TestData;
import org.jboss.pnc.reqour.common.TestUtils;
import org.jboss.pnc.reqour.profile.InternalSCMRepositoryCreationProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.jboss.pnc.api.constants.HttpHeaders.ACCEPT_STRING;
import static org.jboss.pnc.api.constants.HttpHeaders.CONTENT_TYPE_STRING;
import static org.jboss.pnc.reqour.common.TestData.InternalSCMRepositoryCreation.workspaceGroup;
import static org.jboss.pnc.reqour.common.TestUtils.alreadyExistsSuccess;
import static org.jboss.pnc.reqour.common.TestUtils.createInternalSCMRepoCreationRequest;
import static org.jboss.pnc.reqour.common.TestUtils.failed;
import static org.jboss.pnc.reqour.common.TestUtils.newlyCreatedSuccess;

@QuarkusTest
@TestHTTPEndpoint(InternalSCMRepositoryCreationEndpoint.class)
@TestProfile(InternalSCMRepositoryCreationProfile.class)
@ConnectWireMock
public class InternalSCMRepoCreationIT {

    private static final String CALLBACK_PATH = "/callback";
    private static final String GITLAB_API_PATH = "/api/v4";
    private static final Group DIFFERENT_WORKSPACE = new Group().withId(2L)
            .withName("different-workspace")
            .withParentId(workspaceGroup().getId());

    @Inject
    ObjectMapper objectMapper;

    WireMock wireMock;

    @BeforeEach
    void setUp() {
        wireMock.register(WireMock.post(CALLBACK_PATH).willReturn(WireMock.ok()));
    }

    @Test
    void createInternalSCMRepository_newProjectWithoutSubgroup_createsNewProject()
            throws InterruptedException, JsonProcessingException {
        String projectPath = "project";
        TestUtils.registerGet(
                wireMock,
                GITLAB_API_PATH + "/groups/" + workspaceGroup().getId(),
                objectMapper.writeValueAsString(workspaceGroup()));
        wireMock.register(
                WireMock.get(GITLAB_API_PATH + "/projects/" + UrlEncoder.urlEncode("test-workspace/" + projectPath))
                        .willReturn(
                                WireMock.notFound()
                                        .withBody(
                                                objectMapper.writeValueAsString(
                                                        new GitLabApiException(
                                                                "Project with project path " + projectPath
                                                                        + " not found")))
                                        .withHeader(CONTENT_TYPE_STRING, MediaType.APPLICATION_JSON)));
        wireMock.register(
                WireMock.post(GITLAB_API_PATH + "/projects")
                        .withFormParam("namespace_id", WireMock.equalTo(String.valueOf(workspaceGroup().getId())))
                        .withFormParam("name", WireMock.equalTo(projectPath))
                        .withHeader(ACCEPT_STRING, WireMock.equalTo(MediaType.APPLICATION_JSON))
                        .withHeader(CONTENT_TYPE_STRING, WireMock.equalTo(MediaType.APPLICATION_FORM_URLENCODED))
                        .willReturn(
                                WireMock.ok(
                                        objectMapper.writeValueAsString(
                                                new Project().withNamespaceId(workspaceGroup().getId())
                                                        .withName(projectPath)))
                                        .withHeader(CONTENT_TYPE_STRING, MediaType.APPLICATION_JSON)));

        given().when()
                .contentType(MediaType.APPLICATION_JSON)
                .body(TestUtils.createInternalSCMRepoCreationRequest(projectPath, TestData.TASK_ID, CALLBACK_PATH))
                .when()
                .post()
                .then()
                .statusCode(202);

        Thread.sleep(1_000);
        TestUtils.verifyThatCallbackWasSent(
                wireMock,
                CALLBACK_PATH,
                objectMapper.writeValueAsString(newlyCreatedSuccess(projectPath, TestData.TASK_ID)));
    }

    @Test
    void createInternalSCMRepository_alreadyExistingProjectWithoutSubgroup_successWithAlreadyExists()
            throws InterruptedException, JsonProcessingException {
        String projectPath = "project";
        TestUtils.registerGet(
                wireMock,
                GITLAB_API_PATH + "/groups/" + workspaceGroup().getId(),
                objectMapper.writeValueAsString(workspaceGroup()));
        TestUtils.registerGet(
                wireMock,
                GITLAB_API_PATH + "/projects/" + UrlEncoder.urlEncode("test-workspace/" + projectPath),
                objectMapper.writeValueAsString(
                        new Project().withNamespaceId(workspaceGroup().getId()).withName("project")));

        given().when()
                .contentType(MediaType.APPLICATION_JSON)
                .body(createInternalSCMRepoCreationRequest(projectPath, TestData.TASK_ID, CALLBACK_PATH))
                .when()
                .post()
                .then()
                .statusCode(202);

        Thread.sleep(1_000);
        TestUtils.verifyThatCallbackWasSent(
                wireMock,
                CALLBACK_PATH,
                objectMapper.writeValueAsString(alreadyExistsSuccess(projectPath, TestData.TASK_ID)));
    }

    @Test
    void createInternalSCMRepository_newProjectWithSubgroupSameToWorkspace_createsNewProject()
            throws InterruptedException, JsonProcessingException {
        String projectPath = "test-workspace/project";
        TestUtils.registerGet(
                wireMock,
                GITLAB_API_PATH + "/groups/" + workspaceGroup().getId(),
                objectMapper.writeValueAsString(workspaceGroup()));
        wireMock.register(
                WireMock.get(GITLAB_API_PATH + "/projects/" + UrlEncoder.urlEncode(projectPath))
                        .willReturn(
                                WireMock.notFound()
                                        .withBody(
                                                objectMapper.writeValueAsString(
                                                        new GitLabApiException(
                                                                "Project with project path " + projectPath
                                                                        + " not found")))
                                        .withHeader(CONTENT_TYPE_STRING, MediaType.APPLICATION_JSON)));
        wireMock.register(
                WireMock.post(GITLAB_API_PATH + "/projects")
                        .withFormParam("namespace_id", WireMock.equalTo(String.valueOf(workspaceGroup().getId())))
                        .withFormParam("name", WireMock.equalTo("project"))
                        .withHeader(ACCEPT_STRING, WireMock.equalTo(MediaType.APPLICATION_JSON))
                        .withHeader(CONTENT_TYPE_STRING, WireMock.equalTo(MediaType.APPLICATION_FORM_URLENCODED))
                        .willReturn(
                                WireMock.ok(
                                        objectMapper.writeValueAsString(
                                                new Project().withNamespaceId(workspaceGroup().getId())
                                                        .withName("project")))
                                        .withHeader(CONTENT_TYPE_STRING, MediaType.APPLICATION_JSON)));

        given().when()
                .contentType(MediaType.APPLICATION_JSON)
                .body(createInternalSCMRepoCreationRequest(projectPath, TestData.TASK_ID, CALLBACK_PATH))
                .when()
                .post()
                .then()
                .statusCode(202);

        Thread.sleep(1_000);
        TestUtils.verifyThatCallbackWasSent(
                wireMock,
                CALLBACK_PATH,
                objectMapper.writeValueAsString(newlyCreatedSuccess("project", TestData.TASK_ID)));
    }

    @Test
    void createInternalSCMRepository_alreadyExistingProjectWithSubgroupDifferentFromWorkspace_successWithAlreadyExists()
            throws InterruptedException, JsonProcessingException {
        String projectPath = "different-workspace/project";
        TestUtils.registerGet(
                wireMock,
                GITLAB_API_PATH + "/groups/" + workspaceGroup().getId(),
                objectMapper.writeValueAsString(workspaceGroup()));
        TestUtils.registerGet(
                wireMock,
                GITLAB_API_PATH + "/projects/" + UrlEncoder.urlEncode("test-workspace/" + projectPath),
                objectMapper.writeValueAsString(
                        new Project().withNamespaceId(workspaceGroup().getId()).withName(projectPath)));
        TestUtils.registerGet(
                wireMock,
                GITLAB_API_PATH + "/groups/" + UrlEncoder.urlEncode("test-workspace/different-workspace"),
                objectMapper.writeValueAsString(DIFFERENT_WORKSPACE));

        given().when()
                .contentType(MediaType.APPLICATION_JSON)
                .body(createInternalSCMRepoCreationRequest(projectPath, TestData.TASK_ID, CALLBACK_PATH))
                .when()
                .post()
                .then()
                .statusCode(202);

        Thread.sleep(1_000);
        TestUtils.verifyThatCallbackWasSent(
                wireMock,
                CALLBACK_PATH,
                objectMapper.writeValueAsString(alreadyExistsSuccess(projectPath, TestData.TASK_ID)));
    }

    @Test
    void createInternalSCMRepository_newProjectWithSubgroupDifferentFromWorkspace_createsNewProjectAndSubgroup()
            throws JsonProcessingException, InterruptedException {
        String projectPath = "different-workspace/project";
        TestUtils.registerGet(
                wireMock,
                GITLAB_API_PATH + "/groups/" + workspaceGroup().getId(),
                objectMapper.writeValueAsString(workspaceGroup()));
        wireMock.register(
                WireMock.get(GITLAB_API_PATH + "/projects/" + UrlEncoder.urlEncode(projectPath))
                        .willReturn(
                                WireMock.notFound()
                                        .withBody(
                                                objectMapper.writeValueAsString(
                                                        new GitLabApiException(
                                                                "Project with project path " + projectPath
                                                                        + " not found")))
                                        .withHeader(CONTENT_TYPE_STRING, MediaType.APPLICATION_JSON)));
        wireMock.register(
                WireMock.get(GITLAB_API_PATH + "/groups/" + UrlEncoder.urlEncode("test-workspace/different-workspace"))
                        .willReturn(
                                WireMock.notFound()
                                        .withBody(
                                                objectMapper.writeValueAsString(
                                                        new GitLabApiException("Group not found")))));
        wireMock.register(
                WireMock.post(GITLAB_API_PATH + "/groups")
                        .withHeader(CONTENT_TYPE_STRING, WireMock.equalTo(MediaType.APPLICATION_FORM_URLENCODED))
                        .withFormParam("name", WireMock.equalTo("different-workspace"))
                        .willReturn(
                                WireMock.ok(objectMapper.writeValueAsString(DIFFERENT_WORKSPACE))
                                        .withHeader(CONTENT_TYPE_STRING, MediaType.APPLICATION_JSON)));
        wireMock.register(
                WireMock.post(GITLAB_API_PATH + "/projects")
                        .withFormParam("namespace_id", WireMock.equalTo(String.valueOf(DIFFERENT_WORKSPACE.getId())))
                        .withFormParam("name", WireMock.equalTo("project"))
                        .withHeader(ACCEPT_STRING, WireMock.equalTo(MediaType.APPLICATION_JSON))
                        .withHeader(CONTENT_TYPE_STRING, WireMock.equalTo(MediaType.APPLICATION_FORM_URLENCODED))
                        .willReturn(
                                WireMock.ok(
                                        objectMapper.writeValueAsString(
                                                new Project().withNamespaceId(DIFFERENT_WORKSPACE.getId())
                                                        .withName("project")))
                                        .withHeader(CONTENT_TYPE_STRING, MediaType.APPLICATION_JSON)));

        given().when()
                .contentType(MediaType.APPLICATION_JSON)
                .body(createInternalSCMRepoCreationRequest(projectPath, TestData.TASK_ID, CALLBACK_PATH))
                .when()
                .post()
                .then()
                .statusCode(202);

        Thread.sleep(1_000);
        TestUtils.verifyThatCallbackWasSent(
                wireMock,
                CALLBACK_PATH,
                objectMapper.writeValueAsString(newlyCreatedSuccess(projectPath, TestData.TASK_ID)));
    }

    @Test
    void createInternalSCMRepository_invalidRequestWithTooDeepHierarchy_sendsFailInCallback()
            throws JsonProcessingException, InterruptedException {
        wireMock.register(
                WireMock.get(GITLAB_API_PATH + "/groups/" + workspaceGroup().getId())
                        .willReturn(
                                WireMock.ok(objectMapper.writeValueAsString(workspaceGroup()))
                                        .withHeader(CONTENT_TYPE_STRING, MediaType.APPLICATION_JSON)));
        String projectPath = "group/subgroup/project.git";
        String expectedBody = objectMapper.writeValueAsString(failed(projectPath, TestData.TASK_ID));

        given().when()
                .contentType(MediaType.APPLICATION_JSON)
                .body(createInternalSCMRepoCreationRequest(projectPath, TestData.TASK_ID, CALLBACK_PATH))
                .when()
                .post()
                .then()
                .statusCode(202);

        Thread.sleep(1_000);
        TestUtils.verifyThatCallbackWasSent(wireMock, CALLBACK_PATH, expectedBody);
    }
}
