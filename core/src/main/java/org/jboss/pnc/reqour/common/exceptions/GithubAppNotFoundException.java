/*
 * SPDX-FileCopyrightText: Copyright © 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.common.exceptions;

import java.io.IOException;

public class GithubAppNotFoundException extends RuntimeException {
    public GithubAppNotFoundException(IOException cause) {
        super(cause);
    }
}
