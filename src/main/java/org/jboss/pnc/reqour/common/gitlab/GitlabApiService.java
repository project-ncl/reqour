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
import org.jboss.pnc.reqour.config.ConfigUtils;
import org.jboss.pnc.reqour.model.GitBackendConfig;
import org.jboss.pnc.reqour.model.GitlabGetOrCreateProjectResult;
import org.jboss.pnc.reqour.rest.providers.GitlabApiRuntimeException;

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
        delegate = new GitLabApi(GitLabApi.ApiVersion.V4, gitlabConfig.getUrl(), gitlabConfig.getToken());
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
            return delegate.getGroupApi().getGroup(gitlabConfig.getWorkspace() + "/" + subgroupName);
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

    public List<ProtectedTag> getProtectedTags(Project project) {
        try {
            return delegate.getTagsApi().getProtectedTags(project.getId());
        } catch (GitLabApiException e) {
            throw new GitlabApiRuntimeException(e);
        }
    }

    public void configureProtectedTags(Project project, boolean projectAlreadyExisted) {
        try {
            if (gitlabConfig.getProtectedTagsPattern().isEmpty()) {
                return;
            }

            boolean tagsAlreadyExist = projectAlreadyExisted && doesTagProtectionAlreadyExist(project);
            if (tagsAlreadyExist) {
                return;
            }

            delegate.getTagsApi()
                    .protectTag(project.getId(), gitlabConfig.getProtectedTagsPattern().get(), AccessLevel.DEVELOPER);
        } catch (GitLabApiException e) {
            throw new GitlabApiRuntimeException(e);
        }
    }

    private boolean doesTagProtectionAlreadyExist(Project project) {
        Optional<String> protectedTagsPattern = gitlabConfig.getProtectedTagsPattern();
        List<String> protectedTagsAcceptedPatterns = gitlabConfig.getProtectedTagsAcceptedPatterns();

        if (protectedTagsPattern.isPresent() && !protectedTagsAcceptedPatterns.contains(protectedTagsPattern.get())) {
            protectedTagsAcceptedPatterns.add(protectedTagsPattern.get());
        }

        return getProtectedTags(project).stream()
                .map(ProtectedTag::getName)
                .anyMatch(protectedTagsAcceptedPatterns::contains);
    }
}