/*
 * Copyright 2024 Red Hat, Inc.
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
import org.jboss.pnc.reqour.common.profile.AdjustProfile;
import org.jboss.pnc.reqour.common.utils.IOUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@TestProfile(AdjustProfile.class)
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
                .pushAlignedChanges(adjustRequest, manipulatorResult, false);

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
                        .pushAlignedChanges(adjustRequest, manipulatorResult, true))
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