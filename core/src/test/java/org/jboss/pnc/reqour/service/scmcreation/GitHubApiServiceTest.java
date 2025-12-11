/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.service.scmcreation;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import jakarta.inject.Inject;

import org.jboss.pnc.api.enums.InternalSCMCreationStatus;
import org.jboss.pnc.reqour.common.TestDataSupplier;
import org.jboss.pnc.reqour.model.GitHubProjectCreationResult;
import org.jboss.pnc.reqour.service.githubrestapi.GitHubRestClient;
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

    @InjectMock
    GitHubRestClient gitHubRestClient;

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

    @Test
    void doesTagProtectionAlreadyExists_validTagProtectionExists_returnsTrue() {
        Mockito.when(gitHubRestClient.getAllRulesets(TestDataSupplier.InternalSCM.WORKSPACE_NAME))
                .thenReturn(List.of(TestDataSupplier.Cloning.TAG_PROTECTION_RULESET));
        Mockito.when(
                gitHubRestClient.getRuleset(
                        TestDataSupplier.InternalSCM.WORKSPACE_NAME,
                        TestDataSupplier.Cloning.TAG_PROTECTION_RULESET.getId()))
                .thenReturn(TestDataSupplier.Cloning.TAG_PROTECTION_RULESET);

        assertThat(service.doesTagProtectionAlreadyExists(REPOSITORY_NAME)).isTrue();
    }

    @Test
    void doesTagProtectionAlreadyExists_validTagProtectionDoesNotExist_returnsFalse() {
        Mockito.when(gitHubRestClient.getAllRulesets(TestDataSupplier.InternalSCM.WORKSPACE_NAME))
                .thenReturn(Collections.emptyList());

        assertThat(service.doesTagProtectionAlreadyExists(REPOSITORY_NAME)).isFalse();
    }
}