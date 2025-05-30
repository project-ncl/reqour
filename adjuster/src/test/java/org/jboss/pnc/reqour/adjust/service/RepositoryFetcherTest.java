/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jboss.pnc.reqour.common.utils.IOUtils.createTempDir;

import java.io.IOException;
import java.nio.file.Path;

import jakarta.inject.Inject;

import org.apache.commons.io.FileUtils;
import org.jboss.pnc.api.reqour.dto.AdjustRequest;
import org.jboss.pnc.api.reqour.dto.InternalGitRepositoryUrl;
import org.jboss.pnc.reqour.adjust.common.RepoInitializer;
import org.jboss.pnc.reqour.adjust.model.CloningResult;
import org.jboss.pnc.reqour.common.GitCommands;
import org.jboss.pnc.reqour.common.gitlab.GitlabApiService;
import org.jboss.pnc.reqour.model.ProcessContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class RepositoryFetcherTest {

    private static final Path repositoriesRoot = createTempDir(
            "repositories-root-",
            "git repositories needed in a test");
    private static final Path upstreamDir = repositoriesRoot.resolve("upstream");
    private static final Path downstreamDir = repositoriesRoot.resolve("downstream");
    private static Path workdir;

    @Inject
    RepositoryFetcherImpl repositoryFetcher;

    @InjectMock
    GitlabApiService gitlabApiService;

    @Inject
    GitCommands gitCommands;

    @BeforeEach
    void setUp() {
        RepoInitializer.createGitRepositories(upstreamDir, downstreamDir);
        workdir = createTempDir("repository-fetcher-test-", "testing of repository fetcher");
    }

    @AfterEach
    void tearDown() throws IOException {
        RepoInitializer.removeGitRepositories(upstreamDir, downstreamDir);
        FileUtils.deleteDirectory(workdir.toFile());
    }

    @AfterAll
    static void afterAll() throws IOException {
        FileUtils.deleteDirectory(repositoriesRoot.toFile());
        FileUtils.deleteDirectory(workdir.toFile());
    }

    @Test
    void cloneRepository_syncEnabledRefAtUpstream_syncsFromUpstream() {
        Mockito.when(gitlabApiService.doesTagProtectionAlreadyExist(Mockito.any())).thenReturn(true);
        AdjustRequest adjustRequest = AdjustRequest.builder()
                .originRepoUrl(RepoInitializer.getUpstreamRemoteUrl(repositoriesRoot))
                .internalUrl(
                        InternalGitRepositoryUrl.builder()
                                .readwriteUrl(RepoInitializer.getDownstreamRemoteUrl(repositoriesRoot))
                                .build())
                .sync(true)
                .ref("main")
                .build();
        CloningResult expectedCloningResult = new CloningResult(
                gitCommands.revParse(repositoriesRoot.resolve("upstream")),
                false);

        CloningResult actualCloningResult = repositoryFetcher.cloneRepository(adjustRequest, workdir);

        assertThat(actualCloningResult).isEqualTo(expectedCloningResult);
    }

    @Test
    void cloneRepository_syncEnabledRefAtDownstream_noSyncNeeded() {
        Mockito.when(gitlabApiService.doesTagProtectionAlreadyExist(Mockito.any())).thenReturn(true);
        AdjustRequest adjustRequest = AdjustRequest.builder()
                .originRepoUrl(RepoInitializer.getUpstreamRemoteUrl(repositoriesRoot))
                .internalUrl(
                        InternalGitRepositoryUrl.builder()
                                .readwriteUrl(RepoInitializer.getDownstreamRemoteUrl(repositoriesRoot))
                                .build())
                .sync(true)
                .ref("1.1")
                .build();
        gitCommands.checkout(
                "1.1",
                false,
                ProcessContext.defaultBuilderWithWorkdir(repositoriesRoot.resolve("downstream")));
        CloningResult expectedCloningResult = new CloningResult(
                gitCommands.revParse(repositoriesRoot.resolve("downstream")),
                true);

        CloningResult actualCloningResult = repositoryFetcher.cloneRepository(adjustRequest, workdir);

        assertThat(actualCloningResult).isEqualTo(expectedCloningResult);
    }

    @Test
    void cloneRepository_syncDisabled_noSyncNeeded() {
        Mockito.when(gitlabApiService.doesTagProtectionAlreadyExist(Mockito.any())).thenReturn(true);
        AdjustRequest adjustRequest = AdjustRequest.builder()
                .originRepoUrl(RepoInitializer.getUpstreamRemoteUrl(repositoriesRoot))
                .internalUrl(
                        InternalGitRepositoryUrl.builder()
                                .readwriteUrl(RepoInitializer.getDownstreamRemoteUrl(repositoriesRoot))
                                .build())
                .sync(false)
                .ref("main")
                .build();
        CloningResult expectedCloningResult = new CloningResult(
                gitCommands.revParse(repositoriesRoot.resolve("downstream")),
                true);

        CloningResult actualCloningResult = repositoryFetcher.cloneRepository(adjustRequest, workdir);

        assertThat(actualCloningResult).isEqualTo(expectedCloningResult);
    }
}
