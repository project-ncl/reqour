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
package org.jboss.pnc.reqour.facade.clone;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.jboss.pnc.api.reqour.dto.RepositoryCloneRequest;
import org.jboss.pnc.reqour.common.GitCommands;
import org.jboss.pnc.reqour.common.utils.IOUtils;
import org.jboss.pnc.reqour.common.utils.URLUtils;
import org.jboss.pnc.reqour.config.ConfigUtils;
import org.jboss.pnc.reqour.model.ProcessContext;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

/**
 * Clone provider using git.
 */
@ApplicationScoped
@Slf4j
public class GitCloneProvider implements CloneProvider {

    private final ConfigUtils configUtils;
    private final GitCommands gitCommands;

    @Inject
    public GitCloneProvider(ConfigUtils configUtils, GitCommands gitCommands) {
        this.configUtils = configUtils;
        this.gitCommands = gitCommands;
    }

    @Override
    public void clone(RepositoryCloneRequest cloneRequest) {
        Path cloneDir = IOUtils.createTempDirForCloning();

        if (cloneRequest.getRef() == null || isInternalRepoNew(
                URLUtils.addUsernameToUrl(
                        cloneRequest.getTargetRepoUrl(),
                        configUtils.getActiveGitBackend().getUsername()))) {
            cloneEverything(cloneRequest, cloneDir);
        } else {
            cloneRefOnly(cloneRequest, cloneDir);
        }
        // TODO: send callback
    }

    @Override
    public String name() {
        return "git";
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
        pushClonedChanges(request.getRef(), request.getOriginRepoUrl(), targetRemote, processContextBuilder);
    }

    private boolean isInternalRepoNew(String url) {
        Path cloneDir = IOUtils.createTempDirForCloning();

        ProcessContext.Builder processContextBuilder = ProcessContext.builder()
                .workingDirectory(cloneDir)
                .extraEnvVariables(Collections.emptyMap())
                .stdoutConsumer(IOUtils.IGNORE_OUTPUT)
                .stderrConsumer(IOUtils.IGNORE_OUTPUT);

        gitCommands.clone(url, processContextBuilder);
        if (IOUtils.countLines(gitCommands.listTags(processContextBuilder)) > 0) {
            return false;
        }

        return IOUtils.countLines(gitCommands.listBranches(processContextBuilder)) == 0;
    }

    private void pushClonedChanges(
            String ref,
            String originRepo,
            String targetRepo,
            ProcessContext.Builder processContextBuilder) {
        if (gitCommands.isReferenceBranch(ref, originRepo, processContextBuilder)) {
            gitCommands.push(originRepo, ref, false, processContextBuilder);
        } else if (gitCommands.isReferenceTag(ref, originRepo, processContextBuilder)) {
            pushRefWithTags(ref, targetRepo, processContextBuilder);
        } else {
            addTagAndPush(ref, targetRepo, processContextBuilder);
        }
    }

    private void pushRefWithTags(String ref, String remote, ProcessContext.Builder processContextBuilder) {
        if (ref == null) {
            gitCommands.pushAllTags(remote, processContextBuilder);
        } else {
            List<String> reachableTags = IOUtils
                    .splitByNewLine(gitCommands.listTagsReachableFromRef(ref, processContextBuilder));
            gitCommands.pushRefWithTags(ref, remote, reachableTags, processContextBuilder);
        }
    }

    private void addTagAndPush(String ref, String remote, ProcessContext.Builder processContextBuilder) {
        String newTag = "reqour-" + ref;

        if (gitCommands.isReferenceTag(newTag, remote, processContextBuilder)) {
            throw new IllegalArgumentException(
                    String.format(
                            "Cannot create tag %s in the remote %s. This tag probably already exists.",
                            newTag,
                            remote));
        }

        gitCommands.addTag(newTag, processContextBuilder);
        gitCommands.push(remote, ref, false, processContextBuilder);
    }
}
