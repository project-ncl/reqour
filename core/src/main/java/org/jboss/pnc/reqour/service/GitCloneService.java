/*
 * SPDX-FileCopyrightText: Copyright © 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.pnc.api.enums.ResultStatus;
import org.jboss.pnc.api.reqour.dto.RepositoryCloneRequest;
import org.jboss.pnc.api.reqour.dto.RepositoryCloneResponse;
import org.jboss.pnc.api.reqour.dto.ReqourCallback;
import org.jboss.pnc.reqour.common.GitCommands;
import org.jboss.pnc.reqour.common.exceptions.GitException;
import org.jboss.pnc.reqour.common.utils.IOUtils;
import org.jboss.pnc.reqour.common.utils.URLUtils;
import org.jboss.pnc.reqour.config.ConfigUtils;
import org.jboss.pnc.reqour.config.ProtectedSourceCodeGitProviderConfig;
import org.jboss.pnc.reqour.model.ProcessContext;
import org.jboss.pnc.reqour.runtime.UserLogger;
import org.jboss.pnc.reqour.service.api.CloneService;
import org.slf4j.Logger;

import lombok.extern.slf4j.Slf4j;

/**
 * Clone service using git.
 */
@ApplicationScoped
@Slf4j
public class GitCloneService implements CloneService {

    private final ConfigUtils configUtils;
    private final GitCommands gitCommands;
    private final Logger userLogger;

    @Inject
    public GitCloneService(ConfigUtils configUtils, GitCommands gitCommands, @UserLogger Logger userLogger) {
        this.configUtils = configUtils;
        this.gitCommands = gitCommands;
        this.userLogger = userLogger;
    }

    @Override
    public RepositoryCloneResponse clone(RepositoryCloneRequest cloneRequest) {
        Path cloneDir = IOUtils.createTempDirForCloning();

        String adjustedUrl = URLUtils
                .addUsernameToUrl(cloneRequest.getTargetRepoUrl(), configUtils.getActiveGitProviderConfig().username());
        boolean isInternalRepoNew = isInternalRepoNew(adjustedUrl);
        log.debug("Internal repository with adjusted URL '{}' is considered new: {}", adjustedUrl, isInternalRepoNew);

        // If the origin falls under a configured protected source provider, inject that provider's
        // credentials and route the clone through the dedicated source-clone proxy (if configured).
        // Otherwise clone the origin exactly as before.
        String rawOrigin = cloneRequest.getOriginRepoUrl();
        Optional<ProtectedSourceCodeGitProviderConfig> protectedProvider = configUtils
                .findProtectedSourceCodeGitProvider(rawOrigin);
        String originRepoUrl = protectedProvider.map(provider -> {
            log.info(
                    "Using protected source-code provider credentials for origin matching '{}'",
                    provider.urlPrefix());
            return URLUtils.addCredentialsToUrl(rawOrigin, provider.username(), provider.token());
        }).orElse(rawOrigin);
        // Proxy applies ONLY to protected (external) origins, and only to the clone command below — so the
        // subsequent push to the internal target repository is never sent through the external proxy.
        List<String> cloneGitConfig = protectedProvider.isPresent() ? sourceCloneProxyGitConfig() : List.of();

        if (cloneRequest.getRef() == null || isInternalRepoNew) {
            cloneEverything(cloneRequest, originRepoUrl, cloneGitConfig, cloneDir);
        } else {
            cloneRefOnly(cloneRequest, originRepoUrl, cloneGitConfig, cloneDir);
        }

        try {
            IOUtils.deleteTempDir(cloneDir);
        } catch (IOException ex) {
            log.warn("Could not delete the temporary directory", ex);
        }

        return RepositoryCloneResponse.builder()
                .originRepoUrl(cloneRequest.getOriginRepoUrl())
                .targetRepoUrl(cloneRequest.getTargetRepoUrl())
                .callback(ReqourCallback.builder().id(cloneRequest.getTaskId()).status(ResultStatus.SUCCESS).build())
                .build();
    }

