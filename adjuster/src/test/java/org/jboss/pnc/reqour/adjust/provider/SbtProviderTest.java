/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jboss.pnc.reqour.adjust.AdjustTestUtils.assertSystemPropertiesContainExactly;
import static org.jboss.pnc.reqour.adjust.AdjustTestUtils.assertSystemPropertyHasValuesSortedByPriority;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;

import org.assertj.core.data.MapEntry;
import org.jboss.pnc.reqour.adjust.AdjustTestUtils;
import org.jboss.pnc.reqour.adjust.common.TestDataFactory;
import org.jboss.pnc.reqour.adjust.config.ReqourAdjusterConfig;
import org.jboss.pnc.reqour.common.TestDataSupplier;
import org.jboss.pnc.reqour.common.utils.IOUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class SbtProviderTest {

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
        SbtProvider provider = new SbtProvider(
                config.alignment(),
                TestDataFactory.STANDARD_PERSISTENT_REQUEST,
                workdir,
                null,
                null,
                TestDataFactory.userLogger);
        List<String> expectedOverrides = List.of("-DrestMode=PERSISTENT", "-DrestBrewPullActive=true");

        List<String> actualOverrides = provider.computeAlignmentParametersOverrides();

        assertThat(actualOverrides).isEqualTo(expectedOverrides);
    }

    @Test
    void computeAlignmentParametersOverrides_standardTemporaryRequest_overridesCorrectly() {
        SbtProvider provider = new SbtProvider(
                config.alignment(),
                TestDataFactory.STANDARD_TEMPORARY_REQUEST,
                workdir,
                null,
                null,
                TestDataFactory.userLogger);
        List<String> expectedOverrides = List.of(
                "-DrestMode=TEMPORARY",
                "-DversionIncrementalSuffix=temporary-pnc",
                "-DrestBrewPullActive=false");

        List<String> actualOverrides = provider.computeAlignmentParametersOverrides();

        assertThat(actualOverrides).isEqualTo(expectedOverrides);
    }

    @Test
    void computeAlignmentParametersOverrides_servicePersistentRequest_overridesCorrectly() {
        SbtProvider provider = new SbtProvider(
                config.alignment(),
                TestDataFactory.SERVICE_PERSISTENT_REQUEST,
                workdir,
                null,
                null,
                TestDataFactory.userLogger);
        List<String> expectedOverrides = List
                .of("-DrestMode=SERVICE", "-DversionIncrementalSuffix=managedsvc-pnc", "-DrestBrewPullActive=true");

        List<String> actualOverrides = provider.computeAlignmentParametersOverrides();

        assertThat(actualOverrides).isEqualTo(expectedOverrides);
    }

    @Test
    void computeAlignmentParametersOverrides_serviceTemporaryRequest_overridesCorrectly() {
        SbtProvider provider = new SbtProvider(
                config.alignment(),
                TestDataFactory.SERVICE_TEMPORARY_REQUEST,
                workdir,
                null,
                null,
                TestDataFactory.userLogger);
        List<String> expectedOverrides = List.of(
                "-DrestMode=SERVICE_TEMPORARY",
                "-DversionIncrementalSuffix=managedsvc-temporary-pnc",
                "-DrestBrewPullActive=false");

        List<String> actualOverrides = provider.computeAlignmentParametersOverrides();

        assertThat(actualOverrides).isEqualTo(expectedOverrides);
    }

    @Test
    void prepareCommand_standardTemporaryBuildWithPersistentAlignmentPreference_generatedCommandIsCorrect() {
        SbtProvider provider = new SbtProvider(
                config.alignment(),
                adjustTestUtils.getAdjustRequest(Path.of("sbt-request.json")),
                workdir,
                null,
                null,
                TestDataFactory.userLogger);

        List<String> command = provider.prepareCommand();

        assertThat(command).containsSequence(List.of(config.alignment().scalaProviderConfig().sbtPath().toString()));
        assertSystemPropertiesContainExactly(
                command,
                Map.ofEntries(
                        MapEntry.entry("override", 3),
                        MapEntry.entry("restMode", 1),
                        MapEntry.entry("versionIncrementalSuffix", 1),
                        MapEntry.entry("restBrewPullActive", 1)));
        assertSystemPropertyHasValuesSortedByPriority(command, "override", List.of("default", "user", "config"));
        assertSystemPropertyHasValuesSortedByPriority(command, "restMode", List.of("TEMPORARY_PREFER_PERSISTENT"));
        assertSystemPropertyHasValuesSortedByPriority(
                command,
                "versionIncrementalSuffix",
                List.of(
                        TestDataSupplier.Alignment.TEMPORARY_PREFIX_OF_VERSION_SUFFIX + "-"
                                + TestDataSupplier.Alignment.PERMANENT_SUFFIX));
        assertSystemPropertyHasValuesSortedByPriority(command, "restBrewPullActive", List.of("false"));
    }
}