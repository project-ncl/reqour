/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.jboss.pnc.reqour.adjust.AdjustTestUtils.assertSystemPropertiesContainExactly;
import static org.jboss.pnc.reqour.adjust.AdjustTestUtils.assertSystemPropertyHasValuesSortedByPriority;
import static org.jboss.pnc.reqour.adjust.common.TestDataFactory.MANIPULATOR_DISABLED_REQUEST;
import static org.jboss.pnc.reqour.adjust.common.TestDataFactory.STANDARD_BUILD_CATEGORY;
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
import org.jboss.pnc.reqour.adjust.AdjustTestUtils;
import org.jboss.pnc.reqour.adjust.common.TestDataFactory;
import org.jboss.pnc.reqour.adjust.config.ReqourAdjusterConfig;
import org.jboss.pnc.reqour.adjust.exception.AdjusterException;
import org.jboss.pnc.reqour.adjust.service.CommonManipulatorResultExtractor;
import org.jboss.pnc.reqour.adjust.service.RootGavExtractor;
import org.jboss.pnc.reqour.adjust.utils.AdjustmentSystemPropertiesUtils;
import org.jboss.pnc.reqour.common.executor.process.ProcessExecutor;
import org.jboss.pnc.reqour.common.utils.IOUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class MvnProviderTest {

    @Inject
    ReqourAdjusterConfig config;

    @Inject
    AdjustTestUtils adjustTestUtils;

    @InjectMock
    ProcessExecutor processExecutor;

    @Inject
    CommonManipulatorResultExtractor resultExtractor;

    @Inject
    RootGavExtractor rootGavExtractor;

    static Path workdir;
    @Inject
    ObjectMapper objectMapper;

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
        MvnProvider provider = new MvnProvider(
                config.alignment(),
                TestDataFactory.STANDARD_PERSISTENT_REQUEST,
                workdir,
                null,
                null,
                null,
                null,
                TestDataFactory.userLogger);
        List<String> expectedOverrides = List
                .of("-DrestMode=PERSISTENT", "-DversionIncrementalSuffix=pnc", "-DrestBrewPullActive=true");

        List<String> actualOverrides = provider.computeAlignmentParametersOverrides();

        assertThat(actualOverrides).isEqualTo(expectedOverrides);
    }

    @Test
    void computeAlignmentParametersOverrides_standardTemporaryRequest_overridesCorrectly() {
        MvnProvider provider = new MvnProvider(
                config.alignment(),
                TestDataFactory.STANDARD_TEMPORARY_REQUEST,
                workdir,
                null,
                null,
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
        MvnProvider provider = new MvnProvider(
                config.alignment(),
                TestDataFactory.TEST_PERSISTENT_REQUEST,
                workdir,
                null,
                null,
                null,
                null,
                TestDataFactory.userLogger);
        List<String> expectedOverrides = List
                .of(
                        "-DrestMode=TEST",
                        "-DversionIncrementalSuffix=test-pnc",
                        "-DrestBrewPullActive=true");

        List<String> actualOverrides = provider.computeAlignmentParametersOverrides();

        assertThat(actualOverrides).isEqualTo(expectedOverrides);
    }

    @Test
    void computeAlignmentParametersOverrides_serviceTemporaryRequest_overridesCorrectly() {
        MvnProvider provider = new MvnProvider(
                config.alignment(),
                TestDataFactory.TEST_TEMPORARY_REQUEST,
                workdir,
                null,
                null,
                null,
                null,
                TestDataFactory.userLogger);
        List<String> expectedOverrides = List.of(
                "-DrestMode=TEST_TEMPORARY",
                "-DversionIncrementalSuffix=test-temporary-pnc",
                "-DrestBrewPullActive=false");

        List<String> actualOverrides = provider.computeAlignmentParametersOverrides();

        assertThat(actualOverrides).isEqualTo(expectedOverrides);
    }

    @Test
    void prepareCommand_servicePersistentBuildWithPersistentPreference_generatedCommandIsCorrect() {
        MvnProvider provider = new MvnProvider(
                config.alignment(),
                exampleAdjustRequest(),
                workdir,
                null,
                null,
                null,
                null,
                TestDataFactory.userLogger);

        List<String> command = provider.getPreparedCommand();

        assertThat(command).containsSequence(
                List.of(
                        "/usr/lib/jvm/java-11-openjdk/bin/java",
                        "-jar",
                        config.alignment().mvnProviderConfig().cliJarPath().toString(),
                        "-s",
                        config.alignment().mvnProviderConfig().defaultSettingsFilePath().toString()));
        assertSystemPropertiesContainExactly(
                command,
                Map.ofEntries(
                        MapEntry.entry("override", 3),
                        MapEntry.entry("configAlignmentParam", 1),
                        MapEntry.entry("restURL", 1),
                        MapEntry.entry("restMode", 1),
                        MapEntry.entry("versionIncrementalSuffix", 1),
                        MapEntry.entry("versionSuffixAlternatives", 1),
                        MapEntry.entry("restBrewPullActive", 1),
                        MapEntry.entry("additionalAlignmentParam", 2)));
        assertSystemPropertyHasValuesSortedByPriority(command, "override", List.of("default", "user", "config"));
        assertSystemPropertyHasValuesSortedByPriority(command, "configAlignmentParam", List.of("foo"));
        assertSystemPropertyHasValuesSortedByPriority(command, "restURL", List.of("https://da.com/rest/v-1"));
        assertSystemPropertyHasValuesSortedByPriority(command, "restMode", List.of("TEST"));
        assertSystemPropertyHasValuesSortedByPriority(
                command,
                "versionIncrementalSuffix",
                List.of("test-pnc"));
        assertSystemPropertyHasValuesSortedByPriority(
                command,
                "versionSuffixAlternatives",
                List.of("pnc,test-pnc"));
        assertSystemPropertyHasValuesSortedByPriority(command, "restBrewPullActive", List.of("true"));
        assertSystemPropertyHasValuesSortedByPriority(
                command,
                "additionalAlignmentParam",
                List.of("overridable", "non-overridable"));
    }

    @Test
    void prepareCommand_standardTemporaryBuildWithTemporaryPreference_generatedCommandIsCorrect() {
        AdjustRequest adjustRequest = AdjustRequest.builder()
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
                                "-DuserSpecifiedAlignmentParam=foo -DRepour_Java=17 -DsameKeyInDefaultAndUserParams=user -DrestURL=https://user-specified.com/da/v1 -Doverride=user -DversionIncrementalSuffix=user"))
                .tempBuild(true)
                .alignmentPreference(AlignmentPreference.PREFER_TEMPORARY)
                .taskId(TASK_ID)
                .buildType(BuildType.MVN)
                .pncDefaultAlignmentParameters(
                        "-DdefaultAlignmentParam=foo -DsameKeyInDefaultAndUserParams=default -Doverride=default")
                .brewPullActive(false)
                .internalUrl(
                        InternalGitRepositoryUrl.builder()
                                .readwriteUrl("git@gitlab.com:test-workspace/repo/project.git")
                                .readonlyUrl("https://gitlab.com/test-workspace/repo/project.git")
                                .build())
                .build();
        MvnProvider provider = new MvnProvider(
                config.alignment(),
                adjustRequest,
                workdir,
                null,
                null,
                null,
                null,
                TestDataFactory.userLogger);

        List<String> command = provider.getPreparedCommand();

        assertThat(command).containsSequence(
                List.of(
                        "/usr/lib/jvm/java-17-openjdk/bin/java",
                        "-jar",
                        config.alignment().mvnProviderConfig().cliJarPath().toString(),
                        "-s",
                        config.alignment().mvnProviderConfig().temporarySettingsFilePath().toString()));
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
                List.of("user", "temporary-pnc"));
        assertSystemPropertyHasValuesSortedByPriority(command, "restBrewPullActive", List.of("false"));
    }

    @Test
    void prepareCommand_standardBuildWithFileOption_fileOptionAdded() throws IOException {
        Files.createDirectory(workdir.resolve("directory")); // pom file checked for existence
        Files.createFile(workdir.resolve(Path.of("directory/pom.xml"))); // pom file checked for existence

        AdjustRequest adjustRequest = AdjustRequest.builder()
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
                                "--file=directory/pom.xml"))
                .tempBuild(false)
                .alignmentPreference(AlignmentPreference.PREFER_PERSISTENT)
                .taskId(TASK_ID)
                .buildType(BuildType.MVN)
                .pncDefaultAlignmentParameters("-Doverride=default")
                .internalUrl(
                        InternalGitRepositoryUrl.builder()
                                .readwriteUrl("git@gitlab.com:test-workspace/repo/project.git")
                                .readonlyUrl("https://gitlab.com/test-workspace/repo/project.git")
                                .build())
                .build();
        MvnProvider provider = new MvnProvider(
                config.alignment(),
                adjustRequest,
                workdir,
                null,
                null,
                null,
                null,
                TestDataFactory.userLogger);

        List<String> command = provider.getPreparedCommand();

        assertThat(command).containsSequence(
                List.of(
                        "/usr/lib/jvm/java-11-openjdk/bin/java",
                        "-jar",
                        config.alignment().mvnProviderConfig().cliJarPath().toString(),
                        "-s",
                        config.alignment().mvnProviderConfig().defaultSettingsFilePath().toString()));
        assertThat(command).containsSequence("--file=directory/pom.xml");
        assertSystemPropertiesContainExactly(
                command,
                Map.ofEntries(
                        MapEntry.entry("override", 2),
                        MapEntry.entry("configAlignmentParam", 1),
                        MapEntry.entry("restURL", 1),
                        MapEntry.entry("restMode", 1),
                        MapEntry.entry("versionIncrementalSuffix", 1),
                        MapEntry.entry("restBrewPullActive", 1)));
        assertSystemPropertyHasValuesSortedByPriority(command, "override", List.of("default", "config"));
        assertSystemPropertyHasValuesSortedByPriority(command, "configAlignmentParam", List.of("foo"));
        assertSystemPropertyHasValuesSortedByPriority(command, "restURL", List.of("https://da.com/rest/v-1"));
        assertSystemPropertyHasValuesSortedByPriority(command, "restMode", List.of("PERSISTENT"));
        assertSystemPropertyHasValuesSortedByPriority(
                command,
                "versionIncrementalSuffix",
                List.of("pnc"));
        assertSystemPropertyHasValuesSortedByPriority(command, "restBrewPullActive", List.of("false"));

        Files.deleteIfExists(workdir.resolve(Path.of("directory/pom.xml"))); // pom file checked for existence
        Files.deleteIfExists(workdir.resolve("directory")); // pom file checked for existence
    }

    @Test
    void prepareCommand_overridesPrecedence_isSortedCorrectly() {
        AdjustRequest adjustRequest = AdjustRequest.builder()
                .ref("main")
                .callback(
                        Request.builder()
                                .method(Request.Method.POST)
                                .uri(URI.create("https://example.com/callback"))
                                .build())
                .originRepoUrl("git@gitlab.com:repo/project.git")
                .buildConfigParameters(
                        Map.of(
                                BuildConfigurationParameterKeys.BUILD_CATEGORY,
                                TEST_BUILD_CATEGORY,
                                BuildConfigurationParameterKeys.ALIGNMENT_PARAMETERS,
                                "-DadditionalAlignmentParam=user"))
                .taskId(TASK_ID)
                .buildType(BuildType.MVN)
                .internalUrl(
                        InternalGitRepositoryUrl.builder()
                                .readwriteUrl("git@gitlab.com:test-workspace/repo/project.git")
                                .readonlyUrl("https://gitlab.com/test-workspace/repo/project.git")
                                .build())
                .build();
        MvnProvider provider = new MvnProvider(
                config.alignment(),
                adjustRequest,
                workdir,
                null,
                null,
                null,
                null,
                TestDataFactory.userLogger);

        List<String> command = provider.getPreparedCommand();

        assertSystemPropertyHasValuesSortedByPriority(
                command,
                "additionalAlignmentParam",
                List.of("overridable", "user", "non-overridable"));
    }

    @Test
    void obtainManipulatorResult_pmeEnabled_versioningRead() throws IOException {
        Path targetDir = workdir.resolve("target");
        Files.createDirectory(targetDir);
        Files.writeString(
                targetDir.resolve("alignmentReport.json"),
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
        MvnProvider provider = new MvnProvider(
                config.alignment(),
                TestDataFactory.STANDARD_PERSISTENT_REQUEST,
                workdir,
                null,
                null,
                resultExtractor,
                null,
                TestDataFactory.userLogger);
        VersioningState expectedVersioningState = VersioningState.builder()
                .executionRootName("com.example:foo")
                .executionRootVersion("1.0.0.rebuild-00042")
                .build();

        ManipulatorResult manipulatorResult = provider.obtainManipulatorResult();

        assertThat(manipulatorResult.getVersioningState()).isEqualTo(expectedVersioningState);
        assertThat(manipulatorResult.getRemovedRepositories()).isEmpty();
    }

    @Test
    void obtainManipulatorResult_pmeEnabledAndVersionOverridden_specifiedVersionIgnored() throws IOException {
        // in case PME is enabled, but we use version override, it's expected to have no effect at all
        Path targetDir = workdir.resolve("target");
        Files.createDirectory(targetDir);
        Files.writeString(
                targetDir.resolve("alignmentReport.json"),
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
        MvnProvider provider = new MvnProvider(
                config.alignment(),
                AdjustRequest.builder()
                        .buildConfigParameters(
                                Map.of(
                                        BuildConfigurationParameterKeys.BUILD_CATEGORY,
                                        STANDARD_BUILD_CATEGORY,
                                        BuildConfigurationParameterKeys.ALIGNMENT_PARAMETERS,
                                        AdjustmentSystemPropertiesUtils.createAdjustmentSystemProperty(
                                                AdjustmentSystemPropertiesUtils.AdjustmentSystemPropertyName.VERSION_OVERRIDE,
                                                "overridden-version")))
                        .tempBuild(false)
                        .brewPullActive(true)
                        .build(),
                workdir,
                null,
                null,
                resultExtractor,
                null,
                TestDataFactory.userLogger);
        VersioningState expectedVersioningState = VersioningState.builder()
                .executionRootName("com.example:foo")
                .executionRootVersion("1.0.0.rebuild-00042")
                .build();

        ManipulatorResult manipulatorResult = provider.obtainManipulatorResult();

        assertThat(manipulatorResult.getVersioningState()).isEqualTo(expectedVersioningState);
        assertThat(manipulatorResult.getRemovedRepositories()).isEmpty();
    }

    @Test
    void obtainManipulatorResult_pmeDisabled_versioningStateComputedFromPom() throws IOException {
        Files.writeString(
                workdir.resolve("pom.xml"),
                """
                            <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                              xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
                              <modelVersion>4.0.0</modelVersion>

                              <groupId>com.example</groupId>
                              <artifactId>foo</artifactId>
                              <version>1.0.0</version>
                            </project>
                        """);
        MvnProvider provider = new MvnProvider(
                config.alignment(),
                MANIPULATOR_DISABLED_REQUEST,
                workdir,
                objectMapper,
                null,
                resultExtractor,
                rootGavExtractor,
                TestDataFactory.userLogger);
        VersioningState expectedVersioningState = VersioningState.builder()
                .executionRootName("com.example:foo")
                .executionRootVersion("1.0.0")
                .build();

        ManipulatorResult manipulatorResult = provider.obtainManipulatorResult();

        assertThat(manipulatorResult.getVersioningState()).isEqualTo(expectedVersioningState);
        assertThat(manipulatorResult.getRemovedRepositories()).isEmpty();
    }

    @Test
    void obtainManipulatorResult_pmeDisabledAndVersionOverrideProvided_resultCombined() throws IOException {
        Files.writeString(
                workdir.resolve("pom.xml"),
                """
                            <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                              xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
                              <modelVersion>4.0.0</modelVersion>

                              <groupId>com.example</groupId>
                              <artifactId>foo</artifactId>
                              <version>1.0.0-SNAPSHOT</version>
                            </project>
                        """);
        final String overriddenVersion = "1.0.0";
        MvnProvider provider = new MvnProvider(
                config.alignment(),
                AdjustRequest.builder()
                        .buildConfigParameters(
                                Map.of(
                                        BuildConfigurationParameterKeys.ALIGNMENT_PARAMETERS,
                                        String.format(
                                                "%s %s",
                                                AdjustmentSystemPropertiesUtils.createAdjustmentSystemProperty(
                                                        AdjustmentSystemPropertiesUtils.AdjustmentSystemPropertyName.MANIPULATION_DISABLE,
                                                        "true"),
                                                AdjustmentSystemPropertiesUtils.createAdjustmentSystemProperty(
                                                        AdjustmentSystemPropertiesUtils.AdjustmentSystemPropertyName.VERSION_OVERRIDE,
                                                        overriddenVersion))))
                        .tempBuild(false)
                        .brewPullActive(true)
                        .build(),
                workdir,
                objectMapper,
                null,
                resultExtractor,
                rootGavExtractor,
                TestDataFactory.userLogger);
        VersioningState expectedVersioningState = VersioningState.builder()
                .executionRootName("com.example:foo")
                .executionRootVersion(overriddenVersion)
                .build();

        ManipulatorResult manipulatorResult = provider.obtainManipulatorResult();

        assertThat(manipulatorResult.getVersioningState()).isEqualTo(expectedVersioningState);
        assertThat(manipulatorResult.getRemovedRepositories()).isEmpty();
    }

    @Test
    void adjust_manipulatorReturnsNonZeroExitCode_adjusterExceptionIsThrown() {
        Mockito.when(processExecutor.execute(Mockito.any())).thenReturn(1);
        AdjustRequest adjustRequest = exampleAdjustRequest();
        MvnProvider provider = new MvnProvider(
                config.alignment(),
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
                .buildType(BuildType.MVN)
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
