/*
 * SPDX-FileCopyrightText: Copyright © 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.config;

import io.smallrye.config.WithDefault;

/**
 * Credentials for cloning from a <em>protected</em> (private) source-code git provider — a specific
 * host + group/subgroup/organization that the <strong>origin</strong> source is cloned from.
 * <p>
 * This is intentionally distinct from {@link GitProvidersConfig} (the git-providers section), which
 * configures the <strong>push destination</strong> (where the internal mirror repository is created
 * and pushed). When a clone's origin URL falls under {@link #urlPrefix()}, these credentials are
 * applied to that clone; otherwise cloning behaves as before.
 * <p>
 * The {@code urlPrefix} entries are expected to correspond to the whitelisted groups configured in
 * Orch ({@code allowedScmGroups}).
 */
public interface ProtectedSourceCodeGitProviderConfig {

    /**
     * Host + group/subgroup prefix of the protected provider, e.g. {@code gitlab.com/lw/lw-builds}.
     * Matching against an origin URL is path-segment aware (so {@code .../lw-builds} matches
     * {@code .../lw-builds/repo} but not {@code .../lw-buildsX}).
     */
    String urlPrefix();

    /**
     * Username used in the HTTPS clone URL ({@code https://<username>:<token>@host/...}). For GitLab
     * group/project access tokens the bot username is auto-generated and not meaningful, and GitHub
     * ignores the username when the password is a valid token, so this defaults to {@code oauth2}.
     * Override only if a specific provider requires a particular username.
     */
    @WithDefault("oauth2")
    String username();

    /**
     * Access token (e.g. a GitLab group/project access token) used as the password in the HTTPS clone
     * URL. Supply via a secret / environment variable.
     */
    String token();
}
