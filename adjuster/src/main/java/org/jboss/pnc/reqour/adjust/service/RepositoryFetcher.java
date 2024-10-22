/**
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.pnc.api.reqour.dto.AdjustRequest;
import org.jboss.pnc.api.reqour.dto.InternalGitRepositoryUrl;
import org.jboss.pnc.reqour.adjust.exception.AdjusterException;
import org.jboss.pnc.reqour.adjust.model.CloningResult;
import org.jboss.pnc.reqour.common.GitCommands;
import org.jboss.pnc.reqour.common.gitlab.GitlabApiService;
import org.jboss.pnc.reqour.common.utils.URLUtils;
import org.jboss.pnc.reqour.config.ConfigUtils;
import org.jboss.pnc.reqour.model.ProcessContext;
import org.jboss.pnc.reqour.service.GitCloneService;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.jboss.pnc.reqour.common.utils.GitUtils.DEFAULT_REMOTE_NAME;

/**
 * Fetcher of the SCM repository, which takes place before the manipulation phase in order to prepare the environment
 * for the manipulator.
 */
@ApplicationScoped
@Slf4j
public class RepositoryFetcher {

    @Inject
    ConfigUtils configUtils;

    @Inject
    GitCommands gitCommands;

    @Inject
    GitCloneService gitCloneService;

    @Inject
    GitlabApiService gitlabApiService;

    @ConfigProperty(name = "reqour.git.internal-urls")
    Optional<List<String>> internalUrls;

    /**
     * Clone the repository based on the given adjust request into the given directory.
     *
     * @param adjustRequest adjust request specifying the details of the cloning
     * @param workdir directory where to clone
     */
    public CloningResult cloneRepository(AdjustRequest adjustRequest, Path workdir) {
        // TODO[NCL-8829]: MDC -- BEGIN SCM_CLONE

        checkTagProtection(adjustRequest);

        boolean isRefInternal = false;
        String gitUsername = configUtils.getActiveGitBackend().username();

        if (syncEnabled(adjustRequest)) {
            isRefInternal = syncExternalRepo(adjustRequest, workdir, gitUsername);
        } else {
            shallowCloneWithTags(
                    URLUtils.addUsernameToUrl(adjustRequest.getInternalUrl().getReadwriteUrl(), gitUsername),
                    adjustRequest.getRef(),
                    workdir);
        }

        gitCommands.configureCommitter(workdir);
        // Get upstream commit before transforming into fat repository (since that potentially creates a new commit)
        String upstreamCommitId = gitCommands.revParse(workdir);

        CloningResult cloningResult = new CloningResult(upstreamCommitId, isRefInternal);
        transformGitSubmodulesIntoFatRepository(workdir);

        return cloningResult;
        // TODO[NCL-8829]: MDC -- END SCM_CLONE
    }

    private void checkTagProtection(AdjustRequest adjustRequest) {
        log.debug("Checking whether tag protection respects Reqour's tag protection configuration");
        String projectPath = extractProjectPathFromInternalUrl(adjustRequest.getInternalUrl());
        if ("gitlab".equals(configUtils.getActiveGitBackendName())
                && !gitlabApiService.doesTagProtectionAlreadyExist(projectPath)) {
            throw new AdjusterException(
                    String.format(
                            "GitLab repository at path '%s' does not have well-configured protected tags",
                            projectPath));
        }
    }

    private boolean syncEnabled(AdjustRequest adjustRequest) {
        return adjustRequest.isSync() && !adjustRequest.getOriginRepoUrl().isBlank();
    }

    private boolean syncExternalRepo(AdjustRequest adjustRequest, Path workdir, String gitUsername) {
        boolean isRefInternal = false;

        ProcessContext.Builder processContextBuilder = ProcessContext.defaultBuilderWithWorkdir(workdir);
        gitCommands.clone(adjustRequest.getOriginRepoUrl(), processContextBuilder);

        if (gitCommands.doesReferenceExist(adjustRequest.getRef(), processContextBuilder)) {
            boolean isRefPR = gitCommands.isReferencePR(adjustRequest.getRef());
            if (isRefPR) {
                gitCommands.checkoutPR(adjustRequest.getRef(), processContextBuilder);
            } else {
                gitCommands.checkout(adjustRequest.getRef(), true, processContextBuilder);
            }

            String originRemote = "origin_remote";
            gitCommands.renameRemote(DEFAULT_REMOTE_NAME, originRemote, processContextBuilder);
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
            if (gitCommands.doesReferenceExist(adjustRequest.getRef(), processContextBuilder)) {
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

        gitCommands.fetchTags(DEFAULT_REMOTE_NAME, false, processContextBuilder);

        List<String> internalUrls = this.internalUrls.orElse(Collections.emptyList());
        for (var internalUrl : internalUrls) {
            if (adjustRequest.getOriginRepoUrl().contains(internalUrl)) {
                return true;
            }
        }
        return isRefInternal;
    }

    private void shallowCloneWithTags(String url, String ref, Path workdir) {
        ProcessContext.Builder processContextBuilder = ProcessContext.defaultBuilderWithWorkdir(workdir);

        gitCommands.init(false, processContextBuilder);
        gitCommands.addRemote(DEFAULT_REMOTE_NAME, url, processContextBuilder);
        gitCommands.fetchRef(DEFAULT_REMOTE_NAME, ref, true, false, processContextBuilder);
        gitCommands.checkout("FETCH_HEAD", false, processContextBuilder);
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

        log.debug("Repository '{}' is using git submodules, transforming into fat repository", workdir);
        ProcessContext.Builder processContextBuilder = ProcessContext.defaultBuilderWithWorkdir(workdir);
        gitCommands.submoduleUpdateInit(processContextBuilder);

        List<String> submoduleLocations = getSubmoduleLocations(submodulesFile);
        for (var submoduleLocation : submoduleLocations) {
            // 1) Delete the reference to the submodule from the index, but keep the files
            gitCommands.remove(submoduleLocation, true, processContextBuilder);

            // 2) Remove the .git metadata folder
            try {
                log.debug("Removing .git/ folder inside the submodule {}", submoduleLocation);
                FileUtils.deleteDirectory(workdir.resolve(submoduleLocation).resolve(".git").toFile());
            } catch (IOException e) {
                throw new RuntimeException("Cannot delete .git/ folder inside the submodule " + submoduleLocation, e);
            }

            // 3) Add the submodule to the main repository index
            gitCommands.add(submoduleLocation, processContextBuilder);
        }

        // 4) Delete the .gitmodules file
        gitCommands.remove(gitModulesFilename, false, processContextBuilder);

        // 5) Commit the transformation
        gitCommands.commit("Removing submodules and transforming into fat repository", processContextBuilder);
    }

    private static List<String> getSubmoduleLocations(Path submodulesFile) {
        String pathPrefix = "path =";
        try (BufferedReader br = new BufferedReader(new FileReader(submodulesFile.toFile()))) {
            return br.lines()
                    .filter(l -> l.matches(String.format("^\\s+%s \\w+$", pathPrefix)))
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
