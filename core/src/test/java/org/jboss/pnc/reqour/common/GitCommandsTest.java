/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.common;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import jakarta.inject.Inject;

import org.jboss.pnc.reqour.common.profile.UtilsProfile;
import org.jboss.pnc.reqour.model.ProcessContext;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@TestProfile(UtilsProfile.class)
class GitCommandsTest {

    private static final String TEST_LFS_PREFIX = "test-lfs";

    @Inject
    GitCommands gitCommands;

    @Test
    void isLfsPresent_gitattributesNotPresent_returnsFalse() throws IOException {
        Path tempDirectory = Files.createTempDirectory(TEST_LFS_PREFIX);

        assertFalse(gitCommands.isLfsPresent(ProcessContext.withWorkdirAndIgnoringOutput(tempDirectory)));

        Files.delete(tempDirectory);
    }

    @Test
    void isLfsPresent_gitattributesPresentButNoLfsConfigured_returnsFalse() throws IOException {
        Path tempDirectory = Files.createTempDirectory(TEST_LFS_PREFIX);
        Path gitAttributesFilePath = Files.createFile(tempDirectory.resolve(Path.of(GitCommands.GIT_ATTRIBUTES)));

        assertFalse(gitCommands.isLfsPresent(ProcessContext.withWorkdirAndIgnoringOutput(tempDirectory)));

        Files.delete(gitAttributesFilePath);
        Files.delete(tempDirectory);
    }

    @Test
    void isLfsPresent_gitattributesPresentAndLfsConfigured_returnsTrue() throws IOException {
        Path tempDirectory = Files.createTempDirectory(TEST_LFS_PREFIX);
        Path gitAttributesFilePath = Files.createFile(tempDirectory.resolve(Path.of(GitCommands.GIT_ATTRIBUTES)));
        Files.writeString(gitAttributesFilePath, "*.pdf filter=lfs diff=lfs merge=lfs -text");

        assertTrue(gitCommands.isLfsPresent(ProcessContext.withWorkdirAndIgnoringOutput(tempDirectory)));

        Files.delete(gitAttributesFilePath);
        Files.delete(tempDirectory);
    }
}