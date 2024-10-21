/**
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.provider;

import lombok.extern.slf4j.Slf4j;
import org.jboss.pnc.api.reqour.dto.AdjustRequest;
import org.jboss.pnc.reqour.adjust.config.AdjustConfig;
import org.jboss.pnc.reqour.adjust.config.NpmProviderConfig;
import org.jboss.pnc.reqour.adjust.config.manipulator.ProjectManipulatorConfig;
import org.jboss.pnc.reqour.adjust.config.manipulator.common.CommonManipulatorConfigUtils;
import org.jboss.pnc.reqour.adjust.utils.AdjustmentSystemPropertiesUtils;
import org.jboss.pnc.reqour.adjust.utils.InvalidConfigUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.jboss.pnc.reqour.adjust.utils.AdjustmentSystemPropertiesUtils.AdjustmentSystemPropertyName.REST_MODE;
import static org.jboss.pnc.reqour.adjust.utils.AdjustmentSystemPropertiesUtils.AdjustmentSystemPropertyName.VERSION_INCREMENTAL_SUFFIX;

/**
 * Provider for {@link org.jboss.pnc.api.enums.BuildType#NPM} builds.
 */
@Slf4j
public class NpmProvider extends AbstractAdjustProvider<ProjectManipulatorConfig> implements AdjustProvider {

    public NpmProvider(AdjustConfig adjustConfig, AdjustRequest adjustRequest, Path workdir) {
        super();

        NpmProviderConfig npmProviderConfig = adjustConfig.npmProviderConfig();

        config = ProjectManipulatorConfig.builder()
                .pncDefaultAlignmentParameters(
                        CommonManipulatorConfigUtils.transformPncDefaultAlignmentParametersIntoList(adjustRequest))
                .userSpecifiedAlignmentParameters(
                        CommonManipulatorConfigUtils.getExtraAdjustmentParameters(adjustRequest))
                .restMode(CommonManipulatorConfigUtils.computeRestMode(adjustRequest, adjustConfig))
                .prefixOfVersionSuffix(
                        CommonManipulatorConfigUtils.computePrefixOfVersionSuffix(adjustRequest, adjustConfig))
                .alignmentConfigParameters(npmProviderConfig.alignmentParameters())
                .workdir(workdir)
                .cliJarPath(npmProviderConfig.cliJarPath())
                .build();

        validateConfigAndPrepareCommand();
    }

    @Override
    void validateConfig() {
        InvalidConfigUtils.validateResourceAtPathExists(
                config.getCliJarPath(),
                "CLI jar file (specified as '%s') does not exist");
    }

    @Override
    List<String> prepareCommand() {
        Path resultsFile;
        try {
            resultsFile = Files.createFile(Path.of("/tmp").resolve("results" + getGeneratedNumberFromWorkdir()));
        } catch (IOException e) {
            throw new RuntimeException("Cannot create results file", e);
        }

        return AdjustmentSystemPropertiesUtils.joinSystemPropertiesListsIntoList(
                List.of(
                        List.of("java", "-jar", config.getCliJarPath().toString()),
                        config.getPncDefaultAlignmentParameters(),
                        config.getUserSpecifiedAlignmentParameters(),
                        config.getAlignmentConfigParameters(),
                        getComputedAlignmentParameters(),
                        List.of("--result=" + resultsFile)));
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

    private String getGeneratedNumberFromWorkdir() {
        Pattern p = Pattern.compile("^.*?(\\d+).*$");
        Matcher m = p.matcher(config.getWorkdir().toString());

        if (m.matches()) {
            return "-" + m.group(1);
        }
        return "";
    }
}
