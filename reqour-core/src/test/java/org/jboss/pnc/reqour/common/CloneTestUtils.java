/**
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.common;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.transport.TagOpt;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;

@Getter
@Slf4j
public class CloneTestUtils {

    private static final String REQOUR_TEST_PREFIX = "reqour-test-";
    private static final Path CLONE_DIR_PATH = createCloneDir();
    public static final Path SOURCE_REPO_PATH = CLONE_DIR_PATH.resolve(REQOUR_TEST_PREFIX + "source-repo");
    public static final String SOURCE_REPO_URL = "file://" + SOURCE_REPO_PATH;
    public static final Path EMPTY_DEST_REPO_PATH = CLONE_DIR_PATH.resolve(REQOUR_TEST_PREFIX + "empty-dest-repo");
    public static final String EMPTY_DEST_REPO_URL = "file://" + EMPTY_DEST_REPO_PATH;
    public static final Path DEST_REPO_WITH_MAIN_BRANCH_PATH = CLONE_DIR_PATH
            .resolve(REQOUR_TEST_PREFIX + "main-branch-dest-repo");
    public static final String DEST_REPO_WITH_MAIN_BRANCH_URL = "file://" + DEST_REPO_WITH_MAIN_BRANCH_PATH;

    private static Path createCloneDir() {
        var cloneDirPath = Path.of("/tmp");
        try {
            Files.createDirectory(cloneDirPath);
        } catch (FileAlreadyExistsException e) {
            log.info("Directory {} already exists", cloneDirPath);
        } catch (IOException e) {
            throw new RuntimeException("Cannot create clone directory for tests", e);
        }

        return cloneDirPath;
    }

    public static void cloneSourceRepoFromGithub() throws GitAPIException {
        Git sourceRepo = Git.cloneRepository()
                .setURI("https://github.com/project-ncl/test-repo-for-reqour")
                .setDirectory(SOURCE_REPO_PATH.toFile())
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
