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
import java.util.Collections;

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
        log.info("Internal repository with adjusted URL '{}' is considered new: {}", adjustedUrl, isInternalRepoNew);

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

        ProcessContext.Builder processContextBuilder = ProcessContext.builder()
                .workingDirectory(cloneDir)
                .extraEnvVariables(Collections.emptyMap())
                .stdoutConsumer(System.out::println)
                .stderrConsumer(System.err::println);

        String targetRemote = "target";

        // From: https://stackoverflow.com/a/7216269/2907906
        gitCommands.cloneMirror(request.getOriginRepoUrl(), processContextBuilder);
        gitCommands.disableBareRepository(processContextBuilder);
        gitCommands.addRemote(targetRemote, request.getTargetRepoUrl(), processContextBuilder);
        gitCommands.pushAll(targetRemote, processContextBuilder);
        gitCommands.pushAllTags(targetRemote, processContextBuilder);
    }

    private void cloneRefOnly(RepositoryCloneRequest request, Path cloneDir) {
        log.info("Syncing only ref: {}", request.getRef());

        ProcessContext.Builder processContextBuilder = ProcessContext.builder()
                .workingDirectory(cloneDir)
                .extraEnvVariables(Collections.emptyMap())
                .stdoutConsumer(System.out::println)
                .stderrConsumer(System.err::println);

        String targetRemote = "target";

        gitCommands.clone(request.getOriginRepoUrl(), processContextBuilder);
        gitCommands.checkout(request.getRef(), true, processContextBuilder);
        gitCommands.addRemote(targetRemote, request.getTargetRepoUrl(), processContextBuilder);
        pushClonedChanges(request.getRef(), targetRemote, processContextBuilder);
    }

    private boolean isInternalRepoNew(String url) {
        log.info("Checking if internal repository with url '{}' is new", url);

        Path cloneDir = IOUtils.createTempDirForCloning();
        ProcessContext.Builder processContextBuilder = ProcessContext.builder()
                .workingDirectory(cloneDir)
                .extraEnvVariables(Collections.emptyMap())
                .stdoutConsumer(System.out::println)
                .stderrConsumer(System.err::println);

        gitCommands.clone(url, processContextBuilder);
        if (IOUtils.countLines(gitCommands.listTags(processContextBuilder)) > 0) {
            return false;
        }

        return IOUtils.countLines(gitCommands.listBranches(processContextBuilder)) == 0;
    }

    private void pushClonedChanges(String ref, String remote, ProcessContext.Builder processContextBuilder) {
        if (gitCommands.isReferenceBranch(ref, processContextBuilder)) {
            gitCommands.push(remote, ref, false, processContextBuilder);
        } else if (gitCommands.isReferenceTag(ref, processContextBuilder)) {
            gitCommands.pushTags(
                    remote,
                    IOUtils.splitByNewLine(gitCommands.listTagsReachableFromRef(ref, processContextBuilder)),
                    processContextBuilder);
        } else {
            addTagAndPush(ref, remote, processContextBuilder);
        }
    }

    private void addTagAndPush(String ref, String remote, ProcessContext.Builder processContextBuilder) {
        log.info("adding tag and pushing");
        String newTag = "reqour-" + ref;

        if (gitCommands.isReferenceTag(newTag, processContextBuilder)) {
            throw new GitException(
                    String.format(
                            "Cannot create tag '%s' in the remote '%s'. This tag already exists.",
                            newTag,
                            remote));
        }

        gitCommands.addTag(newTag, processContextBuilder);
        gitCommands.push(remote, newTag, false, processContextBuilder);
    }
}
