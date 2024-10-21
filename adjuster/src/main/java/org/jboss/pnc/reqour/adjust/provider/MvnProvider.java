/**
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.provider;

import lombok.extern.slf4j.Slf4j;
import org.jboss.pnc.api.reqour.dto.AdjustRequest;
import org.jboss.pnc.reqour.adjust.config.AdjustConfig;
import org.jboss.pnc.reqour.adjust.config.MvnProviderConfig;
import org.jboss.pnc.reqour.adjust.config.manipulator.PmeConfig;
import org.jboss.pnc.reqour.adjust.config.manipulator.common.CommonManipulatorConfigUtils;
import org.jboss.pnc.reqour.adjust.model.UserSpecifiedAlignmentParameters;
import org.jboss.pnc.reqour.adjust.utils.AdjustmentSystemPropertiesUtils;
import org.jboss.pnc.reqour.adjust.utils.InvalidConfigUtils;

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

    public MvnProvider(AdjustConfig adjustConfig, AdjustRequest adjustRequest, Path workdir) {
        super();

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
                .subFolderWithAlignmentFile(workdir.resolve(userSpecifiedAlignmentParameters.getSubFolder()))
                .cliJarPath(mvnProviderConfig.cliJarPath())
                .settingsFilePath(
                        getPathToSettingsFile(
                                adjustRequest,
                                mvnProviderConfig.defaultSettingsFilePath(),
                                mvnProviderConfig.temporarySettingsFilePath()))
                .isBrewPullEnabled(CommonManipulatorConfigUtils.isBrewPullEnabled(adjustRequest))
                .preferPersistentEnabled(CommonManipulatorConfigUtils.isPreferPersistentEnabled(adjustRequest))
                .build();

        validateConfigAndPrepareCommand();
    }

    @Override
    void validateConfig() {
        InvalidConfigUtils.validateResourceAtPathExists(
                config.getCliJarPath(),
                "CLI jar file (specified at '%s') does not exist");

        InvalidConfigUtils.validateResourceAtPathExists(
                config.getSettingsFilePath(),
                "File with default settings (specified at '%s') does not exist");
    }

    @Override
    List<String> prepareCommand() {
        List<String> userSpecifiedAlignmentParameters = config.getUserSpecifiedAlignmentParameters();
        if (!config.getSubFolderWithAlignmentFile().equals(config.getWorkdir())) {
            userSpecifiedAlignmentParameters.add("--file=" + config.getSubFolderWithAlignmentFile());
        }
        Path jvmLocation = CommonManipulatorConfigUtils.getJvmLocation(userSpecifiedAlignmentParameters);

        return AdjustmentSystemPropertiesUtils.joinSystemPropertiesListsIntoList(
                List.of(
                        List.of(jvmLocation.toString(), "-jar", config.getCliJarPath().toString()),
                        getPmeSettingsParameter(),
                        config.getPncDefaultAlignmentParameters(),
                        config.getUserSpecifiedAlignmentParameters(),
                        config.getAlignmentConfigParameters(),
                        getComputedAlignmentParameters()));
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
}
