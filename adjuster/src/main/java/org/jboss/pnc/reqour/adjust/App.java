/**
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust;

import io.quarkus.picocli.runtime.annotations.TopCommand;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.pnc.api.enums.BuildType;
import org.jboss.pnc.api.reqour.dto.AdjustRequest;
import org.jboss.pnc.api.reqour.dto.AdjustResponse;
import org.jboss.pnc.reqour.adjust.config.AdjustConfig;
import org.jboss.pnc.reqour.adjust.config.ReqourAdjusterConfig;
import org.jboss.pnc.reqour.adjust.provider.AdjustProvider;
import org.jboss.pnc.reqour.adjust.provider.GradleProvider;
import org.jboss.pnc.reqour.adjust.provider.MvnProvider;
import org.jboss.pnc.reqour.adjust.provider.NpmProvider;
import org.jboss.pnc.reqour.adjust.provider.SbtProvider;
import org.jboss.pnc.reqour.common.utils.IOUtils;
import picocli.CommandLine;

import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * The entrypoint of the reqour adjuster.
 */
@TopCommand
@CommandLine.Command(
        name = "adjust",
        description = "Execute the alignment with the corresponding built tool and manipulator",
        mixinStandardHelpOptions = true,
        versionProvider = VersionProvider.class)
@Slf4j
public class App implements Callable<AdjustResponse> {

    @Inject
    ReqourAdjusterConfig config;

    @ConfigProperty(name = "BUILD_TYPE")
    BuildType buildType;

    @ConfigProperty(name = "ADJUST_REQUEST")
    AdjustRequest adjustRequest;

    private static AdjustConfig adjustConfig;
    private static final Path workdir = IOUtils.createTempDirForAdjust();

    @Override
    public AdjustResponse call() throws Exception {
        adjustConfig = config.adjust();
        AdjustProvider adjustProvider = pickAdjustProvider();
        AdjustResponse response = adjustProvider.adjust();
        FileUtils.deleteDirectory(workdir.toFile());
        return response;
    }

    @Produces
    @ApplicationScoped
    AdjustProvider pickAdjustProvider() {
        return switch (buildType) {
            case MVN -> new MvnProvider(adjustConfig, adjustRequest, workdir);
            case NPM -> new NpmProvider(adjustConfig, adjustRequest, workdir);
            case GRADLE -> new GradleProvider(adjustConfig, adjustRequest, workdir);
            case SBT -> new SbtProvider(adjustConfig, adjustRequest, workdir);
        };
    }
}
