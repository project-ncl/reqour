/*
 * SPDX-FileCopyrightText: Copyright © 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.common.exceptions;

/**
 * Thrown when authenticating as the GitHub App or generating an installation token fails.
 */
public class GitHubAppAuthenticationException extends GitHubAppException {

    public GitHubAppAuthenticationException(String message) {
        super(message);
    }

    public GitHubAppAuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}
