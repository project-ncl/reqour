/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.service.clone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.jboss.pnc.reqour.common.CloneTestUtils.DEST_REPO_WITH_MAIN_BRANCH_PATH;
import static org.jboss.pnc.reqour.common.CloneTestUtils.DEST_REPO_WITH_MAIN_BRANCH_URL;
import static org.jboss.pnc.reqour.common.CloneTestUtils.EMPTY_DEST_REPO_PATH;
import static org.jboss.pnc.reqour.common.CloneTestUtils.EMPTY_DEST_REPO_URL;
import static org.jboss.pnc.reqour.common.CloneTestUtils.SOURCE_REPO_PATH;
import static org.jboss.pnc.reqour.common.CloneTestUtils.SOURCE_REPO_URL;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.inject.Inject;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.pnc.api.dto.Request;
import org.jboss.pnc.api.reqour.dto.RepositoryCloneRequest;
import org.jboss.pnc.reqour.common.CloneTestUtils;
import org.jboss.pnc.reqour.common.GitCommands;
import org.jboss.pnc.reqour.common.exceptions.GitException;
import org.jboss.pnc.reqour.common.executor.process.ProcessExecutor;
import org.jboss.pnc.reqour.common.profile.CloningProfile;
import org.jboss.pnc.reqour.common.utils.IOUtils;
import org.jboss.pnc.reqour.model.ProcessContext;
import org.jboss.pnc.reqour.service.GitCloneService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@TestProfile(CloningProfile.class)
class GitCloneServiceTest {

    @Inject
    GitCommands gitCommands;

    @Inject
    GitCloneService service;

    @Inject
    ProcessExecutor processExecutor;

    @BeforeAll
    static void setUpCloneRepo() throws IOException, GitAPIException {
        Files.createDirectory(SOURCE_REPO_PATH);
        CloneTestUtils.cloneSourceRepoFromGithub();
    }

    @AfterAll
    static void removeCloneRepo() throws IOException {
        FileUtils.deleteDirectory(SOURCE_REPO_PATH.toFile());
    }

    @BeforeEach
    void setUp() throws IOException {
        setUpEmptyDestRepo();
        setUpDestRepoWithOnlyMainBranch();
    }

    void setUpEmptyDestRepo() throws IOException {
        Files.createDirectory(EMPTY_DEST_REPO_PATH);
        gitCommands.init(true, ProcessContext.defaultBuilderWithWorkdir(EMPTY_DEST_REPO_PATH));
    }

    void setUpDestRepoWithOnlyMainBranch() throws IOException {
        Files.createDirectory(DEST_REPO_WITH_MAIN_BRANCH_PATH);
        ProcessContext.Builder pcb = ProcessContext.defaultBuilderWithWorkdir(DEST_REPO_WITH_MAIN_BRANCH_PATH);

        processExecutor.execute(
                pcb.command(
                        List.of(
                                "git",
                                "clone",
                                "--bare",
                                "--branch",
                                "main",
                                "--single-branch",
                                "--no-tags",
                                SOURCE_REPO_URL,
                                "."))
                        .build());
        processExecutor.execute(pcb.command(List.of("git", "remote", "remove", "origin")).build());

        // From some reason, 'main' is not created in refs/heads/main, so let's create it manually
        processExecutor
                .execute(pcb.command(List.of("/bin/sh", "-c", "echo $(git rev-parse HEAD) > refs/heads/main")).build());
    }

    @AfterEach
    void tearDown() throws IOException {
        FileUtils.deleteDirectory(EMPTY_DEST_REPO_PATH.toFile());
        FileUtils.deleteDirectory(DEST_REPO_WITH_MAIN_BRANCH_PATH.toFile());
    }

    @Test
    void clone_noRefProvided_clonesAllBranchesAndTags() {
        Set<String> expectedBranches = Set.of("main", "branch1", "branch1-alt", "branch2");
        Set<String> expectedTags = Set.of(
                "initial-commit",
                "branch1-tag1",
                "branch1-tag2",
                "branch1-alt-tag1",
                "branch2-tag1",
                "branch2-tag2",
                "branch2-merged");

        service.clone(
                RepositoryCloneRequest.builder()
                        .originRepoUrl(SOURCE_REPO_URL)
                        .callback(Request.builder().build())
                        .targetRepoUrl(EMPTY_DEST_REPO_URL)
                        .build());

        assertThat(getRepositoryBranches(EMPTY_DEST_REPO_PATH)).isEqualTo(expectedBranches);
        assertThat(getRepositoryTags(EMPTY_DEST_REPO_PATH)).isEqualTo(expectedTags);
    }

    @Test
    void clone_refProvidedButIsNewInternalRepo_clonesAllBranchesAndTags() {
        Set<String> expectedBranches = Set.of("main", "branch1", "branch1-alt", "branch2");
        Set<String> expectedTags = Set.of(
                "initial-commit",
                "branch1-tag1",
                "branch1-tag2",
                "branch1-alt-tag1",
                "branch2-tag1",
                "branch2-tag2",
                "branch2-merged");

        service.clone(
                RepositoryCloneRequest.builder()
                        .originRepoUrl(SOURCE_REPO_URL)
                        .callback(Request.builder().build())
                        .targetRepoUrl(EMPTY_DEST_REPO_URL)
                        .ref("main")
                        .build());

        assertThat(getRepositoryBranches(EMPTY_DEST_REPO_PATH)).isEqualTo(expectedBranches);
        assertThat(getRepositoryTags(EMPTY_DEST_REPO_PATH)).isEqualTo(expectedTags);
    }

