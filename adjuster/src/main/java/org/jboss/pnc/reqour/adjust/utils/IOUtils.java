/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
public class IOUtils {

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
}
