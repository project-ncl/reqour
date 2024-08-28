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
package org.jboss.pnc.reqour.service;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.models.Group;
import org.gitlab4j.api.models.Project;
import org.jboss.pnc.api.dto.Request;
import org.jboss.pnc.api.enums.InternalSCMCreationStatus;
import org.jboss.pnc.api.enums.ResultStatus;
import org.jboss.pnc.api.reqour.dto.InternalSCMCreationRequest;
import org.jboss.pnc.api.reqour.dto.InternalSCMCreationResponse;
import org.jboss.pnc.api.reqour.dto.ReqourCallback;
import org.jboss.pnc.reqour.common.TestData;
import org.jboss.pnc.reqour.common.exceptions.InvalidProjectPathException;
import org.jboss.pnc.reqour.common.gitlab.GitlabApiService;
import org.jboss.pnc.reqour.model.GitlabGetOrCreateProjectResult;
import org.jboss.pnc.reqour.profile.InternalSCMRepositoryCreationProfile;
import org.jboss.pnc.reqour.rest.providers.GitlabApiRuntimeException;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.jboss.pnc.reqour.common.TestUtils.alreadyExistsSuccess;
import static org.jboss.pnc.reqour.common.TestUtils.newlyCreatedSuccess;

@QuarkusTest
@TestProfile(InternalSCMRepositoryCreationProfile.class)
class GitlabRepositoryCreationServiceTest {

    @InjectMock
    GitlabApiService gitlabApiService;

    @Inject
    GitlabRepositoryCreationService service;

    @Test
    void createInternalSCMRepository_newProjectWithoutSubgroup_createsNewProject() {
        InternalSCMCreationResponse expectedResponse = newlyCreatedSuccess("project", TestData.TASK_ID);
        Mockito.when(gitlabApiService.getGroup(Mockito.anyLong()))
                .thenReturn(TestData.InternalSCMRepositoryCreation.workspaceGroup());
        Mockito.doReturn(new GitlabGetOrCreateProjectResult(new Project().withName("project"), expectedResponse))
                .when(gitlabApiService)
                .getOrCreateProject(
                        Mockito.anyString(),
                        Mockito.anyLong(),
                        Mockito.anyString(),
                        Mockito.anyString(),
                        Mockito.anyString(),
                        Mockito.anyString());

        InternalSCMCreationResponse response = service.createInternalSCMRepository(
                InternalSCMCreationRequest.builder().project("project").taskId(TestData.TASK_ID).build());

        assertThat(response).isEqualTo(expectedResponse);
        Mockito.verify(gitlabApiService)
                .getOrCreateProject(
                        "project",
                        TestData.InternalSCMRepositoryCreation.workspaceGroup().getId(),
                        "test-workspace/project",
                        expectedResponse.getReadonlyUrl(),
                        expectedResponse.getReadwriteUrl(),
                        TestData.TASK_ID);
    }

    @Test
    void createInternalSCMRepository_projectWithoutSubgroupContainingGitSuffix_removesGitSuffixAndCreatesNewProject() {
        InternalSCMCreationResponse expectedResponse = newlyCreatedSuccess("project", TestData.TASK_ID);
        Mockito.when(gitlabApiService.getGroup(Mockito.anyLong()))
                .thenReturn(TestData.InternalSCMRepositoryCreation.workspaceGroup());
        Mockito.doReturn(new GitlabGetOrCreateProjectResult(new Project().withName("project"), expectedResponse))
                .when(gitlabApiService)
                .getOrCreateProject(
                        Mockito.anyString(),
                        Mockito.anyLong(),
                        Mockito.anyString(),
                        Mockito.anyString(),
                        Mockito.anyString(),
                        Mockito.anyString());

        InternalSCMCreationResponse response = service.createInternalSCMRepository(
                InternalSCMCreationRequest.builder().project("project").taskId(TestData.TASK_ID).build());

        assertThat(response).isEqualTo(expectedResponse);
        Mockito.verify(gitlabApiService)
                .getOrCreateProject(
                        "project",
                        TestData.InternalSCMRepositoryCreation.workspaceGroup().getId(),
                        "test-workspace/project",
                        expectedResponse.getReadonlyUrl(),
                        expectedResponse.getReadwriteUrl(),
                        TestData.TASK_ID);
    }

    @Test
    void createInternalSCMRepository_existingProjectWithoutSubgroup_successWithAlreadyExists() {
        InternalSCMCreationResponse expectedResponse = alreadyExistsSuccess("project", TestData.TASK_ID);
        Mockito.when(gitlabApiService.getGroup(Mockito.anyLong()))
                .thenReturn(TestData.InternalSCMRepositoryCreation.workspaceGroup());
        Mockito.doReturn(new GitlabGetOrCreateProjectResult(new Project().withName("project"), expectedResponse))
                .when(gitlabApiService)
                .getOrCreateProject(
                        Mockito.anyString(),
                        Mockito.anyLong(),
                        Mockito.anyString(),
                        Mockito.anyString(),
                        Mockito.anyString(),
                        Mockito.anyString());

        InternalSCMCreationResponse response = service.createInternalSCMRepository(
                InternalSCMCreationRequest.builder().project("project").taskId(TestData.TASK_ID).build());

        assertThat(response).isEqualTo(expectedResponse);
        Mockito.verify(gitlabApiService)
                .getOrCreateProject(
                        "project",
                        TestData.InternalSCMRepositoryCreation.workspaceGroup().getId(),
                        "test-workspace/project",
                        expectedResponse.getReadonlyUrl(),
                        expectedResponse.getReadwriteUrl(),
                        TestData.TASK_ID);
    }

