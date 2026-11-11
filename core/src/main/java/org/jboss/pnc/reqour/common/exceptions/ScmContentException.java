/*
 * SPDX-FileCopyrightText: Copyright © 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.common.exceptions;

import lombok.Getter;

/**
 * Failure to serve SCM content, with the HTTP status to answer with.
 */
@Getter
public class ScmContentException extends RuntimeException {

    public static final int BAD_REQUEST = 400;
    public static final int NOT_FOUND = 404;
    public static final int BAD_GATEWAY = 502;
    public static final int SERVICE_UNAVAILABLE = 503;
    public static final int GATEWAY_TIMEOUT = 504;

    private final int status;
    /** Seconds after which the client may try again (Retry-After), or 0. */
    private final int retryAfterSeconds;

    public ScmContentException(int status, String message) {
        this(status, message, null);
    }

    public ScmContentException(int status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.retryAfterSeconds = 0;
    }

    /** A 503 answer: the client may try again after the given seconds. */
    public static ScmContentException unavailable(String message, int retryAfterSeconds) {
        return new ScmContentException(message, retryAfterSeconds);
    }

    private ScmContentException(String message, int retryAfterSeconds) {
        super(message);
        this.status = SERVICE_UNAVAILABLE;
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
