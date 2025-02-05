/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.pnc.api.reqour.dto.AdjustRequest;
import org.jboss.pnc.api.reqour.dto.ManipulatorResult;
import org.jboss.pnc.reqour.adjust.config.AdjustConfig;
import org.jboss.pnc.reqour.adjust.config.GradleProviderConfig;
import org.jboss.pnc.reqour.adjust.config.manipulator.GmeConfig;
import org.jboss.pnc.reqour.adjust.config.manipulator.common.CommonManipulatorConfigUtils;
import org.jboss.pnc.reqour.adjust.model.UserSpecifiedAlignmentParameters;
import org.jboss.pnc.reqour.adjust.service.AdjustmentPusher;
import org.jboss.pnc.reqour.adjust.service.CommonManipulatorResultExtractor;
import org.jboss.pnc.reqour.adjust.utils.AdjustmentSystemPropertiesUtils;
import org.jboss.pnc.reqour.common.executor.process.ProcessExecutor;
import org.jboss.pnc.reqour.common.utils.IOUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.jboss.pnc.reqour.adjust.utils.AdjustmentSystemPropertiesUtils.AdjustmentSystemPropertyName.BREW_PULL_ACTIVE;
import static org.jboss.pnc.reqour.adjust.utils.AdjustmentSystemPropertiesUtils.AdjustmentSystemPropertyName.REST_MODE;
import static org.jboss.pnc.reqour.adjust.utils.AdjustmentSystemPropertiesUtils.AdjustmentSystemPropertyName.VERSION_INCREMENTAL_SUFFIX;
import static org.jboss.pnc.reqour.adjust.utils.AdjustmentSystemPropertiesUtils.AdjustmentSystemPropertyName.VERSION_SUFFIX_ALTERNATIVES;

/**
 * Provider for {@link org.jboss.pnc.api.enums.BuildType#GRADLE} builds.
 */
@Slf4j
public class GradleProvider extends AbstractAdjustProvider<GmeConfig> implements AdjustProvider {

    private final CommonManipulatorResultExtractor adjustResultExtractor;

    public GradleProvider(
            AdjustConfig adjustConfig,
            AdjustRequest adjustRequest,
            Path workdir,
            ObjectMapper objectMapper,
            ProcessExecutor processExecutor,
            CommonManipulatorResultExtractor adjustResultExtractor,
            AdjustmentPusher adjustmentPusher) {
        super(objectMapper, processExecutor, adjustmentPusher);
        this.adjustResultExtractor = adjustResultExtractor;

        GradleProviderConfig gradleProviderConfig = adjustConfig.gradleProviderConfig();
        UserSpecifiedAlignmentParameters userSpecifiedAlignmentParameters = CommonManipulatorConfigUtils
                .parseUserSpecifiedAlignmentParameters(adjustRequest, "t", "target");

        config = GmeConfig.builder()
                .pncDefaultAlignmentParameters(
                        CommonManipulatorConfigUtils.transformPncDefaultAlignmentParametersIntoList(adjustRequest))
                .userSpecifiedAlignmentParameters(userSpecifiedAlignmentParameters.getAlignmentParameters())
                .restMode(CommonManipulatorConfigUtils.computeRestMode(adjustRequest, adjustConfig))
                .prefixOfVersionSuffix(
                        CommonManipulatorConfigUtils.computePrefixOfVersionSuffix(adjustRequest, adjustConfig))
                .alignmentConfigParameters(gradleProviderConfig.alignmentParameters())
                .workdir(workdir.resolve(userSpecifiedAlignmentParameters.getSubFolderWithResults()))
                .gradleAnalyzerPluginInitFilePath(gradleProviderConfig.gradleAnalyzerPluginInitFilePath())
                .cliJarPath(gradleProviderConfig.cliJarPath())
                .defaultGradlePath(gradleProviderConfig.defaultGradlePath())
                .isBrewPullEnabled(CommonManipulatorConfigUtils.isBrewPullEnabled(adjustRequest))
                .preferPersistentEnabled(CommonManipulatorConfigUtils.isPreferPersistentEnabled(adjustRequest))
                .executionRootOverrides(CommonManipulatorConfigUtils.getExecutionRootOverrides(adjustRequest))
                .build();

        if (ConfigProvider.getConfig().getValue("reqour-adjuster.adjust.validate", Boolean.class)) {
            validateConfig();
            log.debug("GME config was successfully initialized and validated: {}", config);
        } else {
            log.debug("GME config was successfully initialized: {}", config);
        }
    }

