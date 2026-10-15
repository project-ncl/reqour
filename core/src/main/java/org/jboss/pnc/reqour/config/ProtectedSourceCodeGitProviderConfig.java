/*
 * SPDX-FileCopyrightText: Copyright © 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.config;

import io.smallrye.config.WithDefault;

/**
 * Credentials for cloning from a protected (private) source-code git provider, matched against an
 * origin URL by {@link #urlPrefix()}.
 */
public interface ProtectedSourceCodeGitProviderConfig {

    /**
     * Host + group/subgroup prefix of the protected provider, e.g. {@code gitlab.com/lw/lw-builds}.
     * Matching is path-segment aware.
     */
    String urlPrefix();

    /** Username used in the HTTPS clone URL. Defaults to {@code oauth2}, which works for token auth. */
    @WithDefault("oauth2")
    String username();

    /** Access token used as the password in the HTTPS clone URL. */
    String token();
}
