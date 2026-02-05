/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.provider;

import static org.jboss.pnc.reqour.adjust.model.GradleAlignmentResultFile.GME_DISABLED;
import static org.jboss.pnc.reqour.adjust.model.GradleAlignmentResultFile.GME_ENABLED;
import static org.jboss.pnc.reqour.adjust.utils.AdjustmentSystemPropertiesUtils.AdjustmentSystemPropertyName.BREW_PULL_ACTIVE;
import static org.jboss.pnc.reqour.adjust.utils.AdjustmentSystemPropertiesUtils.AdjustmentSystemPropertyName.REST_MODE;
import static org.jboss.pnc.reqour.adjust.utils.AdjustmentSystemPropertiesUtils.AdjustmentSystemPropertyName.VERSION_INCREMENTAL_SUFFIX;
import static org.jboss.pnc.reqour.adjust.utils.AdjustmentSystemPropertiesUtils.AdjustmentSystemPropertyName.VERSION_SUFFIX_ALTERNATIVES;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.pnc.api.reqour.dto.AdjustRequest;
import org.jboss.pnc.api.reqour.dto.ManipulatorResult;
import org.jboss.pnc.api.reqour.dto.RemovedRepository;
import org.jboss.pnc.api.reqour.dto.VersioningState;
import org.jboss.pnc.gradlemanipulator.common.io.ManipulationIO;
import org.jboss.pnc.gradlemanipulator.common.model.ManipulationModel;
import org.jboss.pnc.reqour.adjust.config.AlignmentConfig;
import org.jboss.pnc.reqour.adjust.config.GradleProviderConfig;
import org.jboss.pnc.reqour.adjust.config.manipulator.GmeConfig;
import org.jboss.pnc.reqour.adjust.config.manipulator.common.CommonManipulatorConfigUtils;
import org.jboss.pnc.reqour.adjust.exception.AdjusterException;
import org.jboss.pnc.reqour.adjust.model.UserSpecifiedAlignmentParameters;
import org.jboss.pnc.reqour.adjust.service.CommonManipulatorResultExtractor;
import org.jboss.pnc.reqour.adjust.utils.AdjustmentSystemPropertiesUtils;
import org.jboss.pnc.reqour.common.executor.process.ProcessExecutor;
import org.jboss.pnc.reqour.common.utils.IOUtils;
import org.jboss.pnc.reqour.config.ConfigConstants;
import org.slf4j.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * Provider for {@link org.jboss.pnc.api.enums.BuildType#GRADLE} builds.
 */
@Slf4j
public class GradleProvider extends AbstractAdjustProvider<GmeConfig> implements AdjustProvider {

    private final AlignmentConfig alignmentConfig;
    private final CommonManipulatorResultExtractor adjustResultExtractor;

    public GradleProvider(
            AlignmentConfig alignmentConfig,
            AdjustRequest adjustRequest,
            Path workdir,
            ObjectMapper objectMapper,
            ProcessExecutor processExecutor,
            CommonManipulatorResultExtractor adjustResultExtractor,
            Logger userLogger) {
        super(objectMapper, processExecutor, userLogger);
        this.alignmentConfig = alignmentConfig;
        this.adjustResultExtractor = adjustResultExtractor;

        GradleProviderConfig gradleProviderConfig = alignmentConfig.gradleProviderConfig();
        UserSpecifiedAlignmentParameters userSpecifiedAlignmentParameters = CommonManipulatorConfigUtils
                .parseUserSpecifiedAlignmentParameters(adjustRequest, "t", "target");

        config = GmeConfig.builder()
                .pncDefaultAlignmentParameters(
                        CommonManipulatorConfigUtils.transformPncDefaultAlignmentParametersIntoList(adjustRequest))
                .userSpecifiedAlignmentParameters(userSpecifiedAlignmentParameters.getAlignmentParameters())
                .restMode(CommonManipulatorConfigUtils.computeRestMode(adjustRequest, alignmentConfig))
                .prefixOfVersionSuffix(
                        CommonManipulatorConfigUtils.computePrefixOfVersionSuffix(adjustRequest, alignmentConfig))
                .alignmentConfigParameters(gradleProviderConfig.alignmentParameters())
                .workdir(
                        userSpecifiedAlignmentParameters.getLocation().isEmpty() ? workdir
                                : workdir.resolve(userSpecifiedAlignmentParameters.getLocation().get()))
                .gradleAnalyzerPluginInitFilePath(gradleProviderConfig.gradleAnalyzerPluginInitFilePath())
                .cliJarPath(gradleProviderConfig.cliJarPath())
                .defaultGradlePath(gradleProviderConfig.defaultGradlePath())
                .isBrewPullEnabled(CommonManipulatorConfigUtils.isBrewPullEnabled(adjustRequest))
                .preferPersistentEnabled(CommonManipulatorConfigUtils.isPreferPersistentEnabled(adjustRequest))
                .executionRootOverrides(CommonManipulatorConfigUtils.getExecutionRootOverrides(adjustRequest))
                .build();

        if (ConfigProvider.getConfig().getValue(ConfigConstants.VALIDATE_ALIGNMENT_CONFIG, Boolean.class)) {
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
        Path javaLocation = CommonManipulatorConfigUtils
                .getJavaLocation(userLogger, config.getUserSpecifiedAlignmentParameters());
        List<String> targetAndInit = getTargetAndInit();

        return AdjustmentSystemPropertiesUtils.joinSystemPropertiesListsIntoList(
                List.of(
                        List.of(javaLocation.toString(), "-jar", config.getCliJarPath().toString()),
                        targetAndInit,
                        config.getPncDefaultAlignmentParameters(),
                        config.getUserSpecifiedAlignmentParameters(),
                        config.getAlignmentConfigParameters(),
                        computeAlignmentParametersOverrides()));
    }

    @Override
    ManipulatorResult obtainManipulatorResult() {
        final VersioningState versioningState;

        if (isGmeDisabled()) {
            userLogger.info(
                    "GME disabled, parsing result from user-provided {} file",
                    GME_DISABLED.getGmeAlignmentResultFile());
            if (Files.notExists(config.getWorkdir().resolve(GME_DISABLED.getGmeAlignmentResultFile()))) {
                userLogger.warn(
                        "No user-provided {} file found, cannot continue",
                        GME_DISABLED.getGmeAlignmentResultFile());
                throw new AdjusterException(
                        String.format(
                                "GME disabled, but no user-provided file (%s) was found, cannot proceed further",
                                GME_DISABLED.getGmeAlignmentResultFile()));
            }

            ManipulationModel userProvidedResultFile = ManipulationIO
                    .readManipulationModel(config.getWorkdir().toFile());
            versioningState = VersioningState.builder()
                    .executionRootName(userProvidedResultFile.getName())
                    .executionRootVersion(userProvidedResultFile.getVersion())
                    .build();
        } else {
            versioningState = adjustResultExtractor.obtainVersioningStateFromManipulatorResult(
                    GME_ENABLED.getGmeAlignmentResultFile(),
                    config.getExecutionRootOverrides());

        }
        log.debug("Parsed versioning state is: {}", versioningState);
        List<RemovedRepository> removedRepositories = adjustResultExtractor.obtainRemovedRepositories(
                config.getWorkdir(),
                config.getPncDefaultAlignmentParameters());
        log.debug("Parsed removed repositories are: {}", removedRepositories);

        return ManipulatorResult.builder()
                .versioningState(versioningState)
                .removedRepositories(
                        removedRepositories)
                .build();
    }

    private boolean isGmeDisabled() {
        return CommonManipulatorConfigUtils.isManipulatorDisabled(config);
    }

    @Override
    List<String> computeAlignmentParametersOverrides() {
        final List<String> alignmentParameters = new ArrayList<>();

        alignmentParameters
                .add(AdjustmentSystemPropertiesUtils.createAdjustmentSystemProperty(REST_MODE, config.getRestMode()));
        if (!config.getPrefixOfVersionSuffix().isBlank()) {
            alignmentParameters.add(
                    AdjustmentSystemPropertiesUtils.createAdjustmentSystemProperty(
                            VERSION_INCREMENTAL_SUFFIX,
                            config.getPrefixOfVersionSuffix() + "-" + alignmentConfig.suffix().permanent()));
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
                            alignmentConfig.suffix().permanent() + "," + prefixOfSuffixWithoutTemporary + "-"
                                    + alignmentConfig.suffix().permanent()));
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
                config.getWorkdir().resolve("manipulation.json"));
    }
}
