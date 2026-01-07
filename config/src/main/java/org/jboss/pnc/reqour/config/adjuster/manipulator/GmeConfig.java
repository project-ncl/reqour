/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.config.adjuster.manipulator;

import java.nio.file.Path;

import org.jboss.pnc.reqour.config.adjuster.manipulator.common.CommonManipulatorConfig;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.SuperBuilder;

/**
 * Configuration of the gradle manipulator (GME).
 */
@SuperBuilder(toBuilder = true)
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class GmeConfig extends CommonManipulatorConfig {

    /**
     * Path of the {@code analyzer-init.gradle} file
     */
    Path gradleAnalyzerPluginInitFilePath;

    /**
     * Location of the GME jar
     */
    Path cliJarPath;

    /**
     * Default gradle path used in case a user does not provide the gradle wrapper for the build.
     */
    Path defaultGradlePath;

    /**
     * Boolean flag whether manipulator should pull from the brew
     */
    boolean isBrewPullEnabled;

    /**
     * Boolean flag whether manipulator should use {@link org.jboss.pnc.api.enums.AlignmentPreference#PREFER_PERSISTENT}
     * preference
     */
    boolean preferPersistentEnabled;

    /**
     * Overrides for the root GAV provided by user
     */
    ExecutionRootOverrides executionRootOverrides;
}
