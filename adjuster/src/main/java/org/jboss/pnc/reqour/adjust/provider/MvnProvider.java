/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.provider;

import static org.jboss.pnc.reqour.adjust.utils.AdjustmentSystemPropertiesUtils.AdjustmentSystemPropertyName.BREW_PULL_ACTIVE;
import static org.jboss.pnc.reqour.adjust.utils.AdjustmentSystemPropertiesUtils.AdjustmentSystemPropertyName.MANIPULATION_DISABLE;
import static org.jboss.pnc.reqour.adjust.utils.AdjustmentSystemPropertiesUtils.AdjustmentSystemPropertyName.REST_MODE;
import static org.jboss.pnc.reqour.adjust.utils.AdjustmentSystemPropertiesUtils.AdjustmentSystemPropertyName.VERSION_INCREMENTAL_SUFFIX;
import static org.jboss.pnc.reqour.adjust.utils.AdjustmentSystemPropertiesUtils.AdjustmentSystemPropertyName.VERSION_SUFFIX_ALTERNATIVES;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.commonjava.maven.ext.common.json.GAV;
import org.commonjava.maven.ext.common.json.PME;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.pnc.api.reqour.dto.AdjustRequest;
import org.jboss.pnc.api.reqour.dto.ManipulatorResult;
import org.jboss.pnc.api.reqour.dto.RemovedRepository;
import org.jboss.pnc.api.reqour.dto.VersioningState;
import org.jboss.pnc.reqour.adjust.config.AdjustConfig;
import org.jboss.pnc.reqour.adjust.config.MvnProviderConfig;
import org.jboss.pnc.reqour.adjust.config.manipulator.PmeConfig;
import org.jboss.pnc.reqour.adjust.config.manipulator.common.CommonManipulatorConfigUtils;
import org.jboss.pnc.reqour.adjust.exception.AdjusterException;
import org.jboss.pnc.reqour.adjust.model.UserSpecifiedAlignmentParameters;
import org.jboss.pnc.reqour.adjust.service.CommonManipulatorResultExtractor;
import org.jboss.pnc.reqour.adjust.service.RootGavExtractor;
import org.jboss.pnc.reqour.adjust.utils.AdjustmentSystemPropertiesUtils;
import org.jboss.pnc.reqour.common.executor.process.ProcessExecutor;
import org.jboss.pnc.reqour.common.utils.IOUtils;
import org.slf4j.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * Provider for {@link org.jboss.pnc.api.enums.BuildType#MVN} and {@link org.jboss.pnc.api.enums.BuildType#MVN_RPM}
 * builds.
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
            Logger userLogger) {
        super(objectMapper, processExecutor, userLogger);
        this.adjustResultExtractor = adjustResultExtractor;
        this.rootGavExtractor = rootGavExtractor;

        MvnProviderConfig mvnProviderConfig = adjustConfig.mvnProviderConfig();
        UserSpecifiedAlignmentParameters userSpecifiedAlignmentParameters = CommonManipulatorConfigUtils
                .parseUserSpecifiedAlignmentParameters(adjustRequest);

        final Path subFolderWithResults;
        // immutable collection returned, so let's make it mutable, and add there the results file if needed
        final List<String> userAlignmentParametersWithFile = new ArrayList<>(
                userSpecifiedAlignmentParameters.getAlignmentParameters());
        if (userSpecifiedAlignmentParameters.getLocationOption().isEmpty()) {
            subFolderWithResults = workdir;
        } else {
            userAlignmentParametersWithFile.add("--file=" + userSpecifiedAlignmentParameters.getLocationOption().get());
            Path pomFile = workdir.resolve(userSpecifiedAlignmentParameters.getLocationOption().get());
            subFolderWithResults = extractPomFileDirectory(pomFile);
        }

        config = PmeConfig.builder()
                .pncDefaultAlignmentParameters(
                        CommonManipulatorConfigUtils.transformPncDefaultAlignmentParametersIntoList(adjustRequest))
                .userSpecifiedAlignmentParameters(userAlignmentParametersWithFile)
                .restMode(CommonManipulatorConfigUtils.computeRestMode(adjustRequest, adjustConfig))
                .prefixOfVersionSuffix(
                        CommonManipulatorConfigUtils.computePrefixOfVersionSuffix(adjustRequest, adjustConfig))
                .alignmentConfigParameters(mvnProviderConfig.alignmentParameters())
                .workdir(workdir)
                .subFolderWithAlignmentResultFile(subFolderWithResults)
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
            userLogger.info("PME config was successfully initialized and validated: {}", config);
        } else {
            userLogger.info("PME config was successfully initialized: {}", config);
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
        Path javaLocation = CommonManipulatorConfigUtils.getJavaLocation(config.getUserSpecifiedAlignmentParameters());
        return AdjustmentSystemPropertiesUtils.joinSystemPropertiesListsIntoList(
                List.of(
                        List.of(javaLocation.toString(), "-jar", config.getCliJarPath().toString()),
                        getPmeSettingsParameter(),
                        config.getPncDefaultAlignmentParameters(),
                        config.getUserSpecifiedAlignmentParameters(),
                        config.getAlignmentConfigParameters(),
                        computeAlignmentParametersOverrides()));
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

    @Override
    ManipulatorResult obtainManipulatorResult() {
        if (pmeIsDisabled()) {
            log.warn("PME is disabled via extra parameters");
            createAdjustResultsFile();
        }

        VersioningState versioningState = adjustResultExtractor.obtainVersioningState(
                getPathToAlignmentResultFile(),
                config.getExecutionRootOverrides());
        log.debug("Parsed versioning state is: {}", versioningState);

        List<RemovedRepository> removedRepositories = adjustResultExtractor.obtainRemovedRepositories(
                config.getSubFolderWithAlignmentResultFile(),
                AdjustmentSystemPropertiesUtils.joinSystemPropertiesListsIntoStream(
                        List.of(
                                config.getPncDefaultAlignmentParameters(),
                                config.getUserSpecifiedAlignmentParameters(),
                                config.getAlignmentConfigParameters()))
                        .toList());
        log.debug("Parsed removed repositories are: {}", removedRepositories);

        return ManipulatorResult.builder()
                .versioningState(versioningState)
                .removedRepositories(removedRepositories)
                .build();
    }

    @Override
    public boolean failOnNoAlignmentChanges() {
        return !pmeIsDisabled();
    }

    private boolean pmeIsDisabled() {
        String pmeDisabled = AdjustmentSystemPropertiesUtils
                .getSystemPropertyValue(
                        MANIPULATION_DISABLE,
                        config.getUserSpecifiedAlignmentParameters().stream(),
                        "true")
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
        org.jboss.pnc.api.dto.GAV executionRootGav = rootGavExtractor
                .extractGav(config.getSubFolderWithAlignmentResultFile());
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

    private Path extractPomFileDirectory(Path pomFile) {
        log.debug("Extracting directory of pom file {}", pomFile);

        if (!Files.exists(pomFile)) {
            throw new AdjusterException(String.format("Pom file '%s' does not exist", pomFile));
        }
        if (!Files.isRegularFile(pomFile)) {
            throw new AdjusterException(String.format("Specified pom location '%s' is not a file", pomFile));
        }
        Path pomDirectory = pomFile.getParent();
        if (pomDirectory == null) {
            throw new AdjusterException(String.format("Cannot get the directory of the pom file '%s'", pomFile));
        }

        return pomDirectory;
    }
}
