/**
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.provider;

import lombok.extern.slf4j.Slf4j;
import org.jboss.pnc.api.reqour.dto.AdjustRequest;
import org.jboss.pnc.reqour.adjust.config.AdjustConfig;
import org.jboss.pnc.reqour.adjust.config.SbtProviderConfig;
import org.jboss.pnc.reqour.adjust.config.manipulator.SmegConfig;
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

/**
 * Provider for {@link org.jboss.pnc.api.enums.BuildType#SBT} builds.
 */
@Slf4j
public class SbtProvider extends AbstractAdjustProvider<SmegConfig> implements AdjustProvider {

    public SbtProvider(AdjustConfig adjustConfig, AdjustRequest adjustRequest, Path workdir) {
        super();

        SbtProviderConfig sbtProviderConfig = adjustConfig.scalaProviderConfig();
        UserSpecifiedAlignmentParameters userSpecifiedAlignmentParameters = CommonManipulatorConfigUtils
                .parseUserSpecifiedAlignmentParameters(adjustRequest);

        config = SmegConfig.builder()
                .pncDefaultAlignmentParameters(
                        CommonManipulatorConfigUtils.transformPncDefaultAlignmentParametersIntoList(adjustRequest))
                .userSpecifiedAlignmentParameters(userSpecifiedAlignmentParameters.getAlignmentParameters())
                .restMode(CommonManipulatorConfigUtils.computeRestMode(adjustRequest, adjustConfig))
                .prefixOfVersionSuffix(
                        CommonManipulatorConfigUtils.computePrefixOfVersionSuffix(adjustRequest, adjustConfig))
                .alignmentConfigParameters(sbtProviderConfig.alignmentParameters())
                .workdir(workdir.resolve(userSpecifiedAlignmentParameters.getSubFolder()))
                .sbtPath(sbtProviderConfig.sbtPath())
                .build();

        validateConfigAndPrepareCommand();
    }

    @Override
    void validateConfig() {
        InvalidConfigUtils.validateResourceAtPathExists(
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
                        getComputedAlignmentParameters(),
                        List.of("manipulate", "writeReport")));
    }

    private List<String> getComputedAlignmentParameters() {
        final List<String> alignmentParameters = new ArrayList<>();

        alignmentParameters
                .add(AdjustmentSystemPropertiesUtils.createAdjustmentSystemProperty(REST_MODE, config.getRestMode()));
        alignmentParameters.add(
                AdjustmentSystemPropertiesUtils
                        .createAdjustmentSystemProperty(BREW_PULL_ACTIVE, config.isBrewPullEnabled()));
        if (!config.getPrefixOfVersionSuffix().isBlank()) {
            alignmentParameters.add(
                    AdjustmentSystemPropertiesUtils.createAdjustmentSystemProperty(
                            VERSION_INCREMENTAL_SUFFIX,
                            config.getPrefixOfVersionSuffix() + "-redhat"));
        }

        return alignmentParameters;
    }
}
