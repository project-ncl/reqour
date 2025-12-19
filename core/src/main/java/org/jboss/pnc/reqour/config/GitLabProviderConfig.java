/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.config;

import io.smallrye.config.WithName;

/**
 * Git provider configuration specific for {@link org.jboss.pnc.reqour.service.translation.GitProvider#GITLAB}.
 */
public interface GitLabProviderConfig extends GitProviderConfig {

    @WithName("workspace")
    String workspaceName();

    long workspaceId();
}
