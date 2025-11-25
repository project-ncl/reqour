/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.service.scmcreation;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import jakarta.inject.Inject;

import org.jboss.pnc.api.enums.InternalSCMCreationStatus;
import org.jboss.pnc.reqour.common.TestDataSupplier;
import org.jboss.pnc.reqour.model.GitHubProjectCreationResult;
import org.junit.jupiter.api.Test;
import org.kohsuke.github.GHCreateRepositoryBuilder;
import org.kohsuke.github.GHOrganization;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.mockito.Mockito;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class GitHubApiServiceTest {

    @InjectMock
    GitHub gitHub;

    @Inject
    GitHubApiService service;

    private static final String REPOSITORY_NAME = "repository";

    @Test
    void getOrCreateInternalRepository_repositoryDoesNotExist_createsNewRepository() throws IOException {
        GHRepository repository = Mockito.mock(GHRepository.class);
        Mockito.when(repository.getOwnerName()).thenReturn(TestDataSupplier.InternalSCM.WORKSPACE_NAME);
        GHCreateRepositoryBuilder builder = Mockito.mock(GHCreateRepositoryBuilder.class);
        Mockito.when(builder.create()).thenReturn(repository);
        GHOrganization internalOrganization = Mockito.mock(GHOrganization.class);
        Mockito.when(internalOrganization.getRepository(Mockito.anyString())).thenReturn(null);
        Mockito.when(internalOrganization.createRepository(REPOSITORY_NAME)).thenReturn(builder);
        Mockito.when(gitHub.getOrganization(TestDataSupplier.InternalSCM.WORKSPACE_NAME))
                .thenReturn(internalOrganization);

        GitHubProjectCreationResult result = service.getOrCreateInternalRepository(REPOSITORY_NAME);

        assertThat(result.status()).isEqualTo(InternalSCMCreationStatus.SUCCESS_CREATED);
        assertThat(result.repository().getOwnerName()).isEqualTo(TestDataSupplier.InternalSCM.WORKSPACE_NAME);
    }

    @Test
    void getOrCreateInternalRepository_repositoryAlreadyExists_returnsExistingRepository() throws IOException {
        GHOrganization internalOrganization = Mockito.mock(GHOrganization.class);
        GHRepository repository = Mockito.mock(GHRepository.class);
        Mockito.when(repository.getOwnerName()).thenReturn(TestDataSupplier.InternalSCM.WORKSPACE_NAME);
        Mockito.when(gitHub.getOrganization(TestDataSupplier.InternalSCM.WORKSPACE_NAME))
                .thenReturn(internalOrganization);
        Mockito.when(internalOrganization.getRepository(REPOSITORY_NAME)).thenReturn(repository);

        GitHubProjectCreationResult result = service.getOrCreateInternalRepository(REPOSITORY_NAME);

        assertThat(result.status()).isEqualTo(InternalSCMCreationStatus.SUCCESS_ALREADY_EXISTS);
        assertThat(result.repository().getOwnerName()).isEqualTo(TestDataSupplier.InternalSCM.WORKSPACE_NAME);
    }
}