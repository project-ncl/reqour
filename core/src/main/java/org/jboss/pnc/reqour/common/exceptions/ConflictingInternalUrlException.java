/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.common.exceptions;

public class ConflictingInternalUrlException extends RuntimeException {

    public ConflictingInternalUrlException(String message) {
        super(message);
    }
}
