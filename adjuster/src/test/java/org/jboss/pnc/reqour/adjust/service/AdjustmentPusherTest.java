/*
 * SPDX-FileCopyrightText: Copyright © 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.jboss.pnc.reqour.common.CloneTestUtils.SOURCE_REPO_PATH;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import jakarta.inject.Inject;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.jboss.pnc.api.enums.BuildType;
import org.jboss.pnc.api.reqour.dto.AdjustRequest;
import org.jboss.pnc.api.reqour.dto.ManipulatorResult;
import org.jboss.pnc.api.reqour.dto.VersioningState;
import org.jboss.pnc.reqour.adjust.common.RepoInitializer;
import org.jboss.pnc.reqour.adjust.model.AdjustmentPushResult;
import org.jboss.pnc.reqour.adjust.utils.CommonUtils;
import org.jboss.pnc.reqour.common.CloneTestUtils;
import org.jboss.pnc.reqour.common.GitCommands;
import org.jboss.pnc.reqour.common.exceptions.GitException;
import org.jboss.pnc.reqour.common.utils.IOUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class AdjustmentPusherTest {

    private static final Path upstreamDir = IOUtils
            .createTempDir("upstream-repo-", "upstream git repository needed in a test");
    private static Path ADJUST_DIR = CommonUtils.createAdjustDirectory();

    @Inject
    AdjustmentPusherImpl adjustmentPusher;

    @Inject
    GitCommands gitCommands;

    @BeforeEach
    void setUp() throws IOException, GitAPIException {
        Files.createDirectory(SOURCE_REPO_PATH);
        CloneTestUtils.cloneSourceRepoFromGithub();
        RepoInitializer.createGitRepositories(upstreamDir, ADJUST_DIR);
        gitCommands.configureCommitter(ADJUST_DIR);
    }

    @AfterEach
    void tearDown() throws IOException {
        RepoInitializer.removeGitRepositories(upstreamDir, ADJUST_DIR);
        FileUtils.deleteDirectory(ADJUST_DIR.toFile());
        Files.createDirectory(ADJUST_DIR); // AdjustmentPusher checks that /tmp/adjust directory exists
        FileUtils.deleteDirectory(SOURCE_REPO_PATH.toFile());
    }

    @Test
    void pushAlignedChanges_alignmentChangesDone_commitsAndCreatesTagWithBranch() {
        RepoInitializer.makeAlignmentChanges(ADJUST_DIR);
        AdjustRequest adjustRequest = AdjustRequest.builder()
                .buildConfigParameters(Collections.emptyMap())
                .buildType(BuildType.MVN)
                .build();
        String version = "1.0-aligned-00042";
        ManipulatorResult manipulatorResult = ManipulatorResult.builder()
                .versioningState(
                        VersioningState.builder()
                                .executionRootVersion(version)
                                .build())
                .build();
        String upstreamCommit = gitCommands.revParse(upstreamDir);

        AdjustmentPushResult actualPushResult = adjustmentPusher
                .pushAlignedChanges(adjustRequest, manipulatorResult, true);

        assertThat(actualPushResult.tag()).isEqualTo(version);
        assertThat(actualPushResult.commit()).isNotEqualTo(upstreamCommit);
    }

    @Test
    void pushAlignedChanges_untrackedBuildOutput_isNotCommitted() throws IOException {
        RepoInitializer.makeAlignmentChanges(ADJUST_DIR);
        // what PME and builds leave behind when the project does not ignore its build output
        for (String path : List.of(
                "target/pom-manip-ext-marker.txt",
                "my-app/target/alignmentReport.json",
                "build/libs/app.jar",
                ".gradle/8.5/checksums.lock")) {
            Path file = ADJUST_DIR.resolve(path);
            Files.createDirectories(file.getParent());
            Files.writeString(file, "x");
        }

        AdjustmentPushResult result = adjustmentPusher
                .pushAlignedChanges(mvnRequest(), aligned("1.0-aligned-00042"), true);

        List<String> committed = git(ADJUST_DIR, "ls-tree", "-r", "--name-only", result.commit());
        assertThat(committed).contains("my-app/pom.xml")
                .noneMatch(path -> path.contains("target/") || path.contains("build/") || path.contains(".gradle/"));
        assertThat(git(ADJUST_DIR, "show", result.commit() + ":my-app/pom.xml")).anyMatch(
                line -> line.contains("1.0-aligned-00042"));
        // the project's own ignore rules are not changed
        assertThat(git(ADJUST_DIR, "status", "--porcelain", "--", ".gitignore")).isEmpty();
    }

    @Test
    void pushAlignedChanges_trackedFileUnderBuildOutputDirectory_isStillCommitted() throws IOException {
        Path tracked = ADJUST_DIR.resolve("target/tracked.txt");
        Files.createDirectories(tracked.getParent());
        Files.writeString(tracked, "upstream");
        git(ADJUST_DIR, "add", "--force", "target/tracked.txt");
        git(ADJUST_DIR, "commit", "-q", "-m", "a project that tracks a file under target/");
        RepoInitializer.makeAlignmentChanges(ADJUST_DIR);
        Files.writeString(tracked, "aligned");

        AdjustmentPushResult result = adjustmentPusher
                .pushAlignedChanges(mvnRequest(), aligned("1.0-aligned-00042"), true);

        assertThat(git(ADJUST_DIR, "show", result.commit() + ":target/tracked.txt")).containsExactly("aligned");
    }

    private static AdjustRequest mvnRequest() {
        return AdjustRequest.builder().buildConfigParameters(Collections.emptyMap()).buildType(BuildType.MVN).build();
    }

    private static ManipulatorResult aligned(String version) {
        return ManipulatorResult.builder()
                .versioningState(VersioningState.builder().executionRootVersion(version).build())
                .build();
    }

    private static List<String> git(Path dir, String... args) throws IOException {
        List<String> command = new java.util.ArrayList<>(List.of("git"));
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command).directory(dir.toFile()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        try {
            if (process.waitFor() != 0) {
                throw new IOException("git " + String.join(" ", args) + " failed: " + output);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
        return output.isEmpty() ? List.of() : List.of(output.split("\n"));
    }

    @Test
    void pushAlignedChanges_noAlignmentChangesButShouldNotFailOnNoAlignmentChanges_tagsCurrentOriginHead() {
        AdjustRequest adjustRequest = AdjustRequest.builder()
                .buildConfigParameters(Collections.emptyMap())
                .buildType(BuildType.MVN)
                .build();
        String version = "1.0-SNAPSHOT";
        ManipulatorResult manipulatorResult = ManipulatorResult.builder()
                .versioningState(
                        VersioningState.builder()
                                .executionRootVersion(version)
                                .build())
                .build();
        String expectedUpstreamCommit = gitCommands.revParse(upstreamDir);
        AdjustmentPushResult expectedPushResult = new AdjustmentPushResult(expectedUpstreamCommit, version);

        AdjustmentPushResult actualPushResult = adjustmentPusher
                .pushAlignedChanges(adjustRequest, manipulatorResult, true);

        assertThat(actualPushResult).isEqualTo(expectedPushResult);
    }

    @Test
    void pushAlignedChanges_noAlignmentChangesAndShouldFailOnNoAlignmentChanges_failsToCommit() {
        AdjustRequest adjustRequest = AdjustRequest.builder()
                .buildConfigParameters(Collections.emptyMap())
                .buildType(BuildType.MVN)
                .build();
        ManipulatorResult manipulatorResult = ManipulatorResult.builder()
                .versioningState(
                        VersioningState.builder()
                                .executionRootVersion("1.0-SNAPSHOT")
                                .build())
                .build();

        assertThatThrownBy(
                () -> adjustmentPusher
                        .pushAlignedChanges(adjustRequest, manipulatorResult, false))
                .isInstanceOf(GitException.class)
                .hasMessage("Cannot make the commit");
    }

    @Test
    void findTagByTreeSha_tagDoesNotExist_returnsNull() {
        String foundTag = adjustmentPusher.findTagByTreeSha(SOURCE_REPO_PATH, "non-existing-tree-sha");

        assertThat(foundTag).isNull();
    }

    @Test
    void findTagByTreeSha_tagExists_returnsTagName() {
        String foundTag = adjustmentPusher
                .findTagByTreeSha(SOURCE_REPO_PATH, "b3f9bfdb0af4f367ed47e9ddcb40b49be65d6b0b");

        assertThat(foundTag).isEqualTo("branch2-merged");
    }
}