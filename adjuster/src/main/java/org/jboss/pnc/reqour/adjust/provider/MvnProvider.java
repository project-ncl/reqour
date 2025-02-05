/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.commonjava.maven.ext.common.json.GAV;
import org.commonjava.maven.ext.common.json.PME;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.pnc.api.reqour.dto.AdjustRequest;
import org.jboss.pnc.api.reqour.dto.ManipulatorResult;
import org.jboss.pnc.reqour.adjust.config.AdjustConfig;
import org.jboss.pnc.reqour.adjust.config.MvnProviderConfig;
import org.jboss.pnc.reqour.adjust.config.manipulator.PmeConfig;
import org.jboss.pnc.reqour.adjust.config.manipulator.common.CommonManipulatorConfigUtils;
import org.jboss.pnc.reqour.adjust.model.UserSpecifiedAlignmentParameters;
import org.jboss.pnc.reqour.adjust.service.AdjustmentPusher;
import org.jboss.pnc.reqour.adjust.service.CommonManipulatorResultExtractor;
import org.jboss.pnc.reqour.adjust.service.RootGavExtractor;
import org.jboss.pnc.reqour.adjust.utils.AdjustmentSystemPropertiesUtils;
import org.jboss.pnc.reqour.common.executor.process.ProcessExecutor;
import org.jboss.pnc.reqour.common.utils.IOUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.jboss.pnc.reqour.adjust.utils.AdjustmentSystemPropertiesUtils.AdjustmentSystemPropertyName.BREW_PULL_ACTIVE;
import static org.jboss.pnc.reqour.adjust.utils.AdjustmentSystemPropertiesUtils.AdjustmentSystemPropertyName.REST_MODE;
import static org.jboss.pnc.reqour.adjust.utils.AdjustmentSystemPropertiesUtils.AdjustmentSystemPropertyName.VERSION_INCREMENTAL_SUFFIX;
import static org.jboss.pnc.reqour.adjust.utils.AdjustmentSystemPropertiesUtils.AdjustmentSystemPropertyName.VERSION_SUFFIX_ALTERNATIVES;

/**
 * Provider for {@link org.jboss.pnc.api.enums.BuildType#MVN} builds.
 */
@Slf4j
public class MvnProvider extends AbstractAdjustProvider<PmeConfig> implements AdjustProvider {

    private final CommonManipulatorResultExtractor adjustResultExtractor;
    private final RootGavExtractor rootGavExtractor;

    public MvnProvider(
            AdjustConfig adjustConfig,
            AdjustRequest adjustRequest,
            Path workdir,
            ObjectMapper objectMapper,
            ProcessExecutor processExecutor,
            CommonManipulatorResultExtractor adjustResultExtractor,
            RootGavExtractor rootGavExtractor,
            AdjustmentPusher adjustmentPusher) {
        super(objectMapper, processExecutor, adjustmentPusher);
        this.adjustResultExtractor = adjustResultExtractor;
        this.rootGavExtractor = rootGavExtractor;

        MvnProviderConfig mvnProviderConfig = adjustConfig.mvnProviderConfig();
        UserSpecifiedAlignmentParameters userSpecifiedAlignmentParameters = CommonManipulatorConfigUtils
                .parseUserSpecifiedAlignmentParameters(adjustRequest);

        config = PmeConfig.builder()
                .pncDefaultAlignmentParameters(
                        CommonManipulatorConfigUtils.transformPncDefaultAlignmentParametersIntoList(adjustRequest))
                .userSpecifiedAlignmentParameters(userSpecifiedAlignmentParameters.getAlignmentParameters())
                .restMode(CommonManipulatorConfigUtils.computeRestMode(adjustRequest, adjustConfig))
                .prefixOfVersionSuffix(
                        CommonManipulatorConfigUtils.computePrefixOfVersionSuffix(adjustRequest, adjustConfig))
                .alignmentConfigParameters(mvnProviderConfig.alignmentParameters())
                .workdir(workdir)
                .subFolderWithAlignmentResultFile(
                        workdir.resolve(userSpecifiedAlignmentParameters.getSubFolderWithResults()))
                .cliJarPath(mvnProviderConfig.cliJarPath())
                .settingsFilePath(
                        getPathToSettingsFile(
                                adjustRequest,
                                mvnProviderConfig.defaultSettingsFilePath(),
                                mvnProviderConfig.temporarySettingsFilePath()))
                .isBrewPullEnabled(CommonManipulatorConfigUtils.isBrewPullEnabled(adjustRequest))
                .preferPersistentEnabled(CommonManipulatorConfigUtils.isPreferPersistentEnabled(adjustRequest))
                .executionRootOverrides(CommonManipulatorConfigUtils.getExecutionRootOverrides(adjustRequest))
                .build();

        if (ConfigProvider.getConfig().getValue("reqour-adjuster.adjust.validate", Boolean.class)) {
            validateConfig();
            log.debug("PME config was successfully initialized and validated: {}", config);
        } else {
            log.debug("PME config was successfully initialized: {}", config);
        }
    }

    private void validateConfig() {
        IOUtils.validateResourceAtPathExists(config.getCliJarPath(), "CLI jar file (specified at '%s') does not exist");

        IOUtils.validateResourceAtPathExists(
                config.getSettingsFilePath(),
                "File with default settings (specified at '%s') does not exist");
    }

