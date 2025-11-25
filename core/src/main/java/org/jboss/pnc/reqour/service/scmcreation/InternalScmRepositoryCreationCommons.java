/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.service.scmcreation;

import org.jboss.pnc.reqour.common.exceptions.InvalidProjectPathException;

public class InternalScmRepositoryCreationCommons {

    private static final String GIT_SUFFIX = ".git";

    static Project parseProjectPath(String projectPath) {
        if (projectPath.endsWith(GIT_SUFFIX)) {
            projectPath = projectPath.replace(GIT_SUFFIX, "");
        }

        String[] projectPathSplit = projectPath.split("/", 3);

        if (projectPathSplit.length == 1) {
            return new Project(null, projectPath);
        }

        if (projectPathSplit.length == 2) {
            return new Project(projectPathSplit[0], projectPathSplit[1]);
        }

        throw new InvalidProjectPathException(
                String.format("Invalid project path given: '%s'. Expecting at most 1 '/'.", projectPath));
    }

    record Project(String organization, String repository) {
    }
}
