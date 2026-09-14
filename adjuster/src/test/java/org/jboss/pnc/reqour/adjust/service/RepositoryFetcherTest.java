/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jboss.pnc.reqour.common.utils.IOUtils.createTempDir;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import jakarta.inject.Inject;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.jboss.pnc.api.reqour.dto.AdjustRequest;
import org.jboss.pnc.api.reqour.dto.InternalGitRepositoryUrl;
import org.jboss.pnc.reqour.adjust.common.RepoInitializer;
import org.jboss.pnc.reqour.adjust.model.CloningResult;
import org.jboss.pnc.reqour.common.GitCommands;
import org.jboss.pnc.reqour.model.ProcessContext;
import org.jboss.pnc.reqour.service.scmcreation.GitLabApiService;
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
    GitLabApiService gitlabApiService;

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
                ProcessContext.withWorkdirAndIgnoringOutput(repositoriesRoot.resolve("downstream")));
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

    @Test
    void transformGitSubmodulesIntoFatRepository_staleGitmodulesEntry_skipsWithoutFailing() throws Exception {
        // A path can be declared in .gitmodules without a corresponding gitlink in the index (a stale/orphaned
        // entry), e.g. apache-camel's .github/actions/backport. Such a path must be skipped instead of failing the
        // whole transformation with 'git rm --cached ... did not match any files'.
        Path repo = workdir.resolve("stale-submodule-repo");
        Files.createDirectories(repo);
        git(repo, "init", "-q");
        git(repo, "config", "user.email", "test@test");
        git(repo, "config", "user.name", "test");

        // A regular checked-in directory (not a submodule), so it has no .git inside it
        Path actionDir = repo.resolve(".github/actions/backport");
        Files.createDirectories(actionDir);
        Files.writeString(actionDir.resolve("action.yml"), "name: backport\n");

        // Stale .gitmodules stanza referencing that path, but with no gitlink in the index
        Files.writeString(
                repo.resolve(".gitmodules"),
                """
                        [submodule ".github/actions/backport"]
                            path = .github/actions/backport
                            url = https://example.com/backport.git
                        """);
        git(repo, "add", "-A");
        git(repo, "commit", "-q", "-m", "initial");

        repositoryFetcher.transformGitSubmodulesIntoFatRepository(repo);

        // The stale entry's files are preserved and the .gitmodules file is removed
        assertThat(actionDir.resolve("action.yml")).exists();
        assertThat(repo.resolve(".gitmodules")).doesNotExist();
    }

    private static void git(Path workdir, String... args) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(List.of("git"));
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command)
                .directory(workdir.toFile())
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes());
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IllegalStateException(
                    "git " + String.join(" ", args) + " failed (exit " + exitCode + "):\n" + output);
        }
    }

    @Test
    void testSubmoduleParsing() throws Exception {
        URL url = IOUtils.resourceToURL("/git-files/gitmodule");
        Path path = Paths.get(url.toURI());
        List<String> submoduleLocations = RepositoryFetcherImpl.getSubmoduleLocations(path);
        assertThat(submoduleLocations).hasSize(1);
        assertThat(submoduleLocations.get(0)).isEqualTo("submodules/quarkus");

        // now testing for multiple submodules
        url = IOUtils.resourceToURL("/git-files/gitmodule-multi");
        path = Paths.get(url.toURI());
        submoduleLocations = RepositoryFetcherImpl.getSubmoduleLocations(path);
        assertThat(submoduleLocations).hasSize(2);
        assertThat(submoduleLocations.get(0)).isEqualTo("submodules/quarkus");
        assertThat(submoduleLocations.get(1)).isEqualTo("submodules/quarkus-behive/test");

    }
}
