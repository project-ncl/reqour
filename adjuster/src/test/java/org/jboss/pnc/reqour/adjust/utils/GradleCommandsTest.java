/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;

import jakarta.inject.Inject;

import org.jboss.pnc.reqour.adjust.exception.AdjusterException;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
@Disabled
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
    void getVersion_hardcodedVersion_computesCorrectVersion() {
        assertThat(gradleCommands.getVersion(GRADLE_PROJECTS_PATH.resolve("hardcoded-version")))
                .isEqualTo("1.0.42");
    }

    @Test
    void getVersion_noVersionSpecified_computesCorrectVersion() {
        assertThatThrownBy(() -> gradleCommands.getVersion(GRADLE_PROJECTS_PATH.resolve("no-version-specified")))
                .isInstanceOf(AdjusterException.class)
                .hasMessage("No version for Gradle project couldn't be found");
    }
}