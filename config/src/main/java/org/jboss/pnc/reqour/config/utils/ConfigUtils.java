/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.config.utils;

import java.util.HashMap;
import java.util.Optional;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.pnc.api.constants.BuildConfigurationParameterKeys;
import org.jboss.pnc.api.reqour.dto.AdjustRequest;
import org.jboss.pnc.common.http.PNCHttpClientConfig;
import org.jboss.pnc.reqour.config.core.Committer;
import org.jboss.pnc.reqour.config.core.GitProviderConfig;
import org.jboss.pnc.reqour.config.core.GitProvidersConfig;
import org.jboss.pnc.reqour.config.core.ReqourConfig;
import org.jboss.pnc.reqour.config.core.enums.GitProvider;

import lombok.extern.slf4j.Slf4j;

@ApplicationScoped
@Slf4j
public class ConfigUtils {

    @Inject
    ReqourConfig config;

    public Set<String> getAcceptableSchemes() {
        return config.gitConfigs().acceptableSchemes();
    }

    public GitProviderConfig getActiveGitProviderConfig() {
        return switch (getActiveGitProvider()) {
            case GitProvider.GITLAB -> config.gitConfigs().gitProviders().gitlab();
            case GitProvider.GITHUB -> config.gitConfigs().gitProviders().github();
        };
    }

    public PNCHttpClientConfig getPncHttpClientConfig() {
        return config.pncHttpClientConfig();
    }

    public GitProvider getActiveGitProvider() {
        GitProvidersConfig gitProvidersConfig = config.gitConfigs().gitProviders();
        if (gitProvidersConfig.gitlab().enabled()) {
            return GitProvider.GITLAB;
        }
        if (gitProvidersConfig.github().enabled()) {
            return GitProvider.GITHUB;
        }
        throw new IllegalArgumentException("No git provider is enabled");
    }

    public Optional<String> getPrivateGithubUser() {
        return config.gitConfigs().privateGithubUser();
    }

    public Committer getCommitter() {
        return config.gitConfigs().user();
    }

    public static AdjustRequest unescapeUserAlignmentParameters(AdjustRequest adjustRequest) {
        if (adjustRequest.getBuildConfigParameters() == null) {
            return adjustRequest;
        }
        var buildConfigParameters = new HashMap<>(adjustRequest.getBuildConfigParameters());

        String userAlignmentParameters = buildConfigParameters
                .get(BuildConfigurationParameterKeys.ALIGNMENT_PARAMETERS);
        if (userAlignmentParameters == null) {
            return adjustRequest;
        }

        String adjustedUserAlignmentParameters = userAlignmentParameters.replace("\\$", "$");
        buildConfigParameters
                .put(BuildConfigurationParameterKeys.ALIGNMENT_PARAMETERS, adjustedUserAlignmentParameters);

        return adjustRequest.toBuilder().buildConfigParameters(buildConfigParameters).build();
    }
}
