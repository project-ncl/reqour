/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.config.core;

import org.jboss.pnc.reqour.config.core.validation.WithExactlyOneProviderEnabled;

import io.quarkus.runtime.Startup;
import io.smallrye.config.ConfigMapping;

@ConfigMapping(prefix = ConfigConstants.GIT_PROVIDERS) // CDI
@Startup // force eager initialization in order to have validation during startup
@WithExactlyOneProviderEnabled
public interface GitProvidersConfig {

    GitLabProviderConfig gitlab();

    GitHubProviderConfig github();

    GitProviderFaultTolerancePolicy faultTolerance();
}
