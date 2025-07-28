/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jboss.pnc.reqour.adjust.AdjustTestUtils.assertSystemPropertiesContainExactly;
import static org.jboss.pnc.reqour.adjust.AdjustTestUtils.assertSystemPropertyHasValuesSortedByPriority;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;

import org.assertj.core.data.MapEntry;
import org.jboss.pnc.reqour.adjust.AdjustTestUtils;
import org.jboss.pnc.reqour.adjust.common.TestDataFactory;
import org.jboss.pnc.reqour.adjust.config.ReqourAdjusterConfig;
import org.jboss.pnc.reqour.common.profile.GradleAdjustProfile;
import org.jboss.pnc.reqour.common.utils.IOUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@TestProfile(GradleAdjustProfile.class)
class GradleProviderTest {

    @Inject
    ReqourAdjusterConfig config;

    @Inject
    AdjustTestUtils adjustTestUtils;

    static Path workdir;

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
        GradleProvider provider = new GradleProvider(
                config.adjust(),
                TestDataFactory.STANDARD_PERSISTENT_REQUEST,
                workdir,
                null,
                null,
                null,
                TestDataFactory.userLogger);
        List<String> expectedOverrides = List.of("-DrestMode=PERSISTENT", "-DrestBrewPullActive=true");

        List<String> actualOverrides = provider.computeAlignmentParametersOverrides();

