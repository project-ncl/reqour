/*
 * SPDX-FileCopyrightText: Copyright © 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.common.exceptions;

/**
 * Thrown when the GitHub App is not installed on the specified organization or target.
 */
public class GitHubAppInstallationNotFoundException extends GitHubAppException {

    public GitHubAppInstallationNotFoundException(String message) {
        super(message);
    }

    public GitHubAppInstallationNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
