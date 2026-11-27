/*
 * SPDX-FileCopyrightText: Copyright © 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.common.exceptions;

/**
 * Thrown when a GitHub App operation fails for a transient reason (a 5xx server response or a transport-level error)
 * that is worth retrying. This is the only GitHub App exception the fault-tolerance guard retries on; every other
 * failure is treated as permanent and aborts immediately.
 */
public class GitHubAppTransientException extends GitHubAppException {

    public GitHubAppTransientException(String message) {
        super(message);
    }

    public GitHubAppTransientException(String message, Throwable cause) {
        super(message, cause);
    }
}