        assertThat(actualOverrides).isEqualTo(expectedOverrides);
    }

    @Test
    void computeAlignmentParametersOverrides_standardTemporaryRequest_overridesCorrectly() {
        GradleProvider provider = new GradleProvider(
                config.adjust(),
                TestDataFactory.STANDARD_TEMPORARY_REQUEST,
                workdir,
                null,
                null,
                null,
                TestDataFactory.userLogger);
        List<String> expectedOverrides = List.of(
                "-DrestMode=TEMPORARY",
                "-DversionIncrementalSuffix=temporary-redhat",
                "-DrestBrewPullActive=false");

        List<String> actualOverrides = provider.computeAlignmentParametersOverrides();

        assertThat(actualOverrides).isEqualTo(expectedOverrides);
    }

    @Test
    void computeAlignmentParametersOverrides_servicePersistentRequest_overridesCorrectly() {
        GradleProvider provider = new GradleProvider(
                config.adjust(),
                TestDataFactory.SERVICE_PERSISTENT_REQUEST,
                workdir,
                null,
                null,
                null,
                TestDataFactory.userLogger);
        List<String> expectedOverrides = List
                .of("-DrestMode=SERVICE", "-DversionIncrementalSuffix=managedsvc-redhat", "-DrestBrewPullActive=true");

        List<String> actualOverrides = provider.computeAlignmentParametersOverrides();

        assertThat(actualOverrides).isEqualTo(expectedOverrides);
    }

    @Test
    void computeAlignmentParametersOverrides_serviceTemporaryRequest_overridesCorrectly() {
        GradleProvider provider = new GradleProvider(
                config.adjust(),
                TestDataFactory.SERVICE_TEMPORARY_REQUEST,
                workdir,
                null,
                null,
                null,
                TestDataFactory.userLogger);
        List<String> expectedOverrides = List.of(
                "-DrestMode=SERVICE_TEMPORARY",
                "-DversionIncrementalSuffix=managedsvc-temporary-redhat",
                "-DrestBrewPullActive=false");

        List<String> actualOverrides = provider.computeAlignmentParametersOverrides();

        assertThat(actualOverrides).isEqualTo(expectedOverrides);
    }

    @Test
    void prepareCommand_standardPersistentBuildWithPersistentPreference_generatedCommandIsCorrect() {
        GradleProvider provider = new GradleProvider(
                config.adjust(),
                adjustTestUtils.getAdjustRequest(Path.of("gradle-request.json")),
                workdir,
                null,
                null,
                null,
                TestDataFactory.userLogger);

        List<String> command = provider.prepareCommand();

        assertThat(command).containsSequence(
                List.of(
                        "/usr/lib/jvm/java-11-openjdk/bin/java",
                        "-jar",
                        config.adjust().gradleProviderConfig().cliJarPath().toString(),
                        "--target",
                        workdir.toString(),
                        "--init-script",
                        config.adjust().gradleProviderConfig().gradleAnalyzerPluginInitFilePath().toString(),
                        "-l",
                        config.adjust().gradleProviderConfig().defaultGradlePath().toString()));
        assertSystemPropertiesContainExactly(
                command,
                Map.ofEntries(
                        MapEntry.entry("override", 3),
                        MapEntry.entry("restMode", 1),
                        MapEntry.entry("restBrewPullActive", 1)));
        assertSystemPropertyHasValuesSortedByPriority(command, "override", List.of("default", "user", "config"));
        assertSystemPropertyHasValuesSortedByPriority(command, "restMode", List.of("PERSISTENT"));
        assertSystemPropertyHasValuesSortedByPriority(command, "restBrewPullActive", List.of("false"));
    }

    @Test
    void prepareCommand_servicePersistentBuildWithTemporaryPreference_generatedCommandIsCorrect() {
        GradleProvider provider = new GradleProvider(
                config.adjust(),
                adjustTestUtils.getAdjustRequest(Path.of("gradle-request-2.json")),
                workdir,
                null,
                null,
                null,
                TestDataFactory.userLogger);

        List<String> command = provider.prepareCommand();

        assertThat(command).containsSequence(
                List.of(
                        "/usr/lib/jvm/java-11-openjdk/bin/java",
                        "-jar",
                        config.adjust().gradleProviderConfig().cliJarPath().toString(),
                        "--target",
                        workdir.toString(),
                        "--init-script",
                        config.adjust().gradleProviderConfig().gradleAnalyzerPluginInitFilePath().toString(),
                        "-l",
                        config.adjust().gradleProviderConfig().defaultGradlePath().toString()));
        assertSystemPropertiesContainExactly(
                command,
                Map.ofEntries(
                        MapEntry.entry("override", 3),
                        MapEntry.entry("restMode", 1),
                        MapEntry.entry("restBrewPullActive", 1),
                        MapEntry.entry("versionIncrementalSuffix", 2)));
        assertSystemPropertyHasValuesSortedByPriority(command, "override", List.of("default", "user", "config"));
        assertSystemPropertyHasValuesSortedByPriority(command, "restMode", List.of("SERVICE"));
        assertSystemPropertyHasValuesSortedByPriority(command, "restBrewPullActive", List.of("true"));
        assertSystemPropertyHasValuesSortedByPriority(
                command,
                "versionIncrementalSuffix",
                List.of("user", "managedsvc-redhat"));
    }

    @Test
    void prepareCommand_standardBuildWithTargetOption_targetOptionAdded() throws IOException {
        Files.createDirectory(workdir.resolve("gradle-directory")); // gradle target directory checked for existence

        GradleProvider provider = new GradleProvider(
                config.adjust(),
                adjustTestUtils.getAdjustRequest(Path.of("gradle-request-with-target-directory.json")),
                workdir,
                null,
                null,
                null,
                TestDataFactory.userLogger);

        List<String> command = provider.prepareCommand();

        assertThat(command).containsSequence(
                List.of(
                        "/usr/lib/jvm/java-11-openjdk/bin/java",
                        "-jar",
                        config.adjust().gradleProviderConfig().cliJarPath().toString(),
                        "--target",
                        workdir.resolve("gradle-directory").toString(),
                        "--init-script",
                        config.adjust().gradleProviderConfig().gradleAnalyzerPluginInitFilePath().toString(),
                        "-l",
                        config.adjust().gradleProviderConfig().defaultGradlePath().toString()));
        assertSystemPropertiesContainExactly(
                command,
                Map.ofEntries(
                        MapEntry.entry("override", 3),
                        MapEntry.entry("restMode", 1),
                        MapEntry.entry("restBrewPullActive", 1)));
        assertSystemPropertyHasValuesSortedByPriority(command, "override", List.of("default", "user", "config"));
        assertSystemPropertyHasValuesSortedByPriority(command, "restMode", List.of("PERSISTENT"));
        assertSystemPropertyHasValuesSortedByPriority(command, "restBrewPullActive", List.of("false"));

        Files.deleteIfExists(workdir.resolve("gradle-directory")); // gradle target directory checked for existence
    }
}
