/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.picocli.runtime.annotations.TopCommand;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.pnc.api.dto.Request;
import org.jboss.pnc.api.enums.ResultStatus;
import org.jboss.pnc.api.reqour.dto.AdjustRequest;
import org.jboss.pnc.api.reqour.dto.AdjustResponse;
import org.jboss.pnc.api.reqour.dto.ReqourCallback;
import org.jboss.pnc.common.http.PNCHttpClient;
import org.jboss.pnc.reqour.adjust.config.ReqourAdjusterConfig;
import org.jboss.pnc.reqour.adjust.exception.AdjusterException;
import org.jboss.pnc.reqour.adjust.model.AdjustmentResult;
import org.jboss.pnc.reqour.adjust.model.CloningResult;
import org.jboss.pnc.reqour.adjust.provider.AdjustProvider;
import org.jboss.pnc.reqour.adjust.provider.GradleProvider;
import org.jboss.pnc.reqour.adjust.provider.MvnProvider;
import org.jboss.pnc.reqour.adjust.provider.NpmProvider;
import org.jboss.pnc.reqour.adjust.provider.SbtProvider;
import org.jboss.pnc.reqour.adjust.service.AdjustmentPusher;
import org.jboss.pnc.reqour.adjust.service.CommonManipulatorResultExtractor;
import org.jboss.pnc.reqour.adjust.service.RepositoryFetcher;
import org.jboss.pnc.reqour.adjust.service.RootGavExtractor;
import org.jboss.pnc.reqour.common.executor.process.ProcessExecutor;
import org.jboss.pnc.reqour.common.utils.IOUtils;
import org.jboss.pnc.reqour.config.ConfigUtils;
import org.slf4j.MDC;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Path;

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
public class App implements Runnable {

    @Inject
    ReqourAdjusterConfig config;

    @Inject
    ConfigUtils configUtils;

    @ConfigProperty(name = "reqour-adjuster.adjust.request")
    AdjustRequest adjustRequest;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    ProcessExecutor processExecutor;

    @Inject
    RepositoryFetcher repositoryFetcher;

    @Inject
    CommonManipulatorResultExtractor adjustResultExtractor;

    @Inject
    RootGavExtractor rootGavExtractor;

    @Inject
    AdjustmentPusher adjustmentPusher;

    private final Path workdir = IOUtils.createTempDirForAdjust();

    @Override
    public void run() {
        AdjustResponse.AdjustResponseBuilder adjustResponseBuilder = AdjustResponse.builder();

        try {
            configureMDC();
            CloningResult cloningResult = repositoryFetcher.cloneRepository(adjustRequest, workdir);
            AdjustProvider adjustProvider = pickAdjustProvider();
            AdjustmentResult adjustmentResult = adjustProvider.adjust(adjustRequest);
            adjustResponseBuilder.tag(adjustmentResult.adjustmentPushResult().tag())
                    .downstreamCommit(adjustmentResult.adjustmentPushResult().commit())
                    .internalUrl(adjustRequest.getInternalUrl())
                    .upstreamCommit(cloningResult.upstreamCommit())
                    .isRefRevisionInternal(cloningResult.isRefRevisionInternal())
                    .manipulatorResult(adjustmentResult.manipulatorResult())
                    .callback(
                            ReqourCallback.builder()
                                    .id(adjustRequest.getTaskId())
                                    .status(ResultStatus.SUCCESS)
                                    .build());
        } catch (AdjusterException e) {
            log.warn("Alignment exception occurred, setting the status to FAILED");
            log.warn("Exception was: " + e);
            adjustResponseBuilder.callback(
                    ReqourCallback.builder().id(adjustRequest.getTaskId()).status(ResultStatus.FAILED).build());
        } catch (Throwable e) {
            log.warn("Unexpected exception occurred, setting the status to SYSTEM_ERROR");
            log.warn("Exception was: " + e);
            adjustResponseBuilder.callback(
                    ReqourCallback.builder().id(adjustRequest.getTaskId()).status(ResultStatus.SYSTEM_ERROR).build());
        } finally {
            try {
                FileUtils.deleteDirectory(workdir.toFile());
            } catch (IOException e) {
                log.error(String.format("Unable to delete directory '%s' after adjustments", workdir));
            }
        }

        AdjustResponse adjustResponse = adjustResponseBuilder.build();
        sendCallback(adjustRequest.getCallback(), adjustResponse);
    }

    private void sendCallback(Request callback, AdjustResponse adjustResponse) {
        log.debug("Gonna send the callback. Payload is: {}", adjustResponse);
        PNCHttpClient pncHttpClient = new PNCHttpClient(objectMapper, configUtils.getPncHttpClientConfig());
        pncHttpClient.sendRequest(callback, adjustResponse);
    }

    AdjustProvider pickAdjustProvider() {
        return switch (adjustRequest.getBuildType()) {
            case MVN -> new MvnProvider(
                    config.adjust(),
                    adjustRequest,
                    workdir,
                    objectMapper,
                    processExecutor,
                    adjustResultExtractor,
                    rootGavExtractor,
                    adjustmentPusher);
            case GRADLE -> new GradleProvider(
                    config.adjust(),
                    adjustRequest,
                    workdir,
                    objectMapper,
                    processExecutor,
                    adjustResultExtractor,
                    adjustmentPusher);
            case NPM -> new NpmProvider(
                    config.adjust(),
                    adjustRequest,
                    workdir,
                    objectMapper,
                    processExecutor,
                    adjustmentPusher);
            case SBT -> new SbtProvider(
                    config.adjust(),
                    adjustRequest,
                    workdir,
                    objectMapper,
                    processExecutor,
                    adjustmentPusher);
        };
    }

    private void configureMDC() throws JsonProcessingException {
        MDC.clear();
        MDC.setContextMap(objectMapper.readValue(config.serializedMDC(), new TypeReference<>() {
        }));
    }
}
