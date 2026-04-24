/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.jboss.pnc.reqour.adjust.AdjustTestUtils.assertSystemPropertiesContainExactly;
import static org.jboss.pnc.reqour.adjust.AdjustTestUtils.assertSystemPropertyHasValuesSortedByPriority;
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
import org.jboss.pnc.api.reqour.dto.RemovedRepository;
import org.jboss.pnc.api.reqour.dto.VersioningState;
import org.jboss.pnc.reqour.adjust.AdjustTestUtils;
import org.jboss.pnc.reqour.adjust.common.TestDataFactory;
import org.jboss.pnc.reqour.adjust.config.ReqourAdjusterConfig;
import org.jboss.pnc.reqour.adjust.exception.AdjusterException;
import org.jboss.pnc.reqour.adjust.model.GradleAlignmentResultFile;
import org.jboss.pnc.reqour.adjust.service.CommonManipulatorResultExtractor;
import org.jboss.pnc.reqour.common.exceptions.ResourceNotFoundException;
import org.jboss.pnc.reqour.common.utils.IOUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class GradleProviderTest {

    @Inject
    ReqourAdjusterConfig config;

    @Inject
    AdjustTestUtils adjustTestUtils;

    @Inject
    CommonManipulatorResultExtractor resultExtractor;

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
    void obtainManipulatorResult_gmeDisabledUserProvidedNoFile_exceptionThrown() {
        GradleProvider provider = new GradleProvider(
                config.alignment(),
                TestDataFactory.MANIPULATOR_DISABLED_REQUEST,
                workdir,
                null,
                null,
                resultExtractor,
                TestDataFactory.userLogger);

        assertThatThrownBy(provider::obtainManipulatorResult).isInstanceOf(AdjusterException.class)
                .hasMessageContaining("no user-provided file");
    }

    @Test
    void obtainManipulatorResult_gmeDisabledUserProvidedOnlyVersioningFile_versioningReadButRemovedRepositoriesEmpty()
            throws IOException {
        Files.copy(
                Path.of(adjustTestUtils.getGradleProviderResourcesLocation())
                        .resolve(GradleAlignmentResultFile.GME_DISABLED.getGmeAlignmentResultFile()),
                workdir.resolve(GradleAlignmentResultFile.GME_DISABLED.getGmeAlignmentResultFile()));

        GradleProvider provider = new GradleProvider(
                config.alignment(),
                TestDataFactory.MANIPULATOR_DISABLED_REQUEST,
                workdir,
                null,
                null,
                resultExtractor,
                TestDataFactory.userLogger);
        VersioningState expectedVersioningState = VersioningState.builder()
                .executionRootName("foo")
                .executionRootVersion("1.0.42.Final-rebuild-00001")
                .build();

        ManipulatorResult manipulatorResult = provider.obtainManipulatorResult();

        assertThat(manipulatorResult.getVersioningState()).isEqualTo(expectedVersioningState);
        assertThat(manipulatorResult.getRemovedRepositories()).isEmpty();
    }

    @Test
    void obtainManipulatorResult_gmeEnabledUserProvidedOnlyVersioningFile_versioningReadButRemovedRepositoriesEmpty()
            throws IOException {

        Path build = Path.of(workdir.toString(), "build");
        Files.createDirectory(build);
        Files.writeString(
                Path.of(
                        workdir.toString(),
                        GradleAlignmentResultFile.GME_ENABLED.getGmeAlignmentResultFile().toString()),
                """
                          {
                          "executionRoot" : {
                            "groupId" : "com.github.fge",
                            "artifactId" : "btf",
                            "version" : "1.2.0.redhat-00020",
                            "originalGAV" : "com.github.fge:btf:1.2"
                          }
                        }""");

        GradleProvider provider = new GradleProvider(
                config.alignment(),
                TestDataFactory.STANDARD_PERSISTENT_REQUEST,
                workdir,
                null,
                null,
                resultExtractor,
                TestDataFactory.userLogger);
        VersioningState expectedVersioningState = VersioningState.builder()
                .executionRootName("com.github.fge:btf")
                .executionRootVersion("1.2.0.redhat-00020")
                .build();

        ManipulatorResult manipulatorResult = provider.obtainManipulatorResult();

        assertThat(manipulatorResult.getVersioningState()).isEqualTo(expectedVersioningState);
        assertThat(manipulatorResult.getRemovedRepositories()).isEmpty();
    }

    @Test
    void obtainManipulatorResult_gmeEnabledAlignmentResultAtNonStandardLocation_alignmentResultsFileFound()
            throws IOException {

        Path target = Path.of(workdir.toString(), "target");
        Files.createDirectory(target);
        Files.writeString(
                target.resolve(GradleAlignmentResultFile.Constants.ALIGNMENT_REPORT_JSON),
                """
                          {
                          "executionRoot" : {
                            "groupId" : "com.github.fge",
                            "artifactId" : "btf",
                            "version" : "1.2.0.redhat-00020",
                            "originalGAV" : "com.github.fge:btf:1.2"
                          }
                        }""");

        GradleProvider provider = new GradleProvider(
                config.alignment(),
                TestDataFactory.STANDARD_PERSISTENT_REQUEST,
                workdir,
                null,
                null,
                resultExtractor,
                TestDataFactory.userLogger);
        VersioningState expectedVersioningState = VersioningState.builder()
                .executionRootName("com.github.fge:btf")
                .executionRootVersion("1.2.0.redhat-00020")
                .build();

        ManipulatorResult manipulatorResult = provider.obtainManipulatorResult();

        assertThat(manipulatorResult.getVersioningState()).isEqualTo(expectedVersioningState);
        assertThat(manipulatorResult.getRemovedRepositories()).isEmpty();
    }

    @Test
    void obtainManipulatorResult_gmeEnabledAlignmentResultNotFound_resourceNotFoundThrown()
            throws IOException {

        Path weirdBuildDir = Path.of(workdir.toString(), "foo", "bar", "baz");
        Files.createDirectories(weirdBuildDir);
        Files.writeString(
                weirdBuildDir.resolve(GradleAlignmentResultFile.Constants.ALIGNMENT_REPORT_JSON),
                """
                          {
                          "executionRoot" : {
                            "groupId" : "com.github.fge",
                            "artifactId" : "btf",
                            "version" : "1.2.0.redhat-00020",
                            "originalGAV" : "com.github.fge:btf:1.2"
                          }
                        }""");

        GradleProvider provider = new GradleProvider(
                config.alignment(),
                TestDataFactory.STANDARD_PERSISTENT_REQUEST,
                workdir,
                null,
                null,
                resultExtractor,
                TestDataFactory.userLogger);

        assertThatThrownBy(provider::obtainManipulatorResult).isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("No file with alignment results found");
    }

    @Test
    void obtainManipulatorResult_gmeDisabledUserProvidedBothVersioningAndRemovedRepositoriesFiles_bothFilesRead()
            throws IOException {
        Files.copy(
                Path.of(adjustTestUtils.getGradleProviderResourcesLocation())
                        .resolve(GradleAlignmentResultFile.GME_DISABLED.getGmeAlignmentResultFile()),
                workdir.resolve(GradleAlignmentResultFile.GME_DISABLED.getGmeAlignmentResultFile()));
        Files.copy(
                Path.of(adjustTestUtils.getGradleProviderResourcesLocation())
                        .resolve(CommonManipulatorResultExtractor.DEFAULT_REMOVED_REPOSITORIES_FILENAME),
                workdir.resolve(CommonManipulatorResultExtractor.DEFAULT_REMOVED_REPOSITORIES_FILENAME));

        GradleProvider provider = new GradleProvider(
                config.alignment(),
                TestDataFactory.MANIPULATOR_DISABLED_REQUEST,
                workdir,
                null,
                null,
                resultExtractor,
                TestDataFactory.userLogger);
        VersioningState expectedVersioningState = VersioningState.builder()
                .executionRootName("foo")
                .executionRootVersion("1.0.42.Final-rebuild-00001")
                .build();
        List<RemovedRepository> expectedRemovedRepositories = List.of(
                RemovedRepository.builder()
                        .id("foo")
                        .name("foo")
                        .url("https://example.com")
                        .releases(true)
                        .snapshots(true)
                        .build());

        ManipulatorResult manipulatorResult = provider.obtainManipulatorResult();

        assertThat(manipulatorResult.getVersioningState()).isEqualTo(expectedVersioningState);
        assertThat(manipulatorResult.getRemovedRepositories()).isEqualTo(expectedRemovedRepositories);
    }

    @Test
    void computeAlignmentParametersOverrides_standardPersistentRequest_overridesCorrectly() {
        GradleProvider provider = new GradleProvider(
                config.alignment(),
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
                config.alignment(),
                TestDataFactory.STANDARD_TEMPORARY_REQUEST,
                workdir,
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
        GradleProvider provider = new GradleProvider(
                config.alignment(),
                TestDataFactory.TEST_PERSISTENT_REQUEST,
                workdir,
                null,
                null,
                null,
                TestDataFactory.userLogger);
        List<String> expectedOverrides = List
                .of("-DrestMode=TEST", "-DversionIncrementalSuffix=test-pnc", "-DrestBrewPullActive=true");

        List<String> actualOverrides = provider.computeAlignmentParametersOverrides();

        assertThat(actualOverrides).isEqualTo(expectedOverrides);
    }

    @Test
    void computeAlignmentParametersOverrides_serviceTemporaryRequest_overridesCorrectly() {
        GradleProvider provider = new GradleProvider(
                config.alignment(),
                TestDataFactory.TEST_TEMPORARY_REQUEST,
                workdir,
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
    void prepareCommand_standardPersistentBuildWithPersistentPreference_generatedCommandIsCorrect() {
        AdjustRequest adjustRequest = AdjustRequest.builder()
                .ref("master")
                .callback(
                        Request.builder()
                                .method(Request.Method.POST)
                                .uri(URI.create("https://example.com/callback"))
                                .build())
                .sync(true)
                .originRepoUrl("https://github.com/project/repo")
                .buildConfigParameters(Map.of(BuildConfigurationParameterKeys.ALIGNMENT_PARAMETERS, "-Doverride=user"))
                .tempBuild(false)
                .alignmentPreference(AlignmentPreference.PREFER_PERSISTENT)
                .taskId(TASK_ID)
                .buildType(BuildType.GRADLE)
                .pncDefaultAlignmentParameters("-Doverride=default")
                .internalUrl(
                        InternalGitRepositoryUrl.builder()
                                .readwriteUrl("git@gitlab.com:test-workspace/repo/project.git")
                                .readonlyUrl("https://gitlab.com/test-workspace/repo/project.git")
                                .build())
                .build();
        GradleProvider provider = new GradleProvider(
                config.alignment(),
                adjustRequest,
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
                        config.alignment().gradleProviderConfig().cliJarPath().toString(),
                        "--target",
                        workdir.toString(),
                        "--init-script",
                        config.alignment().gradleProviderConfig().gradleAnalyzerPluginInitFilePath().toString(),
                        "-l",
                        config.alignment().gradleProviderConfig().defaultGradlePath().toString()));
        assertSystemPropertiesContainExactly(
                command,
                Map.ofEntries(
                        MapEntry.entry("override", 3),
                        MapEntry.entry("restMode", 1),
                        MapEntry.entry("restBrewPullActive", 1)));
        assertSystemPropertyHasValuesSortedByPriority(command, "override", List.of("default", "user", "config"));
        assertSystemPropertyHasValuesSortedByPriority(
                command,
                "restMode",
                List.of("PERSISTENT"));
        assertSystemPropertyHasValuesSortedByPriority(command, "restBrewPullActive", List.of("false"));
    }

    @Test
    void prepareCommand_testPersistentBuildWithTemporaryPreference_generatedCommandIsCorrect() {
        AdjustRequest adjustRequest = AdjustRequest.builder()
                .ref("master")
                .callback(
                        Request.builder()
                                .method(Request.Method.POST)
                                .uri(URI.create("https://example.com/callback"))
                                .build())
                .sync(true)
                .originRepoUrl("https://github.com/project/repo")
                .buildConfigParameters(
                        Map.of(
                                BuildConfigurationParameterKeys.ALIGNMENT_PARAMETERS,
                                "-Doverride=user -DversionIncrementalSuffix=user",
                                BuildConfigurationParameterKeys.BUILD_CATEGORY,
                                TestDataFactory.TEST_BUILD_CATEGORY))
                .tempBuild(false)
                .alignmentPreference(AlignmentPreference.PREFER_TEMPORARY)
                .taskId(TASK_ID)
                .buildType(BuildType.GRADLE)
                .pncDefaultAlignmentParameters("-Doverride=default")
                .brewPullActive(true)
                .internalUrl(
                        InternalGitRepositoryUrl.builder()
                                .readwriteUrl("git@gitlab.com:test-workspace/repo/project.git")
                                .readonlyUrl("https://gitlab.com/test-workspace/repo/project.git")
                                .build())
                .build();
        GradleProvider provider = new GradleProvider(
                config.alignment(),
                adjustRequest,
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
                        config.alignment().gradleProviderConfig().cliJarPath().toString(),
                        "--target",
                        workdir.toString(),
                        "--init-script",
                        config.alignment().gradleProviderConfig().gradleAnalyzerPluginInitFilePath().toString(),
                        "-l",
                        config.alignment().gradleProviderConfig().defaultGradlePath().toString()));
        assertSystemPropertiesContainExactly(
                command,
                Map.ofEntries(
                        MapEntry.entry("override", 3),
                        MapEntry.entry("restMode", 1),
                        MapEntry.entry("restBrewPullActive", 1),
                        MapEntry.entry("versionIncrementalSuffix", 2)));
        assertSystemPropertyHasValuesSortedByPriority(command, "override", List.of("default", "user", "config"));
        assertSystemPropertyHasValuesSortedByPriority(command, "restMode", List.of("TEST"));
        assertSystemPropertyHasValuesSortedByPriority(command, "restBrewPullActive", List.of("true"));
        assertSystemPropertyHasValuesSortedByPriority(
                command,
                "versionIncrementalSuffix",
                List.of("user", "test-pnc"));
    }

    @Test
    void prepareCommand_standardBuildWithTargetOption_targetOptionAdded() throws IOException {
        Files.createDirectory(workdir.resolve("gradle-directory")); // gradle target directory checked for existence

        AdjustRequest adjustRequest = AdjustRequest.builder()
                .ref("master")
                .callback(
                        Request.builder()
                                .method(Request.Method.POST)
                                .uri(URI.create("https://example.com/callback"))
                                .build())
                .sync(true)
                .originRepoUrl("https://github.com/project/repo")
                .buildConfigParameters(
                        Map.of(
                                BuildConfigurationParameterKeys.ALIGNMENT_PARAMETERS,
                                "-Doverride=user -t=gradle-directory"))
                .tempBuild(false)
                .alignmentPreference(AlignmentPreference.PREFER_PERSISTENT)
                .taskId(TASK_ID)
                .buildType(BuildType.GRADLE)
                .pncDefaultAlignmentParameters("-Doverride=default")
                .brewPullActive(false)
                .internalUrl(
                        InternalGitRepositoryUrl.builder()
                                .readwriteUrl("git@gitlab.com:test-workspace/repo/project.git")
                                .readonlyUrl("https://gitlab.com/test-workspace/repo/project.git")
                                .build())
                .build();
        GradleProvider provider = new GradleProvider(
                config.alignment(),
                adjustRequest,
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
                        config.alignment().gradleProviderConfig().cliJarPath().toString(),
                        "--target",
                        workdir.resolve("gradle-directory").toString(),
                        "--init-script",
                        config.alignment().gradleProviderConfig().gradleAnalyzerPluginInitFilePath().toString(),
                        "-l",
                        config.alignment().gradleProviderConfig().defaultGradlePath().toString()));
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
