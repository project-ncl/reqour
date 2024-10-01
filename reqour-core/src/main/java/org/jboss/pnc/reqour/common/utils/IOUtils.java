/**
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.common.utils;

import org.apache.commons.io.FileUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class IOUtils {

    public static void ignoreOutput(String s) {
    }

    public static Path createTempDirForCloning() {
        try {
            return Files.createTempDirectory("clone-");
        } catch (IOException e) {
            throw new RuntimeException("Cannot create temporary directory for cloning", e);
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
}
