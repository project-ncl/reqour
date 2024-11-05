/**
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.pnc.api.dto.Gav;
import org.jboss.pnc.reqour.adjust.exception.AdjusterException;
import org.jboss.pnc.reqour.common.executor.process.ProcessExecutor;
import org.jboss.pnc.reqour.common.utils.IOUtils;
import org.jboss.pnc.reqour.model.ProcessContext;

import java.nio.file.Path;
import java.util.List;

/**
 * Extractor of the {@code groupId}, {@code artifactId}, and {@code version} from the (effective) pom. <br/>
 * It is using <a href="https://maven.apache.org/plugins/maven-help-plugin/evaluate-mojo.html">Help maven plugin</a> for
 * doing so.
 */
@ApplicationScoped
@Slf4j
public class RootGavExtractor {

    @ConfigProperty(name = "quarkus.profile")
    String profile;

    @Inject
    ProcessExecutor processExecutor;

    private final Path resultsDirectory = Path.of("/tmp");
    private final Path groupIdOutput = resultsDirectory.resolve("groupId.txt");
    private final Path artifactIdOutput = resultsDirectory.resolve("artifactId.txt");
    private final Path versionOutput = resultsDirectory.resolve("version.txt");

    /**
     * Extract the GAV from (effective) pom within the given working directory.
     *
     * @param workdir working directory
     */
    public Gav extractGav(Path workdir) {
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

        return Gav.builder()
                .groupId(IOUtils.readFileContent(groupIdOutput))
                .artifactId(IOUtils.readFileContent(artifactIdOutput))
                .version(IOUtils.readFileContent(versionOutput))
                .build();
    }

    private List<String> generateHelpEvaluateCommand(String property, Path outputFile) {
        // In case we are running tests, use maven wrapper (since it's not possible to run mvn from shell during them)
        String mvnExecutable = ("test".equals(profile)) ? "./mvnw" : "mvn";

        return List.of(mvnExecutable, "help:evaluate", "-Dexpression=project." + property, "-Doutput=" + outputFile);
    }
}
