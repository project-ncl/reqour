/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.provider;

import static org.jboss.pnc.reqour.adjust.utils.AdjustmentSystemPropertiesUtils.AdjustmentSystemPropertyName.REST_MODE;
import static org.jboss.pnc.reqour.adjust.utils.AdjustmentSystemPropertiesUtils.AdjustmentSystemPropertyName.VERSION_INCREMENTAL_SUFFIX;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.pnc.api.reqour.dto.AdjustRequest;
import org.jboss.pnc.api.reqour.dto.ManipulatorResult;
import org.jboss.pnc.api.reqour.dto.VersioningState;
import org.jboss.pnc.npmmanipulator.impl.NpmResult;
import org.jboss.pnc.reqour.adjust.config.AlignmentConfig;
import org.jboss.pnc.reqour.adjust.config.NpmProviderConfig;
import org.jboss.pnc.reqour.adjust.config.manipulator.NpmManipulatorConfig;
import org.jboss.pnc.reqour.adjust.config.manipulator.common.CommonManipulatorConfigUtils;
import org.jboss.pnc.reqour.adjust.model.UserSpecifiedAlignmentParameters;
import org.jboss.pnc.reqour.adjust.utils.AdjustmentSystemPropertiesUtils;
import org.jboss.pnc.reqour.adjust.utils.CommonUtils;
import org.jboss.pnc.reqour.common.executor.process.ProcessExecutor;
import org.jboss.pnc.reqour.common.utils.IOUtils;
import org.jboss.pnc.reqour.config.ConfigConstants;
import org.slf4j.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * Provider for {@link org.jboss.pnc.api.enums.BuildType#NPM} builds.
 */
@Slf4j
public class NpmProvider extends AbstractAdjustProvider<NpmManipulatorConfig> implements AdjustProvider {

    private static final String RESULTS_FILENAME = "results";

    public NpmProvider(
            AlignmentConfig alignmentConfig,
            AdjustRequest adjustRequest,
            Path workdir,
            ObjectMapper objectMapper,
            ProcessExecutor processExecutor,
            Logger userLogger) {
        super(objectMapper, processExecutor, userLogger);

        NpmProviderConfig npmProviderConfig = alignmentConfig.npmProviderConfig();
        UserSpecifiedAlignmentParameters userSpecifiedAlignmentParameters = CommonManipulatorConfigUtils
                .parseUserSpecifiedAlignmentParameters(adjustRequest);

        config = NpmManipulatorConfig.builder()
                .pncDefaultAlignmentParameters(
                        CommonManipulatorConfigUtils.transformPncDefaultAlignmentParametersIntoList(adjustRequest))
                .userSpecifiedAlignmentParameters(userSpecifiedAlignmentParameters.getAlignmentParameters())
                .restMode(CommonManipulatorConfigUtils.computeRestMode(adjustRequest, alignmentConfig))
                .prefixOfVersionSuffix(
                        CommonManipulatorConfigUtils.computePrefixOfVersionSuffix(adjustRequest, alignmentConfig))
                .alignmentConfigParameters(npmProviderConfig.alignmentParameters())
                .workdir(workdir)
                .resultsFilePath(getResultsFile(workdir))
                .cliJarPath(npmProviderConfig.cliJarPath())
                .build();

        if (ConfigProvider.getConfig().getValue(ConfigConstants.VALIDATE_ALIGNMENT_CONFIG, Boolean.class)) {
            validateConfig();
            userLogger.info("NPM manipulator config was successfully initialized and validated: {}", config);
        } else {
            userLogger.info("NPM manipulator config was successfully initialized: {}", config);
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
                        config.getUserSpecifiedAlignmentParameters(),
                        config.getAlignmentConfigParameters(),
                        computeAlignmentParametersOverrides(),
                        List.of("--result=" + config.getResultsFilePath())));
    }

    @Override
    ManipulatorResult obtainManipulatorResult() {
        VersioningState versioningState = obtainVersioningState(config.getResultsFilePath());
        log.debug("Parsed versioning state is: {}", versioningState);

        return ManipulatorResult.builder()
                .versioningState(versioningState)
                .removedRepositories(Collections.emptyList()) // no support of repos removal by NPM manipulator
                .build();
    }

    VersioningState obtainVersioningState(Path resultsFilePath) {
        log.debug("Parsing versioning state from the file: '{}'", resultsFilePath);
        try {
            NpmResult manipulatorResult = objectMapper.readValue(resultsFilePath.toFile(), NpmResult.class);
            userLogger.info(
                    "Got NPM Manipulator result data: {}",
                    CommonUtils.prettyPrint(manipulatorResult));
            return VersioningState.builder()
                    .executionRootName(adjustNpmName(manipulatorResult.getName()))
                    .executionRootVersion(manipulatorResult.getVersion())
                    .build();
        } catch (IOException e) {
            throw new RuntimeException("Cannot deserialize the manipulator result from the manipulator", e);
        }
    }

    private String adjustNpmName(String originalName) {
        String adjustedName = originalName.replaceAll("@", "");
        adjustedName = adjustedName.replaceAll("/", "-");
        adjustedName += "-npm";
        return adjustedName;
    }

    @Override
    List<String> computeAlignmentParametersOverrides() {
        final List<String> alignmentParameters = new ArrayList<>();

        alignmentParameters
                .add(AdjustmentSystemPropertiesUtils.createAdjustmentSystemProperty(REST_MODE, config.getRestMode()));
        if (!config.getPrefixOfVersionSuffix().isBlank()) {
            Optional<String> originalSuffix = AdjustmentSystemPropertiesUtils.getSystemPropertyValue(
                    VERSION_INCREMENTAL_SUFFIX,
                    AdjustmentSystemPropertiesUtils.joinSystemPropertiesListsIntoStream(
                            List.of(
                                    config.getUserSpecifiedAlignmentParameters(),
                                    config.getAlignmentConfigParameters(),
                                    config.getPncDefaultAlignmentParameters())));
            String newSuffix = originalSuffix.map(s -> s.isEmpty() ? s : "-" + s).orElse("");
            alignmentParameters.add(
                    AdjustmentSystemPropertiesUtils.createAdjustmentSystemProperty(
                            VERSION_INCREMENTAL_SUFFIX,
                            config.getPrefixOfVersionSuffix() + newSuffix));
        }

        return alignmentParameters;
    }

    private static Path getResultsFile(Path workdir) {
        Path resultsFile = workdir.resolve(RESULTS_FILENAME);
        if (Files.notExists(resultsFile)) {
            try {
                Files.createFile(resultsFile);
            } catch (IOException e) {
                throw new RuntimeException("Unable to create the file for manipulator results", e);
            }
        }
        return resultsFile;
    }
}