    @Override
    List<String> prepareCommand() {
        List<String> userSpecifiedAlignmentParameters = config.getUserSpecifiedAlignmentParameters();
        if (!config.getSubFolderWithAlignmentResultFile().equals(config.getWorkdir())) {
            userSpecifiedAlignmentParameters.add("--file=" + config.getSubFolderWithAlignmentResultFile());
        }
        Path javaLocation = CommonManipulatorConfigUtils.getJavaLocation(userSpecifiedAlignmentParameters);
        return AdjustmentSystemPropertiesUtils.joinSystemPropertiesListsIntoList(
                List.of(
                        List.of(javaLocation.toString(), "-jar", config.getCliJarPath().toString()),
                        getPmeSettingsParameter(),
                        config.getPncDefaultAlignmentParameters(),
                        config.getUserSpecifiedAlignmentParameters(),
                        config.getAlignmentConfigParameters(),
                        getComputedAlignmentParameters()));
    }

    @Override
    ManipulatorResult obtainManipulatorResult() {
        if (pmeIsDisabled()) {
            log.warn("PME is disabled via extra parameters");
            createAdjustResultsFile();
        }

        return ManipulatorResult.builder()
                .versioningState(
                        adjustResultExtractor.obtainVersioningState(
                                getPathToAlignmentResultFile(),
                                config.getExecutionRootOverrides()))
                .removedRepositories(
                        adjustResultExtractor.obtainRemovedRepositories(
                                config.getSubFolderWithAlignmentResultFile(),
                                AdjustmentSystemPropertiesUtils.joinSystemPropertiesListsIntoStream(
                                        List.of(
                                                config.getPncDefaultAlignmentParameters(),
                                                config.getUserSpecifiedAlignmentParameters(),
                                                config.getAlignmentConfigParameters()))))
                .build();
    }

    private boolean pmeIsDisabled() {
        String pmeDisabled = AdjustmentSystemPropertiesUtils
                .getSystemPropertyValue("-Dmanipulation.disable", config.getUserSpecifiedAlignmentParameters().stream())
                .orElse("false");
        return "true".equals(pmeDisabled);
    }

    private void createAdjustResultsFile() {
        Path adjustResultsFilePath = getDefaultAlignmentResultFile();
        try {
            Files.createFile(adjustResultsFilePath);
        } catch (IOException e) {
            throw new RuntimeException(
                    "Unable to create file with default alignment results (since PME was disabled)",
                    e);
        }
        try {
            objectMapper.writeValue(adjustResultsFilePath.toFile(), getResultWhenPmeIsDisabled());
        } catch (IOException e) {
            throw new RuntimeException(
                    String.format("Unable to write extracted GAV into the file '%s'", adjustResultsFilePath),
                    e);
        }
    }

    private PME getResultWhenPmeIsDisabled() {
        PME manipulatorResult = new PME();
        GAV gav = new GAV();
        org.jboss.pnc.api.dto.GAV executionRootGav = rootGavExtractor.extractGav(config.getWorkdir());
        gav.setGroupId(executionRootGav.getGroupId());
        gav.setArtifactId(executionRootGav.getArtifactId());
        gav.setVersion(executionRootGav.getVersion());
        manipulatorResult.setGav(gav);
        return manipulatorResult;
    }

    private Path getPathToSettingsFile(
            AdjustRequest adjustRequest,
            Path defaultSettingsFilePath,
            Path temporarySettingsFilePath) {
        return (adjustRequest.isTempBuild()) ? temporarySettingsFilePath : defaultSettingsFilePath;
    }

    private List<String> getPmeSettingsParameter() {
        return List.of("-s", config.getSettingsFilePath().toString());
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

        String prefixOfSuffixWithoutTemporary = CommonManipulatorConfigUtils
                .stripTemporarySuffix(config.getPrefixOfVersionSuffix());
        if (config.isPreferPersistentEnabled() && !prefixOfSuffixWithoutTemporary.isBlank()) {
            alignmentParameters.add(
                    AdjustmentSystemPropertiesUtils.createAdjustmentSystemProperty(
                            VERSION_SUFFIX_ALTERNATIVES,
                            "redhat," + prefixOfSuffixWithoutTemporary + "-redhat"));
        }

        alignmentParameters.add(
                AdjustmentSystemPropertiesUtils
                        .createAdjustmentSystemProperty(BREW_PULL_ACTIVE, config.isBrewPullEnabled()));

        return alignmentParameters;
    }

    private Path getPathToAlignmentResultFile() {
        return CommonManipulatorResultExtractor.getAlignmentResultsFilePath(
                getDefaultAlignmentResultFile(),
                config.getSubFolderWithAlignmentResultFile().resolve("target/manipulation.json"));
    }

    private Path getDefaultAlignmentResultFile() {
        Path subFolderTargetDirectory = config.getSubFolderWithAlignmentResultFile().resolve("target");
        if (Files.notExists(subFolderTargetDirectory)) {
            try {
                Files.createDirectory(subFolderTargetDirectory);
            } catch (IOException e) {
                throw new RuntimeException("Unable to create directory for default alignment results file", e);
            }
        }
        return subFolderTargetDirectory.resolve("alignmentReport.json");
    }
}
