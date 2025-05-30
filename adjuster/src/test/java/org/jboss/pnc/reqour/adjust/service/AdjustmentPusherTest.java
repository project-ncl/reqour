/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jboss.pnc.reqour.common.CloneTestUtils.SOURCE_REPO_PATH;
import static org.jboss.pnc.reqour.common.utils.IOUtils.createTempDir;

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
import org.jboss.pnc.reqour.common.CloneTestUtils;
import org.jboss.pnc.reqour.common.profile.AdjustProfile;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@TestProfile(AdjustProfile.class)
class AdjustmentPusherTest {

    @Inject
    AdjustmentPusherImpl adjustmentPusher;

    private static final Path ADJUST_DIR = Path.of("/tmp/adjust");

    @BeforeAll
    static void beforeAll() throws IOException, GitAPIException {
        Files.createDirectory(ADJUST_DIR);
        Files.createDirectory(SOURCE_REPO_PATH);
        CloneTestUtils.cloneSourceRepoFromGithub();
        RepoInitializer.createRepositories(repositoriesRoot);
    }

    @AfterAll
    static void removeCloneRepo() throws IOException {
        FileUtils.deleteDirectory(ADJUST_DIR.toFile());
        FileUtils.deleteDirectory(SOURCE_REPO_PATH.toFile());
        RepoInitializer.removeRepositories(repositoriesRoot);
    }

    @Test
    void pushAlignedChanges() {
        // PME disabled, manipulator just 1.0-SNAPSHOT ?
        AdjustRequest adjustRequest = AdjustRequest.builder()
                .buildConfigParameters(Collections.emptyMap())
                .buildType(BuildType.MVN)
                .build();
        ManipulatorResult manipulatorResult = ManipulatorResult.builder()
                .versioningState(VersioningState.builder()
                        .executionRootVersion("1.0-SNAPSHOT")
                        .build())
                .build();
        AdjustmentPushResult expectedPushResult = new AdjustmentPushResult(null, "1.0-SNAPSHOT");

        AdjustmentPushResult actualPushResult = adjustmentPusher.pushAlignedChanges(adjustRequest, manipulatorResult, false);

        assertThat(actualPushResult).isEqualTo(expectedPushResult);
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