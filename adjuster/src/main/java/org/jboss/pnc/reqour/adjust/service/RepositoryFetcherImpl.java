/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.service;

import static org.jboss.pnc.reqour.common.utils.GitUtils.DEFAULT_REMOTE_NAME;
import static org.jboss.pnc.reqour.common.utils.GitUtils.FETCH_HEAD;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.apache.commons.io.FileUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.pnc.api.reqour.dto.AdjustRequest;
import org.jboss.pnc.api.reqour.dto.InternalGitRepositoryUrl;
import org.jboss.pnc.reqour.adjust.exception.AdjusterException;
import org.jboss.pnc.reqour.adjust.model.CloningResult;
import org.jboss.pnc.reqour.common.GitCommands;
import org.jboss.pnc.reqour.common.utils.URLUtils;
import org.jboss.pnc.reqour.config.core.ConfigConstants;
import org.jboss.pnc.reqour.config.utils.ConfigUtils;
import org.jboss.pnc.reqour.model.ProcessContext;
import org.jboss.pnc.reqour.runtime.UserLogger;
import org.jboss.pnc.reqour.service.GitCloneService;
import org.jboss.pnc.reqour.service.scmcreation.GitHubApiService;
import org.jboss.pnc.reqour.service.scmcreation.GitLabApiService;
import org.slf4j.Logger;

import lombok.extern.slf4j.Slf4j;

@ApplicationScoped
@Slf4j
public class RepositoryFetcherImpl implements RepositoryFetcher {

    private static final String ORIGIN_REMOTE = "origin_remote";

    @Inject
    @UserLogger
    Logger userLogger;

    @Inject
    ConfigUtils configUtils;

    @Inject
    GitCommands gitCommands;

    @Inject
    GitCloneService gitCloneService;

    @Inject
    GitLabApiService gitlabApiService;

    @Inject
    GitHubApiService gitHubApiService;

    @ConfigProperty(name = ConfigConstants.INTERNAL_URLS)
    Optional<List<String>> internalUrls;

    public CloningResult cloneRepository(AdjustRequest adjustRequest, Path workdir) {
        checkTagProtection(adjustRequest);
        String gitUsername = configUtils.getActiveGitProviderConfig().username();

        final boolean isRefInternal;
        if (syncEnabled(adjustRequest)) {
            userLogger.info("Auto-Sync feature activated");
            isRefInternal = syncExternalRepo(adjustRequest, workdir, gitUsername);
        } else {
            userLogger.warn("Auto-Sync feature disabled, working with the downstream repository only");
            shallowCloneWithTags(
                    URLUtils.addUsernameToUrl(adjustRequest.getInternalUrl().getReadwriteUrl(), gitUsername),
                    adjustRequest.getRef(),
                    workdir);
            gitCommands.setupGitLfsIfPresent(
                    ProcessContext.withWorkdirAndConsumers(workdir, userLogger::info, userLogger::warn));
            isRefInternal = true;
        }

        gitCommands.configureCommitter(workdir);
        // Get upstream commit before transforming into fat repository (since that potentially creates a new commit)
        String upstreamCommitId = gitCommands.revParse(workdir);
        userLogger.info("Current Commit ID of repo is: {}", upstreamCommitId);
        CloningResult cloningResult = new CloningResult(upstreamCommitId, isRefInternal);
        transformGitSubmodulesIntoFatRepository(workdir);

        return cloningResult;
    }

    private void checkTagProtection(AdjustRequest adjustRequest) {
        String projectPath = extractProjectPathFromInternalUrl(adjustRequest.getInternalUrl());
        log.debug(
                "Checking whether tag protection respects Reqour's tag protection configuration for project '{}'",
                projectPath);

        boolean _isValid = switch (configUtils.getActiveGitProvider()) {
            case GITLAB -> {
                if (!gitlabApiService.doesTagProtectionAlreadyExist(projectPath)) {
                    throw new AdjusterException(
                            String.format(
                                    "GitLab repository at path '%s' does not have well-configured protected tags",
                                    projectPath));
                }
                yield true;
            }
            case GITHUB -> {
                if (!gitHubApiService.doesTagProtectionAlreadyExists(projectPath)) {
                    throw new AdjusterException(
                            String.format(
                                    "GitHub repository at path '%s' does not have well-configured protected tags",
                                    projectPath));
                }
                yield true;
            }
        };
    }

    private boolean syncEnabled(AdjustRequest adjustRequest) {
        return adjustRequest.isSync() && adjustRequest.getOriginRepoUrl() != null
                && !adjustRequest.getOriginRepoUrl().isBlank();
    }

