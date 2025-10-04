/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.common.executor.process;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.context.ManagedExecutor;
import org.jboss.pnc.reqour.common.utils.IOUtils;
import org.jboss.pnc.reqour.model.ProcessContext;
import org.jboss.pnc.reqour.runtime.UserLogger;
import org.slf4j.Logger;

import lombok.extern.slf4j.Slf4j;

@ApplicationScoped
@Slf4j
public class ProcessExecutorImpl implements ProcessExecutor {

    private final ManagedExecutor executor;
    private final Logger userLogger;

    @Inject
    public ProcessExecutorImpl(ManagedExecutor executor, @UserLogger Logger userLogger) {
        this.executor = executor;
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

        ProcessBuilder processBuilder = new ProcessBuilder(processContext.getCommand())
                .directory(processContext.getWorkingDirectory().toFile());
        processBuilder.environment().putAll(processContext.getExtraEnvVariables());

        try {
            String loggedProcessContext = logProcessContext(
                    processContext.getCommand(),
                    processContext.getWorkingDirectory(),
                    processContext.getExtraEnvVariables());
            userLogger.info("Executing {}", loggedProcessContext);
            Process processStart = processBuilder.start();
            CompletableFuture<Void> stdoutConsumerProcess = createOutputConsumerProcess(
                    processStart.inputReader(),
                    processContext.getStdoutConsumer());
            CompletableFuture<Void> stderrConsumerProcess = createOutputConsumerProcess(
                    processStart.errorReader(),
                    processContext.getStderrConsumer());

            int exitCode = processStart.waitFor();
            stdoutConsumerProcess.join();
            stderrConsumerProcess.join();
            log.debug(
                    "Command with process context {} terminated with the exit code: {}",
                    loggedProcessContext,
                    exitCode);
            return exitCode;
        } catch (InterruptedException | IOException e) {
            throw new RuntimeException(e);
        }
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

    private CompletableFuture<Void> createOutputConsumerProcess(BufferedReader reader, Consumer<String> consumer) {
        return executor.runAsync(() -> {
            String line;
            try (reader) {
                while ((line = reader.readLine()) != null) {
                    consumer.accept(line);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
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
