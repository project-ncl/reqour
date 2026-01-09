/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jboss.pnc.reqour.adjust.AdjustTestUtils.assertSystemPropertiesContainExactly;
import static org.jboss.pnc.reqour.adjust.AdjustTestUtils.assertSystemPropertyHasValuesSortedByPriority;
import static org.jboss.pnc.reqour.adjust.common.TestDataFactory.STANDARD_BUILD_CATEGORY;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;

import org.assertj.core.data.MapEntry;
import org.jboss.pnc.api.constants.BuildConfigurationParameterKeys;
import org.jboss.pnc.api.reqour.dto.AdjustRequest;
import org.jboss.pnc.api.reqour.dto.VersioningState;
import org.jboss.pnc.reqour.adjust.AdjustTestUtils;
import org.jboss.pnc.reqour.adjust.common.TestDataFactory;
import org.jboss.pnc.reqour.adjust.config.ReqourAdjusterConfig;
import org.jboss.pnc.reqour.adjust.utils.AdjustmentSystemPropertiesUtils;
import org.jboss.pnc.reqour.common.utils.IOUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
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
        workdir = IOUtils.createTempRandomDirForAdjust();
    }

    @AfterAll
    static void afterAll() throws IOException {
        IOUtils.deleteTempDir(workdir);
    }

    @Test
    void computeAlignmentParametersOverrides_standardPersistentRequest_overridesCorrectly() {
        NpmProvider provider = new NpmProvider(
                config.alignment(),
                TestDataFactory.STANDARD_PERSISTENT_REQUEST,
                workdir,
                null,
                null,
                TestDataFactory.userLogger);
        List<String> expectedOverrides = List.of("-DrestMode=PERSISTENT");

        List<String> actualOverrides = provider.computeAlignmentParametersOverrides();

        assertThat(actualOverrides).isEqualTo(expectedOverrides);
    }

    @Test
    void computeAlignmentParametersOverrides_standardPersistentRequestWithUserVersionSuffixOverride_overridesCorrectly() {
        NpmProvider provider = new NpmProvider(
                config.alignment(),
                AdjustRequest.builder()
                        .buildConfigParameters(
                                Map.of(
                                        BuildConfigurationParameterKeys.BUILD_CATEGORY,
                                        STANDARD_BUILD_CATEGORY,
                                        BuildConfigurationParameterKeys.ALIGNMENT_PARAMETERS,
                                        AdjustmentSystemPropertiesUtils.createAdjustmentSystemProperty(
                                                AdjustmentSystemPropertiesUtils.AdjustmentSystemPropertyName.VERSION_INCREMENTAL_SUFFIX,
                                                "")))
                        .tempBuild(false)
                        .build(),
                workdir,
                null,
                null,
                TestDataFactory.userLogger);
        List<String> expectedOverrides = List.of("-DrestMode=PERSISTENT");

        List<String> actualOverrides = provider.computeAlignmentParametersOverrides();

        assertThat(actualOverrides).isEqualTo(expectedOverrides);
    }

    @Test
    void computeAlignmentParametersOverrides_standardTemporaryRequest_overridesCorrectly() {
        NpmProvider provider = new NpmProvider(
                config.alignment(),
                TestDataFactory.STANDARD_TEMPORARY_REQUEST,
                workdir,
                null,
                null,
                TestDataFactory.userLogger);
        List<String> expectedOverrides = List
                .of("-DrestMode=TEMPORARY", "-DversionIncrementalSuffix=temporary-config");

        List<String> actualOverrides = provider.computeAlignmentParametersOverrides();

        assertThat(actualOverrides).isEqualTo(expectedOverrides);
    }

    @Test
    void computeAlignmentParametersOverrides_standardTemporaryRequestWithUserVersionSuffixOverride_overridesCorrectly() {
        NpmProvider provider = new NpmProvider(
                config.alignment(),
                AdjustRequest.builder()
                        .buildConfigParameters(
                                Map.of(
                                        BuildConfigurationParameterKeys.BUILD_CATEGORY,
                                        STANDARD_BUILD_CATEGORY,
                                        BuildConfigurationParameterKeys.ALIGNMENT_PARAMETERS,
                                        AdjustmentSystemPropertiesUtils.createAdjustmentSystemProperty(
                                                AdjustmentSystemPropertiesUtils.AdjustmentSystemPropertyName.VERSION_INCREMENTAL_SUFFIX,
                                                "")))
                        .tempBuild(true)
                        .build(),
                workdir,
                null,
                null,
                TestDataFactory.userLogger);
        List<String> expectedOverrides = List
                .of("-DrestMode=TEMPORARY", "-DversionIncrementalSuffix=temporary");

        List<String> actualOverrides = provider.computeAlignmentParametersOverrides();

        assertThat(actualOverrides).isEqualTo(expectedOverrides);
    }

    @Test
    void computeAlignmentParametersOverrides_servicePersistentRequest_overridesCorrectly() {
        NpmProvider provider = new NpmProvider(
                config.alignment(),
                TestDataFactory.SERVICE_PERSISTENT_REQUEST,
                workdir,
                null,
                null,
                TestDataFactory.userLogger);
        List<String> expectedOverrides = List
                .of("-DrestMode=SERVICE", "-DversionIncrementalSuffix=managedsvc-config");

        List<String> actualOverrides = provider.computeAlignmentParametersOverrides();

        assertThat(actualOverrides).isEqualTo(expectedOverrides);
    }

    @Test
    void computeAlignmentParametersOverrides_serviceTemporaryRequest_overridesCorrectly() {
        NpmProvider provider = new NpmProvider(
                config.alignment(),
                TestDataFactory.SERVICE_TEMPORARY_REQUEST,
                workdir,
                null,
                null,
                TestDataFactory.userLogger);
        List<String> expectedOverrides = List.of(
                "-DrestMode=SERVICE_TEMPORARY",
                "-DversionIncrementalSuffix=managedsvc-temporary-config");

        List<String> actualOverrides = provider.computeAlignmentParametersOverrides();

        assertThat(actualOverrides).isEqualTo(expectedOverrides);
    }

    @Test
    void prepareCommand_standardTemporaryBuildWithPersistentPreference_generatedCommandIsCorrect() {
        NpmProvider provider = new NpmProvider(
                config.alignment(),
                adjustTestUtils.getAdjustRequest(Path.of("npm-request.json")),
                workdir,
                null,
                null,
                TestDataFactory.userLogger);

        List<String> command = provider.prepareCommand();

        assertThat(command).containsSequence(
                List.of(
                        "/usr/lib/jvm/java-11-openjdk/bin/java",
                        "-jar",
                        config.alignment().npmProviderConfig().cliJarPath().toString()));
        assertSystemPropertiesContainExactly(
                command,
                Map.ofEntries(
                        MapEntry.entry("override", 3),
                        MapEntry.entry("restMode", 1),
                        MapEntry.entry("versionIncrementalSuffix", 4)));
        assertSystemPropertyHasValuesSortedByPriority(command, "override", List.of("default", "user", "config"));
        assertSystemPropertyHasValuesSortedByPriority(command, "restMode", List.of("TEMPORARY_PREFER_PERSISTENT"));
        assertSystemPropertyHasValuesSortedByPriority(
                command,
                "versionIncrementalSuffix",
                List.of("default", "user", "config", "temporary-user"));
    }

    @Test
    void prepareCommand_standardTemporaryBuildWithTemporaryPreference_generatedCommandIsCorrect() {
        NpmProvider provider = new NpmProvider(
                config.alignment(),
                adjustTestUtils.getAdjustRequest(Path.of("npm-request-2.json")),
                workdir,
                null,
                null,
                TestDataFactory.userLogger);

        List<String> command = provider.prepareCommand();

        assertThat(command).containsSequence(
                List.of(
                        "/usr/lib/jvm/java-11-openjdk/bin/java",
                        "-jar",
                        config.alignment().npmProviderConfig().cliJarPath().toString()));
        assertSystemPropertiesContainExactly(
                command,
                Map.ofEntries(
                        MapEntry.entry("override", 1),
                        MapEntry.entry("restMode", 1),
                        MapEntry.entry("versionIncrementalSuffix", 4)));
        assertSystemPropertyHasValuesSortedByPriority(command, "override", List.of("config"));
        assertSystemPropertyHasValuesSortedByPriority(command, "restMode", List.of("TEMPORARY"));
        assertSystemPropertyHasValuesSortedByPriority(
                command,
                "versionIncrementalSuffix",
                List.of("default", "", "config", "temporary"));
    }

    @Test
    void obtainManipulatorResult_resultWithBasicName_correctlyParsesResult() {
        NpmProvider provider = new NpmProvider(
                config.alignment(),
                adjustTestUtils.getAdjustRequest(Path.of("npm-request.json")),
                workdir,
                objectMapper,
                null,
                TestDataFactory.userLogger);

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
                config.alignment(),
                adjustTestUtils.getAdjustRequest(Path.of("npm-request.json")),
                workdir,
                objectMapper,
                null,
                TestDataFactory.userLogger);

        VersioningState expectedVersioningState = VersioningState.builder()
                .executionRootName("redhat-artifactId-npm")
                .executionRootVersion("0.1.0-rh-00042")
                .build();

        VersioningState actualVersioningState = provider
                .obtainVersioningState(NPM_MANIPULATOR_RESULT_DIR.resolve("result-with-complicated-name.json"));

        assertThat(actualVersioningState).isEqualTo(expectedVersioningState);
    }
}