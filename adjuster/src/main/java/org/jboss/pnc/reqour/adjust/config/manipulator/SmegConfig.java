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
import org.jboss.pnc.reqour.adjust.model.ExecutionRootOverrides;

import java.nio.file.Path;

/**
 * Configuration of the Sbt Manipulator Extension pluGin (SMEG).
 */
@SuperBuilder(toBuilder = true)
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class SmegConfig extends CommonManipulatorConfig {

    /**
     * Path of the SBT
     */
    Path sbtPath;

    /**
     * Boolean flag whether manipulator should pull from the brew
     */
    boolean brewPullEnabled;

    /**
     * Overrides for the root GAV provided by user
     */
    ExecutionRootOverrides executionRootOverrides;
}
