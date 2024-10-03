/**
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.config.manipulator;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.SuperBuilder;
import org.jboss.pnc.reqour.adjust.config.manipulator.common.CommonManipulatorConfig;

import java.nio.file.Path;

/**
 * Configuration of the pom manipulator (PME).
 */
@SuperBuilder(toBuilder = true)
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class PmeConfig extends CommonManipulatorConfig {

    Path cliJarPath;

    Path settingsFilePath;

    boolean isBrewPullEnabled;

    boolean preferPersistentEnabled;

    Path subFolderWithAlignmentFile;
}
