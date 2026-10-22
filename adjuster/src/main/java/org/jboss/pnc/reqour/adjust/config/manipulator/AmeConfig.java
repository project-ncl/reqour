/*
 * SPDX-FileCopyrightText: Copyright © 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.config.manipulator;

import java.nio.file.Path;

import org.jboss.pnc.reqour.adjust.config.manipulator.common.CommonManipulatorConfig;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.SuperBuilder;

@SuperBuilder(toBuilder = true)
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class AmeConfig extends CommonManipulatorConfig {

    /**
     * Location of the AME jar
     */
    Path cliJarPath;

    /**
     * Boolean flag whether manipulator should pull from the brew
     */
    boolean isBrewPullEnabled;

    /**
     * File with alignment results
     */
    Path alignmentResultFile;
}
