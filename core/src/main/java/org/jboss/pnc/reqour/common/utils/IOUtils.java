/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.common.utils;

import org.apache.commons.io.FileUtils;
import org.jboss.pnc.reqour.common.exceptions.ResourceNotFoundException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class IOUtils {

    public static void ignoreOutput(String s) {
    }

    public static Path createTempDirForCloning() {
        return createTempDir("clone-", "cloning");
    }

    public static Path createTempRandomDirForAdjust() {
        return createTempDir("adjust-", "adjust");
    }

    public static Path createTempDir(String prefix, String activity) {
        try {
            return Files.createTempDirectory(prefix);
        } catch (IOException e) {
            throw new RuntimeException("Cannot create temporary directory for " + activity, e);
        }
    }

    public static void deleteTempDir(Path dir) throws IOException {
        FileUtils.deleteDirectory(dir.toFile());
    }

    public static long countLines(String text) {
        return text.lines().count();
    }

    public static List<String> splitByNewLine(String text) {
        return text.lines().toList();
    }

    public static void validateResourceAtPathExists(Path path, String errorMessageTemplate) {
        if (Files.notExists(path)) {
            throw new ResourceNotFoundException(String.format(errorMessageTemplate, path));
        }
    }

    public static String readFileContent(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new RuntimeException(String.format("Unable to read content of file '%s'", file), e);
        }
    }

    public static char transformUppercaseCharToLowercase(char c) {
        if (!('A' <= c && c <= 'Z')) {
            throw new IllegalArgumentException("Expected uppercase letter, got: '" + c + "'");
        }

        int diff = c - 'A';
        return (char) ('a' + diff);
    }
}
