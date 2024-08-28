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

import io.quarkus.arc.lookup.LookupIfProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.gitlab4j.api.models.Group;
import org.jboss.pnc.api.reqour.dto.InternalSCMCreationRequest;
import org.jboss.pnc.api.reqour.dto.InternalSCMCreationResponse;
import org.jboss.pnc.reqour.common.exceptions.InvalidProjectPathException;
import org.jboss.pnc.reqour.common.gitlab.GitlabApiService;
import org.jboss.pnc.reqour.config.ConfigUtils;
import org.jboss.pnc.reqour.model.GitBackendConfig;
import org.jboss.pnc.reqour.model.GitlabGetOrCreateProjectResult;
import org.jboss.pnc.reqour.service.api.InternalSCMRepositoryCreationService;

/**
 * Implementation of {@link InternalSCMRepositoryCreationService} using internal GitLab.
 */
@ApplicationScoped
@LookupIfProperty(name = "reqour.git.git-backends.active", stringValue = "gitlab")
@Slf4j
public class GitlabRepositoryCreationService implements InternalSCMRepositoryCreationService {

    private static final String GIT_SUFFIX = ".git";

    private final ConfigUtils configUtils;
    private final GitlabApiService gitlabApiService;

    @Inject
    public GitlabRepositoryCreationService(ConfigUtils configUtils, GitlabApiService gitlabApiService) {
        this.configUtils = configUtils;
        this.gitlabApiService = gitlabApiService;
    }

    @Override
    public InternalSCMCreationResponse createInternalSCMRepository(InternalSCMCreationRequest creationRequest) {
        GitBackendConfig gitlabConfig = configUtils.getActiveGitBackend();
        long workspaceId = gitlabConfig.getWorkspaceId();
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

        GitlabGetOrCreateProjectResult fetchedProjectResult = gitlabApiService.getOrCreateProject(
                project.projectName(),
                parentId,
                pathWithinWorkspace,
                completeTemplateWithProjectPath(gitlabConfig.getReadOnlyTemplate(), pathToTemplate),
                completeTemplateWithProjectPath(gitlabConfig.getReadWriteTemplate(), pathToTemplate),
                creationRequest.getTaskId());

        // gitlabApiService.configureProtectedTags(
        // fetchedProjectResult.project(),
        // fetchedProjectResult.result().getStatus().equals(InternalSCMCreationStatus.SUCCESS_ALREADY_EXISTS));

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
