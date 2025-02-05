/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.config.manipulator.common;

import lombok.Value;
import lombok.experimental.NonFinal;
import lombok.experimental.SuperBuilder;

import java.nio.file.Path;
import java.util.List;

/**
 * Config, which is common for all the manipulators.
 */
@SuperBuilder(toBuilder = true)
@NonFinal
@Value
public class CommonManipulatorConfig {

    /**
     * Default alignment parameters set by PNC. Users can of course override these defaults, e.g. see
     * {@link this#userSpecifiedAlignmentParameters}.
     */
    List<String> pncDefaultAlignmentParameters;

    /**
     * User-specified alignment parameters from build parameters, see
     * {@link org.jboss.pnc.api.constants.BuildConfigurationParameterKeys#ALIGNMENT_PARAMETERS}.
     */
    List<String> userSpecifiedAlignmentParameters;

    /**
     * Alignment parameters from the config. These alignment parameters define properties crucial for correct working of
     * the alignment phase, e.g. the REST URL of the DA, which cannot be overridden by user from obvious reasons.
     */
    List<String> alignmentConfigParameters;

    /**
     * REST mode
     */
    String restMode;

    /**
     * Prefix of the build suffix version, e.g. 'redhat-'.
     */
    String prefixOfVersionSuffix;

    /**
     * Working directory, in which should the manipulator run.
     */
    Path workdir;
}
