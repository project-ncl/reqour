/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.runtime;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.jboss.pnc.bifrost.upload.BifrostLogUploader;
import org.jboss.pnc.bifrost.upload.BifrostUploadException;
import org.jboss.pnc.bifrost.upload.LogMetadata;
import org.jboss.pnc.bifrost.upload.TagOption;
import org.jboss.pnc.common.log.MDCUtils;
import org.jboss.pnc.reqour.common.utils.IOUtils;
import org.jboss.pnc.reqour.config.ReqourConfig;
import org.jboss.pnc.reqour.enums.FinalLogUploader;
import org.json.JSONObject;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;

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
            userLogger.info("Sending final log into Bifrost");
            bifrostLogUploader.uploadString(message, computeLogMetadata(logUploader));
        } catch (BifrostUploadException ex) {
            throw new BifrostUploadException("Unable to upload string log to Bifrost, log was: " + message, ex);
        }
    }

    public void uploadFileFinalLog(Path finalLogFilePath, FinalLogUploader logUploader) throws BifrostUploadException {
        Path finalLogTransformedPath = Path.of("/tmp", "final-log-transformed.txt");
        try (AutoCloseable _c = IOUtils.createFileAutoCloseable(finalLogTransformedPath);
                BufferedReader br = new BufferedReader(new FileReader(finalLogFilePath.toFile()));
                BufferedWriter bw = new BufferedWriter(new FileWriter(finalLogTransformedPath.toFile()))) {
            transformLogs(br, bw);
            userLogger.info("Sending final log from the file '{}' into Bifrost", finalLogTransformedPath);
            bifrostLogUploader.uploadFile(finalLogTransformedPath.toFile(), computeLogMetadata(logUploader));
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
                        String.format("Unable to upload logs from file '%s' to Bifrost", finalLogFilePath),
                        e);
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
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

    private void transformLogs(BufferedReader br, BufferedWriter bw) throws IOException {
        br.lines().map(originalLine -> {
            JSONObject jsonObject = new JSONObject(originalLine);
            return String.format("[%s] %s", jsonObject.getString("level"), jsonObject.getString("message"));
        }).forEach(str -> {
            try {
                bw.write(str);
                bw.newLine();
            } catch (IOException e) {
                throw new RuntimeException("Unable to write: " + str, e);
            }
        });
        bw.flush();
    }
}
