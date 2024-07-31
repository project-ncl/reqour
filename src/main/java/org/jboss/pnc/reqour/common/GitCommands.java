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
package org.jboss.pnc.reqour.common;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.pnc.reqour.common.exceptions.GitException;
import org.jboss.pnc.reqour.common.executor.process.ProcessExecutor;
import org.jboss.pnc.reqour.common.utils.GitUtils;
import org.jboss.pnc.reqour.model.ProcessContext;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Handles the composing of git commands (by delegating it into {@link GitUtils}) and then executing it as a shell
 * process (by delegating it into {@link ProcessExecutor}).
 */
@ApplicationScoped
@Slf4j
public class GitCommands {

    @ConfigProperty(name = "reqour.git.private-github-user")
    Optional<String> privateGithubUser;

    private final ProcessExecutor processExecutor;

    @Inject
    public GitCommands(ProcessExecutor processExecutor) {
        this.processExecutor = processExecutor;
    }

    public String listBranches(ProcessContext.Builder processContextBuilder) {
        return processExecutor.stdout(processContextBuilder.command(GitUtils.branch()));
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

    public boolean isReferenceBranch(String ref, ProcessContext.Builder processContextBuilder) {
        return processExecutor.execute(processContextBuilder.command(GitUtils.isReferenceBranch(ref)).build()) == 0;
    }

    public boolean isReferenceTag(String ref, ProcessContext.Builder processContextBuilder) {
        return processExecutor.execute(processContextBuilder.command(GitUtils.isReferenceTag(ref)).build()) == 0;
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

    public void addTag(String name, ProcessContext.Builder processContextBuilder) {
        executeGitCommand(GitUtils.addTag(name), processContextBuilder, String.format("Cannot add tag '%s'", name));
    }

    public String listTags(ProcessContext.Builder processContextBuilder) {
        return processExecutor.stdout(processContextBuilder.command(GitUtils.listTags()));
    }

    public String listTagsReachableFromRef(String ref, ProcessContext.Builder processContextBuilder) {
        return processExecutor.stdout(processContextBuilder.command(GitUtils.listTagsReachableFromReference(ref)));
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

    private static boolean isGithubRepository(String url) {
        return url.contains("github.com");
    }

    private String githubRepoCloningInfo(String url) {
        if (privateGithubUser.isEmpty()) {
            log.warn("Private GitHub user not set!");
            return String.format(
                    "If the Github repository is a private repository, you need to add a Github user "
                            + "with read-permissions to '%s'. Please email the Newcastle mailing list for more information.",
                    url);
        }

        return String.format(
                "If the Github repository is a private repository, you need to add the Github user '%s' with read-permissions to '%s'.",
                privateGithubUser.get(),
                url);
    }
}
