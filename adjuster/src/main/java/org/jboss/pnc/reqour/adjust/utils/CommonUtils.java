/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.utils;

import static org.jboss.pnc.reqour.adjust.utils.AdjustmentSystemPropertiesUtils.AdjustmentSystemPropertyName.VERSION_OVERRIDE;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.jboss.pnc.api.reqour.dto.VersioningState;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CommonUtils {

    private static final Path ADJUST_DIR = Path.of("/tmp/adjust");
    private static final ObjectMapper prettyPrinter = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public static Path createAdjustDirectory() {
        try {
            Files.deleteIfExists(ADJUST_DIR);
            return Files.createDirectory(ADJUST_DIR);
        } catch (IOException e) {
            throw new RuntimeException("Unable to create new empty directory for an alignment");
        }
    }

    public static Path getAdjustDir() {
        if (!Files.exists(ADJUST_DIR)) {
            throw new RuntimeException("Adjust directory should exist, but it does not");
        }
        return ADJUST_DIR;
    }

    public static String prettyPrint(Object object) {
        try {
            return prettyPrinter.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            log.warn("Unable to pretty print object {}", object);
            return null;
        }
    }

    public static VersioningState computeResultingVersioningState(
            List<String> preparedCommand,
            VersioningState versioningState) {
        final Optional<String> versionOverride = AdjustmentSystemPropertiesUtils
                .getSystemPropertyValue(VERSION_OVERRIDE, preparedCommand.stream());
        if (versionOverride.isPresent()) {
            log.info("Option to version override the project's version was specified to value: '{}'", versionOverride);
            return VersioningState.builder()
                    .executionRootName(versioningState.getExecutionRootName())
                    .executionRootVersion(versionOverride.get())
                    .build();
        } else {
            return versioningState;
        }
    }
}
