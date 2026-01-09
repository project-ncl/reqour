/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.provider;

import static org.jboss.pnc.reqour.adjust.utils.AdjustmentSystemPropertiesUtils.AdjustmentSystemPropertyName.BREW_PULL_ACTIVE;
import static org.jboss.pnc.reqour.adjust.utils.AdjustmentSystemPropertiesUtils.AdjustmentSystemPropertyName.REST_MODE;
import static org.jboss.pnc.reqour.adjust.utils.AdjustmentSystemPropertiesUtils.AdjustmentSystemPropertyName.VERSION_INCREMENTAL_SUFFIX;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.pnc.api.reqour.dto.AdjustRequest;
import org.jboss.pnc.api.reqour.dto.ManipulatorResult;
import org.jboss.pnc.reqour.adjust.config.AlignmentConfig;
import org.jboss.pnc.reqour.adjust.config.SbtProviderConfig;
import org.jboss.pnc.reqour.adjust.config.manipulator.SmegConfig;
import org.jboss.pnc.reqour.adjust.config.manipulator.common.CommonManipulatorConfigUtils;
import org.jboss.pnc.reqour.adjust.model.UserSpecifiedAlignmentParameters;
import org.jboss.pnc.reqour.adjust.model.smeg.SmegResult;
import org.jboss.pnc.reqour.adjust.utils.AdjustmentSystemPropertiesUtils;
import org.jboss.pnc.reqour.common.executor.process.ProcessExecutor;
import org.jboss.pnc.reqour.common.utils.IOUtils;
import org.jboss.pnc.reqour.config.ConfigConstants;
import org.slf4j.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * Provider for {@link org.jboss.pnc.api.enums.BuildType#SBT} builds.
 */
@Slf4j
public class SbtProvider extends AbstractAdjustProvider<SmegConfig> implements AdjustProvider {

    private final AlignmentConfig alignmentConfig;
    private static final String ALIGNMENT_RESULTS_FILENAME = "manipulations.json";

    public SbtProvider(
            AlignmentConfig alignmentConfig,
            AdjustRequest adjustRequest,
            Path workdir,
            ObjectMapper objectMapper,
            ProcessExecutor processExecutor,
            Logger userLogger) {
        super(objectMapper, processExecutor, userLogger);
        this.alignmentConfig = alignmentConfig;

        SbtProviderConfig sbtProviderConfig = alignmentConfig.scalaProviderConfig();
        UserSpecifiedAlignmentParameters userSpecifiedAlignmentParameters = CommonManipulatorConfigUtils
                .parseUserSpecifiedAlignmentParameters(adjustRequest);

        config = SmegConfig.builder()
                .pncDefaultAlignmentParameters(
                        CommonManipulatorConfigUtils.transformPncDefaultAlignmentParametersIntoList(adjustRequest))
                .userSpecifiedAlignmentParameters(userSpecifiedAlignmentParameters.getAlignmentParameters())
                .restMode(CommonManipulatorConfigUtils.computeRestMode(adjustRequest, alignmentConfig))
                .brewPullEnabled(CommonManipulatorConfigUtils.isBrewPullEnabled(adjustRequest))
                .prefixOfVersionSuffix(
                        CommonManipulatorConfigUtils.computePrefixOfVersionSuffix(adjustRequest, alignmentConfig))
                .alignmentConfigParameters(sbtProviderConfig.alignmentParameters())
                .workdir(
                        userSpecifiedAlignmentParameters.getLocation().isEmpty() ? workdir
                                : workdir.resolve(userSpecifiedAlignmentParameters.getLocation().get()))
                .sbtPath(sbtProviderConfig.sbtPath())
                .executionRootOverrides(CommonManipulatorConfigUtils.getExecutionRootOverrides(adjustRequest))
                .build();

        if (ConfigProvider.getConfig().getValue(ConfigConstants.VALIDATE_ALIGNMENT_CONFIG, Boolean.class)) {
            validateConfig();
            log.debug("SMEG config was successfully initialized and validated: {}", config);
        } else {
            log.debug("SMEG config was successfully initialized: {}", config);
        }
    }

    private void validateConfig() {
        IOUtils.validateResourceAtPathExists(
                config.getSbtPath(),
                "Scala build tool (specified at '%s') does not exist");
    }

    @Override
    List<String> prepareCommand() {

        return AdjustmentSystemPropertiesUtils.joinSystemPropertiesListsIntoList(
                List.of(
                        List.of(config.getSbtPath().toString()),
                        config.getPncDefaultAlignmentParameters(),
                        config.getUserSpecifiedAlignmentParameters(),
                        config.getAlignmentConfigParameters(),
                        computeAlignmentParametersOverrides(),
                        List.of("manipulate", "writeReport")));
    }

    @Override
    ManipulatorResult obtainManipulatorResult() {
        Path alignmentResultsFile = getPathToAlignmentResultsFile();
        try {
            log.debug(
                    "Gonna parse manipulator result from the file: '{}': {}",
                    alignmentResultsFile,
                    Files.readString(alignmentResultsFile));
            SmegResult smegResult = objectMapper
                    .readValue(alignmentResultsFile.toFile(), SmegResult.class);
            log.debug("Parsed SMEg result: {}", smegResult);
            ManipulatorResult manipulatorResult = smegResult.toManipulatorResult();
            log.debug("Unified manipulator result: {}", manipulatorResult);
            return manipulatorResult;
        } catch (IOException e) {
            throw new RuntimeException("Unable to deserialize SMEG result", e);
        }
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

        return alignmentParameters;
    }

    private Path getPathToAlignmentResultsFile() {
        return config.getWorkdir().resolve(ALIGNMENT_RESULTS_FILENAME);
    }
}
