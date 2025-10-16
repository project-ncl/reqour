/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.config;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.smallrye.config.WithName;
import io.smallrye.config.WithParentName;

/**
 * Configuration of all git-related stuff, e.g. git providers and acceptable schemes.
 */
public interface GitConfig {

    @WithName("git-providers")
    GitProvidersConfig gitProvidersConfig();

    Set<String> acceptableSchemes();

    Optional<String> privateGithubUser();

    Optional<List<String>> internalUrls();

    Committer user();

    interface GitProvidersConfig {

        @WithParentName
        Map<String, GitProviderConfig> gitProviders();

        @WithName("active")
        String activeGitProvider();
    }
}
