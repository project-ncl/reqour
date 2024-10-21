/**
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.config;

import io.smallrye.config.ConfigMapping;

/**
 * Configuration for the whole Reqour adjuster module.
 */
@ConfigMapping(prefix = "reqour-adjuster")
public interface ReqourAdjusterConfig {

    AdjustConfig adjust();
}