    private void validateConfig() {
        IOUtils.validateResourceAtPathExists(
                config.getGradleAnalyzerPluginInitFilePath(),
                "Gradle init file (specified as '%s') does not exist");
        IOUtils.validateResourceAtPathExists(config.getCliJarPath(), "CLI jar file (specified as '%s') does not exist");
        IOUtils.validateResourceAtPathExists(config.getWorkdir(), "Directory '%s' set as working does not exist");
        IOUtils.validateResourceAtPathExists(
                config.getDefaultGradlePath(),
                "Specified gradle path '%s' is non-existent");
    }

    @Override
    List<String> prepareCommand() {
        List<String> computedAlignmentParameters = getComputedAlignmentParameters();
        Path javaLocation = CommonManipulatorConfigUtils.getJavaLocation(config.getUserSpecifiedAlignmentParameters());
        List<String> targetAndInit = getTargetAndInit();

        return AdjustmentSystemPropertiesUtils.joinSystemPropertiesListsIntoList(
                List.of(
                        List.of(javaLocation.toString(), "-jar", config.getCliJarPath().toString()),
                        targetAndInit,
                        config.getPncDefaultAlignmentParameters(),
                        config.getUserSpecifiedAlignmentParameters(),
                        config.getAlignmentConfigParameters(),
                        computedAlignmentParameters));
    }

    @Override
    ManipulatorResult obtainManipulatorResult() {
        return ManipulatorResult.builder()
                .versioningState(
                        adjustResultExtractor.obtainVersioningState(
                                getPathToAlignmentResultFile(),
                                config.getExecutionRootOverrides()))
                .removedRepositories(
                        adjustResultExtractor.obtainRemovedRepositories(
                                config.getWorkdir(),
                                config.getPncDefaultAlignmentParameters().stream()))
                .build();
    }

    private List<String> getComputedAlignmentParameters() {
        final List<String> alignmentParameters = new ArrayList<>();

        alignmentParameters
                .add(AdjustmentSystemPropertiesUtils.createAdjustmentSystemProperty(REST_MODE, config.getRestMode()));
        if (!config.getPrefixOfVersionSuffix().isBlank()) {
            alignmentParameters.add(
                    AdjustmentSystemPropertiesUtils.createAdjustmentSystemProperty(
                            VERSION_INCREMENTAL_SUFFIX,
                            config.getPrefixOfVersionSuffix() + "-redhat"));
        }
        alignmentParameters.add(
                AdjustmentSystemPropertiesUtils
                        .createAdjustmentSystemProperty(BREW_PULL_ACTIVE, config.isBrewPullEnabled()));

        String prefixOfSuffixWithoutTemporary = CommonManipulatorConfigUtils
                .stripTemporarySuffix(config.getPrefixOfVersionSuffix());
        if (config.isPreferPersistentEnabled() && !prefixOfSuffixWithoutTemporary.isBlank()) {
            alignmentParameters.add(
                    AdjustmentSystemPropertiesUtils.createAdjustmentSystemProperty(
                            VERSION_SUFFIX_ALTERNATIVES,
                            "redhat," + prefixOfSuffixWithoutTemporary + "-redhat"));
        }

        return alignmentParameters;
    }

    private List<String> getTargetAndInit() {
        List<String> targetAndInit = new ArrayList<>(
                List.of(
                        "--target",
                        config.getWorkdir().toString(),
                        "--init-script",
                        config.getGradleAnalyzerPluginInitFilePath().toString()));

        if (!gradleWrapperExists()) {
            targetAndInit.addAll(List.of("-l", config.getDefaultGradlePath().toString()));
        }

        return targetAndInit;
    }

    private boolean gradleWrapperExists() {
        return Files.exists(config.getWorkdir().resolve("gradlew"));
    }

    private Path getPathToAlignmentResultFile() {
        return CommonManipulatorResultExtractor.getAlignmentResultsFilePath(
                config.getWorkdir().resolve("build/alignmentReport.json"),
                config.getWorkdir().resolve("manipulations.json"));
    }
}
