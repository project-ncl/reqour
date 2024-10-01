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

    public static List<String> isReferenceBranch(String ref) {
        return List.of("git", "show-ref", "-q", "--heads", ref);
    }

    public static List<String> isReferenceTag(String ref) {
        return List.of("git", "show-ref", "-q", "--tags", ref);
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

    public static List<String> listTags() {
        return List.of("git", "tag");
    }

    public static List<String> listTagsReachableFromReference(String ref) {
        return List.of("git", "tag", "--merged", ref);
    }

    public static List<String> addTag(String name) {
        return List.of("git", "tag", name);
    }

    public static List<String> version() {
        return List.of("git", "--version");
    }
}
