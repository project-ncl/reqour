/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.jboss.pnc.reqour.adjust.AdjustTestUtils.assertSystemPropertiesContainExactly;
import static org.jboss.pnc.reqour.adjust.AdjustTestUtils.assertSystemPropertyHasValuesSortedByPriority;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;

import org.assertj.core.data.MapEntry;
import org.jboss.pnc.api.reqour.dto.AdjustRequest;
import org.jboss.pnc.reqour.adjust.AdjustTestUtils;
import org.jboss.pnc.reqour.adjust.common.TestDataFactory;
import org.jboss.pnc.reqour.adjust.config.ReqourAdjusterConfig;
import org.jboss.pnc.reqour.adjust.exception.AdjusterException;
import org.jboss.pnc.reqour.common.executor.process.ProcessExecutor;
import org.jboss.pnc.reqour.common.profile.MvnAdjustProfile;
import org.jboss.pnc.reqour.common.utils.IOUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@TestProfile(MvnAdjustProfile.class)
public class MvnProviderTest {

    @Inject
    ReqourAdjusterConfig config;

    @Inject
    AdjustTestUtils adjustTestUtils;

    @InjectMock
    ProcessExecutor processExecutor;

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
        MvnProvider provider = new MvnProvider(
                config.adjust(),
                TestDataFactory.STANDARD_PERSISTENT_REQUEST,
                workdir,
                null,
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
        MvnProvider provider = new MvnProvider(
                config.adjust(),
                TestDataFactory.STANDARD_TEMPORARY_REQUEST,
                workdir,
                null,
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
        MvnProvider provider = new MvnProvider(
                config.adjust(),
                TestDataFactory.SERVICE_PERSISTENT_REQUEST,
                workdir,
                null,
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
        MvnProvider provider = new MvnProvider(
                config.adjust(),
                TestDataFactory.SERVICE_TEMPORARY_REQUEST,
                workdir,
                null,
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
    void prepareCommand_servicePersistentBuildWithPersistentPreference_generatedCommandIsCorrect() {
        MvnProvider provider = new MvnProvider(
                config.adjust(),
                adjustTestUtils.getAdjustRequest(Path.of("mvn-request.json")),
                workdir,
                null,
                null,
                null,
                null,
                TestDataFactory.userLogger);

        List<String> command = provider.prepareCommand();

        assertThat(command).containsSequence(
                List.of(
                        "/usr/lib/jvm/java-11-openjdk/bin/java",
                        "-jar",
                        config.adjust().mvnProviderConfig().cliJarPath().toString(),
                        "-s",
                        config.adjust().mvnProviderConfig().defaultSettingsFilePath().toString()));
        assertSystemPropertiesContainExactly(
                command,
                Map.ofEntries(
                        MapEntry.entry("override", 3),
                        MapEntry.entry("configAlignmentParam", 1),
                        MapEntry.entry("restURL", 1),
                        MapEntry.entry("restMode", 1),
                        MapEntry.entry("versionIncrementalSuffix", 2),
                        MapEntry.entry("versionSuffixAlternatives", 1),
                        MapEntry.entry("restBrewPullActive", 1)));
        assertSystemPropertyHasValuesSortedByPriority(command, "override", List.of("default", "user", "config"));
        assertSystemPropertyHasValuesSortedByPriority(command, "configAlignmentParam", List.of("foo"));
        assertSystemPropertyHasValuesSortedByPriority(command, "restURL", List.of("https://da.com/rest/v-1"));
        assertSystemPropertyHasValuesSortedByPriority(command, "restMode", List.of("SERVICE"));
        assertSystemPropertyHasValuesSortedByPriority(
                command,
                "versionIncrementalSuffix",
                List.of("redhat", "managedsvc-redhat"));
        assertSystemPropertyHasValuesSortedByPriority(
                command,
                "versionSuffixAlternatives",
                List.of("redhat,managedsvc-redhat"));
        assertSystemPropertyHasValuesSortedByPriority(command, "restBrewPullActive", List.of("true"));
    }

    @Test
    void prepareCommand_standardTemporaryBuildWithTemporaryPreference_generatedCommandIsCorrect() {
        MvnProvider provider = new MvnProvider(
                config.adjust(),
                adjustTestUtils.getAdjustRequest(Path.of("mvn-request-2.json")),
                workdir,
                null,
                null,
                null,
                null,
                TestDataFactory.userLogger);

        List<String> command = provider.prepareCommand();

        assertThat(command).containsSequence(
                List.of(
                        "/usr/lib/jvm/java-17-openjdk/bin/java",
                        "-jar",
                        config.adjust().mvnProviderConfig().cliJarPath().toString(),
                        "-s",
                        config.adjust().mvnProviderConfig().temporarySettingsFilePath().toString()));
        assertSystemPropertiesContainExactly(
                command,
                Map.ofEntries(
                        MapEntry.entry("Repour_Java", 1),
                        MapEntry.entry("override", 3),
                        MapEntry.entry("defaultAlignmentParam", 1),
                        MapEntry.entry("sameKeyInDefaultAndUserParams", 2),
                        MapEntry.entry("userSpecifiedAlignmentParam", 1),
                        MapEntry.entry("configAlignmentParam", 1),
                        MapEntry.entry("restURL", 2),
                        MapEntry.entry("restMode", 1),
                        MapEntry.entry("versionIncrementalSuffix", 2),
                        MapEntry.entry("restBrewPullActive", 1)));
        assertSystemPropertyHasValuesSortedByPriority(command, "Repour_Java", List.of("17"));
        assertSystemPropertyHasValuesSortedByPriority(command, "defaultAlignmentParam", List.of("foo"));
        assertSystemPropertyHasValuesSortedByPriority(
                command,
                "sameKeyInDefaultAndUserParams",
                List.of("default", "user"));
        assertSystemPropertyHasValuesSortedByPriority(command, "override", List.of("default", "user", "config"));
        assertSystemPropertyHasValuesSortedByPriority(command, "userSpecifiedAlignmentParam", List.of("foo"));
        assertSystemPropertyHasValuesSortedByPriority(command, "configAlignmentParam", List.of("foo"));
        assertSystemPropertyHasValuesSortedByPriority(
                command,
                "restURL",
                List.of("https://user-specified.com/da/v1", "https://da.com/rest/v-1"));
        assertSystemPropertyHasValuesSortedByPriority(command, "restMode", List.of("TEMPORARY"));
        assertSystemPropertyHasValuesSortedByPriority(
                command,
                "versionIncrementalSuffix",
                List.of("redhat", "temporary-redhat"));
        assertSystemPropertyHasValuesSortedByPriority(command, "restBrewPullActive", List.of("false"));
    }

    @Test
    void adjust_manipulatorReturnsNonZeroExitCode_adjusterExceptionIsThrown() {
        Mockito.when(processExecutor.execute(Mockito.any())).thenReturn(1);
        AdjustRequest adjustRequest = adjustTestUtils.getAdjustRequest(Path.of("mvn-request.json"));
        MvnProvider provider = new MvnProvider(
                config.adjust(),
                adjustRequest,
                workdir,
                null,
                processExecutor,
                null,
                null,
                TestDataFactory.userLogger);

        assertThatThrownBy(() -> provider.adjust(adjustRequest)).isInstanceOf(AdjusterException.class)
                .hasMessage("Manipulator subprocess ended with non-zero exit code");
    }
}
