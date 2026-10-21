/*
 * SPDX-FileCopyrightText: Copyright © 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jboss.pnc.reqour.adjust.AdjustTestUtils.assertSystemPropertiesContainExactly;
import static org.jboss.pnc.reqour.adjust.AdjustTestUtils.assertSystemPropertyHasValuesSortedByPriority;
import static org.jboss.pnc.reqour.adjust.common.TestDataFactory.TEST_BUILD_CATEGORY;
import static org.jboss.pnc.reqour.common.TestDataSupplier.TASK_ID;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;

import org.assertj.core.data.MapEntry;
import org.jboss.pnc.api.constants.BuildConfigurationParameterKeys;
import org.jboss.pnc.api.dto.Request;
import org.jboss.pnc.api.enums.AlignmentPreference;
import org.jboss.pnc.api.enums.BuildType;
import org.jboss.pnc.api.reqour.dto.AdjustRequest;
import org.jboss.pnc.api.reqour.dto.InternalGitRepositoryUrl;
import org.jboss.pnc.api.reqour.dto.ManipulatorResult;
import org.jboss.pnc.api.reqour.dto.VersioningState;
import org.jboss.pnc.reqour.adjust.common.TestDataFactory;
import org.jboss.pnc.reqour.adjust.config.ReqourAdjusterConfig;
import org.jboss.pnc.reqour.adjust.service.CommonManipulatorResultExtractor;
import org.jboss.pnc.reqour.adjust.utils.ScriptPrefetcher;
import org.jboss.pnc.reqour.common.utils.IOUtils;
import org.jboss.pnc.reqour.config.ReqourCoreConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class AntProviderTest {

    @Inject
    ReqourAdjusterConfig config;

    @Inject
    ReqourCoreConfig coreConfig;

    @Inject
    CommonManipulatorResultExtractor resultExtractor;

    @Inject
    ScriptPrefetcher scriptPrefetcher;

    static Path workdir;

    @BeforeEach
    void setUp() {
        workdir = IOUtils.createTempRandomDirForAdjust();
    }

    @AfterEach
    void afterEach() throws IOException {
        IOUtils.deleteTempDir(workdir);
    }

    @Test
    void computeAlignmentParametersOverrides_standardPersistentRequest_overridesCorrectly() {
        AntProvider provider = new AntProvider(
                config.alignment(),
                coreConfig.envs(),
                TestDataFactory.STANDARD_PERSISTENT_REQUEST,
                workdir,
                null,
                null,
                TestDataFactory.userLogger,
                scriptPrefetcher,
                null,
                config.manipulatorAuth());
        List<String> expectedOverrides = List
                .of("-DrestMode=PERSISTENT", "-DversionIncrementalSuffix=pnc", "-DrestBrewPullActive=true");

        List<String> actualOverrides = provider.computeAlignmentParametersOverrides();

        assertThat(actualOverrides).isEqualTo(expectedOverrides);
    }

    @Test
    void prepareCommand_servicePersistentBuild_generatedCommandIsCorrect() {
        AntProvider provider = new AntProvider(
                config.alignment(),
                coreConfig.envs(),
                exampleAdjustRequest(),
                workdir,
                null,
                null,
                TestDataFactory.userLogger,
                scriptPrefetcher,
                null,
                config.manipulatorAuth());

        List<String> command = provider.getPreparedCommand();

        assertThat(command).containsSequence(
                List.of(
                        "/usr/lib/jvm/java-11-openjdk/bin/java",
                        "-jar",
                        config.alignment().antProviderConfig().cliJarPath().toString()));
        assertSystemPropertiesContainExactly(
                command,
                Map.ofEntries(
                        MapEntry.entry("override", 3),
                        MapEntry.entry("restMode", 1),
                        MapEntry.entry("versionIncrementalSuffix", 1),
                        MapEntry.entry("restBrewPullActive", 1),
                        MapEntry.entry("additionalAlignmentParam", 2)));
        assertSystemPropertyHasValuesSortedByPriority(command, "override", List.of("default", "user", "config"));
        assertSystemPropertyHasValuesSortedByPriority(command, "restMode", List.of("TEST"));
        assertSystemPropertyHasValuesSortedByPriority(command, "versionIncrementalSuffix", List.of("test-pnc"));
        assertSystemPropertyHasValuesSortedByPriority(command, "restBrewPullActive", List.of("true"));
        assertSystemPropertyHasValuesSortedByPriority(
                command,
                "additionalAlignmentParam",
                List.of("overridable", "non-overridable"));
    }

    @Test
    void obtainManipulatorResult_versioningRead() throws IOException {
        Files.writeString(
                workdir.resolve("alignmentReport.json"),
                """
                                {
                                  "executionRoot": {
                                    "groupId": "com.example",
                                    "artifactId": "foo",
                                    "version": "1.0.0.rebuild-00042",
                                    "originalGAV": "com.example:foo:1.0.0"
                                  },
                                  "modules": []
                                }
                        """);
        AntProvider provider = new AntProvider(
                config.alignment(),
                coreConfig.envs(),
                TestDataFactory.STANDARD_PERSISTENT_REQUEST,
                workdir,
                null,
                null,
                TestDataFactory.userLogger,
                scriptPrefetcher,
                resultExtractor,
                config.manipulatorAuth());
        VersioningState expectedVersioningState = VersioningState.builder()
                .executionRootName("com.example:foo")
                .executionRootVersion("1.0.0.rebuild-00042")
                .build();

        ManipulatorResult manipulatorResult = provider.obtainManipulatorResult();

        assertThat(manipulatorResult.getVersioningState()).isEqualTo(expectedVersioningState);
        assertThat(manipulatorResult.getRemovedRepositories()).isEmpty();
    }

    private static AdjustRequest exampleAdjustRequest() {
        return AdjustRequest.builder()
                .ref("main")
                .callback(
                        Request.builder()
                                .method(Request.Method.POST)
                                .uri(URI.create("https://example.com/callback"))
                                .build())
                .sync(true)
                .originRepoUrl("git@gitlab.com:repo/project.git")
                .buildConfigParameters(
                        Map.of(
                                BuildConfigurationParameterKeys.ALIGNMENT_PARAMETERS,
                                "-Doverride=user",
                                BuildConfigurationParameterKeys.BUILD_CATEGORY,
                                TEST_BUILD_CATEGORY))
                .tempBuild(false)
                .alignmentPreference(AlignmentPreference.PREFER_PERSISTENT)
                .taskId(TASK_ID)
                .buildType(BuildType.ANT)
                .pncDefaultAlignmentParameters("-Doverride=default")
                .brewPullActive(true)
                .internalUrl(
                        InternalGitRepositoryUrl.builder()
                                .readwriteUrl("git@gitlab.com:test-workspace/repo/project.git")
                                .readonlyUrl("https://gitlab.com/test-workspace/repo/project.git")
                                .build())
                .build();
    }
}
