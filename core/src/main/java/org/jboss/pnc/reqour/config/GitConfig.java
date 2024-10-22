/**
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.config;

import io.smallrye.config.WithName;
import org.jboss.pnc.reqour.config.validation.WithExistingActive;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Configuration of all git-related stuff, e.g. git backends and acceptable schemes.
 */
public interface GitConfig {

    @WithName("git-backends")
    GitBackendsConfig gitBackendsConfig();

    Set<String> acceptableSchemes();

    Optional<String> privateGithubUser();

    Optional<List<String>> internalUrls();

    Committer user();

    @WithExistingActive
    interface GitBackendsConfig {

        @WithName("available")
        Map<String, GitBackendConfig> availableGitBackends();

        @WithName("active")
        String activeGitBackend();
    }
}
