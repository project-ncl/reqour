/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.config;

import java.util.Optional;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.pnc.common.http.PNCHttpClientConfig;
import org.jboss.pnc.reqour.service.translation.GitProvider;

import lombok.extern.slf4j.Slf4j;

@ApplicationScoped
@Slf4j
public class ConfigUtils {

    @Inject
    ReqourCoreConfig config;

    public Set<String> getAcceptableSchemes() {
        return config.git().acceptableSchemes();
    }

    public GitProviderConfig getActiveGitProviderConfig() {
        return switch (getActiveGitProvider()) {
            case GITLAB -> config.git().gitProviders().gitlab();
            case GITHUB -> config.git().gitProviders().github();
        };
    }

    public PNCHttpClientConfig getPncHttpClientConfig() {
        return config.pncHttpClientConfig();
    }

    public GitProvider getActiveGitProvider() {
        GitProvidersConfig gitProvidersConfig = config.git().gitProviders();
        if (gitProvidersConfig.gitlab().enabled()) {
            return GitProvider.GITLAB;
        }
        if (gitProvidersConfig.github().enabled()) {
            return GitProvider.GITHUB;
        }
        throw new IllegalArgumentException("No git provider is enabled");
    }

    public String getActiveGitProvidersHostname() {
        return switch (getActiveGitProvider()) {
            case GITLAB -> config.git().gitProviders().gitlab().hostname();
            case GITHUB -> config.git().gitProviders().github().hostname();
        };
    }

    public Optional<String> getPrivateGithubUser() {
        return config.git().privateGithubUser();
    }

    public Committer getCommitter() {
        return config.git().user();
    }
}
