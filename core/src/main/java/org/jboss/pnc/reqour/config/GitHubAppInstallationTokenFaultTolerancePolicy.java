/*
 * SPDX-FileCopyrightText: Copyright © 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.config;

import jakarta.validation.constraints.Positive;
import jakarta.ws.rs.DefaultValue;

import io.smallrye.config.ConfigMapping;

/**
 * Fault Tolerance Policy for GitHubAppInstallationToken.
 */
@ConfigMapping(prefix = ConfigConstants.GITHUB_APP_INSTALLATION_TOKEN_FAULT_TOLERANCE) // CDI
public interface GitHubAppInstallationTokenFaultTolerancePolicy {

    String GITHUB_APP_INSTALLATION_TOKEN_FAULT_TOLERANCE_GUARD = "github-app-installation-token-fault-tolerance-policy";

    @DefaultValue("Tolerance policy for GitHubAppInstallationProvider")
    String description();

    RetryPolicy retry();

    interface RetryPolicy {

        @Positive
        @DefaultValue("3")
        Integer maxRetries();

        ExponentialBackoffPolicy exponentialBackoff();

        interface ExponentialBackoffPolicy {
            @Positive
            @DefaultValue("2")
            Integer factor();

            @Positive
            @DefaultValue("5")
            Integer maxDelaySeconds();
        }
    }
}
