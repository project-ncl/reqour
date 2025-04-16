/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.runtime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.pnc.bifrost.upload.BifrostLogUploader;
import org.jboss.pnc.bifrost.upload.BifrostUploadException;
import org.jboss.pnc.bifrost.upload.LogMetadata;
import org.jboss.pnc.bifrost.upload.TagOption;
import org.jboss.pnc.common.log.MDCUtils;
import org.jboss.pnc.reqour.config.ReqourConfig;
import org.jboss.pnc.reqour.enums.FinalLogUploader;
import org.slf4j.Logger;

import lombok.extern.slf4j.Slf4j;

@ApplicationScoped
@Slf4j
public class BifrostLogUploaderWrapper {

    @Inject
    ReqourConfig config;

    @Inject
    BifrostLogUploader bifrostLogUploader;

    @Inject
    @UserLogger
    Logger userLogger;

    public void uploadStringFinalLog(String message, FinalLogUploader logUploader) {
        try {
            userLogger.info("Sending final log '{}' into Bifrost", message);
            bifrostLogUploader.uploadString(message, computeLogMetadata(logUploader));
        } catch (BifrostUploadException ex) {
            throw new BifrostUploadException("Unable to upload string log to Bifrost, log was: " + message, ex);
        }
    }

    public void uploadFileFinalLog(Path finalLogFilePath, FinalLogUploader logUploader) throws BifrostUploadException {
        try {
            userLogger.info("Sending final log from the file '{}' into Bifrost", finalLogFilePath);
            bifrostLogUploader.uploadFile(finalLogFilePath.toFile(), computeLogMetadata(logUploader));
        } catch (BifrostUploadException ex) {
            try {
                throw new BifrostUploadException(
                        String.format(
                                "Unable to upload logs from file '%s' to Bifrost, log was: %s",
                                finalLogFilePath,
                                Files.readString(finalLogFilePath)),
                        ex);
            } catch (IOException e) {
                log.error("Unable to read the file '{}' with the final log.", finalLogFilePath);
                throw new BifrostUploadException(
                        String.format("Unable to upload logs from file '%s' to Bifrost", finalLogFilePath));
            }
        }
    }

    private LogMetadata computeLogMetadata(FinalLogUploader logUploader) {
        return LogMetadata.builder()
                .headers(MDCUtils.getHeadersFromMDC())
                .loggerName(config.log().finalLog().uploaderBaseName() + "." + logUploader.getName())
                .tag(TagOption.ALIGNMENT_LOG)
                .endTime(OffsetDateTime.now())
                .build();
    }
}
