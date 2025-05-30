/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.common;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;

import org.apache.commons.io.FileUtils;

public class RepoInitializer {

    public static void createRepositories(Path workdir) {
        ProcessBuilder pb = new ProcessBuilder()
                .command("./configure_upstream_and_downstream.sh", workdir.toString())
                .directory(Path.of("src", "test", "resources").toFile());
        try {
            Process process = pb.start();
            BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()));
            br.lines().forEach(System.out::println);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void removeRepositories(Path workdir) throws IOException {
        FileUtils.deleteDirectory(workdir.toFile());
    }

    public static String getUpstreamPath(Path workdir) {
        return "file://" + workdir.resolve("upstream");
    }

    public static String getDownstreamPath(Path workdir) {
        return "file://" + workdir.resolve("downstream");
    }
}
