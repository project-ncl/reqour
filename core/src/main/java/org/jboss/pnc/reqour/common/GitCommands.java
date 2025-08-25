/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.common;

import static org.jboss.pnc.reqour.common.utils.GitUtils.DEFAULT_REMOTE_NAME;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.pnc.reqour.common.exceptions.GitException;
import org.jboss.pnc.reqour.common.executor.process.ProcessExecutor;
import org.jboss.pnc.reqour.common.utils.GitUtils;
import org.jboss.pnc.reqour.common.utils.IOUtils;
import org.jboss.pnc.reqour.config.Committer;
import org.jboss.pnc.reqour.config.ConfigUtils;
import org.jboss.pnc.reqour.model.ProcessContext;
import org.jboss.pnc.reqour.runtime.UserLogger;
import org.slf4j.Logger;

import lombok.extern.slf4j.Slf4j;

/**
 * Handles the composing of git commands (by delegating it into {@link GitUtils}) and then executing it as a shell
 * process (by delegating it into {@link ProcessExecutor}).
 */
@ApplicationScoped
@Slf4j
public class GitCommands {

    public static final String GIT_ATTRIBUTES = ".gitattributes";
    private static final String LFS_PATTERN = "lfs";

    @Inject
    ConfigUtils configUtils;

    @Inject
    @UserLogger
    Logger userLogger;

    private final ProcessExecutor processExecutor;

    @Inject
    public GitCommands(ProcessExecutor processExecutor) {
        this.processExecutor = processExecutor;
    }

    private void executeGitCommand(
            List<String> command,
            ProcessContext.Builder processContextBuilder,
            String errorMessage) {
        int exitCode = processExecutor.execute(processContextBuilder.command(command).build());
        if (exitCode != 0) {
            throw new GitException(errorMessage);
        }
    }

    private String getSingleValueResultOfGitCommand(ProcessContext.Builder processContextBuilder) {
        return processExecutor.stdout(processContextBuilder).stripTrailing();
    }

    private List<String> getLinesStdoutOfGitCommand(ProcessContext.Builder processContextBuilder) {
        return IOUtils.splitByNewLine(processExecutor.stdout(processContextBuilder));
    }

    public void add(String filename, ProcessContext.Builder processContextBuilder) {
        executeGitCommand(
                GitUtils.add(filename),
                processContextBuilder,
                String.format("Cannot add file '%s'", filename));
    }

    public void addAll(ProcessContext.Builder processContextBuilder) {
        executeGitCommand(
                GitUtils.addAll(),
                processContextBuilder,
                "Cannot add all resources from index to staging area");
    }

    public void createBranch(String branchName, ProcessContext.Builder processContextBuilder) {
        executeGitCommand(
                GitUtils.createBranch(branchName),
                processContextBuilder,
                String.format("Unable to create new branch '%s'", branchName));
    }

    public List<String> listBranches(ProcessContext.Builder processContextBuilder) {
        return getLinesStdoutOfGitCommand(processContextBuilder.command(GitUtils.branch()));
    }

    public void checkout(String ref, boolean force, ProcessContext.Builder processContextBuilder) {
        executeGitCommand(
                GitUtils.checkout(ref, force),
                processContextBuilder,
                String.format("Cannot checkout to '%s'", ref));
    }

    public void clone(String url, ProcessContext.Builder processContextBuilder) {
        tryClone(
                GitUtils::clone,
                processContextBuilder,
                url,
                String.format("Failed to clone repository from '%s'.", url));
    }

    public void cloneMirror(String url, ProcessContext.Builder processContextBuilder) {
        tryClone(
                GitUtils::cloneMirror,
                processContextBuilder,
                url,
                String.format("Cannot mirror-clone the repository from '%s'.", url));
    }

    private void tryClone(
            Function<String, List<String>> commandSupplier,
            ProcessContext.Builder processContextBuilder,
            String url,
            String errorMessage) {
        if (isGithubRepository(url)) {
            errorMessage += " " + githubRepoCloningInfo(url);
        }

        executeGitCommand(commandSupplier.apply(url), processContextBuilder, errorMessage);
    }

    public void commit(String commitMessage, ProcessContext.Builder processContextBuilder) {
        executeGitCommand(GitUtils.commit(commitMessage), processContextBuilder, "Cannot make the commit");
    }

    public void configureCommitter(Path workdir) {
        Committer committer = configUtils.getCommitter();
        ProcessContext.Builder processContextBuilder = ProcessContext
                .withWorkdirAndConsumers(workdir, log::debug, log::warn);
        executeGitCommand(
                GitUtils.configureUserEmail(committer.email()),
                processContextBuilder,
                "Cannot configure user email");
        executeGitCommand(
                GitUtils.configureUserName(committer.name()),
                processContextBuilder,
                "Cannot configure user name");
    }

    public void disableBareRepository(ProcessContext.Builder processContextBuilder) {
        executeGitCommand(
                GitUtils.disableBareRepository(),
                processContextBuilder,
                "Cannot disable this bare repository");
    }

