/**
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.common.exceptions;

import javax.validation.ValidationException;

/**
 * Exception thrown in case the task ID has the format that Reqour does not support.
 */
public class UnsupportedTaskIdFormatException extends ValidationException {

    public UnsupportedTaskIdFormatException(String message) {
        super(message);
    }
}
