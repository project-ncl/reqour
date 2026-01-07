/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.config.core;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Configuration of all git-related stuff, e.g. git providers and acceptable schemes.
 */
public interface GitConfig {

    GitProvidersConfig gitProviders();

    Set<String> acceptableSchemes();

    Optional<String> privateGithubUser();

    Optional<List<String>> internalUrls();

    Committer user();
}
