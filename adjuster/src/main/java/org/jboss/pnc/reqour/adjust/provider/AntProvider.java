/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.provider;

import static org.jboss.pnc.reqour.adjust.utils.AdjustmentSystemPropertiesUtils.AdjustmentSystemPropertyName.BREW_PULL_ACTIVE;
import static org.jboss.pnc.reqour.adjust.utils.AdjustmentSystemPropertiesUtils.AdjustmentSystemPropertyName.JSON_REPORT_FILE;
import static org.jboss.pnc.reqour.adjust.utils.AdjustmentSystemPropertiesUtils.AdjustmentSystemPropertyName.REST_MODE;
import static org.jboss.pnc.reqour.adjust.utils.AdjustmentSystemPropertiesUtils.AdjustmentSystemPropertyName.VERSION_INCREMENTAL_SUFFIX;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.pnc.api.reqour.dto.AdjustRequest;
import org.jboss.pnc.api.reqour.dto.ManipulatorResult;
import org.jboss.pnc.api.reqour.dto.VersioningState;
import org.jboss.pnc.reqour.adjust.config.AlignmentConfig;
import org.jboss.pnc.reqour.adjust.config.AntProviderConfig;
import org.jboss.pnc.reqour.adjust.config.manipulator.AmeConfig;
import org.jboss.pnc.reqour.adjust.config.manipulator.common.CommonManipulatorConfigUtils;
import org.jboss.pnc.reqour.adjust.model.UserSpecifiedAlignmentParameters;
import org.jboss.pnc.reqour.adjust.service.CommonManipulatorResultExtractor;
import org.jboss.pnc.reqour.adjust.utils.AdjustmentSystemPropertiesUtils;
import org.jboss.pnc.reqour.adjust.utils.CommonUtils;
import org.jboss.pnc.reqour.common.executor.process.ProcessExecutor;
import org.jboss.pnc.reqour.common.utils.IOUtils;
import org.jboss.pnc.reqour.config.ConfigConstants;
import org.jboss.pnc.reqour.config.EnvironmentConfig;
import org.slf4j.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * Provider for {@link org.jboss.pnc.api.enums.BuildType#ANT} builds.
 */
@Slf4j
public class AntProvider extends AbstractAdjustProvider<AmeConfig> {

    public final static String DEFAULT_ALIGNMENT_REPORT_FILENAME = "alignmentReport.json";

    private final CommonManipulatorResultExtractor adjustResultExtractor;

    public AntProvider(
            AlignmentConfig alignmentConfig,
            EnvironmentConfig envConfig,
            AdjustRequest adjustRequest,
            Path workdir,
            ObjectMapper objectMapper,
            ProcessExecutor processExecutor,
            Logger userLogger,
            CommonManipulatorResultExtractor adjustResultExtractor) {
        super(objectMapper, processExecutor, envConfig, userLogger);
        this.adjustResultExtractor = adjustResultExtractor;

        AntProviderConfig antProviderConfig = alignmentConfig.antProviderConfig();
        UserSpecifiedAlignmentParameters userSpecifiedAlignmentParameters = CommonManipulatorConfigUtils
                .parseUserSpecifiedAlignmentParameters(adjustRequest);

        final List<String> userAlignmentParametersWithFile = new ArrayList<>(
                userSpecifiedAlignmentParameters.getAlignmentParameters());
        if (userSpecifiedAlignmentParameters.getLocation().isPresent()) {
            userAlignmentParametersWithFile.add("--file=" + userSpecifiedAlignmentParameters.getLocation().get());
        }

        config = AmeConfig.builder()
                .pncDefaultAlignmentParameters(
                        CommonManipulatorConfigUtils.transformPncDefaultAlignmentParametersIntoList(adjustRequest))
                .userSpecifiedAlignmentParameters(userAlignmentParametersWithFile)
                .restMode(CommonManipulatorConfigUtils.computeRestMode(adjustRequest, alignmentConfig))
                .versionIncrementalSuffix(
                        CommonManipulatorConfigUtils.computeVersionIncrementalSuffix(adjustRequest, alignmentConfig))
                .alignmentConfigParameters(antProviderConfig.alignmentParameters())
                .additionalOverridableAlignmentParameters(
                        CommonManipulatorConfigUtils
                                .computeAdditionalOverridableAlignmentParameters(adjustRequest, alignmentConfig))
                .additionalNonOverridableAlignmentParameters(
                        CommonManipulatorConfigUtils
                                .computeAdditionalNonOverridableAlignmentParameters(adjustRequest, alignmentConfig))
                .cliJarPath(antProviderConfig.cliJarPath())
                .workdir(workdir)
                .isBrewPullEnabled(CommonManipulatorConfigUtils.isBrewPullEnabled(adjustRequest))
                .alignmentResultFile(getPathToAlignmentResultFile(userSpecifiedAlignmentParameters, workdir))
                .build();

        if (ConfigProvider.getConfig().getValue(ConfigConstants.VALIDATE_ALIGNMENT_CONFIG, Boolean.class)) {
            validateConfig();
            userLogger.info("AME config was successfully initialized and validated: {}", config);
        } else {
            userLogger.info("AME config was successfully initialized: {}", config);
        }
    }

    private void validateConfig() {
        IOUtils.validateResourceAtPathExists(config.getCliJarPath(), "CLI jar file (specified as '%s') does not exist");
    }

    @Override
    List<String> prepareCommand() {
        Path javaLocation = CommonManipulatorConfigUtils
                .getJavaLocation(userLogger, config.getUserSpecifiedAlignmentParameters());
        return AdjustmentSystemPropertiesUtils.joinSystemPropertiesListsIntoList(
                List.of(
                        List.of(javaLocation.toString(), "-jar", config.getCliJarPath().toString()),
                        config.getPncDefaultAlignmentParameters(),
                        // overridable alignment params are placed before users params, so that they can override it if needed
                        config.getAdditionalOverridableAlignmentParameters(),
                        config.getUserSpecifiedAlignmentParameters(),
                        config.getAlignmentConfigParameters(),
                        computeAlignmentParametersOverrides(),
                        // non-overridable parameters are placed at the very end of manipulation command, so that a user cannot override them
                        config.getAdditionalNonOverridableAlignmentParameters()));
    }

    @Override
    List<String> computeAlignmentParametersOverrides() {
        final List<String> alignmentParameters = new ArrayList<>();

        alignmentParameters
                .add(AdjustmentSystemPropertiesUtils.createAdjustmentSystemProperty(REST_MODE, config.getRestMode()));
        alignmentParameters.add(
                AdjustmentSystemPropertiesUtils.createAdjustmentSystemProperty(
                        VERSION_INCREMENTAL_SUFFIX,
                        config.getVersionIncrementalSuffix()));
        alignmentParameters.add(
                AdjustmentSystemPropertiesUtils
                        .createAdjustmentSystemProperty(BREW_PULL_ACTIVE, config.isBrewPullEnabled()));

        return alignmentParameters;
    }

    @Override
    ManipulatorResult obtainManipulatorResult() {
        VersioningState versioningState = adjustResultExtractor.obtainVersioningStateFromManipulatorResult(
                config.getAlignmentResultFile());
        log.debug("Parsed versioning state is: {}", versioningState);

        return ManipulatorResult.builder()
                .versioningState(versioningState)
                .removedRepositories(Collections.emptyList()) // no removed repositories support for AME
                .build();
    }

    private Path getPathToAlignmentResultFile(
            UserSpecifiedAlignmentParameters userSpecifiedAlignmentParameters,
            Path workdir) {
        Optional<String> alignmentReportFile = AdjustmentSystemPropertiesUtils.getSystemPropertyValue(
                JSON_REPORT_FILE,
                userSpecifiedAlignmentParameters.getAlignmentParameters().stream());

        if (alignmentReportFile.isPresent()) {
            // In case alignment file was specified through the CLI option, we try to find it from the current workdir
            userLogger.info("Alignment report file CLI option specified as: '{}'", alignmentReportFile.get());
            return workdir.resolve(Path.of(alignmentReportFile.get()));
        }

        // Otherwise, the default location is in the same folder as build.xml file is
        final Path buildFileFolder;
        if (userSpecifiedAlignmentParameters.getLocation().isEmpty()) {
            buildFileFolder = workdir;
        } else {
            Path buildFile = workdir.resolve(userSpecifiedAlignmentParameters.getLocation().get());
            buildFileFolder = CommonUtils.extractDirectoryOfBuildFile(buildFile);
        }
        return buildFileFolder.resolve(DEFAULT_ALIGNMENT_REPORT_FILENAME);
    }
}
