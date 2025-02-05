/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.config.manipulator;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.SuperBuilder;
import org.jboss.pnc.reqour.adjust.config.manipulator.common.CommonManipulatorConfig;
import org.jboss.pnc.reqour.adjust.model.ExecutionRootOverrides;

import java.nio.file.Path;

/**
 * Configuration of the pom manipulator (PME).
 */
@SuperBuilder(toBuilder = true)
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class PmeConfig extends CommonManipulatorConfig {

    /**
     * Location of the PME jar
     */
    Path cliJarPath;

    /**
     * Path of the settings file, which will the PME use
     */
    Path settingsFilePath;

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
     * Sub-folder where will be alignment results file accessible once the manipulation finishes
     */
    Path subFolderWithAlignmentResultFile;

    /**
     * Overrides for the root GAV provided by user
     */
    ExecutionRootOverrides executionRootOverrides;
}
