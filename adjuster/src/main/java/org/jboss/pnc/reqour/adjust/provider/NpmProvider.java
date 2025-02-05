/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.pnc.api.dto.GA;
import org.jboss.pnc.api.dto.GAV;
import org.jboss.pnc.api.reqour.dto.AdjustRequest;
import org.jboss.pnc.api.reqour.dto.ManipulatorResult;
import org.jboss.pnc.api.reqour.dto.VersioningState;
import org.jboss.pnc.projectmanipulator.npm.NpmResult;
import org.jboss.pnc.reqour.adjust.config.AdjustConfig;
import org.jboss.pnc.reqour.adjust.config.NpmProviderConfig;
import org.jboss.pnc.reqour.adjust.config.manipulator.ProjectManipulatorConfig;
import org.jboss.pnc.reqour.adjust.config.manipulator.common.CommonManipulatorConfigUtils;
import org.jboss.pnc.reqour.adjust.service.AdjustmentPusher;
import org.jboss.pnc.reqour.adjust.utils.AdjustmentSystemPropertiesUtils;
import org.jboss.pnc.reqour.common.executor.process.ProcessExecutor;
import org.jboss.pnc.reqour.common.utils.IOUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.jboss.pnc.reqour.adjust.utils.AdjustmentSystemPropertiesUtils.AdjustmentSystemPropertyName.REST_MODE;
import static org.jboss.pnc.reqour.adjust.utils.AdjustmentSystemPropertiesUtils.AdjustmentSystemPropertyName.VERSION_INCREMENTAL_SUFFIX;

/**
 * Provider for {@link org.jboss.pnc.api.enums.BuildType#NPM} builds.
 */
@Slf4j
public class NpmProvider extends AbstractAdjustProvider<ProjectManipulatorConfig> implements AdjustProvider {

    public NpmProvider(
            AdjustConfig adjustConfig,
            AdjustRequest adjustRequest,
            Path workdir,
            ObjectMapper objectMapper,
            ProcessExecutor processExecutor,
            AdjustmentPusher adjustmentPusher) {
        super(objectMapper, processExecutor, adjustmentPusher);

        NpmProviderConfig npmProviderConfig = adjustConfig.npmProviderConfig();

        config = ProjectManipulatorConfig.builder()
                .pncDefaultAlignmentParameters(
                        CommonManipulatorConfigUtils.transformPncDefaultAlignmentParametersIntoList(adjustRequest))
                .userSpecifiedAlignmentParameters(
                        CommonManipulatorConfigUtils.getUserSpecifiedAlignmentParameters(adjustRequest))
                .restMode(CommonManipulatorConfigUtils.computeRestMode(adjustRequest, adjustConfig))
                .prefixOfVersionSuffix(
                        CommonManipulatorConfigUtils.computePrefixOfVersionSuffix(adjustRequest, adjustConfig))
                .alignmentConfigParameters(npmProviderConfig.alignmentParameters())
                .workdir(workdir)
                .resultsFilePath(getResultsFile(workdir))
                .cliJarPath(npmProviderConfig.cliJarPath())
                .build();

        if (ConfigProvider.getConfig().getValue("reqour-adjuster.adjust.validate", Boolean.class)) {
            validateConfig();
            log.debug("Project manipulator config was successfully initialized and validated: {}", config);
        } else {
            log.debug("Project manipulator config was successfully initialized: {}", config);
        }
    }

    private void validateConfig() {
        IOUtils.validateResourceAtPathExists(config.getCliJarPath(), "CLI jar file (specified as '%s') does not exist");
    }

    @Override
    List<String> prepareCommand() {
        Path javaLocation = CommonManipulatorConfigUtils.getJavaLocation(config.getUserSpecifiedAlignmentParameters());
        return AdjustmentSystemPropertiesUtils.joinSystemPropertiesListsIntoList(
                List.of(
                        List.of(javaLocation.toString(), "-jar", config.getCliJarPath().toString()),
                        config.getPncDefaultAlignmentParameters(),
                        config.getUserSpecifiedAlignmentParameters(),
                        config.getAlignmentConfigParameters(),
                        getComputedAlignmentParameters(),
                        List.of("--result=" + config.getResultsFilePath())));
    }

    @Override
    ManipulatorResult obtainManipulatorResult() {
        return ManipulatorResult.builder()
                .versioningState(obtainVersioningState(config.getResultsFilePath()))
                .removedRepositories(Collections.emptyList()) // no support of repos removal by project manipulator
                .build();
    }

    VersioningState obtainVersioningState(Path resultsFilePath) {
        try {
            NpmResult manipulatorResult = objectMapper.readValue(resultsFilePath.toFile(), NpmResult.class);
            return VersioningState.builder()
                    .executionRootName(adjustNpmName(manipulatorResult.getName()))
                    .executionRootVersion(manipulatorResult.getVersion())
                    .build();
        } catch (IOException e) {
            throw new RuntimeException("Cannot deserialize the manipulator result", e);
        }
    }

    private String adjustNpmName(String originalName) {
        String adjustedName = originalName.replaceAll("@", "");
        adjustedName = adjustedName.replaceAll("/", "-");
        adjustedName += "-npm";
        return adjustedName;
    }

    private List<String> getComputedAlignmentParameters() {
        final List<String> alignmentParameters = new ArrayList<>();

        alignmentParameters
                .add(AdjustmentSystemPropertiesUtils.createAdjustmentSystemProperty(REST_MODE, config.getRestMode()));
        if (!config.getPrefixOfVersionSuffix().isBlank()) {
            Optional<String> originalSuffix = AdjustmentSystemPropertiesUtils.getSystemPropertyValue(
                    VERSION_INCREMENTAL_SUFFIX,
                    AdjustmentSystemPropertiesUtils.joinSystemPropertiesListsIntoStream(
                            List.of(
                                    config.getAlignmentConfigParameters(),
                                    config.getUserSpecifiedAlignmentParameters(),
                                    config.getPncDefaultAlignmentParameters())));
            String newSuffix = originalSuffix.map(s -> "-" + s).orElse("");
            alignmentParameters.add(
                    AdjustmentSystemPropertiesUtils.createAdjustmentSystemProperty(
                            VERSION_INCREMENTAL_SUFFIX,
                            config.getPrefixOfVersionSuffix() + newSuffix));
        }

        return alignmentParameters;
    }

    private static Path getResultsFile(Path workdir) {
        Path resultsFile = workdir.resolve("results");
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