    /**
     * Per-clone git config options routing the origin clone through the configured source-clone proxy,
     * or an empty list if no proxy is configured. Passed as {@code git -c http.proxy=...} so only the
     * (external) origin fetch is proxied. The proxy URL may embed credentials; they are masked in command
     * logs by the process executor and must never be logged directly.
     */
    private List<String> sourceCloneProxyGitConfig() {
        return configUtils.getSourceCloneProxy().map(proxy -> {
            String proxyUrl = URLUtils
                    .addCredentialsToUrl(proxy.url(), proxy.username().orElse(null), proxy.password().orElse(null));
            log.info("Routing protected-origin clone through the configured source-clone proxy");
            return List.of("http.proxy=" + proxyUrl);
        }).orElse(List.of());
    }

    private void cloneEverything(
            RepositoryCloneRequest request,
            String originRepoUrl,
            List<String> cloneGitConfig,
            Path cloneDir) {
        log.info("Syncing everything");

        ProcessContext.Builder processContextBuilder = ProcessContext
                .withWorkdirAndConsumers(cloneDir, userLogger::info, userLogger::warn);

        String targetRemote = "target";

        // From: https://stackoverflow.com/a/7216269/2907906
        gitCommands.cloneMirror(originRepoUrl, cloneGitConfig, processContextBuilder);
        gitCommands.disableBareRepository(processContextBuilder);
        gitCommands.resetHard(processContextBuilder);
        gitCommands.setupGitLfsIfPresent(processContextBuilder);
        gitCommands.addRemote(targetRemote, request.getTargetRepoUrl(), processContextBuilder);
        gitCommands.pushAll(targetRemote, processContextBuilder);
        gitCommands.pushAllTags(targetRemote, processContextBuilder);
    }

    private void cloneRefOnly(
            RepositoryCloneRequest request,
            String originRepoUrl,
            List<String> cloneGitConfig,
            Path cloneDir) {
        log.info("Syncing only ref: {}", request.getRef());

        ProcessContext.Builder processContextBuilder = ProcessContext
                .withWorkdirAndConsumers(cloneDir, userLogger::info, userLogger::warn);

        String targetRemote = "target";

        gitCommands.clone(originRepoUrl, cloneGitConfig, processContextBuilder);
        gitCommands.setupGitLfsIfPresent(processContextBuilder);
        gitCommands.checkout(request.getRef(), true, processContextBuilder);
        gitCommands.addRemote(targetRemote, request.getTargetRepoUrl(), processContextBuilder);
        pushClonedChanges(request.getRef(), targetRemote, processContextBuilder);
    }

    private boolean isInternalRepoNew(String url) {
        log.info("Checking if internal repository with url '{}' is new", url);

        Path cloneDir = IOUtils.createTempDirForCloning();

        gitCommands.clone(url, ProcessContext.withWorkdirAndConsumers(cloneDir, userLogger::info, userLogger::warn));

        ProcessContext.Builder processContextBuilder = ProcessContext.withWorkdirAndIgnoringOutput(cloneDir);
        final boolean isInternalRepoNew;
        if (gitCommands.listTags(processContextBuilder).isEmpty()) {
            isInternalRepoNew = gitCommands.listBranches(processContextBuilder).isEmpty();
        } else {
            isInternalRepoNew = false;
        }

        try {
            IOUtils.deleteTempDir(cloneDir);
        } catch (IOException ex) {
            log.warn("Could not delete the temporary directory", ex);
        }
        return isInternalRepoNew;
    }

    public void pushClonedChanges(String ref, String remote, ProcessContext.Builder processContextBuilder) {
        if (gitCommands.doesBranchExistsLocally(ref, processContextBuilder)) {
            gitCommands.push(remote, ref, false, processContextBuilder);
        } else if (gitCommands.doesTagExistLocally(ref, processContextBuilder)) {
            gitCommands.pushTags(
                    remote,
                    gitCommands.listTagsReachableFromRef(ref, processContextBuilder),
                    processContextBuilder);
        } else {
            addTagAndPush(ref, remote, processContextBuilder);
        }
    }

    private void addTagAndPush(String ref, String remote, ProcessContext.Builder processContextBuilder) {
        log.info("adding tag and pushing");
        String newTag = "reqour-sync-" + ref;

        if (gitCommands.doesTagExistLocally(newTag, processContextBuilder)) {
            throw new GitException(
                    String.format(
                            "Cannot create tag '%s' in the remote '%s'. This tag already exists.",
                            newTag,
                            remote));
        }

        gitCommands.createLightweightTag(newTag, processContextBuilder);
        gitCommands.push(remote, newTag, false, processContextBuilder);
    }
}
