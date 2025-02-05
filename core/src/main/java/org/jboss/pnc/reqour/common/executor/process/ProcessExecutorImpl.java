/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.common.executor.process;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.jboss.pnc.reqour.model.ProcessContext;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

@ApplicationScoped
@Slf4j
public class ProcessExecutorImpl implements ProcessExecutor {

    private final ManagedExecutor executor;

    @Inject
    public ProcessExecutorImpl(ManagedExecutor executor) {
        this.executor = executor;
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
            log.info("Executing the command with the process context: {}", loggedProcessContext);
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
            log.info(
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

    private static String logProcessContext(
            List<String> command,
            Path workingDirectory,
            Map<String, String> extraEnvVariables) {
        return String.format(
                "{command: %s, working directory: %s, extra env variables: %s}",
                command,
                workingDirectory,
                extraEnvVariables);
    }
}
