/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.apache.commons.io.FileUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.pnc.api.dto.GA;
import org.jboss.pnc.api.dto.GAV;
import org.jboss.pnc.reqour.common.executor.process.ProcessExecutor;
import org.jboss.pnc.reqour.common.utils.IOUtils;
import org.jboss.pnc.reqour.model.ProcessContext;

import lombok.extern.slf4j.Slf4j;

/**
 * Extractor of the {@code groupId}, {@code artifactId}, and {@code version} from the (effective) pom. <br/>
 * It is using <a href="https://maven.apache.org/plugins/maven-help-plugin/evaluate-mojo.html">Help maven plugin</a> for
 * doing so.
 */
@ApplicationScoped
@Slf4j
public class RootGavExtractor {

    @ConfigProperty(name = "reqour-adjuster.maven-executable")
    String mavenExecutable;

    @Inject
    ProcessExecutor processExecutor;

    private final Path resultsDirectory = IOUtils
            .createTempDir("gav-extractor-results-", "recording of GAV extractor results");

    private final Path groupIdOutput = resultsDirectory.resolve("groupId.txt");
    private final Path artifactIdOutput = resultsDirectory.resolve("artifactId.txt");
    private final Path versionOutput = resultsDirectory.resolve("version.txt");

    /**
     * Extract the GAV from (effective) pom within the given working directory.
     *
     * @param workdir working directory
     */
    public GAV extractGav(Path workdir) {
        log.debug("Extracting GAV from POM in directory '{}'", workdir);
        ProcessContext.Builder processContextBuilder = ProcessContext.defaultBuilderWithWorkdir(workdir)
                .stdoutConsumer(IOUtils::ignoreOutput);

        int exitCode = 0;
        exitCode += processExecutor
                .execute(processContextBuilder.command(generateHelpEvaluateCommand("groupId", groupIdOutput)).build());
        exitCode += processExecutor.execute(
                processContextBuilder.command(generateHelpEvaluateCommand("artifactId", artifactIdOutput)).build());
        exitCode += processExecutor
                .execute(processContextBuilder.command(generateHelpEvaluateCommand("version", versionOutput)).build());

        if (exitCode != 0) {
            throw new RuntimeException(String.format("Unable to extract GAV from directory '%s'", workdir));
        }

        GAV extractedGav = GAV.builder()
                .ga(
                        GA.builder()
                                .groupId(IOUtils.readFileContent(groupIdOutput))
                                .artifactId(IOUtils.readFileContent(artifactIdOutput))
                                .build())
                .version(IOUtils.readFileContent(versionOutput))
                .build();
        try {
            FileUtils.deleteDirectory(resultsDirectory.toFile());
        } catch (IOException e) {
            log.warn("Unable to delete directory with results of GAV extractor: '{}'", resultsDirectory);
        }
        return extractedGav;
    }

    private List<String> generateHelpEvaluateCommand(String property, Path outputFile) {
        return List.of(mavenExecutable, "help:evaluate", "-Dexpression=project." + property, "-Doutput=" + outputFile);
    }
}
