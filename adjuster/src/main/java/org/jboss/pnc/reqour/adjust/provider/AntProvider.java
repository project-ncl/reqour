/*
 * SPDX-FileCopyrightText: Copyright © 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.provider;

import static org.jboss.pnc.reqour.adjust.utils.AdjustmentSystemPropertiesUtils.AdjustmentSystemPropertyName.BREW_PULL_ACTIVE;
import static org.jboss.pnc.reqour.adjust.utils.AdjustmentSystemPropertiesUtils.AdjustmentSystemPropertyName.REST_MODE;
import static org.jboss.pnc.reqour.adjust.utils.AdjustmentSystemPropertiesUtils.AdjustmentSystemPropertyName.VERSION_INCREMENTAL_SUFFIX;
import static org.jboss.pnc.reqour.adjust.utils.AdjustmentSystemPropertiesUtils.getSystemPropertyFormat;
import static org.jboss.pnc.reqour.adjust.utils.AdjustmentSystemPropertiesUtils.getSystemPropertyValue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.pnc.api.reqour.dto.AdjustRequest;
import org.jboss.pnc.api.reqour.dto.ManipulatorResult;
import org.jboss.pnc.api.reqour.dto.VersioningState;
import org.jboss.pnc.common.log.LogSanitizer;
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

    private final static String DEFAULT_ALIGNMENT_RESULT_FILENAME = "alignmentReport.json";
    private static final String OUTPUT_FILE_PROP_NAME = "reportJSONOutputFile";

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

        final Path subFolderWithResults;
        // immutable collection returned, so let's make it mutable and add there the result file if needed
        final List<String> userAlignmentParametersWithFile = new ArrayList<>(
                userSpecifiedAlignmentParameters.getAlignmentParameters());
        if (userSpecifiedAlignmentParameters.getLocation().isEmpty()) {
            subFolderWithResults = workdir;
        } else {
            userAlignmentParametersWithFile.add("--file=" + userSpecifiedAlignmentParameters.getLocation().get());
            Path buildFile = workdir.resolve(userSpecifiedAlignmentParameters.getLocation().get());
            subFolderWithResults = CommonUtils.extractDirectoryOfFile(buildFile);
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
                .cliJarPath(antProviderConfig.cliJarPath())
                .workdir(workdir)
                .isBrewPullEnabled(CommonManipulatorConfigUtils.isBrewPullEnabled(adjustRequest))
                .subFolderWithAlignmentResultFile(subFolderWithResults)
                .build();

        if (ConfigProvider.getConfig().getValue(ConfigConstants.VALIDATE_ALIGNMENT_CONFIG, Boolean.class)) {
            validateConfig();
            log.debug(
                    "AME config was successfully initialized and validated: {}",
                    LogSanitizer.maskKnownSecrets(config.toString()));
        } else {
            log.debug(
                    "AME config was successfully initialized: {}",
                    LogSanitizer.maskKnownSecrets(config.toString()));
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
                getPathToAlignmentResultFile());
        log.debug("Parsed versioning state is: {}", versioningState);

        return ManipulatorResult.builder()
                .versioningState(versioningState)
                .removedRepositories(Collections.emptyList()) // no removed repositories support for AME
                .build();
    }

    private Path getPathToAlignmentResultFile() {
        final Path subFolderWithAlignmentResultFile = config.getSubFolderWithAlignmentResultFile();
        final String alignmentResultFileName = getSystemPropertyValue(
                getSystemPropertyFormat(OUTPUT_FILE_PROP_NAME),
                config.getUserSpecifiedAlignmentParameters().stream()).orElse(DEFAULT_ALIGNMENT_RESULT_FILENAME);

        return subFolderWithAlignmentResultFile.resolve(alignmentResultFileName);
    }
}
