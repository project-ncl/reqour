/**
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.common.gitlab;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.quarkus.arc.lookup.LookupIfProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.gitlab4j.api.Constants;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.models.AccessLevel;
import org.gitlab4j.api.models.Group;
import org.gitlab4j.api.models.GroupParams;
import org.gitlab4j.api.models.Project;
import org.gitlab4j.api.models.ProtectedTag;
import org.jboss.pnc.api.enums.InternalSCMCreationStatus;
import org.jboss.pnc.api.enums.ResultStatus;
import org.jboss.pnc.api.reqour.dto.InternalSCMCreationResponse;
import org.jboss.pnc.api.reqour.dto.ReqourCallback;
import org.jboss.pnc.reqour.common.exceptions.GitlabApiRuntimeException;
import org.jboss.pnc.reqour.config.ConfigUtils;
import org.jboss.pnc.reqour.config.GitBackendConfig;
import org.jboss.pnc.reqour.model.GitlabGetOrCreateProjectResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Thin wrapper around {@link GitLabApi} for performing GitLab API calls.
 */
@ApplicationScoped
@LookupIfProperty(name = "reqour.git.git-backends.active", stringValue = "gitlab")
public class GitlabApiService {

    private final GitLabApi delegate;
    private final GitBackendConfig gitlabConfig;

    @Inject
    public GitlabApiService(ConfigUtils configUtils) {
        gitlabConfig = configUtils.getActiveGitBackend();
        delegate = new GitLabApi(GitLabApi.ApiVersion.V4, gitlabConfig.url(), gitlabConfig.token());
    }

    public Group createGroup(String name, long parentId) {
        try {
            return delegate.getGroupApi()
                    .createGroup(
                            new GroupParams().withName(name)
                                    .withPath(name)
                                    .withParentId(parentId)
                                    .withDefaultBranchProtection(Constants.DefaultBranchProtectionLevel.NOT_PROTECTED));
        } catch (GitLabApiException e) {
            throw new GitlabApiRuntimeException(e);
        }
    }

    public Group getGroup(long workspaceId) {
        try {
            return delegate.getGroupApi().getGroup(workspaceId);
        } catch (GitLabApiException e) {
            throw new GitlabApiRuntimeException(e);
        }
    }

    public Group getOrCreateSubgroup(long parentId, String subgroupName) {
        try {
            return delegate.getGroupApi().getGroup(gitlabConfig.workspaceName() + "/" + subgroupName);
        } catch (GitLabApiException e) {
            if (e.getHttpStatus() == HttpResponseStatus.NOT_FOUND.code()) {
                return createGroup(subgroupName, parentId);
            }
            throw new GitlabApiRuntimeException(e);
        }
    }

    public GitlabGetOrCreateProjectResult getOrCreateProject(
            String projectName,
            long parentId,
            String projectPath,
            String readonlyUrl,
            String readwriteUrl,
            String taskId) {
        InternalSCMCreationResponse.InternalSCMCreationResponseBuilder scmCreationResponseBuilder = InternalSCMCreationResponse
                .builder()
                .readonlyUrl(readonlyUrl)
                .readwriteUrl(readwriteUrl)
                .callback(ReqourCallback.builder().status(ResultStatus.SUCCESS).id(taskId).build());
        try {
            return new GitlabGetOrCreateProjectResult(
                    getProject(projectPath),
                    scmCreationResponseBuilder.status(InternalSCMCreationStatus.SUCCESS_ALREADY_EXISTS).build());
        } catch (GitLabApiException e) {
            if (e.getHttpStatus() == HttpResponseStatus.NOT_FOUND.code()) {
                return createProject(projectName, parentId, scmCreationResponseBuilder);
            }
            throw new GitlabApiRuntimeException(e);
        }
    }

    public Project getProject(String projectPath) throws GitLabApiException {
        return delegate.getProjectApi().getProject(projectPath);
    }

    private GitlabGetOrCreateProjectResult createProject(
            String projectName,
            long parentId,
            InternalSCMCreationResponse.InternalSCMCreationResponseBuilder scmCreationResponseBuilder) {
        try {
            return new GitlabGetOrCreateProjectResult(
                    delegate.getProjectApi().createProject(parentId, projectName),
                    scmCreationResponseBuilder.status(InternalSCMCreationStatus.SUCCESS_CREATED).build());
        } catch (GitLabApiException ex) {
            throw new GitlabApiRuntimeException(ex);
        }
    }

    public List<ProtectedTag> getProtectedTags(Long projectId) {
        try {
            return delegate.getTagsApi().getProtectedTags(projectId, 1, 100);
        } catch (GitLabApiException e) {
            throw new GitlabApiRuntimeException(e);
        }
    }

    public void configureProtectedTags(Long projectId, boolean projectAlreadyExisted) {
        try {
            GitBackendConfig.TagProtectionConfig tagProtectionConfig = gitlabConfig.tagProtection();
            if (tagProtectionConfig.protectedTagsPattern().isEmpty()) {
                return;
            }

            boolean tagsAlreadyExist = projectAlreadyExisted && doesTagProtectionAlreadyExist(projectId);
            if (tagsAlreadyExist) {
                return;
            }

            delegate.getTagsApi()
                    .protectTag(projectId, tagProtectionConfig.protectedTagsPattern().get(), AccessLevel.DEVELOPER);
        } catch (GitLabApiException e) {
            throw new GitlabApiRuntimeException(e);
        }
    }

    public boolean doesTagProtectionAlreadyExist(Long projectId) {
        GitBackendConfig.TagProtectionConfig tagProtectionConfig = gitlabConfig.tagProtection();
        Optional<String> protectedTagsPattern = tagProtectionConfig.protectedTagsPattern();
        List<String> protectedTagsAcceptedPatterns = tagProtectionConfig.protectedTagsAcceptedPatterns()
                .orElse(new ArrayList<>());

        if (protectedTagsPattern.isPresent() && !protectedTagsAcceptedPatterns.contains(protectedTagsPattern.get())) {
            protectedTagsAcceptedPatterns.add(protectedTagsPattern.get());
        }

        return getProtectedTags(projectId).stream()
                .map(ProtectedTag::getName)
                .anyMatch(protectedTagsAcceptedPatterns::contains);
    }
}
