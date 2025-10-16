/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.gitlab4j.api.models.Group;
import org.jboss.pnc.api.enums.InternalSCMCreationStatus;
import org.jboss.pnc.api.reqour.dto.InternalSCMCreationRequest;
import org.jboss.pnc.api.reqour.dto.InternalSCMCreationResponse;
import org.jboss.pnc.reqour.common.exceptions.InvalidProjectPathException;
import org.jboss.pnc.reqour.common.gitlab.GitlabApiService;
import org.jboss.pnc.reqour.config.ConfigUtils;
import org.jboss.pnc.reqour.config.GitProviderConfig;
import org.jboss.pnc.reqour.model.GitlabGetOrCreateProjectResult;
import org.jboss.pnc.reqour.service.api.InternalSCMRepositoryCreationService;

import io.quarkus.arc.lookup.LookupIfProperty;
import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of {@link InternalSCMRepositoryCreationService} using GitLab as its provider.
 */
@ApplicationScoped
@LookupIfProperty(name = "reqour.git.git-providers.active", stringValue = "gitlab")
@Slf4j
public class GitlabRepositoryCreationService implements InternalSCMRepositoryCreationService {

    private static final String GIT_SUFFIX = ".git";

    private final GitProviderConfig gitProviderConfig;
    private final GitlabApiService gitlabApiService;

    @Inject
    public GitlabRepositoryCreationService(ConfigUtils configUtils, GitlabApiService gitlabApiService) {
        this.gitProviderConfig = configUtils.getActiveGitProviderConfig();
        this.gitlabApiService = gitlabApiService;
    }

    @Override
    public InternalSCMCreationResponse createInternalSCMRepository(InternalSCMCreationRequest creationRequest) {
        long workspaceId = gitProviderConfig.workspaceId();
        Group workspaceGroup = gitlabApiService.getGroup(workspaceId);
        Project project = parseProjectPath(creationRequest.getProject());

        long parentId;
        String pathWithinWorkspace;
        String pathToTemplate;
        if (subgroupNameIsEmptyOrMatchesWorkspace(project.subgroupName(), workspaceGroup.getName())) {
            parentId = workspaceId;
            pathWithinWorkspace = workspaceGroup.getName() + "/" + project.projectName;
            pathToTemplate = project.projectName;
        } else {
            Group subgroup = gitlabApiService.getOrCreateSubgroup(workspaceGroup.getId(), project.subgroupName());
            parentId = subgroup.getId();
            pathWithinWorkspace = workspaceGroup.getName() + "/" + creationRequest.getProject();
            pathToTemplate = creationRequest.getProject();
        }

        log.debug("Project path within the PNC workspace: '{}'", pathWithinWorkspace);
        String projectReadonlyUrl = completeTemplateWithProjectPath(
                gitProviderConfig.readOnlyTemplate(),
                pathToTemplate);
        String projectReadwriteUrl = completeTemplateWithProjectPath(
                gitProviderConfig.readWriteTemplate(),
                pathToTemplate);
        log.debug("Readonly URL is: {}", projectReadonlyUrl);
        log.debug("Readwrite URL is: {}", projectReadwriteUrl);

        GitlabGetOrCreateProjectResult fetchedProjectResult = gitlabApiService.getOrCreateProject(
                project.projectName(),
                parentId,
                pathWithinWorkspace,
                projectReadonlyUrl,
                projectReadwriteUrl,
                creationRequest.getTaskId());

        gitlabApiService.configureProtectedTags(
                fetchedProjectResult.project().getId(),
                fetchedProjectResult.result().getStatus().equals(InternalSCMCreationStatus.SUCCESS_ALREADY_EXISTS));

        return fetchedProjectResult.result();
    }

    private static Project parseProjectPath(String projectPath) {
        if (projectPath.endsWith(GIT_SUFFIX)) {
            projectPath = projectPath.replace(GIT_SUFFIX, "");
        }

        String[] projectPathSplitted = projectPath.split("/", 3);

        if (projectPathSplitted.length == 1) {
            return new Project(null, projectPath);
        }

        if (projectPathSplitted.length == 2) {
            return new Project(projectPathSplitted[0], projectPathSplitted[1]);
        }

        throw new InvalidProjectPathException(
                String.format("Invalid project path given: '%s'. Expecting at most 1 '/'.", projectPath));
    }

    private static boolean subgroupNameIsEmptyOrMatchesWorkspace(String subgroupName, String workspaceGroupName) {
        return subgroupName == null || subgroupName.equals(workspaceGroupName);
    }

    public static String completeTemplateWithProjectPath(String template, String projectPath) {
        return String.format(template, projectPath);
    }

    private record Project(String subgroupName, String projectName) {
    }
}
