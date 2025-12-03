/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.service.scmcreation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.jboss.pnc.reqour.common.TestDataSupplier.CALLBACK_PATH;

import java.net.URI;

import jakarta.inject.Inject;

import org.jboss.pnc.api.dto.Request;
import org.jboss.pnc.api.enums.InternalSCMCreationStatus;
import org.jboss.pnc.api.reqour.dto.InternalSCMCreationRequest;
import org.jboss.pnc.api.reqour.dto.InternalSCMCreationResponse;
import org.jboss.pnc.reqour.common.TestDataSupplier;
import org.jboss.pnc.reqour.common.TestUtils;
import org.jboss.pnc.reqour.common.exceptions.InvalidProjectPathException;
import org.jboss.pnc.reqour.model.GitLabProjectCreationResult;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class GitLabRepositoryCreationServiceTest {

    @InjectMock
    GitLabApiService gitlabApiService;

    @Inject
    GitLabRepositoryCreationService service;

    @Test
    void createInternalSCMRepository_newProjectWithoutSubgroup_createsNewProject() {
        String projectPath = TestDataSupplier.InternalSCM.WORKSPACE_NAME + "/"
                + TestDataSupplier.InternalSCM.PROJECT_NAME;
        InternalSCMCreationResponse expectedResponse = TestUtils
                .newlyCreatedSuccess(projectPath, TestDataSupplier.TASK_ID);
        Mockito.when(gitlabApiService.getGroup(Mockito.anyLong()))
                .thenReturn(TestDataSupplier.InternalSCM.workspaceGroup());
        Mockito.doReturn(
                new GitLabProjectCreationResult(
                        TestDataSupplier.InternalSCM.projectFromTestWorkspace(),
                        InternalSCMCreationStatus.SUCCESS_CREATED))
                .when(gitlabApiService)
                .getOrCreateProject(
                        Mockito.anyString(),
                        Mockito.anyLong(),
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
                        projectPath);
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
                new GitLabProjectCreationResult(
                        TestDataSupplier.InternalSCM.projectFromTestWorkspace(),
                        InternalSCMCreationStatus.SUCCESS_CREATED))
                .when(gitlabApiService)
                .getOrCreateProject(
                        Mockito.anyString(),
                        Mockito.anyLong(),
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
                        projectPath);
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
                new GitLabProjectCreationResult(
                        TestDataSupplier.InternalSCM.projectFromTestWorkspace(),
                        InternalSCMCreationStatus.SUCCESS_ALREADY_EXISTS))
                .when(gitlabApiService)
                .getOrCreateProject(
                        Mockito.anyString(),
                        Mockito.anyLong(),
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
                        projectPath);
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
                new GitLabProjectCreationResult(
                        TestDataSupplier.InternalSCM.projectFromTestWorkspace(),
                        InternalSCMCreationStatus.SUCCESS_CREATED))
                .when(gitlabApiService)
                .getOrCreateProject(
                        Mockito.anyString(),
                        Mockito.anyLong(),
                        Mockito.anyString());
        Mockito.doNothing().when(gitlabApiService).configureProtectedTags(Mockito.anyLong(), Mockito.anyBoolean());

        InternalSCMCreationResponse response = service.createInternalSCMRepository(
                InternalSCMCreationRequest.builder().project(projectPath).taskId(TestDataSupplier.TASK_ID).build());

        assertThat(response).isEqualTo(expectedResponse);
        Mockito.verify(gitlabApiService)
                .getOrCreateProject(
                        TestDataSupplier.InternalSCM.PROJECT_NAME,
                        TestDataSupplier.InternalSCM.WORKSPACE_ID,
                        projectPath);
        Mockito.verify(gitlabApiService).configureProtectedTags(TestDataSupplier.InternalSCM.PROJECT_ID, false);
    }

    @Test
    void createInternalSCMRepository_newProjectWithSubgroupFromDifferentWorkspace_successWithAlreadyExists() {
        String projectPath = TestDataSupplier.InternalSCM.DIFFERENT_WORKSPACE_NAME + "/"
                + TestDataSupplier.InternalSCM.PROJECT_NAME;
        InternalSCMCreationResponse expectedResponse = TestUtils.alreadyExistsSuccess(
                TestDataSupplier.InternalSCM.projectFromDifferentWorkspace().getPathWithNamespace(),
                TestDataSupplier.TASK_ID);
        Mockito.doReturn(TestDataSupplier.InternalSCM.differentWorkspaceGroup())
                .when(gitlabApiService)
                .getOrCreateSubgroup(Mockito.anyLong(), Mockito.anyString());
        Mockito.doReturn(
                new GitLabProjectCreationResult(
                        TestDataSupplier.InternalSCM.projectFromDifferentWorkspace(),
                        InternalSCMCreationStatus.SUCCESS_ALREADY_EXISTS))
                .when(gitlabApiService)
                .getOrCreateProject(
                        Mockito.anyString(),
                        Mockito.anyLong(),
                        Mockito.anyString());
        Mockito.doNothing().when(gitlabApiService).configureProtectedTags(Mockito.anyLong(), Mockito.anyBoolean());

        InternalSCMCreationResponse response = service.createInternalSCMRepository(
                InternalSCMCreationRequest.builder().project(projectPath).taskId(TestDataSupplier.TASK_ID).build());

        assertThat(response).isEqualTo(expectedResponse);
        Mockito.verify(gitlabApiService)
                .getOrCreateSubgroup(
                        TestDataSupplier.InternalSCM.WORKSPACE_ID,
                        TestDataSupplier.InternalSCM.DIFFERENT_WORKSPACE_NAME);
        Mockito.verify(gitlabApiService)
                .getOrCreateProject(
                        TestDataSupplier.InternalSCM.PROJECT_NAME,
                        TestDataSupplier.InternalSCM.DIFFERENT_WORKSPACE_ID,
                        TestDataSupplier.InternalSCM.projectFromDifferentWorkspace().getPathWithNamespace());
        Mockito.verify(gitlabApiService)
                .configureProtectedTags(TestDataSupplier.InternalSCM.DIFFERENT_PROJECT_ID, true);
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