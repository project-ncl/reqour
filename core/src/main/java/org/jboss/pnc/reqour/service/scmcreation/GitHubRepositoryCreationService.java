/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.service.scmcreation;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.pnc.api.enums.ResultStatus;
import org.jboss.pnc.api.reqour.dto.InternalSCMCreationRequest;
import org.jboss.pnc.api.reqour.dto.InternalSCMCreationResponse;
import org.jboss.pnc.api.reqour.dto.ReqourCallback;
import org.jboss.pnc.reqour.config.core.ConfigConstants;
import org.jboss.pnc.reqour.config.core.GitHubProviderConfig;
import org.jboss.pnc.reqour.config.core.GitProvidersConfig;
import org.jboss.pnc.reqour.model.GitHubProjectCreationResult;
import org.jboss.pnc.reqour.service.api.InternalSCMRepositoryCreationService;
import org.jboss.pnc.reqour.service.translation.GitHubTranslationService;

import io.quarkus.arc.lookup.LookupIfProperty;
import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of {@link InternalSCMRepositoryCreationService} using GitHub as its provider.
 */
@ApplicationScoped
@LookupIfProperty(name = ConfigConstants.GITHUB_PROVIDER_ENABLED, stringValue = ConfigConstants.TRUE)
@Slf4j
public class GitHubRepositoryCreationService implements InternalSCMRepositoryCreationService {

    private final GitHubProviderConfig gitHubProviderConfig;
    private final GitHubApiService gitHubApiService;
    private final GitHubTranslationService gitHubTranslationService;

    @Inject
    public GitHubRepositoryCreationService(
            GitProvidersConfig gitProvidersConfig,
            GitHubApiService gitHubApiService,
            GitHubTranslationService gitHubTranslationService) {
        this.gitHubProviderConfig = gitProvidersConfig.github();
        this.gitHubApiService = gitHubApiService;
        this.gitHubTranslationService = gitHubTranslationService;
    }

    @Override
    public InternalSCMCreationResponse createInternalSCMRepository(InternalSCMCreationRequest creationRequest) {
        String projectPath = computeProjectPath(creationRequest);

        GitHubProjectCreationResult project = gitHubApiService.getOrCreateInternalRepository(projectPath);
        return InternalSCMCreationResponse.builder()
                .readonlyUrl(
                        InternalSCMRepositoryCreationService.completeTemplateWithProjectPath(
                                gitHubProviderConfig.readOnlyTemplate(),
                                projectPath))
                .readwriteUrl(
                        InternalSCMRepositoryCreationService.completeTemplateWithProjectPath(
                                gitHubProviderConfig.readWriteTemplate(),
                                projectPath))
                .status(project.status())
                .callback(ReqourCallback.builder().status(ResultStatus.SUCCESS).id(creationRequest.getTaskId()).build())
                .build();
    }

    @Override
    public String computeProjectPath(InternalSCMCreationRequest creationRequest) {
        log.info("Computing project path for the project: {}", creationRequest.getProject());
        InternalScmRepositoryCreationCommons.Project projectParsed = InternalScmRepositoryCreationCommons
                .parseProjectPath(creationRequest.getProject());
        String projectPath = gitHubTranslationService
                .adjustRepositoryName(
                        projectParsed.repository(),
                        projectParsed.organization(),
                        gitHubProviderConfig.internalOrganizationName());
        log.info("Computed project path is: {}", projectPath);
        return projectPath;
    }
}
