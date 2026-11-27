/*
 * SPDX-FileCopyrightText: Copyright © 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.common.exceptions;

/**
 * Thrown when the GitHub App private key PEM is malformed, invalid, or unsupported.
 */
public class InvalidPrivateKeyException extends GitHubAppException {

    public InvalidPrivateKeyException(String message) {
        super(message);
    }

    public InvalidPrivateKeyException(String message, Throwable cause) {
        super(message, cause);
    }
}
