/**
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.model;

import lombok.Builder;
import lombok.Value;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Value
@Builder(builderClassName = "Builder", toBuilder = true)
public class ProcessContext {

    List<String> command;
    Path workingDirectory;
    Map<String, String> extraEnvVariables;
    Consumer<String> stdoutConsumer;
    Consumer<String> stderrConsumer;

    public static ProcessContext.Builder defaultBuilderWithWorkdir(Path workdir) {
        if (Files.notExists(workdir)) {
            throw new RuntimeException(
                    String.format("Directory '%s' which should be working directory does not exist", workdir));
        }

        return ProcessContext.builder()
                .workingDirectory(workdir)
                .extraEnvVariables(Collections.emptyMap())
                .stdoutConsumer(System.out::println)
                .stderrConsumer(System.out::println);
    }
}
