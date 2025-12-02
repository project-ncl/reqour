/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.service.scmcreation;

import java.io.IOException;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.pnc.api.enums.InternalSCMCreationStatus;
import org.jboss.pnc.reqour.common.exceptions.GitHubApiException;
import org.jboss.pnc.reqour.config.ConfigConstants;
import org.jboss.pnc.reqour.config.ConfigUtils;
import org.jboss.pnc.reqour.config.GitProviderConfig;
import org.jboss.pnc.reqour.config.GitProviderFaultTolerancePolicy;
import org.jboss.pnc.reqour.model.GitHubProjectCreationResult;
import org.jboss.pnc.reqour.runtime.UserLogger;
import org.kohsuke.github.GHOrganization;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.slf4j.Logger;

import io.quarkus.arc.lookup.LookupIfProperty;
import io.smallrye.faulttolerance.api.ApplyGuard;
import lombok.extern.slf4j.Slf4j;

/**
 * Thin wrapper around {@link GitHub} for performing GitHub API calls.
 */
@ApplicationScoped
@LookupIfProperty(name = ConfigConstants.ACTIVE_GIT_PROVIDER, stringValue = ConfigConstants.GITHUB)
@Slf4j
public class GitHubApiService {

    private final GitHub delegate;
    private final GitProviderConfig gitProviderConfig;
    private final Logger userLogger;

    @Inject
    public GitHubApiService(GitHub delegate, ConfigUtils configUtils, @UserLogger Logger userLogger) {
        this.delegate = delegate;
        gitProviderConfig = configUtils.getActiveGitProviderConfig();
        this.userLogger = userLogger;
    }

    /**
     * Gets a repository given by its repository name from the GitHub's internal organization.
     *
     * @param repositoryName name of the repository
     * @return the repository corresponding to the requested repository name, together with the information whether the
     *         repository was newly created or already existed beforehand
     */
    public GitHubProjectCreationResult getOrCreateInternalRepository(String repositoryName) {
        GHOrganization internalOrganization = getInternalOrganization();
        GHRepository foundRepository = getInternalRepository(internalOrganization, repositoryName);

        if (foundRepository != null) {
            userLogger.info("Repository with the path '{}' already exists: {}", repositoryName, foundRepository);
            return new GitHubProjectCreationResult(foundRepository, InternalSCMCreationStatus.SUCCESS_ALREADY_EXISTS);
        }

        userLogger.info("Repository with the path '{}' doesn't exist yet, creating it now", repositoryName);
        return new GitHubProjectCreationResult(
                createInternalRepository(repositoryName, internalOrganization),
                InternalSCMCreationStatus.SUCCESS_CREATED);
    }

    @ApplyGuard(GitProviderFaultTolerancePolicy.GIT_PROVIDERS_FAULT_TOLERANCE_GUARD)
    public GHOrganization getInternalOrganization() {
        try {
            return delegate.getOrganization(gitProviderConfig.workspaceName());
        } catch (IOException e) {
            throw new GitHubApiException(
                    String.format("Cannot find the organization %s at GitHub", gitProviderConfig.workspaceName()),
                    e);
        }
    }

    @ApplyGuard(GitProviderFaultTolerancePolicy.GIT_PROVIDERS_FAULT_TOLERANCE_GUARD)
    public GHRepository getInternalRepository(GHOrganization organization, String repositoryName) {
        try {
            return organization.getRepository(repositoryName);
        } catch (IOException e) {
            throw new GitHubApiException(
                    String.format(
                            "Cannot fetch the repository %s from the organization %s",
                            organization,
                            repositoryName),
                    e);
        }
    }

    @ApplyGuard(GitProviderFaultTolerancePolicy.GIT_PROVIDERS_FAULT_TOLERANCE_GUARD)
    public GHRepository createInternalRepository(String repositoryName, GHOrganization internalOrganization) {
        try {
            return internalOrganization.createRepository(repositoryName).create();
        } catch (IOException e) {
            throw new GitHubApiException(
                    String.format(
                            "Cannot create the repository '%s' in the organization '%s'",
                            repositoryName,
                            internalOrganization),
                    e);
        }
    }
}
