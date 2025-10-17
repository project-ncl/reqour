/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.service.translation;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.pnc.api.reqour.dto.validation.GitRepositoryURLValidator;
import org.jboss.pnc.reqour.config.ConfigConstants;
import org.jboss.pnc.reqour.config.GitHubProviderConfig;
import org.jboss.pnc.reqour.config.ReqourConfig;
import org.jboss.pnc.reqour.service.api.TranslationService;

import io.quarkus.arc.lookup.LookupIfProperty;
import lombok.extern.slf4j.Slf4j;

@ApplicationScoped
@LookupIfProperty(name = ConfigConstants.ACTIVE_GIT_PROVIDER, stringValue = "github")
@Slf4j
public class GithubTranslationService implements TranslationService {

    private static final String INTERNAL_PREFIX_SEPARATOR = "-";

    private final GitHubProviderConfig gitHubProviderConfig;
    private final TranslationServiceCommons translationServiceCommons;

    public GithubTranslationService(ReqourConfig reqourConfig, TranslationServiceCommons translationServiceCommons) {
        this.gitHubProviderConfig = reqourConfig.gitConfigs().gitProvidersConfig().githubProviderConfig();
        this.translationServiceCommons = translationServiceCommons;
    }

    @Override
    public String externalToInternal(String externalUrl) {
        GitRepositoryURLValidator.ParsedURL url = translationServiceCommons.parseUrl(externalUrl);

        String repository = TranslationServiceCommons.removeGitSuffix(url.getRepository());
        String gitServer = gitHubProviderConfig.gitUrlInternalTemplate();
        String organization = computeOrganization(url.getOrganization());

        String internalUrl = TranslationServiceCommons.computeInternalUrl(gitServer, organization, repository);
        log.info("For external URL '{}' was computed corresponding internal URL as: '{}'", externalUrl, internalUrl);
        return internalUrl;
    }

    private String computeOrganization(String organization) {
        if (organization == null || isInternalOrganization(organization)) {
            return getInternalPrefix();
        }

        return getInternalPrefix() + INTERNAL_PREFIX_SEPARATOR + organization;
    }

    private String getInternalPrefix() {
        return gitHubProviderConfig.internalPrefix();
    }

    private boolean isInternalOrganization(String organization) {
        return organization.equals(getInternalPrefix());
    }
}