    public void init(boolean bare, ProcessContext.Builder processContextBuilder) {
        executeGitCommand(
                GitUtils.init(bare),
                processContextBuilder,
                "Unable to make this directory as Git repository");
    }

    public boolean doesBranchExistAtRemote(String branch, ProcessContext.Builder processContextBuilder) {
        return doesBranchExistAtRemote(DEFAULT_REMOTE_NAME, branch, processContextBuilder);
    }

    public boolean doesBranchExistAtRemote(String remote, String branch, ProcessContext.Builder processContextBuilder) {
        log.debug("Checking whether branch '{}' exists at remote '{}'", branch, remote);
        return processExecutor
                .execute(processContextBuilder.command(GitUtils.doesBranchExistAtRemote(remote, branch)).build()) == 0;
    }

    public boolean doesBranchExistsLocally(String ref, ProcessContext.Builder processContextBuilder) {
        return processExecutor
                .execute(processContextBuilder.command(GitUtils.doesBranchExistLocally(ref)).build()) == 0;
    }

    public void setupGitLfsIfPresent(ProcessContext.Builder processContextBuilder) {
        if (isLfsPresent(processContextBuilder)) {
            userLogger.info("LFS detected in the repository");
            processExecutor.execute(processContextBuilder.command(GitUtils.lfsInstall()).build());
            processExecutor.execute(processContextBuilder.command(GitUtils.lfsFetchAll()).build());
        }
    }

    boolean isLfsPresent(ProcessContext.Builder processContextBuilder) {
        Path gitattributesFilePath = processContextBuilder.build().getWorkingDirectory().resolve(GIT_ATTRIBUTES);
        return Files.exists(gitattributesFilePath) && IOUtils.fileContainsString(gitattributesFilePath, LFS_PATTERN);
    }

    public boolean doesTagExistLocally(String ref, ProcessContext.Builder processContextBuilder) {
        return processExecutor.execute(processContextBuilder.command(GitUtils.doesTagExistLocally(ref)).build()) == 0;
    }

    public boolean doesTagExistAtRemote(String remote, String ref, ProcessContext.Builder processContextBuilder) {
        log.debug("Checking whether tag '{}' exists at remote '{}'", ref, remote);
        return processExecutor
                .execute(processContextBuilder.command(GitUtils.doesTagExistAtRemote(remote, ref)).build()) == 0;
    }

    public boolean doesShaExists(String ref, ProcessContext.Builder processContextBuilder) {
        log.debug("Checking whether sha '{}' exists in the current tree", ref);
        return processExecutor.execute(processContextBuilder.command(GitUtils.doesShaExists(ref)).build()) == 0;
    }

    public static boolean isReferencePR(String ref) {
        Pattern githubPRPattern = Pattern.compile("^pull/\\d+$");
        Pattern gitlabPRPattern = Pattern.compile("^merge-requests/\\d+$");

        Matcher githubMatcher = githubPRPattern.matcher(ref);
        Matcher gitlabMatcher = gitlabPRPattern.matcher(ref);

        return githubMatcher.matches() || gitlabMatcher.matches();
    }

    public void checkoutPR(String ref, ProcessContext.Builder processContextBuilder) {
        fetchRef(DEFAULT_REMOTE_NAME, modifyPullRequestRefToBeFetchable(ref), false, false, processContextBuilder);
        checkout(GitUtils.FETCH_HEAD, false, processContextBuilder);
    }

    public boolean doesReferenceExistRemotely(String ref, ProcessContext.Builder processContextBuilder) {
        return doesReferenceExistAtRemote(DEFAULT_REMOTE_NAME, ref, processContextBuilder);
    }

    public boolean doesReferenceExistAtRemote(String remote, String ref, ProcessContext.Builder processContextBuilder) {
        return doesTagExistAtRemote(remote, ref, processContextBuilder)
                || doesBranchExistAtRemote(remote, ref, processContextBuilder)
                || doesShaExists(ref, processContextBuilder)
                || doesPRExistsAtRemote(remote, ref, processContextBuilder);
    }

    public boolean doesPRExistsAtRemote(String remote, String ref, ProcessContext.Builder processContextBuilder) {
        log.debug("Checking whether PR '{}' exists at remote '{}'", ref, remote);
        try {
            fetchRef(remote, modifyPullRequestRefToBeFetchable(ref), false, true, processContextBuilder);
            return true;
        } catch (GitException ex) {
            return false;
        }
    }

    public void push(String ref, boolean force, ProcessContext.Builder processContextBuilder) {
        push(DEFAULT_REMOTE_NAME, ref, force, processContextBuilder);
    }

    public void push(String remote, String ref, boolean force, ProcessContext.Builder processContextBuilder) {
        executeGitCommand(
                GitUtils.push(remote, ref, force),
                processContextBuilder,
                String.format("Cannot %spush reference '%s' to '%s'", force ? "force-" : "", ref, remote));
    }

