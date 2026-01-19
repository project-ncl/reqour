/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.config;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import jakarta.ws.rs.DefaultValue;

/**
 * Configuration of all git-related stuff, e.g. git providers and acceptable schemes.
 */
public interface GitConfig {

    GitProvidersConfig gitProviders();

    Set<String> acceptableSchemes();

    Optional<String> privateGithubUser();

    Optional<List<String>> internalUrls();

    /**
     * Boolean flag whether internal URLs provided in some requests should be validated towards active git provider's
     * hostname.<br/>
     * For instance, when GitHub@IBM is set as active git provider, requests with GitLab@RedHat as its internal URL
     * should fail.
     */
    @DefaultValue("true")
    boolean validateInternalUrl();

    Committer user();
}
