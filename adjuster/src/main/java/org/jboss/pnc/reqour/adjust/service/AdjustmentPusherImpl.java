/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.jboss.pnc.api.enums.BuildType;
import org.jboss.pnc.api.reqour.dto.AdjustRequest;
import org.jboss.pnc.api.reqour.dto.ManipulatorResult;
import org.jboss.pnc.reqour.adjust.model.AdjustmentPushResult;
import org.jboss.pnc.reqour.common.GitCommands;
import org.jboss.pnc.reqour.common.executor.process.ProcessExecutor;
import org.jboss.pnc.reqour.common.utils.GitUtils;
import org.jboss.pnc.reqour.common.utils.IOUtils;
import org.jboss.pnc.reqour.model.ProcessContext;
import org.jboss.pnc.reqour.runtime.UserLogger;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for pushing alignment changes once the manipulation took place.
 */
@ApplicationScoped
@Slf4j
public class AdjustmentPusherImpl implements AdjustmentPusher {

    @Inject
    ProcessExecutor processExecutor;

    @Inject
    GitCommands gitCommands;

    @Inject
    @UserLogger
    Logger userLogger;

    private final Path workdir = org.jboss.pnc.reqour.adjust.utils.IOUtils.getAdjustDir();

    @Override
    public AdjustmentPushResult pushAlignedChanges(AdjustRequest adjustRequest, ManipulatorResult manipulatorResult) {
        userLogger.info("Pushing aligned changes");
        prepareSearchingBranch(workdir);
        String tagName = findTag(workdir);
        if (tagName == null) {
            log.debug("No existing commit/tag with changes to commit is present. Creating new commit/tag");
            tagName = createTag(
                    workdir,
                    manipulatorResult.getVersioningState().getExecutionRootVersion(),
                    getTagMessage(adjustRequest.getRef(), adjustRequest.getBuildType()));
        }
        userLogger.info("Tag name is '{}'", tagName);

        pushTag(workdir, tagName);
        String taggedCommitId = getCommitOfTag(workdir, tagName);
        createBranchForTaggedCommit(workdir, tagName, taggedCommitId);

        return new AdjustmentPushResult(taggedCommitId, tagName);
    }

    private void prepareSearchingBranch(Path workdir) {
        ProcessContext.Builder processContextBuilder = ProcessContext.defaultBuilderWithWorkdir(workdir);

        gitCommands.createBranch("reqour_search_temp_branch_" + UUID.randomUUID(), processContextBuilder);
        gitCommands.addAll(processContextBuilder);
    }

    private String findTag(Path workdir) {
        ProcessContext.Builder processContextBuilder = ProcessContext.defaultBuilderWithWorkdir(workdir);

        String currentTreeSha = gitCommands.writeTree(processContextBuilder);
        log.debug("Current tree SHA is: {}", currentTreeSha);
        return findTagByTreeSha(workdir, currentTreeSha);
    }

    /**
     * Try to find the tag corresponding to the provided tree SHA
     */
    String findTagByTreeSha(Path workdir, String treeSha) {
        ProcessContext.Builder processContextBuilder = ProcessContext.defaultBuilderWithWorkdir(workdir);
        List<String> tags = IOUtils.splitByNewLine(
                processExecutor.stdout(
                        processContextBuilder.command(
                                List.of("git", "--no-pager", "log", "--pretty=%T::%d", "--tags", "--no-walk"))));

        Optional<String> treeReferences = tags.stream()
                .filter(entry -> entry.startsWith(treeSha))
                .findFirst()
                .map(entry -> entry.split("::")[1].strip());
        if (treeReferences.isEmpty()) {
            return null;
        }

        Pattern p = Pattern.compile("^\\(.*?tag: ([^,)]+).*\\)$");
        Matcher m = p.matcher(treeReferences.get());
        if (m.matches()) {
            String tagName = m.group(1);
            log.debug("The tag with tree SHA {} was found: '{}'", treeSha, tagName);
            return tagName;
        }

        log.debug("No tag with the tree SHA {} was found", treeSha);
        return null;
    }

    private void pushTag(Path workdir, String tagName) {
        ProcessContext.Builder processContextBuilder = ProcessContext.defaultBuilderWithWorkdir(workdir);
        gitCommands.pushTags(GitUtils.DEFAULT_REMOTE_NAME, List.of(tagName), processContextBuilder);
    }

    private String createTag(Path workdir, String alignmentRootVersion, String tagMessage) {
        ProcessContext.Builder processContextBuilder = ProcessContext.defaultBuilderWithWorkdir(workdir);
        gitCommands.commit("Reqour", processContextBuilder);
        String commitId = gitCommands.revParse(workdir);

        String tagName = (alignmentRootVersion != null) ? alignmentRootVersion : String.format("reqour-%s", commitId);
        boolean doesTagAlreadyExist = gitCommands.doesTagExistLocally(tagName, processContextBuilder);
        if (doesTagAlreadyExist) {
            tagName = String.format("%s-%s", tagName, commitId.substring(0, 8));
        }

        if (gitCommands.doesTagExistLocally(tagName, processContextBuilder)) {
            return tagName;
        }

        gitCommands.createAnnotatedTag(tagName, tagMessage, processContextBuilder);
        return tagName;
    }

    private String getCommitOfTag(Path workdir, String tagName) {
        return gitCommands.getCommitByTag(tagName, ProcessContext.defaultBuilderWithWorkdir(workdir));
    }

    private void createBranchForTaggedCommit(Path workdir, String tagName, String commitId) {
        ProcessContext.Builder processContextBuilder = ProcessContext.defaultBuilderWithWorkdir(workdir);
        String branchName = "branch-reqour-" + tagName + "-" + commitId;

        boolean doesBranchAlreadyExist = gitCommands.doesBranchExistAtRemote(branchName, processContextBuilder);
        if (doesBranchAlreadyExist) {
            log.debug("Branch '{}' already exists, skipping the creation", branchName);
            // In case the branch already exists, nothing to do (we're assuming that this means the commit is already in
            // the branch)
            return;
        }

        log.debug("Creating branch '{}'", branchName);
        gitCommands.createBranch(branchName, processContextBuilder);
        gitCommands.push(branchName, false, processContextBuilder);
    }

    private String getTagMessage(String originalReference, BuildType adjustType) {
        return String.format(
                "Tag automatically generated from Reqour\n" + "Original Reference: %s\n" + "Adjust Type: %s",
                originalReference,
                adjustType);
    }
}
