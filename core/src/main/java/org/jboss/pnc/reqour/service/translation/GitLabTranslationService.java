/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.service.translation;

import static org.jboss.pnc.reqour.service.translation.TranslationServiceCommons.URL_PATH_SEPARATOR;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.pnc.api.reqour.dto.validation.GitRepositoryURLValidator;
import org.jboss.pnc.reqour.config.ConfigConstants;
import org.jboss.pnc.reqour.config.ConfigUtils;
import org.jboss.pnc.reqour.config.GitProviderConfig;
import org.jboss.pnc.reqour.service.api.TranslationService;

import io.quarkus.arc.lookup.LookupIfProperty;
import lombok.extern.slf4j.Slf4j;

/**
 * Translation service used when the git provider is GitLab.
 */
@ApplicationScoped
@LookupIfProperty(name = ConfigConstants.ACTIVE_GIT_PROVIDER, stringValue = ConfigConstants.GITLAB)
@Slf4j
public class GitLabTranslationService implements TranslationService {

    private final GitProviderConfig gitLabProviderConfig;
    private final TranslationServiceCommons translationServiceCommons;

    public GitLabTranslationService(ConfigUtils configUtils, TranslationServiceCommons translationServiceCommons) {
        this.gitLabProviderConfig = configUtils.getActiveGitProviderConfig();
        this.translationServiceCommons = translationServiceCommons;
    }

    @Override
    public String externalToInternal(String externalUrl) {
        GitRepositoryURLValidator.ParsedURL url = translationServiceCommons.parseUrl(externalUrl);

        String repository = TranslationServiceCommons.removeGitSuffix(url.getRepository());
        String gitServerWithWorkspace = gitLabProviderConfig.gitUrlInternalTemplate();
        String organization = computeOrganization(gitServerWithWorkspace, url.getOrganization());

        String internalUrl = TranslationServiceCommons
                .computeInternalUrl(gitServerWithWorkspace, organization, repository);
        log.info("For external URL '{}' was computed corresponding internal URL as: '{}'", externalUrl, internalUrl);
        return internalUrl;
    }

    private String computeOrganization(String gitServerWithWorkspace, String organization) {
        if (organization == null || isInternalOrganization(gitServerWithWorkspace, organization)) {
            return null;
        }

        return organization;
    }

    private boolean isInternalOrganization(String gitServerWithWorkspace, String organization) {
        return gitServerWithWorkspace.endsWith(organization + URL_PATH_SEPARATOR);
    }
}
