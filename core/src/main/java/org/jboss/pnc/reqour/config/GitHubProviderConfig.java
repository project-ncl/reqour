/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.config;

/**
 * GitHub-specific configuration, which contains additional properties besides {@link GitProviderConfig}.
 */
public interface GitHubProviderConfig extends GitProviderConfig {

    String internalPrefix();
}
