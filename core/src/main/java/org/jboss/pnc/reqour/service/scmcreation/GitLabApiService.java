/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.service.scmcreation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
import org.jboss.pnc.reqour.common.exceptions.GitLabApiRuntimeException;
import org.jboss.pnc.reqour.config.ConfigConstants;
import org.jboss.pnc.reqour.config.GitLabProviderConfig;
import org.jboss.pnc.reqour.config.GitProviderConfig;
import org.jboss.pnc.reqour.config.GitProviderFaultTolerancePolicy;
import org.jboss.pnc.reqour.config.GitProvidersConfig;
import org.jboss.pnc.reqour.model.GitLabProjectCreationResult;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.quarkus.arc.lookup.LookupIfProperty;
import io.smallrye.faulttolerance.api.ApplyGuard;
import lombok.extern.slf4j.Slf4j;

/**
 * Thin wrapper around {@link GitLabApi} for performing GitLab API calls.
 */
@ApplicationScoped
@LookupIfProperty(name = ConfigConstants.GITLAB_PROVIDER_ENABLED, stringValue = ConfigConstants.TRUE)
@Slf4j
public class GitLabApiService {

    private final GitLabApi delegate;
    private final GitLabProviderConfig gitLabProviderConfig;

    @Inject
    public GitLabApiService(GitProvidersConfig gitProvidersConfig, GitLabApi delegate) {
        this.gitLabProviderConfig = gitProvidersConfig.gitlab();
        this.delegate = delegate;
    }

    @ApplyGuard(GitProviderFaultTolerancePolicy.GIT_PROVIDERS_FAULT_TOLERANCE_GUARD)
    public Group createGroup(String name, long parentId) {
        try {
            return delegate.getGroupApi()
                    .createGroup(
                            new GroupParams().withName(name)
                                    .withPath(name)
                                    .withParentId(parentId)
                                    .withDefaultBranchProtection(Constants.DefaultBranchProtectionLevel.NOT_PROTECTED));
        } catch (GitLabApiException e) {
            throw new GitLabApiRuntimeException(e);
        }
    }

    @ApplyGuard(GitProviderFaultTolerancePolicy.GIT_PROVIDERS_FAULT_TOLERANCE_GUARD)
    public Group getGroup(long workspaceId) {
        try {
            return delegate.getGroupApi().getGroup(workspaceId);
        } catch (GitLabApiException e) {
            throw new GitLabApiRuntimeException(e);
        }
    }

    @ApplyGuard(GitProviderFaultTolerancePolicy.GIT_PROVIDERS_FAULT_TOLERANCE_GUARD)
    public Group getOrCreateSubgroup(long parentId, String subgroupName) {
        try {
            return delegate.getGroupApi().getGroup(gitLabProviderConfig.workspaceName() + "/" + subgroupName);
        } catch (GitLabApiException e) {
            if (e.getHttpStatus() == HttpResponseStatus.NOT_FOUND.code()) {
                return createGroup(subgroupName, parentId);
            }
            throw new GitLabApiRuntimeException(e);
        }
    }

    @ApplyGuard(GitProviderFaultTolerancePolicy.GIT_PROVIDERS_FAULT_TOLERANCE_GUARD)
    public GitLabProjectCreationResult getOrCreateProject(
            String projectName,
            long parentId,
            String projectPath) {
        try {
            GitLabProjectCreationResult foundProject = new GitLabProjectCreationResult(
                    _getProject(projectPath),
                    InternalSCMCreationStatus.SUCCESS_ALREADY_EXISTS);
            log.debug(
                    "Project '{}' (id={}) already exists",
                    foundProject.project().getName(),
                    foundProject.project().getId());
            return foundProject;
        } catch (GitLabApiException e) {
            if (e.getHttpStatus() == HttpResponseStatus.NOT_FOUND.code()) {
                GitLabProjectCreationResult createdProject = _createProject(
                        projectName,
                        parentId);
                log.debug(
                        "Project '{}' (id={}) was newly created",
                        createdProject.project().getName(),
                        createdProject.project().getId());
                return createdProject;
            }
            throw new GitLabApiRuntimeException(e);
        }
    }

