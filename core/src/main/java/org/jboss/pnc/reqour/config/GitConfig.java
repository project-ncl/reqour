/*
 * SPDX-FileCopyrightText: Copyright © 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.config;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.smallrye.config.WithDefault;

/**
 * Configuration of all git-related stuff, e.g. git providers and acceptable schemes.
 */
public interface GitConfig {

    GitProvidersConfig gitProviders();

    /**
     * Credentials for cloning from protected (private) source-code providers, keyed by host+group
     * prefix. Used only for the origin clone when a match is found; unrelated to {@link #gitProviders()}
     * (the push destination). Absent/empty = previous behavior for all origins.
     */
    Optional<List<ProtectedSourceCodeGitProviderConfig>> protectedSourceCodeGitProviders();

    /**
     * Optional forward proxy used only when cloning the origin of a matched protected source provider
     * (e.g. a firewalled env that can reach GitLab only via an authenticated proxy). Absent = no proxy.
     */
    Optional<SourceCloneProxyConfig> sourceCloneProxy();

    Set<String> acceptableSchemes();

    Optional<String> privateGithubUser();

    Optional<List<String>> internalUrls();

    /**
     * Boolean flag whether internal URLs provided in some requests should be validated towards active git provider's
     * hostname.<br/>
     * For instance, when GitHub@IBM is set as active git provider, requests with GitLab@RedHat as its internal URL
     * should fail.
     */
    @WithDefault("true")
    boolean validateInternalUrl();

    Committer user();
}
