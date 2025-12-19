/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.service.scmcreation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.List;

import jakarta.inject.Inject;

import org.jboss.pnc.api.enums.InternalSCMCreationStatus;
import org.jboss.pnc.reqour.common.TestDataSupplier;
import org.jboss.pnc.reqour.common.exceptions.GitHubApiException;
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
public class GitHubApiServiceFaultToleranceTest {

    @InjectMock
    GitHub gitHub;

    @InjectMock
    GitHubRestClient gitHubRestClient;

    @Inject
    GitHubApiService service;

    private static final String REPOSITORY_NAME = "repository";

    @Test
    void getOrCreateInternalRepository_repositoryDoesNotExist_createsNewRepositoryAfterRetries() throws IOException {
        GHRepository repository = Mockito.mock(GHRepository.class);
        Mockito.when(repository.getOwnerName()).thenReturn(TestDataSupplier.InternalSCM.INTERNAL_ORGANIZATION_NAME);
        GHCreateRepositoryBuilder builder = Mockito.mock(GHCreateRepositoryBuilder.class);
        Mockito.when(builder.create()).thenReturn(repository);
        GHOrganization internalOrganization = Mockito.mock(GHOrganization.class);
        Mockito.when(gitHub.getOrganization(TestDataSupplier.InternalSCM.INTERNAL_ORGANIZATION_NAME))
                .thenThrow(new GitHubApiException("Service unavailable"))
                .thenReturn(internalOrganization);
        Mockito.when(internalOrganization.getRepository(REPOSITORY_NAME))
                .thenThrow(new GitHubApiException("Service unavailable"))
                .thenReturn(null);
        Mockito.when(internalOrganization.createRepository(REPOSITORY_NAME))
                .thenThrow(new GitHubApiException("Service unavailable"))
                .thenThrow(new GitHubApiException("Service unavailable x2"))
                .thenReturn(builder);

        GitHubProjectCreationResult result = service.getOrCreateInternalRepository(REPOSITORY_NAME);

        assertThat(result.status()).isEqualTo(InternalSCMCreationStatus.SUCCESS_CREATED);
        assertThat(result.repository().getOwnerName())
                .isEqualTo(TestDataSupplier.InternalSCM.INTERNAL_ORGANIZATION_NAME);
    }

    @Test
    void getOrCreateInternalRepository_repositoryDoesNotExist_failsToCreateBecauseGitHubIsUnavailable()
            throws IOException {
        GHRepository repository = Mockito.mock(GHRepository.class);
        Mockito.when(repository.getOwnerName()).thenReturn(TestDataSupplier.InternalSCM.INTERNAL_ORGANIZATION_NAME);
        GHCreateRepositoryBuilder builder = Mockito.mock(GHCreateRepositoryBuilder.class);
        Mockito.when(builder.create()).thenReturn(repository);
        GHOrganization internalOrganization = Mockito.mock(GHOrganization.class);
        Mockito.when(gitHub.getOrganization(TestDataSupplier.InternalSCM.INTERNAL_ORGANIZATION_NAME))
                .thenThrow(new GitHubApiException("Service unavailable"))
                .thenThrow(new GitHubApiException("Service unavailable x2"))
                .thenThrow(new GitHubApiException("Service unavailable x3"))
                .thenReturn(internalOrganization);
        Mockito.when(internalOrganization.getRepository(REPOSITORY_NAME))
                .thenReturn(null);
        Mockito.when(internalOrganization.createRepository(REPOSITORY_NAME))
                .thenReturn(builder);

        assertThatThrownBy(() -> service.getOrCreateInternalRepository(REPOSITORY_NAME))
                .isInstanceOf(GitHubApiException.class)
                .hasMessage("Service unavailable x3");
    }

    @Test
    void doesTagProtectionAlreadyExists_validTagProtectionExists_returnsTrueAfterRetries() {
        Mockito.when(gitHubRestClient.getAllRulesets(TestDataSupplier.InternalSCM.INTERNAL_ORGANIZATION_NAME))
                .thenThrow(new GitHubApiException("Service unavailable"))
                .thenThrow(new GitHubApiException("Service unavailable x2"))
                .thenReturn(List.of(TestDataSupplier.Cloning.TAG_PROTECTION_RULESET));
        Mockito.when(
                gitHubRestClient.getRuleset(
                        TestDataSupplier.InternalSCM.INTERNAL_ORGANIZATION_NAME,
                        TestDataSupplier.Cloning.TAG_PROTECTION_RULESET.getId()))
                .thenThrow(new GitHubApiException("Service unavailable"))
                .thenReturn(TestDataSupplier.Cloning.TAG_PROTECTION_RULESET);

        assertThat(service.doesTagProtectionAlreadyExists(REPOSITORY_NAME)).isTrue();
    }
}