    public void pushAll(String remote, ProcessContext.Builder processContextBuilder) {
        executeGitCommand(
                GitUtils.pushAll(remote),
                processContextBuilder,
                String.format("Cannot push all to '%s'", remote));
    }

    public void pushTags(String remote, List<String> tags, ProcessContext.Builder processContextBuilder) {
        executeGitCommand(
                GitUtils.pushTags(remote, tags),
                processContextBuilder,
                String.format("Cannot push to '%s' the following tags: %s", remote, tags));
    }

    public void pushAllTags(String remote, ProcessContext.Builder processContextBuilder) {
        executeGitCommand(
                GitUtils.pushAllTags(remote),
                processContextBuilder,
                String.format("Cannot push tags to '%s'", remote));
    }

    public void addRemote(String remote, String url, ProcessContext.Builder processContextBuilder) {
        executeGitCommand(
                GitUtils.addRemote(remote, url),
                processContextBuilder,
                String.format("Cannot add new remote '%s' to '%s'", remote, url));
    }

    public void renameRemote(String oldName, String newName, ProcessContext.Builder processContextBuilder) {
        executeGitCommand(
                GitUtils.renameRemote(oldName, newName),
                processContextBuilder,
                String.format("Unable to rename remote '%s' to '%s'", oldName, newName));
    }

    public String revParse(Path workdir) {
        return revParse(workdir, "HEAD");
    }

    public String revParse(Path workdir, String ref) {
        return getSingleValueResultOfGitCommand(
                ProcessContext.withWorkdirAndIgnoringOutput(workdir).command(GitUtils.revParse(ref)));
    }

    public void remove(String filename, boolean cached, ProcessContext.Builder processContextBuilder) {
        executeGitCommand(
                GitUtils.remove(filename, cached),
                processContextBuilder,
                String.format("Cannot delete the file '%s'", filename));
    }

    public void submoduleUpdateInit(ProcessContext.Builder processContextBuilder) {
        executeGitCommand(GitUtils.submoduleUpdateInit(), processContextBuilder, "Cannot make submodule update init");
    }

    public void createAnnotatedTag(String name, String message, ProcessContext.Builder processContextBuilder) {
        executeGitCommand(
                GitUtils.createAnnotatedTag(name, message),
                processContextBuilder,
                String.format("Unable to create annotated tag '%s'", name));
    }

    public void createLightweightTag(String name, ProcessContext.Builder processContextBuilder) {
        executeGitCommand(
                GitUtils.createLightweightTag(name),
                processContextBuilder,
                String.format("Cannot add tag '%s'", name));
    }

    public List<String> listTags(ProcessContext.Builder processContextBuilder) {
        return getLinesStdoutOfGitCommand(processContextBuilder.command(GitUtils.listTags()));
    }

    public List<String> listTagsReachableFromRef(String ref, ProcessContext.Builder processContextBuilder) {
        return getLinesStdoutOfGitCommand(processContextBuilder.command(GitUtils.listTagsReachableFromReference(ref)));
    }

    public String getCommitByTag(String tagName, ProcessContext.Builder processContextBuilder) {
        return getSingleValueResultOfGitCommand(processContextBuilder.command(GitUtils.getCommitByTag(tagName)));
    }

    public void fetchRef(
            String remote,
            String ref,
            boolean fetchShallowly,
            boolean dryRun,
            ProcessContext.Builder processContextBuilder) {
        executeGitCommand(
                GitUtils.fetchRef(remote, ref, fetchShallowly, dryRun),
                processContextBuilder,
                String.format("Cannot fetch reference '%s'", ref));
    }

    public void fetchTags(String remote, boolean shallow, ProcessContext.Builder processContextBuilder) {
        executeGitCommand(
                GitUtils.fetchTags(remote, shallow),
                processContextBuilder,
                String.format("Cannot fetch tags from remote '%s'", remote));
    }

    public String writeTree(ProcessContext.Builder processContextBuilder) {
        return getSingleValueResultOfGitCommand(processContextBuilder.command(GitUtils.writeTree()));
    }

    public void resetHard(ProcessContext.Builder processContextBuilder) {
        executeGitCommand(
                GitUtils.resetHard(),
                processContextBuilder,
                "Cannot reset hard this repository");
    }

    private static String modifyPullRequestRefToBeFetchable(String ref) {
        if (!isReferencePR(ref)) {
            throw new GitException(String.format("Given reference (%s) does not match PR reference format", ref));
        }
        return ref + "/head";
    }

    private static boolean isGithubRepository(String url) {
        return url.contains("github.com");
    }

    private String githubRepoCloningInfo(String url) {
        if (configUtils.getPrivateGithubUser().isEmpty()) {
            log.warn("Private GitHub user not set!");
            return String.format(
                    "If the Github repository is a private repository, you need to add a Github user "
                            + "with read-permissions to '%s'. Please email the Newcastle mailing list for more information.",
                    url);
        }

        return String.format(
                "If the Github repository is a private repository, you need to add the Github user '%s' with read-permissions to '%s'.",
                configUtils.getPrivateGithubUser().get(),
                url);
    }
}
