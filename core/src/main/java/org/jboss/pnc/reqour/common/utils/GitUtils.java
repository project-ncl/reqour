/**
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.common.utils;

import org.jboss.pnc.reqour.common.exceptions.GitException;
import org.jboss.pnc.reqour.model.ProcessContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class, which composes the full git commands. <br/>
 * <br/>
 * Note: These commands are often further used as {@link ProcessContext#getCommand()}. That's the reason why it is
 * returning {@code List<String>} (instead of {@code String}).
 */
public class GitUtils {

    public static final String DEFAULT_REMOTE_NAME = "origin";
    public static final String FETCH_HEAD = "FETCH_HEAD";

    public static List<String> add(String filename) {
        return List.of("git", "add", filename);
    }

    public static List<String> addAll() {
        return List.of("git", "add", "--all");
    }

    public static List<String> branch() {
        return List.of("git", "branch", "-a");
    }

    public static List<String> checkout(String ref, boolean force) {
        List<String> command = new ArrayList<>(List.of("git", "checkout"));
        if (force) {
            command.add("--force");
        }
        command.add(ref);
        command.add("--");

        return command;
    }

    public static List<String> clone(String url) {
        return List.of("git", "clone", "--", url, ".");
    }

    public static List<String> cloneMirror(String url) {
        return List.of("git", "clone", "--mirror", "--", url, ".");
    }

    public static List<String> commit(String commitMessage) {
        return List.of("git", "commit", "-m", commitMessage);
    }

    public static List<String> configureUserEmail(String email) {
        return List.of("git", "config", "--local", "user.email", email);
    }

    public static List<String> configureUserName(String username) {
        return List.of("git", "config", "--local", "user.name", username);
    }

    public static List<String> disableBareRepository() {
        return List.of("git", "config", "--bool", "core.bare", "false");
    }

    public static List<String> init(boolean bare) {
        List<String> command = new ArrayList<>(List.of("git", "init"));
        if (bare) {
            command.add("--bare");
        }

        return command;
    }

    public static List<String> doesBranchExistAtRemote(String remote, String branch) {
        return List.of("git", "show-branch", String.format("remotes/%s/%s", remote, branch));
    }

    public static List<String> isReferenceBranch(String ref) {
        return List.of("git", "show-ref", "-q", "--heads", ref);
    }

    public static List<String> isReferenceTag(String ref) {
        return List.of("git", "show-ref", "-q", "--tags", ref);
    }

    public static List<String> doesShaExists(String ref) {
        return List.of("git", "cat-file", "-e", ref + "^{commit}");
    }

    public static List<String> fetchRef(String remote, String ref, boolean fetchShallowly, boolean dryRun) {
        List<String> command = new ArrayList<>(List.of("git", "fetch", remote, ref));
        if (fetchShallowly) {
            command.add("--depth=1");
        }
        if (dryRun) {
            command.add("--dry-run");
        }
        return command;
    }

    public static List<String> push(String remote, String ref, boolean force) {
        List<String> command = new ArrayList<>(List.of("git", "push"));
        if (force) {
            command.add("--force");
        }
        command.add(remote);
        command.add(ref);
        command.add("--");

        return command;
    }

    public static List<String> pushAll(String remote) {
        return List.of("git", "push", "--all", remote, "--");
    }

    public static List<String> pushTags(String remote, List<String> tags) {
        if (tags.isEmpty()) {
            throw new GitException("Cannot push tags to " + remote + ", since tags is an empty array.");
        }

        var command = new ArrayList<>(List.of("git", "push", remote, "tag"));
        command.addAll(tags);
        return command;
    }

    public static List<String> pushAllTags(String remote) {
        return List.of("git", "push", "--tags", remote, "--");
    }

    public static List<String> pushRefWithTags(String ref, String remote, List<String> tags) {
        if (tags.isEmpty()) {
            throw new GitException("Cannot push tags to " + remote + ", since tags is an empty array.");
        }

        var command = new ArrayList<>(List.of("git", "push", remote, ref, "tag"));
        command.addAll(tags);
        return command;
    }

    public static List<String> addRemote(String remote, String url) {
        return List.of("git", "remote", "add", remote, url, "--");
    }

    public static List<String> renameRemote(String oldRemote, String newRemote) {
        return List.of("git", "remote", "rename", oldRemote, newRemote);
    }

    public static List<String> revParse(String ref) {
        return List.of("git", "rev-parse", ref);
    }

    public static List<String> remove(String filename, boolean cached) {
        List<String> command = new ArrayList<>(List.of("git", "rm", filename));
        if (cached) {
            command.add("--cached");
        }
        return command;
    }

    public static List<String> submoduleUpdateInit() {
        return List.of("git", "submodule", "update", "--init");
    }

    public static List<String> createBranch(String branchName) {
        return List.of("git", "switch", "-c", branchName);
    }

    public static List<String> listTags() {
        return List.of("git", "tag");
    }

    public static List<String> listTagsReachableFromReference(String ref) {
        return List.of("git", "tag", "--merged", ref);
    }

    public static List<String> createAnnotatedTag(String name, String message) {
        return List.of("git", "tag", "-a", "-m", message, name);
    }

    public static List<String> createLightweightTag(String name) {
        return List.of("git", "tag", name);
    }

    public static List<String> fetchTags(String remote, boolean shallow) {
        List<String> command = new ArrayList<>(List.of("git", "fetch", remote, "--tags", "-q"));
        if (shallow) {
            command.add("--depth=1");
        }
        return command;
    }

    public static List<String> getCommitByTag(String tagName) {
        return List.of("git", "rev-list", "-n", "1", tagName);
    }

    public static List<String> version() {
        return List.of("git", "--version");
    }

    public static List<String> writeTree() {
        return List.of("git", "write-tree");
    }
}
