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

    TimeoutPolicy timeout();

    interface RetryPolicy {

        @Positive
        @DefaultValue("3")
        Integer maxRetries();

        /**
         * Delay before the first retry, in seconds. Must be positive: the exponential backoff multiplies this value by
         * the {@link ExponentialBackoffPolicy#factor()} on each subsequent retry, so a zero initial delay would keep
         * every delay at zero and make the backoff (and {@link ExponentialBackoffPolicy#maxDelaySeconds()})
         * ineffective. It must also stay strictly below {@link ExponentialBackoffPolicy#maxDelaySeconds()}, which
         * SmallRye requires when building the backoff.
         */
        @Positive
        @DefaultValue("1")
        Integer initialDelaySeconds();

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

    interface TimeoutPolicy {

        /**
         * Maximum duration of a single attempt, in seconds. The SmallRye {@code Guard} applies the timeout inside the
         * retry, so it bounds each attempt (not the whole flow): a stuck call is interrupted after this many seconds
         * and
         * the guard then retries it per {@link RetryPolicy}.
         */
        @Positive
        @DefaultValue("60")
        Integer durationSeconds();
    }
}
