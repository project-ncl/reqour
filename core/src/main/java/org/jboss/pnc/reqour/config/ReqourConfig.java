/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.config;

import org.jboss.pnc.common.http.PNCHttpClientConfig;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithName;

@ConfigMapping(prefix = "reqour")
public interface ReqourConfig {

    @WithName("git")
    GitConfig gitConfigs();

    PNCHttpClientConfig pncHttpClientConfig();

    LogConfig log();
}
