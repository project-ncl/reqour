/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.service.translation;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.pnc.api.reqour.dto.validation.GitRepositoryURLValidator;
import org.jboss.pnc.reqour.common.exceptions.InvalidExternalUrlException;
import org.jboss.pnc.reqour.config.ConfigUtils;

import lombok.extern.slf4j.Slf4j;

@ApplicationScoped
@Slf4j
public class TranslationServiceCommons {

    static final String GIT_SUFFIX = ".git";
    static final String URL_PATH_SEPARATOR = "/";

    @Inject
    ConfigUtils configUtils;

    public GitRepositoryURLValidator.ParsedURL parseUrl(String externalUrl) {
        GitRepositoryURLValidator.ParsedURL url = GitRepositoryURLValidator.parseURL(externalUrl);
        if (url == null) {
            log.debug("Invalid external URL provided: {}", externalUrl);
            throw new InvalidExternalUrlException("Invalid external URL provided: " + externalUrl);
        }

        if (url.getProtocol() != null && !configUtils.getAcceptableSchemes().contains(url.getProtocol())) {
            log.debug("Invalid protocol of external URL provided: {}", externalUrl);
            throw new InvalidExternalUrlException(
                    "Invalid protocol (" + url.getProtocol() + ") given. Available protocols are: "
                            + configUtils.getAcceptableSchemes());
        }

        log.info("Provided external URL ({}), was successfully parsed to: {}", externalUrl, url);
        return url;
    }

    static String removeGitSuffix(String repository) {
        return repository.replace(GIT_SUFFIX, "");
    }

    static String computeInternalUrl(String gitServer, String organization, String repository) {
        return gitServer + ((organization == null) ? repository : organization + URL_PATH_SEPARATOR + repository)
                + GIT_SUFFIX;
    }
}
