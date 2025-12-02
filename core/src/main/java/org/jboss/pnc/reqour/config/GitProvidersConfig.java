/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.config;

import java.util.Map;

import io.smallrye.config.WithName;
import io.smallrye.config.WithParentName;

public interface GitProvidersConfig {

    @WithParentName
    Map<String, GitProviderConfig> gitProviders();

    @WithName("active")
    String activeGitProvider();

    GitProviderFaultTolerancePolicy faultTolerance();
}
