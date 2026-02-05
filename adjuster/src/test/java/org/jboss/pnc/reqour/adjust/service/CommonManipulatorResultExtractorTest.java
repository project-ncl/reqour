/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import jakarta.inject.Inject;

import org.jboss.pnc.api.reqour.dto.RemovedRepository;
import org.jboss.pnc.api.reqour.dto.VersioningState;
import org.jboss.pnc.reqour.adjust.model.ExecutionRootOverrides;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class CommonManipulatorResultExtractorTest {

    @Inject
    CommonManipulatorResultExtractor manipulatorResultExtractor;

    private final Path MANIPULATOR_RESULT_EXTRACTOR_TEST_DIR = Path
            .of("src/test/resources/service/manipulator-result-extractor");
    private final Path ALIGNMENT_RESULT_FILE = MANIPULATOR_RESULT_EXTRACTOR_TEST_DIR.resolve("alignmentResult.json");

    @Test
    void obtainVersioningState_FromManipulatorResult_noOverridesProvided_parsesResultWithoutOverrides() {
        VersioningState expectedVersioningState = VersioningState.builder()
                .executionRootName("com.example:foo")
                .executionRootVersion("1.0.42.rh-7")
                .build();

        VersioningState actualVersioningState = manipulatorResultExtractor
                .obtainVersioningStateFromManipulatorResult(
                        ALIGNMENT_RESULT_FILE,
                        ExecutionRootOverrides.noOverrides());

        assertThat(actualVersioningState).isEqualTo(expectedVersioningState);
    }

    @Test
    void obtainVersioningState_FromManipulatorResult_overridesProvided_parsesResultAndUsesOverrides() {
        VersioningState expectedVersioningState = VersioningState.builder()
                .executionRootName("org.foo.bar:baz")
                .executionRootVersion("1.0.42.rh-7")
                .build();
        ExecutionRootOverrides rootOverrides = new ExecutionRootOverrides("org.foo.bar", "baz");

        VersioningState actualVersioningState = manipulatorResultExtractor
                .obtainVersioningStateFromManipulatorResult(ALIGNMENT_RESULT_FILE, rootOverrides);

        assertThat(actualVersioningState).isEqualTo(expectedVersioningState);
    }

    @Test
    void obtainVersioningState_FromManipulatorResult_onlyGroupIdOverrideProvided_parsesResultAndUsesOverrides() {
        VersioningState expectedVersioningState = VersioningState.builder()
                .executionRootName("org.foo.bar:null")
                .executionRootVersion("1.0.42.rh-7")
                .build();
        ExecutionRootOverrides rootOverrides = new ExecutionRootOverrides("org.foo.bar", null);

        VersioningState actualVersioningState = manipulatorResultExtractor
                .obtainVersioningStateFromManipulatorResult(ALIGNMENT_RESULT_FILE, rootOverrides);

        assertThat(actualVersioningState).isEqualTo(expectedVersioningState);
    }

    @Test
    void obtainRemovedRepositories_repoRemovalBackupFileNotSpecified_returnsEmptyArray() {
        List<String> manipulatorParameters = Collections.emptyList();
        List<RemovedRepository> expectedRemovedRepositories = Collections.emptyList();

        List<RemovedRepository> actualRemovedRepositories = manipulatorResultExtractor
                .obtainRemovedRepositories(MANIPULATOR_RESULT_EXTRACTOR_TEST_DIR, manipulatorParameters);

        assertThat(actualRemovedRepositories).isEqualTo(expectedRemovedRepositories);
    }

    @Test
    void obtainRemovedRepositories_repoRemovalBackupFileNotExists_returnsEmptyArray() {
        List<String> manipulatorParameters = List.of(
                String.format(
                        "%s=%s",
                        CommonManipulatorResultExtractor.REMOVED_REPOSITORIES_KEY,
                        MANIPULATOR_RESULT_EXTRACTOR_TEST_DIR.resolve("non-existent-repo-backup.xml")));
        List<RemovedRepository> expectedRemovedRepositories = Collections.emptyList();

        List<RemovedRepository> actualRemovedRepositories = manipulatorResultExtractor
                .obtainRemovedRepositories(MANIPULATOR_RESULT_EXTRACTOR_TEST_DIR, manipulatorParameters);

        assertThat(actualRemovedRepositories).isEqualTo(expectedRemovedRepositories);
    }

    @Test
    void obtainRemovedRepositories_repoRemovalBackupPresent_parsesTheFileAndReturnsResult() {
        List<String> manipulatorParameters = List.of(
                String.format("%s=%s", CommonManipulatorResultExtractor.REMOVED_REPOSITORIES_KEY, "repos-backup.xml"));
        List<RemovedRepository> expectedRemovedRepositories = List.of(
                RemovedRepository.builder()
                        .releases(true)
                        .snapshots(true)
                        .id("MavenRepo-0")
                        .name("MavenRepo-0")
                        .url("https://repo.maven.apache.org/maven2/")
                        .build(),
                RemovedRepository.builder()
                        .releases(false)
                        .snapshots(true)
                        .id("MavenRepo-1")
                        .name("MavenRepo-1")
                        .url("file:///${project.basedir}/src/test/mavenRepo1")
                        .build());

        List<RemovedRepository> actualRemovedRepositories = manipulatorResultExtractor
                .obtainRemovedRepositories(MANIPULATOR_RESULT_EXTRACTOR_TEST_DIR, manipulatorParameters);

        assertThat(actualRemovedRepositories).isEqualTo(expectedRemovedRepositories);
    }

    @Test
    void getAlignmentResultsFilePath_severalFilesProvided_returnsFirstOneExisting() {
        Path actualAlignmentResultFilePath = CommonManipulatorResultExtractor.getAlignmentResultsFilePath(
                MANIPULATOR_RESULT_EXTRACTOR_TEST_DIR.resolve("non-existent-file.json"),
                ALIGNMENT_RESULT_FILE,
                MANIPULATOR_RESULT_EXTRACTOR_TEST_DIR.resolve("existing.json"));

        assertThat(actualAlignmentResultFilePath).isEqualTo(ALIGNMENT_RESULT_FILE);
    }
}