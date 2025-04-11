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
import org.jboss.pnc.api.dto.Request;
import org.jboss.pnc.api.enums.ResultStatus;
import org.jboss.pnc.api.reqour.dto.AdjustRequest;
import org.jboss.pnc.api.reqour.dto.AdjustResponse;
import org.jboss.pnc.api.reqour.dto.ManipulatorResult;
import org.jboss.pnc.api.reqour.dto.ReqourCallback;
import org.jboss.pnc.bifrost.upload.BifrostUploadException;
import org.jboss.pnc.common.concurrent.HeartbeatScheduler;
import org.jboss.pnc.common.http.PNCHttpClient;
import org.jboss.pnc.common.log.ProcessStageUtils;
import org.jboss.pnc.reqour.adjust.config.ReqourAdjusterConfig;
import org.jboss.pnc.reqour.adjust.enums.AdjustProcessStage;
import org.jboss.pnc.reqour.adjust.exception.AdjusterException;
import org.jboss.pnc.reqour.adjust.model.AdjustmentPushResult;
import org.jboss.pnc.reqour.adjust.model.CloningResult;
import org.jboss.pnc.reqour.adjust.provider.AdjustProvider;
import org.jboss.pnc.reqour.adjust.provider.AdjustProviderPicker;
import org.jboss.pnc.reqour.adjust.service.AdjustmentPusher;
import org.jboss.pnc.reqour.adjust.service.RepositoryFetcher;
import org.jboss.pnc.reqour.adjust.utils.IOUtils;
import org.jboss.pnc.reqour.enums.FinalLogUploader;
import org.jboss.pnc.reqour.runtime.BifrostLogUploaderWrapper;
import org.jboss.pnc.reqour.runtime.UserLogger;
import org.slf4j.Logger;
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
    ObjectMapper objectMapper;

    @Inject
    PNCHttpClient pncHttpClient;

    @Inject
    RepositoryFetcher repositoryFetcher;

    @Inject
    AdjustProviderPicker adjustProviderPicker;

    @Inject
    AdjustmentPusher adjustmentPusher;

    @Inject
    BifrostLogUploaderWrapper bifrostLogUploader;

    @Inject
    @UserLogger
    Logger userLogger;

    @Inject
    HeartbeatScheduler heartbeatScheduler;

    private final Path workdir = IOUtils.createAdjustDirectory();

    @Override
    public void run() {
        AdjustRequest adjustRequest = config.adjust().request();
        AdjustResponse.AdjustResponseBuilder adjustResponseBuilder = AdjustResponse.builder();

        try {
            configureMDC();
            heartbeatScheduler.subscribeRequest(adjustRequest.getTaskId(), adjustRequest.getHeartbeatConfig());

            final CloningResult cloningResult;
            try (AutoCloseable _c = ProcessStageUtils.startCloseableStage(AdjustProcessStage.SCM_CLONE.name())) {
                cloningResult = repositoryFetcher.cloneRepository(adjustRequest, workdir);
            }

            try (AutoCloseable _c = ProcessStageUtils.startCloseableStage(AdjustProcessStage.ALIGNMENT.name())) {
                AdjustProvider adjustProvider = adjustProviderPicker.pickAdjustProvider(adjustRequest);
                ManipulatorResult manipulatorResult = adjustProvider.adjust(adjustRequest);
                AdjustmentPushResult adjustmentPushResult = adjustmentPusher
                        .pushAlignedChanges(adjustRequest, manipulatorResult);
                combineResultsOfStages(
                        adjustRequest,
                        cloningResult,
                        adjustmentPushResult,
                        adjustResponseBuilder,
                        manipulatorResult);
            }
        } catch (AdjusterException e) {
            log.warn("Alignment exception occurred, setting the status to FAILED");
            userLogger.warn("Exception was: " + e);
            adjustResponseBuilder.callback(
                    ReqourCallback.builder().id(adjustRequest.getTaskId()).status(ResultStatus.FAILED).build());
        } catch (Exception e) {
            log.warn("Unexpected exception occurred, setting the status to SYSTEM_ERROR");
            userLogger.warn("Exception was: " + e);
            adjustResponseBuilder.callback(
                    ReqourCallback.builder().id(adjustRequest.getTaskId()).status(ResultStatus.SYSTEM_ERROR).build());
        } finally {
            try {
                FileUtils.deleteDirectory(workdir.toFile());
            } catch (IOException e) {
                log.error(String.format("Unable to delete directory '%s' after adjustments", workdir));
            }

            try {
                bifrostLogUploader.uploadFileFinalLog(config.log().finalLogFilePath(), FinalLogUploader.ADJUSTER);
            } catch (BifrostUploadException e) {
                userLogger.error("Could not send final log to Bifrost, exiting with system error.", e);
                adjustResponseBuilder.callback(
                        ReqourCallback.builder()
                                .id(adjustRequest.getTaskId())
                                .status(ResultStatus.SYSTEM_ERROR)
                                .build());
            }

            AdjustResponse adjustResponse = adjustResponseBuilder.build();
            sendCallback(adjustRequest.getCallback(), adjustResponse);
        }
    }

    private void combineResultsOfStages(
            AdjustRequest adjustRequest,
            CloningResult cloningResult,
            AdjustmentPushResult adjustmentPushResult,
            AdjustResponse.AdjustResponseBuilder adjustResponseBuilder,
            ManipulatorResult manipulatorResult) {
        adjustResponseBuilder.tag(adjustmentPushResult.tag())
                .downstreamCommit(adjustmentPushResult.commit())
                .internalUrl(adjustRequest.getInternalUrl())
                .upstreamCommit(cloningResult.upstreamCommit())
                .isRefRevisionInternal(cloningResult.isRefRevisionInternal())
                .manipulatorResult(manipulatorResult)
                .callback(ReqourCallback.builder().id(adjustRequest.getTaskId()).status(ResultStatus.SUCCESS).build());
    }

    private void sendCallback(Request callback, AdjustResponse adjustResponse) {
        log.debug("Gonna send the callback. Payload is: {}", adjustResponse);
        pncHttpClient.sendRequest(callback, adjustResponse);
    }

    private void configureMDC() throws JsonProcessingException {
        MDC.clear();
        MDC.setContextMap(objectMapper.readValue(config.serializedMDC(), new TypeReference<>() {
        }));
        log.debug("Parsed MDC: {}", MDC.getCopyOfContextMap());
    }
}
