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
class GradleProviderTest {

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
    void prepareCommand_standardPersistentBuildWithPersistentPreference_generatedCommandIsCorrect() {
        GradleProvider provider = new GradleProvider(
                config.adjust(),
                testUtils.getAdjustRequest(Path.of("gradle-request.json")),
                workdir);

        List<String> command = provider.preparedCommand;

        assertThat(command).containsSequence(
                List.of(
                        "java",
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
                testUtils.getAdjustRequest(Path.of("gradle-request-2.json")),
                workdir);

        List<String> command = provider.preparedCommand;

        assertThat(command).containsSequence(
                List.of(
                        "java",
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
}
