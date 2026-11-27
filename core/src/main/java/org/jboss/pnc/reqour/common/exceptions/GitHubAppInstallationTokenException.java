/*
 * SPDX-FileCopyrightText: Copyright © 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.common.exceptions;

/**
 * Thrown when minting a GitHub App installation token fails, or the token is returned without an expiry.
 */
public class GitHubAppInstallationTokenException extends GitHubAppException {

    public GitHubAppInstallationTokenException(String message) {
        super(message);
    }

    public GitHubAppInstallationTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}
