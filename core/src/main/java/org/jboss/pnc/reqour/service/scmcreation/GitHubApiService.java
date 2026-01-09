/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.service.scmcreation;

import java.io.IOException;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.pnc.api.enums.InternalSCMCreationStatus;
import org.jboss.pnc.reqour.common.exceptions.GitHubApiException;
import org.jboss.pnc.reqour.config.ConfigConstants;
import org.jboss.pnc.reqour.config.GitHubProviderConfig;
import org.jboss.pnc.reqour.config.GitProviderConfig;
import org.jboss.pnc.reqour.config.GitProviderFaultTolerancePolicy;
import org.jboss.pnc.reqour.config.GitProvidersConfig;
import org.jboss.pnc.reqour.model.GitHubProjectCreationResult;
import org.jboss.pnc.reqour.runtime.api.github.GitHubRestClient;
import org.jboss.pnc.reqour.runtime.api.github.model.GHRuleset;
import org.jboss.pnc.reqour.runtime.api.github.model.GHRulesetCondition;
import org.jboss.pnc.reqour.runtime.api.github.model.GHRulesetEnforcement;
import org.jboss.pnc.reqour.runtime.api.github.model.GHRulesetRule;
import org.jboss.pnc.reqour.runtime.api.github.model.GHRulesetSourceType;
import org.jboss.pnc.reqour.runtime.api.github.model.GHRulesetTarget;
import org.kohsuke.github.GHOrganization;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

import io.quarkus.arc.lookup.LookupIfProperty;
import io.smallrye.faulttolerance.api.ApplyGuard;
import lombok.extern.slf4j.Slf4j;

/**
 * Thin wrapper around {@link GitHub} for performing GitHub API calls.
 */
@ApplicationScoped
@LookupIfProperty(name = ConfigConstants.GITHUB_PROVIDER_ENABLED, stringValue = ConfigConstants.TRUE)
@Slf4j
public class GitHubApiService {

    public static final String ALL_REPOSITORIES_PATTERN = "~ALL";

    private final GitHub delegate;
    private final GitHubProviderConfig gitHubProviderConfig;
    private final GitHubRestClient gitHubRestClient;

    @Inject
    public GitHubApiService(GitHub delegate, GitProvidersConfig gitProvidersConfig, GitHubRestClient gitHubRestClient) {
        this.delegate = delegate;
        this.gitHubProviderConfig = gitProvidersConfig.github();
        this.gitHubRestClient = gitHubRestClient;
    }

    /**
     * Gets (or creates if it does not yet exist) a repository given by its repository name from (at) the GitHub's
     * internal organization.
     *
     * @param repositoryName name of the repository
     * @return the repository corresponding to the requested repository name, together with the information whether the
     *         repository was newly created or already existed beforehand
     */
    public GitHubProjectCreationResult getOrCreateInternalRepository(String repositoryName) {
        GHOrganization internalOrganization = getInternalOrganization();
        GHRepository foundRepository = getInternalRepository(internalOrganization, repositoryName);

        if (foundRepository != null) {
            log.info("Repository with the path '{}' already exists: {}", repositoryName, foundRepository);
            return new GitHubProjectCreationResult(foundRepository, InternalSCMCreationStatus.SUCCESS_ALREADY_EXISTS);
        }

        log.info("Repository with the path '{}' doesn't exist yet, creating it now", repositoryName);
        return new GitHubProjectCreationResult(
                createInternalRepository(repositoryName, internalOrganization),
                InternalSCMCreationStatus.SUCCESS_CREATED);
    }

    @ApplyGuard(GitProviderFaultTolerancePolicy.GIT_PROVIDERS_FAULT_TOLERANCE_GUARD)
    public GHOrganization getInternalOrganization() {
        try {
            return delegate.getOrganization(gitHubProviderConfig.internalOrganizationName());
        } catch (IOException e) {
            throw new GitHubApiException(
                    String.format(
                            "Cannot find the organization %s at GitHub",
                            gitHubProviderConfig.internalOrganizationName()),
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
                            repositoryName,
                            organization),
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

    public boolean doesTagProtectionAlreadyExists(String repositoryName) {
        List<GHRuleset> rulesets = getInternalOrganizationRulesets();
        return rulesets.stream()
                .filter(ruleset -> ruleset.getEnforcement().equals(GHRulesetEnforcement.ACTIVE))
                .filter(ruleset -> ruleset.getTarget().equals(GHRulesetTarget.TAG))
                .map(GHRuleset::getId)
                .anyMatch(tagRulesetId -> isValidProtectedTagConfiguration(tagRulesetId, repositoryName));
    }

    private boolean isValidProtectedTagConfiguration(Integer rulesetId, String repositoryName) {
        GHRuleset ruleset = getRuleset(rulesetId);
        log.debug("Checking the ruleset: {}", ruleset);

        if (!ruleset.getTarget().equals(GHRulesetTarget.TAG)) {
            throw new IllegalArgumentException("Expected tag ruleset, got: " + ruleset);
        }

        return ruleset.getSourceType().equals(GHRulesetSourceType.ORGANIZATION) &&
                ruleset.getSource().equals(gitHubProviderConfig.internalOrganizationName()) &&
                ruleset.getEnforcement().equals(GHRulesetEnforcement.ACTIVE) &&
                isValidRulesetCondition(ruleset.getConditions(), repositoryName) &&
                ruleset.getRules().contains(GHRulesetRule.of(GHRulesetRule.GHRulesetRuleType.DELETION)) &&
                ruleset.getRules().contains(GHRulesetRule.of(GHRulesetRule.GHRulesetRuleType.NON_FAST_FORWARD));
    }

    private boolean isValidRulesetCondition(GHRulesetCondition condition, String repositoryName) {
        GitProviderConfig.TagProtectionConfig tagProtectionConfig = gitHubProviderConfig.tagProtection();
        return (condition.getRepositoryName().getInclude().contains(repositoryName)
                || condition.getRepositoryName().getInclude().contains(ALL_REPOSITORIES_PATTERN))
                && (tagProtectionConfig.protectedTagsPattern().isEmpty() || condition.getRefName()
                        .getInclude()
                        .contains("refs/tags/" + tagProtectionConfig.protectedTagsPattern().get()));
    }

    @ApplyGuard(GitProviderFaultTolerancePolicy.GIT_PROVIDERS_FAULT_TOLERANCE_GUARD)
    public List<GHRuleset> getInternalOrganizationRulesets() {
        return gitHubRestClient.getAllRulesets(gitHubProviderConfig.internalOrganizationName());
    }

    @ApplyGuard(GitProviderFaultTolerancePolicy.GIT_PROVIDERS_FAULT_TOLERANCE_GUARD)
    public GHRuleset getRuleset(Integer rulesetId) {
        return gitHubRestClient.getRuleset(gitHubProviderConfig.internalOrganizationName(), rulesetId);
    }
}
