/**
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.config;

import io.smallrye.config.ConfigMapping;

import java.nio.file.Path;

/**
 * Configuration for the whole Reqour adjuster module.
 */
@ConfigMapping(prefix = "reqour-adjuster")
public interface ReqourAdjusterConfig {

    /**
     * Location of the input request. This is identical request as is obtained in the {@code POST /adjust} endpoint of
     * the {@code reqour-rest} module.
     */
    Path inputLocation();

    AdjustConfig adjust();
}
