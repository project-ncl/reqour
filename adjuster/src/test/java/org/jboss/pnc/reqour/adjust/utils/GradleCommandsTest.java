/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.logging.LogRecord;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.quarkus.test.LogCollectingTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.ResourceArg;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
@QuarkusTestResource(
        value = LogCollectingTestResource.class,
        restrictToAnnotatedClass = true,
        initArgs = @ResourceArg(name = LogCollectingTestResource.LEVEL, value = "FINE"))
class GradleCommandsTest {

    private final Path GRADLE_PROJECTS_PATH = Path.of("src/test/resources/projects/gradle");

    @Inject
    GradleCommands gradleCommands;

    @Test
    void getName_hardcodedName_computesCorrectName() {
        assertThat(gradleCommands.getName(GRADLE_PROJECTS_PATH.resolve("hardcoded-version")))
                .isEqualTo("hardcoded-version");
    }

    @Test
    void getGroup_hardcodedGroup_computesCorrectGroup() {
        assertThat(gradleCommands.getGroup(GRADLE_PROJECTS_PATH.resolve("hardcoded-version")))
                .isEqualTo("org.example.hardcoded");
    }

    @Test
    void getGroup_noGroupSpecified_returnsEmptyString() {
        assertThat(gradleCommands.getGroup(GRADLE_PROJECTS_PATH.resolve("no-version-specified")))
                .isEmpty();
    }

    @Test
    void getVersion_hardcodedVersion_computesCorrectVersion() {
        assertThat(gradleCommands.getVersion(GRADLE_PROJECTS_PATH.resolve("hardcoded-version")))
                .isEqualTo("1.0.42");
    }

    @Test
    void getVersion_noVersionSpecified_computesCorrectVersion() {
        assertThat(gradleCommands.getVersion(GRADLE_PROJECTS_PATH.resolve("no-version-specified")))
                .isEqualTo("unspecified");
        List<LogRecord> logRecords = LogCollectingTestResource.current().getRecords();
        assertTrue(
                logRecords.stream()
                        .anyMatch(
                                r -> LogCollectingTestResource.format(r)
                                        .contains("No version for Gradle project could be found")));
    }

    @Test
    void getGroupAndName_hardcodedGroupAndName_computesCorrectCombination() {
        Path projectPath = GRADLE_PROJECTS_PATH.resolve("hardcoded-version");
        String group = gradleCommands.getGroup(projectPath);
        String name = gradleCommands.getName(projectPath);

        assertThat(group + ":" + name).isEqualTo("org.example.hardcoded:hardcoded-version");
    }

    @Test
    void getGroupAndName_noGroupSpecified_computesNameOnly() {
        Path projectPath = GRADLE_PROJECTS_PATH.resolve("no-version-specified");
        String group = gradleCommands.getGroup(projectPath);
        String name = gradleCommands.getName(projectPath);

        // When group is empty, the combination should just be ":name"
        assertThat(group + ":" + name).isEqualTo(":no-version-specified");
    }
}