/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.assertj.core.data.MapEntry;
import org.jboss.pnc.api.reqour.dto.VersioningState;
import org.jboss.pnc.reqour.adjust.AdjustTestUtils;
import org.jboss.pnc.reqour.adjust.config.ReqourAdjusterConfig;
import org.jboss.pnc.reqour.common.profile.NpmAdjustProfile;
import org.jboss.pnc.reqour.common.utils.IOUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jboss.pnc.reqour.adjust.AdjustTestUtils.assertSystemPropertiesContainExactly;
import static org.jboss.pnc.reqour.adjust.AdjustTestUtils.assertSystemPropertyHasValuesSortedByPriority;

@QuarkusTest
@TestProfile(NpmAdjustProfile.class)
class NpmProviderTest {

    @Inject
    ReqourAdjusterConfig config;

    @Inject
    AdjustTestUtils adjustTestUtils;

    @Inject
    ObjectMapper objectMapper;

    static Path workdir;

    private final Path NPM_MANIPULATOR_RESULT_DIR = Path.of("src/test/resources/providers/npm");

    @BeforeAll
    static void beforeAll() {
        workdir = IOUtils.createTempDirForAdjust();
    }

    @AfterAll
    static void afterAll() throws IOException {
        IOUtils.deleteTempDir(workdir);
    }

    @Test
    void prepareCommand_standardTemporaryBuildWithPersistentPreference_generatedCommandIsCorrect() {
        NpmProvider provider = new NpmProvider(
                config.adjust(),
                adjustTestUtils.getAdjustRequest(Path.of("npm-request.json")),
                workdir,
                null,
                null,
                null);

        List<String> command = provider.prepareCommand();

        assertThat(command).containsSequence(
                List.of(
                        "/usr/lib/jvm/java-11-openjdk/bin/java",
                        "-jar",
                        config.adjust().npmProviderConfig().cliJarPath().toString()));
        assertSystemPropertiesContainExactly(
                command,
                Map.ofEntries(
                        MapEntry.entry("override", 3),
                        MapEntry.entry("restMode", 1),
                        MapEntry.entry("versionIncrementalSuffix", 3)));
        assertSystemPropertyHasValuesSortedByPriority(command, "override", List.of("default", "user", "config"));
        assertSystemPropertyHasValuesSortedByPriority(command, "restMode", List.of("TEMPORARY_PREFER_PERSISTENT"));
        assertSystemPropertyHasValuesSortedByPriority(
                command,
                "versionIncrementalSuffix",
                List.of("default", "user", "temporary-user"));
    }

    @Test
    void obtainManipulatorResult_resultWithBasicName_correctlyParsesResult() {
        NpmProvider provider = new NpmProvider(
                config.adjust(),
                adjustTestUtils.getAdjustRequest(Path.of("npm-request.json")),
                workdir,
                objectMapper,
                null,
                null);

        VersioningState expectedVersioningState = VersioningState.builder()
                .executionRootName("artifactId-npm")
                .executionRootVersion("0.1.0-rh-00042")
                .build();

        VersioningState actualVersioningState = provider
                .obtainVersioningState(NPM_MANIPULATOR_RESULT_DIR.resolve("result-with-basic-name.json"));

        assertThat(actualVersioningState).isEqualTo(expectedVersioningState);
    }

    @Test
    void obtainManipulatorResult_resultWithComplicatedName_correctlyParsesResult() {
        NpmProvider provider = new NpmProvider(
                config.adjust(),
                adjustTestUtils.getAdjustRequest(Path.of("npm-request.json")),
                workdir,
                objectMapper,
                null,
                null);

        VersioningState expectedVersioningState = VersioningState.builder()
                .executionRootName("redhat-artifactId-npm")
                .executionRootVersion("0.1.0-rh-00042")
                .build();

        VersioningState actualVersioningState = provider
                .obtainVersioningState(NPM_MANIPULATOR_RESULT_DIR.resolve("result-with-complicated-name.json"));

        assertThat(actualVersioningState).isEqualTo(expectedVersioningState);
    }
}