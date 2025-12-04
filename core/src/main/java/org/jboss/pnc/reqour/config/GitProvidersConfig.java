/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.config;

import java.util.Map;

import org.jboss.pnc.reqour.config.validation.WithExistingActiveGitProvider;

import io.quarkus.runtime.Startup;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithName;
import io.smallrye.config.WithParentName;

@ConfigMapping(prefix = ConfigConstants.GIT_PROVIDERS) // CDI
@Startup // force eager initialization in order to have validation during startup
@WithExistingActiveGitProvider
public interface GitProvidersConfig {

    @WithParentName
    Map<String, GitProviderConfig> gitProviders();

    @WithName("active")
    String activeGitProvider();

    GitProviderFaultTolerancePolicy faultTolerance();
}
