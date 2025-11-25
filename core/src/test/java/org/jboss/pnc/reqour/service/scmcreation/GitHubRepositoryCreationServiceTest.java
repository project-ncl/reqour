/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.service.scmcreation;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.jboss.pnc.api.enums.InternalSCMCreationStatus;
import org.jboss.pnc.api.reqour.dto.InternalSCMCreationRequest;
import org.jboss.pnc.api.reqour.dto.InternalSCMCreationResponse;
import org.jboss.pnc.reqour.common.TestDataSupplier;
import org.jboss.pnc.reqour.common.TestUtils;
import org.jboss.pnc.reqour.common.profile.InternalSCMRepositoryCreationProfile;
import org.jboss.pnc.reqour.model.GitHubProjectCreationResult;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@TestProfile(InternalSCMRepositoryCreationProfile.class)
public class GitHubRepositoryCreationServiceTest {

    @InjectMock
    GitHubApiService gitHubApiService;

    @Inject
    GitHubRepositoryCreationService service;

    @Test
    void createInternalSCMRepository_repositoryWithNonInternalGroup_createsRepositoryInInternalGroupWithAlignedName() {
        String projectPath = "organization/repository";
        String alignedRepositoryName = "organization-repository";
        Mockito.when(gitHubApiService.getOrCreateInternalRepository(alignedRepositoryName))
                .thenReturn(new GitHubProjectCreationResult(null, InternalSCMCreationStatus.SUCCESS_CREATED));
        InternalSCMCreationResponse expectedResponse = TestUtils.newlyCreatedSuccess(
                TestDataSupplier.InternalSCM.WORKSPACE_NAME + "/" + alignedRepositoryName,
                TestDataSupplier.TASK_ID);

        InternalSCMCreationResponse response = service.createInternalSCMRepository(
                InternalSCMCreationRequest.builder()
                        .project(projectPath)
                        .taskId(TestDataSupplier.TASK_ID)
                        .build());

        assertThat(response).isEqualTo(expectedResponse);
    }

    @Test
    void createInternalSCMRepository_repositoryWithoutGroupAlreadyExists_returnsSuccessWithAlreadyExists() {
        String projectPath = "repository";
        String alignedRepositoryName = "repository";
        Mockito.when(gitHubApiService.getOrCreateInternalRepository(alignedRepositoryName))
                .thenReturn(new GitHubProjectCreationResult(null, InternalSCMCreationStatus.SUCCESS_ALREADY_EXISTS));
        InternalSCMCreationResponse expectedResponse = TestUtils.alreadyExistsSuccess(
                TestDataSupplier.InternalSCM.WORKSPACE_NAME + "/" + alignedRepositoryName,
                TestDataSupplier.TASK_ID);

        InternalSCMCreationResponse response = service.createInternalSCMRepository(
                InternalSCMCreationRequest.builder()
                        .project(projectPath)
                        .taskId(TestDataSupplier.TASK_ID)
                        .build());

        assertThat(response).isEqualTo(expectedResponse);
    }
}
