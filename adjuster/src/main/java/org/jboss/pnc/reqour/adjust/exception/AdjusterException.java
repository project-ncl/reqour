/**
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.exception;

/**
 * Application exception wrapping expected errors.<br/>
 * In case this exception is thrown, we are returning {@code FAILED} status into BPM.<br/>
 * Otherwise, we return {@code SYSTEM_ERROR} (since unexpected error happened).
 */
public class AdjusterException extends RuntimeException {

    public AdjusterException(String message) {
        super(message);
    }

    public AdjusterException(Throwable cause) {
        super(cause);
    }

    public AdjusterException(String message, Throwable cause) {
        super(message, cause);
    }
}
