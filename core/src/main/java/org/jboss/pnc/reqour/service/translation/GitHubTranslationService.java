/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.service.translation;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.pnc.api.reqour.dto.validation.GitRepositoryURLValidator;
import org.jboss.pnc.reqour.config.ConfigConstants;
import org.jboss.pnc.reqour.config.GitHubProviderConfig;
import org.jboss.pnc.reqour.config.GitProvidersConfig;
import org.jboss.pnc.reqour.service.api.TranslationService;

import io.quarkus.arc.lookup.LookupIfProperty;
import lombok.extern.slf4j.Slf4j;

@ApplicationScoped
@LookupIfProperty(name = ConfigConstants.GITHUB_PROVIDER_ENABLED, stringValue = ConfigConstants.TRUE)
@Slf4j
public class GitHubTranslationService implements TranslationService {

    private static final String ORGANIZATION_REPOSITORY_SEPARATOR = "-";

    private final GitHubProviderConfig gitHubProviderConfig;
    private final TranslationServiceCommons translationServiceCommons;

    public GitHubTranslationService(
            GitProvidersConfig gitProvidersConfig,
            TranslationServiceCommons translationServiceCommons) {
        this.gitHubProviderConfig = gitProvidersConfig.github();
        this.translationServiceCommons = translationServiceCommons;
    }

    @Override
    public String externalToInternal(String externalUrl) {
        GitRepositoryURLValidator.ParsedURL url = translationServiceCommons.parseUrl(externalUrl);

        String internalOrganization = gitHubProviderConfig.internalOrganizationName();
        String repository = adjustRepositoryName(url.getRepository(), url.getOrganization(), internalOrganization);
        String gitServer = gitHubProviderConfig.gitUrlInternalTemplate();

        String internalUrl = TranslationServiceCommons.computeInternalUrl(gitServer, internalOrganization, repository);
        log.info("For external URL '{}' was computed corresponding internal URL as: '{}'", externalUrl, internalUrl);
        return internalUrl;
    }

    public String adjustRepositoryName(String repository, String organization, String internalOrganization) {
        String repositoryWithoutGitSuffix = TranslationServiceCommons.removeGitSuffix(repository);
        if (organization == null || organization.equals(internalOrganization)) {
            return repositoryWithoutGitSuffix;
        }
        return organization + ORGANIZATION_REPOSITORY_SEPARATOR + repositoryWithoutGitSuffix;
    }
}
