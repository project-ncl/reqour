/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.common;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.io.FileUtils;

public class RepoInitializer {

    public static void createGitRepositories(Path upstreamDir, Path downstreamDir) {
        createDirectoryIfNotExists(upstreamDir);
        createDirectoryIfNotExists(downstreamDir);

        ProcessBuilder pb = new ProcessBuilder()
                .command(
                        "scripts/configure_upstream_and_downstream.sh",
                        "-u",
                        upstreamDir.toString(),
                        "-d",
                        downstreamDir.toString())
                .directory(Path.of("src", "test", "resources").toFile());
        try {
            Process process = pb.start();
            streamProcessOutput(process.getInputStream());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void makeAlignmentChanges(Path downstreamRepository) {
        try {
            ProcessBuilder pb = new ProcessBuilder()
                    .directory(Path.of("src", "test", "resources").toFile())
                    .command("scripts/make_alignment_changes.sh", "-d", downstreamRepository.toString());
            Process process = pb.start();
            streamProcessOutput(process.getInputStream());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void removeGitRepositories(Path upstreamDir, Path downstreamDir) throws IOException {
        FileUtils.deleteDirectory(upstreamDir.toFile());
        FileUtils.deleteDirectory(downstreamDir.toFile());
    }

    public static String getUpstreamRemoteUrl(Path workdir) {
        return "file://" + workdir.resolve("upstream");
    }

    public static String getDownstreamRemoteUrl(Path workdir) {
        return "file://" + workdir.resolve("downstream");
    }

    private static void createDirectoryIfNotExists(Path dir) {
        if (!Files.exists(dir)) {
            try {
                Files.createDirectory(dir);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static void streamProcessOutput(InputStream is) {
        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        br.lines().forEach(System.out::println);
    }
}
