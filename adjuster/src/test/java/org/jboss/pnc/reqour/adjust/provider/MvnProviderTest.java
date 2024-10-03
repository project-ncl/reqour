/**
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.provider;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.assertj.core.data.MapEntry;
import org.jboss.pnc.reqour.adjust.config.ReqourAdjusterConfig;
import org.jboss.pnc.reqour.common.utils.IOUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jboss.pnc.reqour.adjust.provider.TestUtils.assertSystemPropertiesContainExactly;
import static org.jboss.pnc.reqour.adjust.provider.TestUtils.assertSystemPropertyHasValuesSortedByPriority;

@QuarkusTest
public class MvnProviderTest {

    @Inject
    ReqourAdjusterConfig config;

    @Inject
    TestUtils testUtils;

    static Path workdir;

    @BeforeAll
    static void beforeAll() {
        workdir = IOUtils.createTempDirForAdjust();
    }

    @AfterAll
    static void afterAll() throws IOException {
        IOUtils.deleteTempDir(workdir);
    }

    @Test
    void prepareCommand_servicePersistentBuildWithPersistentPreference_generatedCommandIsCorrect() {
        MvnProvider provider = new MvnProvider(
                config.adjust(),
                testUtils.getAdjustRequest(Path.of("mvn-request.json")),
                workdir);

        List<String> command = provider.preparedCommand;

        assertThat(command).containsSequence(
                List.of(
                        "java",
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
                testUtils.getAdjustRequest(Path.of("mvn-request-2.json")),
                workdir);

        List<String> command = provider.preparedCommand;

        assertThat(command).containsSequence(
                List.of(
                        "java",
                        "-jar",
                        config.adjust().mvnProviderConfig().cliJarPath().toString(),
                        "-s",
                        config.adjust().mvnProviderConfig().temporarySettingsFilePath().toString()));
        assertSystemPropertiesContainExactly(
                command,
                Map.ofEntries(
                        MapEntry.entry("override", 3),
                        MapEntry.entry("defaultAlignmentParam", 1),
                        MapEntry.entry("sameKeyInDefaultAndUserParams", 2),
                        MapEntry.entry("userSpecifiedAlignmentParam", 1),
                        MapEntry.entry("configAlignmentParam", 1),
                        MapEntry.entry("restURL", 2),
                        MapEntry.entry("restMode", 1),
                        MapEntry.entry("versionIncrementalSuffix", 2),
                        MapEntry.entry("restBrewPullActive", 1)));
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
}
