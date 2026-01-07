/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.config.adjuster;

import org.jboss.pnc.reqour.config.core.ConfigConstants;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithName;

/**
 * Configuration for the whole Reqour adjuster module.
 */
@ConfigMapping(prefix = ConfigConstants.ADJUSTER_CONFIG) // CDI
public interface ReqourAdjusterConfig {

    AlignmentConfig alignment();

    String mavenExecutable();

    @WithName("mdc")
    String serializedMDC();

    LogConfig log();
}
