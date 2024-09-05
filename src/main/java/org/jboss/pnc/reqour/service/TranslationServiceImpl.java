/**
 * JBoss, Home of Professional Open Source.
 * Copyright 2024-2024 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
import org.jboss.pnc.reqour.model.GitBackend;

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
        String gitServer = adjustGitServer(configUtils.getActiveGitBackend().getGitUrlInternalTemplate());
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
        if (organization == null) {
            return null;
        }

        GitBackend activeGitBackend = configUtils.getActiveGitBackend();
        if (activeGitBackend.getName().equals("gitlab")
                && !repositoryOrganizationManagesGitServer(gitServer, organization)) {
            return organization;
        }
        return null;
    }

    private boolean repositoryOrganizationManagesGitServer(String gitServer, String repositoryOrganization) {
        return gitServer.endsWith(repositoryOrganization);
    }

    private String computeInternalUrlFromRepositoryName(String repositoryName, String gitServer) {
        return gitServer + URL_PATH_SEPARATOR + repositoryName + GIT_SUFFIX;
    }
}
