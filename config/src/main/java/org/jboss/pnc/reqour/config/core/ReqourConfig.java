/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.config.core;

import org.jboss.pnc.common.http.PNCHttpClientConfig;
import org.jboss.pnc.reqour.config.adjuster.ReqourAdjusterConfig;
import org.jboss.pnc.reqour.config.rest.ReqourRestConfig;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithName;

@ConfigMapping(prefix = "reqour")
public interface ReqourConfig {

    @WithName("git")
    GitConfig gitConfigs();

    LogConfig log();

    PNCHttpClientConfig pncHttpClientConfig();

    ReqourAdjusterConfig adjuster();

    ReqourRestConfig rest();
}
