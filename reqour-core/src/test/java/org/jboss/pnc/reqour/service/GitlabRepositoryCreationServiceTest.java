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
import org.jboss.pnc.api.dto.Request;
import org.jboss.pnc.api.reqour.dto.InternalSCMCreationRequest;
import org.jboss.pnc.api.reqour.dto.InternalSCMCreationResponse;
import org.jboss.pnc.reqour.common.TestDataSupplier;
import org.jboss.pnc.reqour.common.TestUtils;
import org.jboss.pnc.reqour.common.exceptions.GitlabApiRuntimeException;
import org.jboss.pnc.reqour.common.exceptions.InvalidProjectPathException;
import org.jboss.pnc.reqour.common.gitlab.GitlabApiService;
import org.jboss.pnc.reqour.model.GitlabGetOrCreateProjectResult;
import org.jboss.pnc.reqour.common.profile.InternalSCMRepositoryCreationProfile;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@QuarkusTest
@TestProfile(InternalSCMRepositoryCreationProfile.class)
class GitlabRepositoryCreationServiceTest {

    private static final String CALLBACK_PATH = "/callback";

    @InjectMock
    GitlabApiService gitlabApiService;

    @Inject
    GitlabRepositoryCreationService service;

    @Test
    void createInternalSCMRepository_newProjectWithoutSubgroup_createsNewProject() {
        String projectPath = TestDataSupplier.InternalSCM.WORKSPACE_NAME + "/"
                + TestDataSupplier.InternalSCM.PROJECT_NAME;
        InternalSCMCreationResponse expectedResponse = TestUtils
                .newlyCreatedSuccess(projectPath, TestDataSupplier.TASK_ID);
        Mockito.when(gitlabApiService.getGroup(Mockito.anyLong()))
                .thenReturn(TestDataSupplier.InternalSCM.workspaceGroup());
        Mockito.doReturn(
                new GitlabGetOrCreateProjectResult(
                        TestDataSupplier.InternalSCM.projectFromTestWorkspace(),
                        expectedResponse))
                .when(gitlabApiService)
                .getOrCreateProject(
                        Mockito.anyString(),
                        Mockito.anyLong(),
                        Mockito.anyString(),
                        Mockito.anyString(),
                        Mockito.anyString(),
                        Mockito.anyString());
        Mockito.doNothing().when(gitlabApiService).configureProtectedTags(Mockito.anyLong(), Mockito.anyBoolean());

        InternalSCMCreationResponse response = service.createInternalSCMRepository(
                InternalSCMCreationRequest.builder()
                        .project(TestDataSupplier.InternalSCM.PROJECT_NAME)
                        .taskId(TestDataSupplier.TASK_ID)
                        .build());

        assertThat(response).isEqualTo(expectedResponse);
        Mockito.verify(gitlabApiService)
                .getOrCreateProject(
                        TestDataSupplier.InternalSCM.PROJECT_NAME,
                        TestDataSupplier.InternalSCM.WORKSPACE_ID,
                        projectPath,
                        expectedResponse.getReadonlyUrl(),
                        expectedResponse.getReadwriteUrl(),
                        TestDataSupplier.TASK_ID);
        Mockito.verify(gitlabApiService).configureProtectedTags(TestDataSupplier.InternalSCM.PROJECT_ID, false);
    }

    @Test
    void createInternalSCMRepository_projectWithoutSubgroupContainingGitSuffix_removesGitSuffixAndCreatesNewProject() {
        String projectPath = TestDataSupplier.InternalSCM.WORKSPACE_NAME + "/"
                + TestDataSupplier.InternalSCM.PROJECT_NAME;
        InternalSCMCreationResponse expectedResponse = TestUtils
                .newlyCreatedSuccess(projectPath, TestDataSupplier.TASK_ID);
        Mockito.when(gitlabApiService.getGroup(Mockito.anyLong()))
                .thenReturn(TestDataSupplier.InternalSCM.workspaceGroup());
        Mockito.doReturn(
                new GitlabGetOrCreateProjectResult(
                        TestDataSupplier.InternalSCM.projectFromTestWorkspace(),
                        expectedResponse))
                .when(gitlabApiService)
                .getOrCreateProject(
                        Mockito.anyString(),
                        Mockito.anyLong(),
                        Mockito.anyString(),
                        Mockito.anyString(),
                        Mockito.anyString(),
                        Mockito.anyString());
        Mockito.doNothing().when(gitlabApiService).configureProtectedTags(Mockito.anyLong(), Mockito.anyBoolean());

        InternalSCMCreationResponse response = service.createInternalSCMRepository(
                InternalSCMCreationRequest.builder()
                        .project(TestDataSupplier.InternalSCM.PROJECT_NAME)
                        .taskId(TestDataSupplier.TASK_ID)
                        .build());

        assertThat(response).isEqualTo(expectedResponse);
        Mockito.verify(gitlabApiService)
                .getOrCreateProject(
                        TestDataSupplier.InternalSCM.PROJECT_NAME,
                        TestDataSupplier.InternalSCM.WORKSPACE_ID,
                        projectPath,
                        expectedResponse.getReadonlyUrl(),
                        expectedResponse.getReadwriteUrl(),
                        TestDataSupplier.TASK_ID);
        Mockito.verify(gitlabApiService).configureProtectedTags(TestDataSupplier.InternalSCM.PROJECT_ID, false);
    }

