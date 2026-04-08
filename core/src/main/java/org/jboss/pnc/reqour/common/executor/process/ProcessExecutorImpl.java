/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.common.executor.process;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.pnc.reqour.common.utils.IOUtils;
import org.jboss.pnc.reqour.model.ProcessContext;
import org.jboss.pnc.reqour.runtime.UserLogger;
import org.slf4j.Logger;

import io.smallrye.common.process.ProcessBuilder;
import lombok.extern.slf4j.Slf4j;

@ApplicationScoped
@Slf4j
public class ProcessExecutorImpl implements ProcessExecutor {

    private final Logger userLogger;
    private final static int MAX_LINE_LENGTH = 8192;

    @Inject
    public ProcessExecutorImpl(@UserLogger Logger userLogger) {
        this.userLogger = userLogger;
    }

    @Override
    public int execute(ProcessContext processContext) {
        if (Files.notExists(processContext.getWorkingDirectory())) {
            throw new IllegalArgumentException(
                    "Directory at the path " + processContext.getWorkingDirectory() + " does not exist");
        }
        if (!Files.isDirectory(processContext.getWorkingDirectory())) {
            throw new IllegalArgumentException(processContext.getWorkingDirectory() + " is not a directory");
        }

        final var holder = new ExitCodeHolder();
        final List<String> commandArgs = processContext.getCommand().subList(1, processContext.getCommand().size());
        final var processBuilder = ProcessBuilder.newBuilder(processContext.getCommand().getFirst())
                .arguments(commandArgs)
                .directory(processContext.getWorkingDirectory())
                .environment(Optional.ofNullable(processContext.getExtraEnvVariables()).orElse(Collections.emptyMap()))
                .exitCodeChecker(x -> {
                    holder.setExitCode(x);
                    // process is allowed to exit with any status code, it's being checked at higher levels
                    // e.g. whether specific processes are allowed to exit with non-zero status codes
                    return true;
                });

        String loggedProcessContext = logProcessContext(
                processContext.getCommand(),
                processContext.getWorkingDirectory(),
                processContext.getExtraEnvVariables());
        userLogger.info("Executing {}", loggedProcessContext);

        // Configure consumers for stdout and stderr
        processBuilder.output().consumeLinesWith(MAX_LINE_LENGTH, processContext.getStdoutConsumer()::accept);
        processBuilder.error().consumeLinesWith(MAX_LINE_LENGTH, processContext.getStderrConsumer()::accept);

        // Run with configured consumers
        processBuilder.run();
        int exitCode = holder.getExitCode();
        log.debug(
                "Command with process context {} terminated with the exit code: {}",
                loggedProcessContext,
                exitCode);
        return exitCode;
    }

    @Override
    public String stdout(ProcessContext.Builder processContextBuilder) {
        StringBuilder sb = new StringBuilder();

        ProcessContext processContext = processContextBuilder
                .stdoutConsumer(s -> sb.append(s).append(System.lineSeparator()))
                .build();

        execute(processContext);
        return sb.toString();
    }

    static String logProcessContext(
            List<String> command,
            Path workingDirectory,
            Map<String, String> extraEnvVariables) {
        final StringBuilder result = new StringBuilder();
        if (workingDirectory != null) {
            IOUtils.quoteIfNeeded(result.append("cd "), workingDirectory.toString()).append(" && ");
        }
        if (extraEnvVariables != null && !extraEnvVariables.isEmpty()) {
            extraEnvVariables.forEach((k, v) -> IOUtils.quoteIfNeeded(result.append(k).append("="), v).append(' '));
        }
        command.forEach(arg -> IOUtils.quoteIfNeeded(result, arg).append(' '));
        final int newLen = result.length() - 1;
        if (newLen >= 0 && result.charAt(newLen) == ' ') {
            result.setLength(newLen);
        }
        return result.toString();
    }
}
