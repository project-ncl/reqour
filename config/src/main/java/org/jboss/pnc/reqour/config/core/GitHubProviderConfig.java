/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.config.core;

import io.smallrye.config.WithName;

/**
 * Git provider configuration specific for {@link org.jboss.pnc.reqour.service.translation.GitProvider#GITHUB}.
 */
public interface GitHubProviderConfig extends GitProviderConfig {

    @WithName("internal-organization")
    String internalOrganizationName();
}
