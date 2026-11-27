/*
 * SPDX-FileCopyrightText: Copyright © 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.common.exceptions;

/**
 * Base exception for GitHub App operations.
 */
public class GitHubAppException extends RuntimeException {

    public GitHubAppException(String message) {
        super(message);
    }

    public GitHubAppException(String message, Throwable cause) {
        super(message, cause);
    }
}
