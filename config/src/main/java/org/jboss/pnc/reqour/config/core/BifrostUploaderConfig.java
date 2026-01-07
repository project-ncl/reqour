/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.config.core;

import java.net.URI;

import io.smallrye.config.WithDefault;

public interface BifrostUploaderConfig {

    URI baseUrl();

    @WithDefault("6")
    int maxRetries();

    @WithDefault("10")
    int retryDelay();
}
