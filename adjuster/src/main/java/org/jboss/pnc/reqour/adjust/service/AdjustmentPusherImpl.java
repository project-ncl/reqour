/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.service;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.pnc.api.enums.BuildType;
import org.jboss.pnc.api.reqour.dto.AdjustRequest;
import org.jboss.pnc.api.reqour.dto.ManipulatorResult;
import org.jboss.pnc.reqour.adjust.model.AdjustmentPushResult;
import org.jboss.pnc.reqour.adjust.utils.CommonUtils;
import org.jboss.pnc.reqour.common.GitCommands;
import org.jboss.pnc.reqour.common.exceptions.GitException;
import org.jboss.pnc.reqour.common.executor.process.ProcessExecutor;
import org.jboss.pnc.reqour.common.utils.GitUtils;
import org.jboss.pnc.reqour.common.utils.IOUtils;
import org.jboss.pnc.reqour.model.ProcessContext;
import org.jboss.pnc.reqour.runtime.UserLogger;
import org.slf4j.Logger;

import lombok.extern.slf4j.Slf4j;

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

    private final Path workdir = CommonUtils.getAdjustDir();

    @Override
    public AdjustmentPushResult pushAlignedChanges(
            AdjustRequest adjustRequest,
            ManipulatorResult manipulatorResult,
            boolean failOnNoAlignmentChanges) {
        userLogger.info("Pushing aligned changes");
        prepareSearchingBranch(workdir);
        String tagName = findTag(workdir);
        if (tagName == null) {
            log.debug("No existing commit/tag with changes to commit is present. Creating new commit/tag");
            tagName = createTag(
                    workdir,
                    manipulatorResult.getVersioningState().getExecutionRootVersion(),
                    getTagMessage(adjustRequest.getRef(), adjustRequest.getBuildType()),
                    failOnNoAlignmentChanges);
        }
        userLogger.info("Tag name is '{}'", tagName);

        pushTag(workdir, tagName);
        String taggedCommitId = getCommitOfTag(workdir, tagName);
        createBranchForTaggedCommit(workdir, tagName, taggedCommitId);

        return new AdjustmentPushResult(taggedCommitId, tagName);
    }

    private void prepareSearchingBranch(Path workdir) {
        ProcessContext.Builder processContextBuilder = ProcessContext
                .withWorkdirAndConsumers(workdir, userLogger::info, userLogger::warn);

        gitCommands.createBranch("reqour_search_temp_branch_" + UUID.randomUUID(), processContextBuilder);
        gitCommands.addAll(processContextBuilder);
    }

    private String findTag(Path workdir) {
        ProcessContext.Builder processContextBuilder = ProcessContext
                .withWorkdirAndConsumers(workdir, userLogger::info, userLogger::warn);

        String currentTreeSha = gitCommands.writeTree(processContextBuilder);
        log.debug("Current tree SHA is: {}", currentTreeSha);
        return findTagByTreeSha(workdir, currentTreeSha);
    }

    /**
     * Try to find the tag corresponding to the provided tree SHA
     */
    String findTagByTreeSha(Path workdir, String treeSha) {
        ProcessContext.Builder processContextBuilder = ProcessContext
                .withWorkdirAndConsumers(workdir, userLogger::info, userLogger::warn);
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
        ProcessContext.Builder processContextBuilder = ProcessContext
                .withWorkdirAndConsumers(workdir, userLogger::info, userLogger::warn);
        gitCommands.pushTags(GitUtils.DEFAULT_REMOTE_NAME, List.of(tagName), processContextBuilder);
    }

    private String createTag(
            Path workdir,
            String alignmentRootVersion,
            String tagMessage,
            boolean failOnNoAlignmentChanges) {
        ProcessContext.Builder processContextBuilder = ProcessContext
                .withWorkdirAndConsumers(workdir, userLogger::info, userLogger::warn);

        String commitId = tryCommitChanges(processContextBuilder, failOnNoAlignmentChanges);

        log.debug("Going to create a new tag for commit: {}", commitId);
        String tagName = computeTagName(processContextBuilder, alignmentRootVersion, commitId);
        log.debug("The commit is going to be tagged as '{}'", tagName);

        tryTagHead(processContextBuilder, tagName, tagMessage, failOnNoAlignmentChanges);
        return tagName;
    }

    private String tryCommitChanges(ProcessContext.Builder processContextBuilder, boolean failOnNoAlignmentChanges) {
        try {
            return commitChanges(processContextBuilder);
        } catch (GitException ex) {
            if (failOnNoAlignmentChanges) {
                // in case no alignment changes do matter, we (intentionally) re-throw the exception
                throw ex;
            }
            log.warn("Reqour failed to commit, but was set not to fail on no alignment changes");
            // no changes were made, so the current HEAD is the right commit SHA
            return gitCommands.revParse(workdir);
        }
    }

    private String commitChanges(ProcessContext.Builder processContextBuilder) {
        log.debug("Committing changes done by Reqour");
        gitCommands.commit("Reqour", processContextBuilder);
        return gitCommands.revParse(workdir);
    }

    private String computeTagName(
            ProcessContext.Builder processContextBuilder,
            String alignmentRootVersion,
            String commitId) {
        String tagName = (alignmentRootVersion != null) ? alignmentRootVersion : String.format("reqour-%s", commitId);
        if (gitCommands.doesTagExistLocally(tagName, processContextBuilder)) {
            return adjustTagName(tagName, commitId);
        }
        return tagName;
    }

    private void tryTagHead(
            ProcessContext.Builder processContextBuilder,
            String tagName,
            String tagMessage,
            boolean failOnNoAlignmentChanges) {
        try {
            tagHead(processContextBuilder, tagName, tagMessage);
        } catch (GitException ex) {
            if (failOnNoAlignmentChanges) {
                // in case no alignment changes do matter, we (intentionally) re-throw the exception
                throw ex;
            }
            log.warn("Reqour failed to create a new tag, but was set not to fail on no alignment changes");
        }
    }

    private void tagHead(ProcessContext.Builder processContextBuilder, String tagName, String tagMessage) {
        if (!gitCommands.doesTagExistLocally(tagName, processContextBuilder)) {
            gitCommands.createAnnotatedTag(tagName, tagMessage, processContextBuilder);
        }
    }

    private String getCommitOfTag(Path workdir, String tagName) {
        ProcessContext.Builder processContextBuilder = ProcessContext
                .withWorkdirAndConsumers(workdir, userLogger::info, userLogger::warn);
        return gitCommands.getCommitByTag(tagName, processContextBuilder);
    }

    private static String adjustTagName(String tagName, String commitId) {
        return String.format("%s-%s", tagName, commitId.substring(0, 8));
    }

    private void createBranchForTaggedCommit(Path workdir, String tagName, String commitId) {
        ProcessContext.Builder processContextBuilder = ProcessContext
                .withWorkdirAndConsumers(workdir, userLogger::info, userLogger::warn);
        String branchName = "branch-reqour-" + tagName + "-" + commitId;

        boolean doesBranchAlreadyExist = gitCommands.doesBranchExistAtRemote(branchName, processContextBuilder);
        if (doesBranchAlreadyExist) {
            userLogger.debug("Branch '{}' already exists, skipping the creation", branchName);
            // In case the branch already exists, nothing to do (we're assuming that this means the commit is already in
            // the branch)
            return;
        }

        userLogger.debug("Creating branch '{}'", branchName);
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
