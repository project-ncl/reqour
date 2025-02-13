/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.service;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.jboss.pnc.reqour.common.CloneTestUtils;
import org.jboss.pnc.reqour.common.profile.AdjustProfile;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jboss.pnc.reqour.common.CloneTestUtils.SOURCE_REPO_PATH;

@QuarkusTest
@TestProfile(AdjustProfile.class)
class AdjustmentPusherTest {

    @Inject
    AdjustmentPusherImpl adjustmentPusher;

    private static final Path ADJUST_DIR = Path.of("/tmp/adjust"); // adjustment pusher impl makes sure it exists

    @BeforeAll
    static void beforeAll() throws IOException, GitAPIException {
        Files.createDirectory(ADJUST_DIR);
        Files.createDirectory(SOURCE_REPO_PATH);
        CloneTestUtils.cloneSourceRepoFromGithub();
    }

    @AfterAll
    static void removeCloneRepo() throws IOException {
        FileUtils.deleteDirectory(ADJUST_DIR.toFile());
        FileUtils.deleteDirectory(SOURCE_REPO_PATH.toFile());
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