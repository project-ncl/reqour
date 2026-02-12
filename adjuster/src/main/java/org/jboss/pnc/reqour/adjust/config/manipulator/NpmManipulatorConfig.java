/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.config.manipulator;

import java.nio.file.Path;

import org.jboss.pnc.reqour.adjust.config.manipulator.common.CommonManipulatorConfig;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.SuperBuilder;

/**
 * Configuration of the NPM manipulator.
 */
@SuperBuilder(toBuilder = true)
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class NpmManipulatorConfig extends CommonManipulatorConfig {

    /**
     * Location of the manipulator's jar
     */
    Path cliJarPath;

    /**
     * The path to manipulation results file
     */
    Path resultsFilePath;
}
