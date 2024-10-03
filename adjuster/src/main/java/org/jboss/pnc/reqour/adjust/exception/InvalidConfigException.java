/**
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.exception;

public class InvalidConfigException extends RuntimeException {

    public InvalidConfigException(String message) {
        super(message);
    }
}