    @Test
    void createInternalSCMRepository_existingProjectWithoutSubgroup_successWithAlreadyExists() {
        String projectPath = TestDataSupplier.InternalSCM.WORKSPACE_NAME + "/"
                + TestDataSupplier.InternalSCM.PROJECT_NAME;
        InternalSCMCreationResponse expectedResponse = TestUtils
                .alreadyExistsSuccess(projectPath, TestDataSupplier.TASK_ID);
        Mockito.when(gitlabApiService.getGroup(Mockito.anyLong()))
                .thenReturn(TestDataSupplier.InternalSCM.workspaceGroup());
        Mockito.doReturn(
                new GitlabGetOrCreateProjectResult(
                        TestDataSupplier.InternalSCM.projectFromTestWorkspace(),
                        expectedResponse))
                .when(gitlabApiService)
                .getOrCreateProject(
                        Mockito.anyString(),
                        Mockito.anyLong(),
                        Mockito.anyString(),
                        Mockito.anyString(),
                        Mockito.anyString(),
                        Mockito.anyString());
        Mockito.doNothing().when(gitlabApiService).configureProtectedTags(Mockito.anyLong(), Mockito.anyBoolean());

        InternalSCMCreationResponse response = service.createInternalSCMRepository(
                InternalSCMCreationRequest.builder()
                        .project(TestDataSupplier.InternalSCM.PROJECT_NAME)
                        .taskId(TestDataSupplier.TASK_ID)
                        .build());

        assertThat(response).isEqualTo(expectedResponse);
        Mockito.verify(gitlabApiService)
                .getOrCreateProject(
                        TestDataSupplier.InternalSCM.PROJECT_NAME,
                        TestDataSupplier.InternalSCM.WORKSPACE_ID,
                        projectPath,
                        expectedResponse.getReadonlyUrl(),
                        expectedResponse.getReadwriteUrl(),
                        TestDataSupplier.TASK_ID);
        Mockito.verify(gitlabApiService).configureProtectedTags(TestDataSupplier.InternalSCM.PROJECT_ID, true);
    }

    @Test
    void createInternalSCMRepository_newProjectContainingSubgroupSameToWorkspace_createsNewProject() {
        String projectPath = TestDataSupplier.InternalSCM.WORKSPACE_NAME + "/"
                + TestDataSupplier.InternalSCM.PROJECT_NAME;
        InternalSCMCreationResponse expectedResponse = TestUtils
                .newlyCreatedSuccess(projectPath, TestDataSupplier.TASK_ID);
        Mockito.when(gitlabApiService.getGroup(Mockito.anyLong()))
                .thenReturn(TestDataSupplier.InternalSCM.workspaceGroup());
        Mockito.doReturn(
                new GitlabGetOrCreateProjectResult(
                        TestDataSupplier.InternalSCM.projectFromTestWorkspace(),
                        expectedResponse))
                .when(gitlabApiService)
                .getOrCreateProject(
                        Mockito.anyString(),
                        Mockito.anyLong(),
                        Mockito.anyString(),
                        Mockito.anyString(),
                        Mockito.anyString(),
                        Mockito.anyString());
        Mockito.doNothing().when(gitlabApiService).configureProtectedTags(Mockito.anyLong(), Mockito.anyBoolean());

        InternalSCMCreationResponse response = service.createInternalSCMRepository(
                InternalSCMCreationRequest.builder().project(projectPath).taskId(TestDataSupplier.TASK_ID).build());

        assertThat(response).isEqualTo(expectedResponse);
        Mockito.verify(gitlabApiService)
                .getOrCreateProject(
                        TestDataSupplier.InternalSCM.PROJECT_NAME,
                        TestDataSupplier.InternalSCM.WORKSPACE_ID,
                        projectPath,
                        expectedResponse.getReadonlyUrl(),
                        expectedResponse.getReadwriteUrl(),
                        TestDataSupplier.TASK_ID);
        Mockito.verify(gitlabApiService).configureProtectedTags(TestDataSupplier.InternalSCM.PROJECT_ID, false);
    }

