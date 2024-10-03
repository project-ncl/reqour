/**
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.jboss.pnc.api.reqour.dto.TranslateRequest;
import org.jboss.pnc.api.reqour.dto.TranslateResponse;
import org.jboss.pnc.api.reqour.dto.validation.GitRepositoryURLValidator;
import org.jboss.pnc.reqour.common.exceptions.InvalidExternalUrlException;
import org.jboss.pnc.reqour.config.ConfigUtils;
import org.jboss.pnc.reqour.service.api.TranslationService;

@ApplicationScoped
@Slf4j
public class TranslationServiceImpl implements TranslationService {

    private static final String URL_PATH_SEPARATOR = "/";
    private static final String GIT_SUFFIX = ".git";

    private final ConfigUtils configUtils;

    @Inject
    public TranslationServiceImpl(ConfigUtils configUtils) {
        this.configUtils = configUtils;
    }

    @Override
    public TranslateResponse externalToInternal(TranslateRequest request) {
        String externalUrl = request.getExternalUrl();

        GitRepositoryURLValidator.ParsedURL url = GitRepositoryURLValidator.parseURL(request.getExternalUrl());
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

        log.debug("Provided external URL ({}), was successfully parsed to: {}", request.getExternalUrl(), url);

        String repository = adjustRepository(url.getRepository());
        String gitServer = adjustGitServer(configUtils.getActiveGitBackend().gitUrlInternalTemplate());
        String organization = computeOrganization(url.getOrganization(), gitServer);

        String repositoryName = (organization == null) ? repository : organization + URL_PATH_SEPARATOR + repository;

        return TranslateResponse.builder()
                .externalUrl(externalUrl)
                .internalUrl(computeInternalUrlFromRepositoryName(repositoryName, gitServer))
                .build();
    }

    private static String adjustRepository(String repository) {
        return repository.replace(GIT_SUFFIX, "");
    }

    private static String adjustGitServer(String gitServer) {
        return gitServer.replace(URL_PATH_SEPARATOR, "");
    }

    private String computeOrganization(String organization, String gitServer) {
        if (organization == null || isInternalPncOrganization(gitServer, organization)) {
            return null;
        }

        return organization;
    }

    private boolean isInternalPncOrganization(String gitServer, String repositoryOrganization) {
        return configUtils.getActiveGitBackendName().equals("gitlab") && gitServer.endsWith(repositoryOrganization);
    }

    private String computeInternalUrlFromRepositoryName(String repositoryName, String gitServer) {
        return gitServer + URL_PATH_SEPARATOR + repositoryName + GIT_SUFFIX;
    }
}