    private boolean syncExternalRepo(AdjustRequest adjustRequest, Path workdir, String gitUsername) {
        boolean isRefInternal = false;

        ProcessContext.Builder processContextBuilder = ProcessContext
                .withWorkdirAndConsumers(workdir, userLogger::info, userLogger::warn);
        gitCommands.clone(adjustRequest.getOriginRepoUrl(), processContextBuilder);
        gitCommands.setupGitLfsIfPresent(processContextBuilder);

        if (gitCommands.doesReferenceExistRemotely(adjustRequest.getRef(), processContextBuilder)) {
            boolean isRefPR = GitCommands.isReferencePR(adjustRequest.getRef());
            if (isRefPR) {
                gitCommands.checkoutPR(adjustRequest.getRef(), processContextBuilder);
            } else {
                gitCommands.checkout(adjustRequest.getRef(), true, processContextBuilder);
            }

            gitCommands.renameRemote(DEFAULT_REMOTE_NAME, ORIGIN_REMOTE, processContextBuilder);
            gitCommands.addRemote(
                    DEFAULT_REMOTE_NAME,
                    adjustRequest.getInternalUrl().getReadwriteUrl(),
                    processContextBuilder);

            if (isRefPR) {
                log.warn(
                        "Syncing of Pull Request to downstream repository disabled since the ref '{}' is a pull request",
                        adjustRequest.getRef());
            } else {
                gitCloneService.pushClonedChanges(adjustRequest.getRef(), DEFAULT_REMOTE_NAME, processContextBuilder);
            }
        } else {
            log.warn(
                    "Upstream repository does not have the reference '{}'. Trying to see if it is present in downstream repository",
                    adjustRequest.getRef());

            try {
                FileUtils.deleteDirectory(workdir.toFile());
                Files.createDirectory(workdir);
            } catch (IOException e) {
                throw new RuntimeException(
                        "Unable to delete and create empty working directory where to clone downstream repository content");
            }

            gitCommands.clone(
                    URLUtils.addUsernameToUrl(adjustRequest.getInternalUrl().getReadwriteUrl(), gitUsername),
                    processContextBuilder);
            if (gitCommands.doesReferenceExistRemotely(adjustRequest.getRef(), processContextBuilder)) {
                log.debug("Downstream repository has the ref, but not the upstream one. No syncing required!");
                gitCommands.checkout(adjustRequest.getRef(), true, processContextBuilder);
                isRefInternal = true;
            } else {
                throw new AdjusterException(
                        String.format(
                                "Neither upstream nor downstream repository has the reference '%s' present. Cannot proceed",
                                adjustRequest.getRef()));
            }
        }

        List<String> internalUrls = this.internalUrls.orElse(Collections.emptyList());
        for (var internalUrl : internalUrls) {
            if (adjustRequest.getOriginRepoUrl() != null && adjustRequest.getOriginRepoUrl().contains(internalUrl)) {
                isRefInternal = true;
                break;
            }
        }

        gitCommands.fetchTags(DEFAULT_REMOTE_NAME, false, processContextBuilder);
        return isRefInternal;
    }

    private void shallowCloneWithTags(String url, String ref, Path workdir) {
        ProcessContext.Builder processContextBuilder = ProcessContext
                .withWorkdirAndConsumers(workdir, userLogger::info, userLogger::warn);

        gitCommands.init(false, processContextBuilder);
        gitCommands.addRemote(DEFAULT_REMOTE_NAME, url, processContextBuilder);
        gitCommands.fetchRef(DEFAULT_REMOTE_NAME, ref, true, false, processContextBuilder);
        gitCommands.checkout(FETCH_HEAD, false, processContextBuilder);
        gitCommands.fetchTags(DEFAULT_REMOTE_NAME, true, processContextBuilder);
    }

    /**
     * Transform git repository containing submodules into fat repository as is described in
     * <a href="https://www.atlassian.com/git/articles/core-concept-workflows-and-tips#integrate-submodule">Atlassian
     * docs</a>.
     */
    private void transformGitSubmodulesIntoFatRepository(Path workdir) {
        String gitModulesFilename = ".gitmodules";
        Path submodulesFile = workdir.resolve(gitModulesFilename);

        if (Files.notExists(submodulesFile)) {
            return;
        }

        userLogger.debug("Repository '{}' is using git submodules, transforming into fat repository", workdir);
        ProcessContext.Builder processContextBuilder = ProcessContext
                .withWorkdirAndConsumers(workdir, userLogger::info, userLogger::warn);
        gitCommands.submoduleUpdateInit(processContextBuilder);

        List<String> submoduleLocations = getSubmoduleLocations(submodulesFile);
        for (var submoduleLocation : submoduleLocations) {
            // 1) Delete the reference to the submodule from the index, but keep the files
            gitCommands.remove(submoduleLocation, true, processContextBuilder);

            // 2) Remove the .git metadata file
            try {
                log.debug("Removing .git file inside the submodule {}", submoduleLocation);
                // use forceDelete since it will delete the .git, whether it's a file or a directory
                FileUtils.forceDelete(workdir.resolve(submoduleLocation).resolve(".git").toFile());
            } catch (IOException e) {
                throw new RuntimeException("Cannot delete .git file inside the submodule " + submoduleLocation, e);
            }

            // 3) Add the submodule to the main repository index
            gitCommands.add(submoduleLocation, processContextBuilder);
        }

        // 4) Delete the .gitmodules file
        gitCommands.remove(gitModulesFilename, false, processContextBuilder);

        // 5) Commit the transformation
        gitCommands.commit("Removing submodules and transforming into fat repository", processContextBuilder);
    }

    static List<String> getSubmoduleLocations(Path submodulesFile) {
        String pathPrefix = "path =";
        try (BufferedReader br = new BufferedReader(new FileReader(submodulesFile.toFile()))) {
            // we don't use \w+ to get the path since the path might contain a slash, which is not a word character
            return br.lines()
                    .filter(l -> l.matches(String.format("^\\s+%s \\S+$", pathPrefix)))
                    .map(l -> l.replace(pathPrefix, "").stripLeading())
                    .toList();
        } catch (IOException e) {
            throw new RuntimeException(
                    String.format("Unable to extract submodule locations from '%s'", submodulesFile),
                    e);
        }
    }

    private String extractProjectPathFromInternalUrl(InternalGitRepositoryUrl internalUrl) {
        String result = internalUrl.getReadwriteUrl().split(":")[1];
        String gitSuffix = ".git";
        if (result.endsWith(gitSuffix)) {
            return result.substring(0, result.length() - gitSuffix.length());
        }

        return result;
    }
}
