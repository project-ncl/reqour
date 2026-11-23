/*
 * SPDX-FileCopyrightText: Copyright © 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.common.exceptions;

/**
 * Thrown when the GitHub App is not installed on the specified organization or target.
 */
public class GitHubAppInstallationTokenException extends GitHubAppException {

    public GitHubAppInstallationTokenException(String message) {
        super(message);
    }

    public GitHubAppInstallationTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}
