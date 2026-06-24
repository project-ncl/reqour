/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.utils;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.pnc.reqour.adjust.exception.AdjusterException;
import org.jboss.pnc.reqour.common.executor.process.ProcessExecutor;
import org.jboss.pnc.reqour.config.EnvironmentConfig;
import org.jboss.pnc.reqour.config.ReqourCoreConfig;
import org.jboss.pnc.reqour.model.ProcessContext;
import org.jboss.pnc.reqour.runtime.UserLogger;
import org.slf4j.Logger;

import lombok.extern.slf4j.Slf4j;

@ApplicationScoped
@Slf4j
public class GradleCommands {

    private final String UNSPECIFIED_VERSION = "unspecified";

    @Inject
    ReqourCoreConfig coreConfig;

    @Inject
    @UserLogger
    Logger userLogger;

    @Inject
    ProcessExecutor processExecutor;

    /**
     * Given the root directory of a Gradle project, find out its name.
     *
     * @return name of the project
     */
    public String getName(Path rootDir) {
        return getProperty(rootDir, "name");
    }

    /**
     * Given the root directory of a Gradle project, find out its version.<br/>
     * Throw an exception in case no version couldn't be found.
     *
     * @return version of the project
     */
    public String getVersion(Path rootDir) {
        final String version = getProperty(rootDir, "version");
        if (UNSPECIFIED_VERSION.equals(version)) {
            throw new AdjusterException("No version for Gradle project couldn't be found");
        }
        return version;
    }

    private String getProperty(Path rootDir, String property) {
        log.debug("About to compute project's {} for a Gradle project located at {}", property, rootDir);

        final ProcessContext.Builder processContextBuilder = ProcessContext
                .withWorkdirAndConsumers(rootDir, userLogger::info, userLogger::warn)
                .extraEnvVariables(
                        Map.ofEntries(
                                // In order to locate Gradle executable, we need to specify several environment variables
                                Map.entry(EnvironmentConfig.JAVA_HOME_ENV_VARIABLE, coreConfig.envs().javaHome()),
                                Map.entry(EnvironmentConfig.PATH_ENV_VARIABLE, coreConfig.envs().path())));

        // Find out whether Gradle can be run through 'gradle' executable
        String gradleExecutable = "gradle";
        int exitCode = processExecutor
                .execute(processContextBuilder.command(List.of("sh", "-c", gradleExecutable + " --version")).build());
        if (exitCode != 0) {
            log.warn("Gradle cannot be run through '{}' executable. Trying out the gradle wrapper.", gradleExecutable);

            // If not successful, try also the Gradle wrapper
            gradleExecutable = "./gradlew";
            exitCode = processExecutor.execute(
                    processContextBuilder.command(List.of("sh", "-c", gradleExecutable + " --version")).build());
            if (exitCode != 0) {
                throw new AdjusterException("No gradle executable found");
            }
        }

        String result = processExecutor.stdout(
                processContextBuilder.command(
                        List.of(
                                "sh",
                                "-c",
                                String.format("%s properties | sed -n 's/^%s: //p'", gradleExecutable, property))))
                .stripTrailing();

        log.debug("Computed property {} is: {}", property, result);
        return result;
    }
}
