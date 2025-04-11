/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.jboss.pnc.api.enums.ResultStatus;
import org.jboss.pnc.api.reqour.dto.RepositoryCloneRequest;
import org.jboss.pnc.api.reqour.dto.RepositoryCloneResponse;
import org.jboss.pnc.api.reqour.dto.ReqourCallback;
import org.jboss.pnc.reqour.common.GitCommands;
import org.jboss.pnc.reqour.common.exceptions.GitException;
import org.jboss.pnc.reqour.common.utils.IOUtils;
import org.jboss.pnc.reqour.common.utils.URLUtils;
import org.jboss.pnc.reqour.config.ConfigUtils;
import org.jboss.pnc.reqour.model.ProcessContext;
import org.jboss.pnc.reqour.service.api.CloneService;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Clone service using git.
 */
@ApplicationScoped
@Slf4j
public class GitCloneService implements CloneService {

    private final ConfigUtils configUtils;
    private final GitCommands gitCommands;

    @Inject
    public GitCloneService(ConfigUtils configUtils, GitCommands gitCommands) {
        this.configUtils = configUtils;
        this.gitCommands = gitCommands;
    }

    @Override
    public RepositoryCloneResponse clone(RepositoryCloneRequest cloneRequest) {
        Path cloneDir = IOUtils.createTempDirForCloning();

        String adjustedUrl = URLUtils
                .addUsernameToUrl(cloneRequest.getTargetRepoUrl(), configUtils.getActiveGitBackend().username());
        boolean isInternalRepoNew = isInternalRepoNew(adjustedUrl);
        log.debug("Internal repository with adjusted URL '{}' is considered new: {}", adjustedUrl, isInternalRepoNew);

        if (cloneRequest.getRef() == null || isInternalRepoNew) {
            cloneEverything(cloneRequest, cloneDir);
        } else {
            cloneRefOnly(cloneRequest, cloneDir);
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

    private void cloneEverything(RepositoryCloneRequest request, Path cloneDir) {
        log.info("Syncing everything");

        ProcessContext.Builder processContextBuilder = ProcessContext.defaultBuilderWithWorkdir(cloneDir);

        String targetRemote = "target";

        // From: https://stackoverflow.com/a/7216269/2907906
        gitCommands.cloneMirror(request.getOriginRepoUrl(), processContextBuilder);
        gitCommands.disableBareRepository(processContextBuilder);
        gitCommands.setupGitLfsIfPresent(processContextBuilder);
        gitCommands.addRemote(targetRemote, request.getTargetRepoUrl(), processContextBuilder);
        gitCommands.pushAll(targetRemote, processContextBuilder);
        gitCommands.pushAllTags(targetRemote, processContextBuilder);
    }

    private void cloneRefOnly(RepositoryCloneRequest request, Path cloneDir) {
        log.info("Syncing only ref: {}", request.getRef());

        ProcessContext.Builder processContextBuilder = ProcessContext.defaultBuilderWithWorkdir(cloneDir);

        String targetRemote = "target";

        gitCommands.clone(request.getOriginRepoUrl(), processContextBuilder);
        gitCommands.setupGitLfsIfPresent(processContextBuilder);
        gitCommands.checkout(request.getRef(), true, processContextBuilder);
        gitCommands.addRemote(targetRemote, request.getTargetRepoUrl(), processContextBuilder);
        pushClonedChanges(request.getRef(), targetRemote, processContextBuilder);
    }

    private boolean isInternalRepoNew(String url) {
        log.info("Checking if internal repository with url '{}' is new", url);

        Path cloneDir = IOUtils.createTempDirForCloning();
        ProcessContext.Builder processContextBuilder = ProcessContext.defaultBuilderWithWorkdir(cloneDir);

        gitCommands.clone(url, processContextBuilder);

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
