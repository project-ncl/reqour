/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.service.scmcreation;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.gitlab4j.api.models.Group;
import org.jboss.pnc.api.enums.InternalSCMCreationStatus;
import org.jboss.pnc.api.enums.ResultStatus;
import org.jboss.pnc.api.reqour.dto.InternalSCMCreationRequest;
import org.jboss.pnc.api.reqour.dto.InternalSCMCreationResponse;
import org.jboss.pnc.api.reqour.dto.ReqourCallback;
import org.jboss.pnc.reqour.config.ConfigConstants;
import org.jboss.pnc.reqour.config.GitLabProviderConfig;
import org.jboss.pnc.reqour.config.GitProvidersConfig;
import org.jboss.pnc.reqour.model.GitLabProjectCreationResult;
import org.jboss.pnc.reqour.service.api.InternalSCMRepositoryCreationService;

import io.quarkus.arc.lookup.LookupIfProperty;
import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of {@link InternalSCMRepositoryCreationService} using GitLab as its provider.
 */
@ApplicationScoped
@LookupIfProperty(name = ConfigConstants.GITLAB_PROVIDER_ENABLED, stringValue = ConfigConstants.TRUE)
@Slf4j
public class GitLabRepositoryCreationService implements InternalSCMRepositoryCreationService {

    private final GitLabProviderConfig gitLabProviderConfig;
    private final GitLabApiService gitlabApiService;

    @Inject
    public GitLabRepositoryCreationService(GitProvidersConfig gitProvidersConfig, GitLabApiService gitlabApiService) {
        this.gitLabProviderConfig = gitProvidersConfig.gitlab();
        this.gitlabApiService = gitlabApiService;
    }

    @Override
    public InternalSCMCreationResponse createInternalSCMRepository(InternalSCMCreationRequest creationRequest) {
        long workspaceId = gitLabProviderConfig.workspaceId();
        String workspaceName = gitLabProviderConfig.workspaceName();
        InternalScmRepositoryCreationCommons.Project project = InternalScmRepositoryCreationCommons
                .parseProjectPath(creationRequest.getProject());

        long parentId;
        String pathWithinWorkspace;
        if (subgroupNameIsEmptyOrMatchesWorkspace(project.organization())) {
            parentId = workspaceId;
            pathWithinWorkspace = workspaceName + "/" + project.repository();
        } else {
            Group subgroup = gitlabApiService.getOrCreateSubgroup(workspaceId, project.organization());
            parentId = subgroup.getId();
            pathWithinWorkspace = workspaceName + "/" + creationRequest.getProject();
        }
        String projectPath = computeProjectPath(creationRequest);

        log.debug("Project path within the PNC workspace: '{}'", pathWithinWorkspace);
        String projectReadonlyUrl = InternalSCMRepositoryCreationService.completeTemplateWithProjectPath(
                gitLabProviderConfig.readOnlyTemplate(),
                projectPath);
        String projectReadwriteUrl = InternalSCMRepositoryCreationService.completeTemplateWithProjectPath(
                gitLabProviderConfig.readWriteTemplate(),
                projectPath);
        log.debug("Readonly URL is: {}", projectReadonlyUrl);
        log.debug("Readwrite URL is: {}", projectReadwriteUrl);

        GitLabProjectCreationResult fetchedProjectResult = gitlabApiService.getOrCreateProject(
                project.repository(),
                parentId,
                pathWithinWorkspace);

        gitlabApiService.configureProtectedTags(
                fetchedProjectResult.project().getId(),
                fetchedProjectResult.status().equals(InternalSCMCreationStatus.SUCCESS_ALREADY_EXISTS));

        return InternalSCMCreationResponse.builder()
                .readonlyUrl(projectReadonlyUrl)
                .readwriteUrl(projectReadwriteUrl)
                .callback(ReqourCallback.builder().id(creationRequest.getTaskId()).status(ResultStatus.SUCCESS).build())
                .status(fetchedProjectResult.status())
                .build();
    }

    @Override
    public String computeProjectPath(InternalSCMCreationRequest creationRequest) {
        log.info("Computing project path for the project: {}", creationRequest.getProject());
        InternalScmRepositoryCreationCommons.Project project = InternalScmRepositoryCreationCommons
                .parseProjectPath(creationRequest.getProject());

        String projectPath = subgroupNameIsEmptyOrMatchesWorkspace(project.organization()) ? project.repository()
                : creationRequest.getProject();
        log.info("Computed project path is: {}", projectPath);
        return projectPath;
    }

    private boolean subgroupNameIsEmptyOrMatchesWorkspace(String subgroupName) {
        return subgroupName == null || subgroupName.equals(gitLabProviderConfig.workspaceName());
    }
}
