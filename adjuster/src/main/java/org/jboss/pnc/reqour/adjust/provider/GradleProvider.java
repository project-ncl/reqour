/**
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.provider;

import lombok.extern.slf4j.Slf4j;
import org.jboss.pnc.api.reqour.dto.AdjustRequest;
import org.jboss.pnc.reqour.adjust.config.AdjustConfig;
import org.jboss.pnc.reqour.adjust.config.GradleProviderConfig;
import org.jboss.pnc.reqour.adjust.config.manipulator.GmeConfig;
import org.jboss.pnc.reqour.adjust.config.manipulator.common.CommonManipulatorConfigUtils;
import org.jboss.pnc.reqour.adjust.model.UserSpecifiedAlignmentParameters;
import org.jboss.pnc.reqour.adjust.utils.AdjustmentSystemPropertiesUtils;
import org.jboss.pnc.reqour.adjust.utils.InvalidConfigUtils;

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

    public GradleProvider(AdjustConfig adjustConfig, AdjustRequest adjustRequest, Path workdir) {
        super();

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
                .workdir(workdir.resolve(userSpecifiedAlignmentParameters.getSubFolder()))
                .gradleAnalyzerPluginInitFilePath(gradleProviderConfig.gradleAnalyzerPluginInitFilePath())
                .cliJarPath(gradleProviderConfig.cliJarPath())
                .defaultGradlePath(gradleProviderConfig.defaultGradlePath())
                .isBrewPullEnabled(CommonManipulatorConfigUtils.isBrewPullEnabled(adjustRequest))
                .preferPersistentEnabled(CommonManipulatorConfigUtils.isPreferPersistentEnabled(adjustRequest))
                .build();

        validateConfigAndPrepareCommand();
    }

    @Override
    void validateConfig() {
        InvalidConfigUtils.validateResourceAtPathExists(
                config.getGradleAnalyzerPluginInitFilePath(),
                "Gradle init file (specified as '%s') does not exist");
        InvalidConfigUtils.validateResourceAtPathExists(
                config.getCliJarPath(),
                "CLI jar file (specified as '%s') does not exist");
        InvalidConfigUtils
                .validateResourceAtPathExists(config.getWorkdir(), "Directory '%s' set as working does not exist");
        InvalidConfigUtils.validateResourceAtPathExists(
                config.getDefaultGradlePath(),
                "Specified gradle path '%s' is non-existent");
    }

    @Override
    List<String> prepareCommand() {
        List<String> computedAlignmentParameters = getComputedAlignmentParameters();
        Path jvmLocation = CommonManipulatorConfigUtils.getJvmLocation(config.getUserSpecifiedAlignmentParameters());
        List<String> targetAndInit = getTargetAndInit();

        return AdjustmentSystemPropertiesUtils.joinSystemPropertiesListsIntoList(
                List.of(
                        List.of(jvmLocation.toString(), "-jar", config.getCliJarPath().toString()),
                        targetAndInit,
                        config.getPncDefaultAlignmentParameters(),
                        config.getUserSpecifiedAlignmentParameters(),
                        config.getAlignmentConfigParameters(),
                        computedAlignmentParameters));
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

        if (!gradleWrapperSpecified()) {
            targetAndInit.addAll(List.of("-l", config.getDefaultGradlePath().toString()));
        }

        return targetAndInit;
    }

    private boolean gradleWrapperSpecified() {
        return Files.exists(config.getWorkdir().resolve("gradlew"));
    }
}
