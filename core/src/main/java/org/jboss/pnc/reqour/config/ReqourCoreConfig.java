/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.config;

import org.jboss.pnc.common.http.PNCHttpClientConfig;

import io.smallrye.config.ConfigMapping;

/**
 * Configuration of the reqour-core module
 */
@ConfigMapping(prefix = ConfigConstants.REQOUR_CORE_CONFIG)
public interface ReqourCoreConfig {

    GitConfig git();

    LogConfig log();

    PNCHttpClientConfig pncHttpClientConfig();
}
