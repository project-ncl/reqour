/**
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust;

import io.quarkus.picocli.runtime.annotations.TopCommand;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.apache.commons.io.FileUtils;
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

import java.io.IOException;
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

    @Inject
    ObjectMapper objectMapper;

    @CommandLine.Parameters(description = "Adjust type", arity = "1")
    BuildType type;

    @CommandLine.Option(
            names = { "-i", "--input-file" },
            description = "Location of the input file (with the request). If not present, defaults to what is int the config (see 'reqour-adjuster.input-location').",
            arity = "0..1")
    Path inputLocation;

    private static AdjustConfig adjustConfig;
    private static AdjustRequest adjustRequest;
    private static final Path workdir = IOUtils.createTempDirForAdjust();

    @Override
    public AdjustResponse call() throws Exception {
        adjustConfig = config.adjust();
        adjustRequest = getAdjustRequest();
        AdjustProvider adjustProvider = pickAdjustProvider();
        AdjustResponse response = adjustProvider.adjust();
        FileUtils.deleteDirectory(workdir.toFile());
        return response;
    }

    @Produces
    @ApplicationScoped
    AdjustProvider pickAdjustProvider() {
        return switch (type) {
            case MVN -> new MvnProvider(adjustConfig, adjustRequest, workdir);
            case NPM -> new NpmProvider(adjustConfig, adjustRequest, workdir);
            case GRADLE -> new GradleProvider(adjustConfig, adjustRequest, workdir);
            case SBT -> new SbtProvider(adjustConfig, adjustRequest, workdir);
        };
    }

    private AdjustRequest getAdjustRequest() {
        Path requestLocation = getInputFileLocation();
        try {
            AdjustRequest request = objectMapper.readValue(requestLocation.toFile(), AdjustRequest.class);
            log.debug("Parsed request: {}", request);
            return request;
        } catch (IOException e) {
            throw new RuntimeException("Error occurred when reading the request from file " + requestLocation, e);
        }
    }

    private Path getInputFileLocation() {
        if (inputLocation == null) {
            log.debug(
                    "Input file not specified as CLI argument. Hence, using the default input location from the config: {}",
                    config.inputLocation());
            return config.inputLocation();
        }

        log.debug("Input file CLI argument specified as: {}", inputLocation);
        return inputLocation;
    }
}
