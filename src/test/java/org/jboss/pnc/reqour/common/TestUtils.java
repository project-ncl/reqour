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

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.transport.TagOpt;
import org.jboss.pnc.api.dto.Request;
import org.jboss.pnc.api.reqour.dto.Callback;
import org.jboss.pnc.api.reqour.dto.RepositoryCloneRequest;
import org.jboss.pnc.api.reqour.dto.RepositoryCloneResponseCallback;
import org.jboss.pnc.api.reqour.dto.TranslateRequest;
import org.jboss.pnc.api.reqour.dto.TranslateResponse;

import java.net.URI;
import java.nio.file.Path;
import java.util.UUID;

public class TestUtils {

    public static final Path CLONE_TEST_PATH = Path.of("src", "test", "resources");
    public static final Path SOURCE_REPO_ABSOLUTE_PATH = CLONE_TEST_PATH.resolve("source-repo").toAbsolutePath();
    public static final String SOURCE_REPO_URL = "file://" + SOURCE_REPO_ABSOLUTE_PATH;
    public static final Path EMPTY_DEST_REPO_ABSOLUTE_PATH = CLONE_TEST_PATH.resolve("empty-dest-repo")
            .toAbsolutePath();
    public static final String EMPTY_DEST_REPO_URL = "file://" + EMPTY_DEST_REPO_ABSOLUTE_PATH;
    public static final Path DEST_REPO_WITH_MAIN_BRANCH_ABSOLUTE_PATH = CLONE_TEST_PATH.resolve("main-branch-dest-repo")
            .toAbsolutePath();
    public static final String DEST_REPO_WITH_MAIN_BRANCH_URL = "file://" + DEST_REPO_WITH_MAIN_BRANCH_ABSOLUTE_PATH;

    public static TranslateResponse createTranslateResponseFromExternalUrl(String externalUrl, String internalUrl) {
        return TranslateResponse.builder().externalUrl(externalUrl).internalUrl(internalUrl).build();
    }

    public static TranslateRequest createTranslateRequestFromExternalUrl(String externalUrl) {
        return TranslateRequest.builder().externalUrl(externalUrl).build();
    }

    public static RepositoryCloneRequest createRepositoryCloneRequest(
            String originRepoUrl,
            String targetRepoUrl,
            String callbackUrl,
            String taskId) {
        return RepositoryCloneRequest.builder()
                .type("git")
                .originRepoUrl(originRepoUrl)
                .targetRepoUrl(targetRepoUrl)
                .taskId(taskId)
                .callback(Request.builder().method(Request.Method.POST).uri(URI.create(callbackUrl)).build())
                .build();
    }

    public static RepositoryCloneResponseCallback createRepositoryCloneResponseCallback(
            String originRepoUrl,
            String targetRepoUrl,
            int status,
            String taskId) {
        return RepositoryCloneResponseCallback.builder()
                .type("git")
                .originRepoUrl(originRepoUrl)
                .targetRepoUrl(targetRepoUrl)
                .callback(Callback.builder().status(status).id(taskId).build())
                .build();
    }

    public static void cloneSourceRepoFromGithub() throws GitAPIException {
        Git sourceRepo = Git.cloneRepository()
                .setURI("https://github.com/project-ncl/test-repo-for-reqour")
                .setDirectory(SOURCE_REPO_ABSOLUTE_PATH.toFile())
                .setCloneAllBranches(true)
                .setTagOption(TagOpt.FETCH_TAGS)
                .call();

        setTrackingOfBranches(sourceRepo);
    }

    private static void setTrackingOfBranches(Git sourceRepo) throws GitAPIException {
        sourceRepo.branchList()
                .setListMode(ListBranchCommand.ListMode.ALL)
                .call()
                .stream()
                .map(Ref::getName)
                .filter(b -> b.contains("remotes"))
                .filter(b -> !b.contains("main"))
                .map(b -> b.substring(b.lastIndexOf("/") + 1))
                .forEach(b -> checkoutToBranch(sourceRepo, b));
    }

    private static void checkoutToBranch(Git sourceRepo, String branch) {
        try {
            sourceRepo.checkout().setCreateBranch(true).setName(branch).call();
        } catch (GitAPIException e) {
            throw new RuntimeException(e);
        }
    }

}