    @ApplyGuard(GitProviderFaultTolerancePolicy.GIT_PROVIDERS_FAULT_TOLERANCE_GUARD)
    public Project getProject(String projectPath) throws GitLabApiException {
        return _getProject((projectPath));
    }

    /**
     * Use when you do not want fault tolerance being applied from the caller, unlike {@link this#getProject(String)}.
     */
    private Project _getProject(String projectPath) throws GitLabApiException {
        return delegate.getProjectApi().getProject(projectPath);
    }

    @ApplyGuard(GitProviderFaultTolerancePolicy.GIT_PROVIDERS_FAULT_TOLERANCE_GUARD)
    public GitLabProjectCreationResult createProject(
            String projectName,
            long parentId) {
        return _createProject(projectName, parentId);
    }

    /**
     * Use when you do not want fault tolerance being applied from the caller, unlike {@link this#createProject(String,
     * long)}.
     */
    private GitLabProjectCreationResult _createProject(String projectName, long parentId) {
        try {
            return new GitLabProjectCreationResult(
                    delegate.getProjectApi().createProject(parentId, projectName),
                    InternalSCMCreationStatus.SUCCESS_CREATED);
        } catch (GitLabApiException ex) {
            throw new GitLabApiRuntimeException(ex);
        }
    }

    @ApplyGuard(GitProviderFaultTolerancePolicy.GIT_PROVIDERS_FAULT_TOLERANCE_GUARD)
    public List<ProtectedTag> getProtectedTags(Object projectIdOrPath) {
        try {
            return delegate.getTagsApi().getProtectedTags(projectIdOrPath, 1, 100);
        } catch (GitLabApiException e) {
            throw new GitLabApiRuntimeException(e);
        }
    }

    @ApplyGuard(GitProviderFaultTolerancePolicy.GIT_PROVIDERS_FAULT_TOLERANCE_GUARD)
    public void configureProtectedTags(Long projectId, boolean projectAlreadyExisted) {
        try {
            GitProviderConfig.TagProtectionConfig tagProtectionConfig = gitLabProviderConfig.tagProtection();
            if (tagProtectionConfig.protectedTagsPattern().isEmpty()) {
                return;
            }

            boolean tagsAlreadyExist = projectAlreadyExisted && doesTagProtectionAlreadyExist(projectId);
            if (tagsAlreadyExist) {
                log.debug("Tag protection of project with id={} already exists", projectId);
                return;
            }

            delegate.getTagsApi()
                    .protectTag(projectId, tagProtectionConfig.protectedTagsPattern().get(), AccessLevel.DEVELOPER);
            log.debug("Tag protection for project with id={} successfully initialized", projectId);
        } catch (GitLabApiException e) {
            throw new GitLabApiRuntimeException(e);
        }
    }

    public boolean doesTagProtectionAlreadyExist(Object projectIdOrPath) {
        GitProviderConfig.TagProtectionConfig tagProtectionConfig = gitLabProviderConfig.tagProtection();
        Optional<String> protectedTagsPattern = tagProtectionConfig.protectedTagsPattern();
        List<String> protectedTagsAcceptedPatterns = tagProtectionConfig.protectedTagsAcceptedPatterns()
                .orElse(new ArrayList<>());

        if (protectedTagsPattern.isPresent() && !protectedTagsAcceptedPatterns.contains(protectedTagsPattern.get())) {
            protectedTagsAcceptedPatterns.add(protectedTagsPattern.get());
        }

        return getProtectedTags(projectIdOrPath).stream()
                .map(ProtectedTag::getName)
                .anyMatch(protectedTagsAcceptedPatterns::contains);
    }
}