    @Test
    void createInternalSCMRepository_newProjectContainingSubgroupSameToWorkspace_createsNewProject() {
        String projectPath = "test-workspace/project";
        InternalSCMCreationResponse expectedResponse = newlyCreatedSuccess("project", TestData.TASK_ID);
        Mockito.when(gitlabApiService.getGroup(Mockito.anyLong()))
                .thenReturn(TestData.InternalSCMRepositoryCreation.workspaceGroup());
        Mockito.doReturn(new GitlabGetOrCreateProjectResult(new Project().withName("project"), expectedResponse))
                .when(gitlabApiService)
                .getOrCreateProject(
                        Mockito.anyString(),
                        Mockito.anyLong(),
                        Mockito.anyString(),
                        Mockito.anyString(),
                        Mockito.anyString(),
                        Mockito.anyString());

        InternalSCMCreationResponse response = service.createInternalSCMRepository(
                InternalSCMCreationRequest.builder().project(projectPath).taskId(TestData.TASK_ID).build());

        assertThat(response).isEqualTo(expectedResponse);
        Mockito.verify(gitlabApiService)
                .getOrCreateProject(
                        "project",
                        TestData.InternalSCMRepositoryCreation.workspaceGroup().getId(),
                        projectPath,
                        expectedResponse.getReadonlyUrl(),
                        expectedResponse.getReadwriteUrl(),
                        TestData.TASK_ID);
    }

    @Test
    void createInternalSCMRepository_newProjectWithSubgroupFromDifferentWorkspace_successWithAlreadyExists() {
        Group differentGroup = new Group().withId(42L).withName("different-group");
        InternalSCMCreationResponse expectedResponse = InternalSCMCreationResponse.builder()
                .readonlyUrl("http://localhost/test-workspace/" + differentGroup.getName() + "/project.git")
                .readwriteUrl("git@localhost:test-workspace/" + differentGroup.getName() + "/project.git")
                .status(InternalSCMCreationStatus.SUCCESS_ALREADY_EXISTS)
                .callback(ReqourCallback.builder().id(TestData.TASK_ID).status(ResultStatus.SUCCESS).build())
                .build();
        Mockito.when(gitlabApiService.getGroup(Mockito.anyLong()))
                .thenReturn(TestData.InternalSCMRepositoryCreation.workspaceGroup());
        Mockito.doReturn(differentGroup)
                .when(gitlabApiService)
                .getOrCreateSubgroup(Mockito.anyLong(), Mockito.anyString());
        Mockito.when(
                gitlabApiService.getOrCreateProject(
                        Mockito.anyString(),
                        Mockito.anyLong(),
                        Mockito.anyString(),
                        Mockito.anyString(),
                        Mockito.anyString(),
                        Mockito.anyString()))
                .thenReturn(new GitlabGetOrCreateProjectResult(new Project(), expectedResponse));

        InternalSCMCreationResponse response = service.createInternalSCMRepository(
                InternalSCMCreationRequest.builder()
                        .project(differentGroup.getName() + "/project")
                        .taskId(TestData.TASK_ID)
                        .build());

        assertThat(response).isEqualTo(expectedResponse);
        Mockito.verify(gitlabApiService).getGroup(TestData.InternalSCMRepositoryCreation.workspaceGroup().getId());
        Mockito.verify(gitlabApiService)
                .getOrCreateSubgroup(
                        TestData.InternalSCMRepositoryCreation.workspaceGroup().getId(),
                        differentGroup.getName());
        Mockito.verify(gitlabApiService)
                .getOrCreateProject(
                        "project",
                        differentGroup.getId(),
                        "test-workspace/" + differentGroup.getName() + "/project",
                        expectedResponse.getReadonlyUrl(),
                        expectedResponse.getReadwriteUrl(),
                        TestData.TASK_ID);
    }

    @Test
    void createInternalSCMRepository_workspaceDoesNotExist_throwsException() {
        String errorMessage = "Such a workspace does not exist";
        Mockito.doThrow(new GitlabApiRuntimeException(new GitLabApiException(errorMessage)))
                .when(gitlabApiService)
                .getGroup(Mockito.anyLong());

        assertThatThrownBy(
                () -> service.createInternalSCMRepository(
                        InternalSCMCreationRequest.builder()
                                .project("project")
                                .taskId(TestData.TASK_ID)
                                .callback(
                                        Request.builder()
                                                .method(Request.Method.POST)
                                                .uri(URI.create("/callback"))
                                                .build())
                                .build()))
                .hasMessageContaining(errorMessage);
    }

    @Test
    void createInternalSCMRepository_projectContainsTooManySlashes_throwsException() {
        String project = "group/subgroup/project";
        Mockito.when(gitlabApiService.getGroup(Mockito.anyLong()))
                .thenReturn(TestData.InternalSCMRepositoryCreation.workspaceGroup());

        assertThatThrownBy(
                () -> service.createInternalSCMRepository(
                        InternalSCMCreationRequest.builder()
                                .project(project)
                                .taskId(TestData.TASK_ID)
                                .callback(
                                        Request.builder()
                                                .method(Request.Method.POST)
                                                .uri(URI.create("/callback"))
                                                .build())
                                .build()))
                .isInstanceOf(InvalidProjectPathException.class)
                .hasMessageContaining(
                        String.format("Invalid project path given: '%s'. Expecting at most 1 '/'.", project));
    }
}