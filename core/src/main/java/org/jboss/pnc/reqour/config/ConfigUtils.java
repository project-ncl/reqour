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

import lombok.extern.slf4j.Slf4j;

@ApplicationScoped
@Slf4j
public class ConfigUtils {

    @Inject
    ReqourConfig config;

    public Set<String> getAcceptableSchemes() {
        return config.gitConfigs().acceptableSchemes();
    }

    public GitBackendConfig getActiveGitBackend() {
        String activeGitBackendName = getActiveGitBackendName();
        return config.gitConfigs().gitBackendsConfig().availableGitBackends().get(activeGitBackendName);
    }

    public PNCHttpClientConfig getPncHttpClientConfig() {
        return config.pncHttpClientConfig();
    }

    public String getActiveGitBackendName() {
        return config.gitConfigs().gitBackendsConfig().activeGitBackend();
    }

    public Optional<String> getPrivateGithubUser() {
        return config.gitConfigs().privateGithubUser();
    }

    public Committer getCommitter() {
        return config.gitConfigs().user();
    }
}
