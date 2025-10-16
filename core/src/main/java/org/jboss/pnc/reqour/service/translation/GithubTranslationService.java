/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.service.translation;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.pnc.api.reqour.dto.validation.GitRepositoryURLValidator;
import org.jboss.pnc.reqour.config.ConfigUtils;
import org.jboss.pnc.reqour.config.GitProviderConfig;
import org.jboss.pnc.reqour.service.api.TranslationService;

import io.quarkus.arc.lookup.LookupIfProperty;
import lombok.extern.slf4j.Slf4j;

@ApplicationScoped
@LookupIfProperty(name = "reqour.git.git-providers.active", stringValue = "github")
@Slf4j
public class GithubTranslationService implements TranslationService {

    private static final String ORGANIZATION_REPOSITORY_SEPARATOR = "-";

    private final GitProviderConfig gitProviderConfig;
    private final TranslationServiceCommons translationServiceCommons;

    public GithubTranslationService(ConfigUtils configUtils, TranslationServiceCommons translationServiceCommons) {
        this.gitProviderConfig = configUtils.getActiveGitProviderConfig();
        this.translationServiceCommons = translationServiceCommons;
    }

    @Override
    public String externalToInternal(String externalUrl) {
        GitRepositoryURLValidator.ParsedURL url = translationServiceCommons.parseUrl(externalUrl);

        String internalOrganization = gitProviderConfig.workspaceName();
        String repository = adjustRepositoryName(url.getRepository(), url.getOrganization(), internalOrganization);
        String gitServer = gitProviderConfig.gitUrlInternalTemplate();

        String internalUrl = TranslationServiceCommons.computeInternalUrl(gitServer, internalOrganization, repository);
        log.info("For external URL '{}' was computed corresponding internal URL as: '{}'", externalUrl, internalUrl);
        return internalUrl;
    }

    private String adjustRepositoryName(String repository, String organization, String internalOrganization) {
        String repositoryWithoutGitSuffix = TranslationServiceCommons.removeGitSuffix(repository);
        if (organization == null || organization.equals(internalOrganization)) {
            return repositoryWithoutGitSuffix;
        }
        return organization + ORGANIZATION_REPOSITORY_SEPARATOR + repositoryWithoutGitSuffix;
    }
}