    @Test
    void createInternalSCMRepository_newProjectWithSubgroupFromDifferentWorkspace_successWithAlreadyExists() {
        String projectPath = TestDataSupplier.InternalSCM.DIFFERENT_WORKSPACE_NAME + "/"
                + TestDataSupplier.InternalSCM.PROJECT_NAME;
        InternalSCMCreationResponse expectedResponse = TestUtils.alreadyExistsSuccess(
                TestDataSupplier.InternalSCM.projectFromDifferentWorkspace().getPathWithNamespace(),
                TestDataSupplier.TASK_ID);
        Mockito.when(gitlabApiService.getGroup(Mockito.anyLong()))
                .thenReturn(TestDataSupplier.InternalSCM.workspaceGroup());
        Mockito.doReturn(TestDataSupplier.InternalSCM.differentWorkspaceGroup())
                .when(gitlabApiService)
                .getOrCreateSubgroup(Mockito.anyLong(), Mockito.anyString());
        Mockito.doReturn(
                new GitlabGetOrCreateProjectResult(
                        TestDataSupplier.InternalSCM.projectFromDifferentWorkspace(),
                        expectedResponse))
                .when(gitlabApiService)
                .getOrCreateProject(
                        Mockito.anyString(),
                        Mockito.anyLong(),
                        Mockito.anyString(),
                        Mockito.anyString(),
                        Mockito.anyString(),
                        Mockito.anyString());
        Mockito.doNothing().when(gitlabApiService).configureProtectedTags(Mockito.anyLong(), Mockito.anyBoolean());

        InternalSCMCreationResponse response = service.createInternalSCMRepository(
                InternalSCMCreationRequest.builder().project(projectPath).taskId(TestDataSupplier.TASK_ID).build());

        assertThat(response).isEqualTo(expectedResponse);
        Mockito.verify(gitlabApiService).getGroup(TestDataSupplier.InternalSCM.WORKSPACE_ID);
        Mockito.verify(gitlabApiService)
                .getOrCreateSubgroup(
                        TestDataSupplier.InternalSCM.WORKSPACE_ID,
                        TestDataSupplier.InternalSCM.DIFFERENT_WORKSPACE_NAME);
        Mockito.verify(gitlabApiService)
                .getOrCreateProject(
                        TestDataSupplier.InternalSCM.PROJECT_NAME,
                        TestDataSupplier.InternalSCM.DIFFERENT_WORKSPACE_ID,
                        TestDataSupplier.InternalSCM.projectFromDifferentWorkspace().getPathWithNamespace(),
                        expectedResponse.getReadonlyUrl(),
                        expectedResponse.getReadwriteUrl(),
                        TestDataSupplier.TASK_ID);
        Mockito.verify(gitlabApiService)
                .configureProtectedTags(TestDataSupplier.InternalSCM.DIFFERENT_PROJECT_ID, true);
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
                                .project("doesn't really matter")
                                .taskId(TestDataSupplier.TASK_ID)
                                .callback(
                                        Request.builder()
                                                .method(Request.Method.POST)
                                                .uri(URI.create(CALLBACK_PATH))
                                                .build())
                                .build()))
                .hasMessageContaining(errorMessage);
    }

    @Test
    void createInternalSCMRepository_projectContainsTooManySlashes_throwsException() {
        String project = "group/subgroup/project";
        Mockito.when(gitlabApiService.getGroup(Mockito.anyLong()))
                .thenReturn(TestDataSupplier.InternalSCM.workspaceGroup());

        assertThatThrownBy(
                () -> service.createInternalSCMRepository(
                        InternalSCMCreationRequest.builder()
                                .project(project)
                                .taskId(TestDataSupplier.TASK_ID)
                                .callback(
                                        Request.builder()
                                                .method(Request.Method.POST)
                                                .uri(URI.create(CALLBACK_PATH))
                                                .build())
                                .build()))
                .isInstanceOf(InvalidProjectPathException.class)
                .hasMessageContaining(
                        String.format("Invalid project path given: '%s'. Expecting at most 1 '/'.", project));
    }
}