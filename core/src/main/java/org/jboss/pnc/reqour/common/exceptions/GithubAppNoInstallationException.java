/*
 * SPDX-FileCopyrightText: Copyright © 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.common.exceptions;

public class GithubAppNoInstallationException extends RuntimeException {
    public GithubAppNoInstallationException(String cause) {
        super(cause);
    }
}