    @Test
    void clone_existingBranchToClone_clonesOnlyThatBranch() {
        Set<String> expectedBranches = Set.of("main", "branch1");
        Set<String> expectedTags = Set.of();

        service.clone(
                RepositoryCloneRequest.builder()
                        .originRepoUrl(SOURCE_REPO_URL)
                        .callback(Request.builder().build())
                        .targetRepoUrl(DEST_REPO_WITH_MAIN_BRANCH_URL)
                        .ref("branch1")
                        .build());

        assertThat(getRepositoryBranches(DEST_REPO_WITH_MAIN_BRANCH_PATH)).isEqualTo(expectedBranches);
        assertThat(getRepositoryTags(DEST_REPO_WITH_MAIN_BRANCH_PATH)).isEqualTo(expectedTags);
    }

    @Test
    void clone_existingTag_clonesAllReachableTags() {
        Set<String> expectedBranches = Set.of("main");
        Set<String> expectedTags = Set.of("branch2-tag2", "branch2-tag1", "initial-commit");

        service.clone(
                RepositoryCloneRequest.builder()
                        .originRepoUrl(SOURCE_REPO_URL)
                        .callback(Request.builder().build())
                        .targetRepoUrl(DEST_REPO_WITH_MAIN_BRANCH_URL)
                        .ref("branch2-tag2")
                        .build());

        assertThat(getRepositoryBranches(DEST_REPO_WITH_MAIN_BRANCH_PATH)).isEqualTo(expectedBranches);
        assertThat(getRepositoryTags(DEST_REPO_WITH_MAIN_BRANCH_PATH)).isEqualTo(expectedTags);
    }

    @Test
    void clone_existingRefWhichIsNotBranchNorTag_pushesThatRefAsNewTag() {
        Set<String> expectedBranches = Set.of("main");
        String afterInitialAtMainSha = processExecutor
                .stdout(
                        ProcessContext.builder()
                                .command(List.of("/bin/sh", "-c", "git rev-list main | tac | sed -n '2 p'"))
                                .workingDirectory(SOURCE_REPO_PATH)
                                .extraEnvVariables(Collections.emptyMap())
                                .stderrConsumer(IOUtils::ignoreOutput))
                .strip();
        Set<String> expectedTags = Set.of("reqour-sync-" + afterInitialAtMainSha);

        service.clone(
                RepositoryCloneRequest.builder()
                        .originRepoUrl(SOURCE_REPO_URL)
                        .callback(Request.builder().build())
                        .targetRepoUrl(DEST_REPO_WITH_MAIN_BRANCH_URL)
                        .ref(afterInitialAtMainSha)
                        .build());

        assertThat(getRepositoryBranches(DEST_REPO_WITH_MAIN_BRANCH_PATH)).isEqualTo(expectedBranches);
        assertThat(getRepositoryTags(DEST_REPO_WITH_MAIN_BRANCH_PATH)).isEqualTo(expectedTags);
    }

    @Test
    void clone_nonExistingRefToClone_throwsGitException() {
        String nonExistingRef = "non-existing";
        assertThatThrownBy(
                () -> service.clone(
                        RepositoryCloneRequest.builder()
                                .originRepoUrl(SOURCE_REPO_URL)
                                .callback(Request.builder().build())
                                .targetRepoUrl(DEST_REPO_WITH_MAIN_BRANCH_URL)
                                .ref(nonExistingRef)
                                .build()))
                .isInstanceOf(GitException.class)
                .hasMessage("Cannot checkout to '" + nonExistingRef + "'");
    }

    @Test
    void clone_cloningFromPrivateGithubRepo_throwsGitException() {
        String originUrl = "git@github.com:user/non-existent.git";
        String testUser = ConfigProvider.getConfig().getValue("reqour.git.private-github-user", String.class);

        assertThatThrownBy(
                () -> service.clone(
                        RepositoryCloneRequest.builder()
                                .originRepoUrl(originUrl)
                                .targetRepoUrl(EMPTY_DEST_REPO_URL)
                                .ref("main")
                                .callback(Request.builder().build())
                                .build()))
                .isInstanceOf(GitException.class)
                .hasMessageContaining(
                        "If the Github repository is a private repository, you need to add the Github user '" + testUser
                                + "' with read-permissions to '" + originUrl + "'");
    }

    private static Set<String> getRepositoryBranches(Path repoPath) {
        return getGitObjects(repoPath, Path.of("refs", "heads"));
    }

    private static Set<String> getRepositoryTags(Path repoPath) {
        return getGitObjects(repoPath, Path.of("refs", "tags"));
    }

    private static Set<String> getGitObjects(Path repoPath, Path gitObjectsPath) {
        return Arrays.stream(Objects.requireNonNull(repoPath.resolve(gitObjectsPath).toFile().listFiles()))
                .map(File::getName)
                .collect(Collectors.toSet());
    }
}
