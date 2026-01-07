/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.config.rest;

import java.nio.file.Path;
import java.time.Duration;

import org.jboss.pnc.reqour.config.core.ConfigConstants;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = ConfigConstants.REST_CONFIG) // CDI
public interface ReqourRestConfig {

    Path jobDefinitionFilePath();

    String appEnvironment();

    String reqourSecretKey();

    String indyUrl();

    String saslJaasConf();

    RetryConfig openshiftRetryConfig();

    interface RetryConfig {
        @WithDefault("PT1s")
        Duration backoffInitialDelay();

        @WithDefault("PT60s")
        Duration backoffMaxDelay();

        @WithDefault("-1")
        int maxRetries();

        @WithDefault("PT5m")
        Duration maxDuration();
    }
}
