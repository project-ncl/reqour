/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.config;

import java.time.temporal.ChronoUnit;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.ws.rs.DefaultValue;

import io.smallrye.config.ConfigMapping;

/**
 * Fault Tolerance Policy for Git Providers (e.g. GitHub or GitLab).
 */
@ConfigMapping(prefix = ConfigConstants.GIT_PROVIDERS_FAULT_TOLERANCE) // CDI
public interface GitProviderFaultTolerancePolicy {

    String GIT_PROVIDERS_FAULT_TOLERANCE_GUARD = "git-providers-fault-tolerance-policy";

    String description();

    RetryPolicy retry();

    TimeoutPolicy timeout();

    interface RetryPolicy {

        @Positive
        @DefaultValue("3")
        Integer maxRetries();

        @PositiveOrZero
        @DefaultValue("0")
        Integer initialDelay();

        ChronoUnit initialDelayUnit();

        ExponentialBackoffPolicy exponentialBackoff();

        interface ExponentialBackoffPolicy {
            @Positive
            @DefaultValue("2")
            Integer factor();

            @Positive
            Integer maxDelay();

            ChronoUnit maxDelayUnit();
        }
    }

    interface TimeoutPolicy {
        @Positive
        @DefaultValue("1000")
        Integer duration();

        ChronoUnit durationUnit();
    }
}
