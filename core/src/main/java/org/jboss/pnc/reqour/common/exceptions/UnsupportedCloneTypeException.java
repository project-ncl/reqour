/**
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.common.exceptions;

import jakarta.validation.ValidationException;

public class UnsupportedCloneTypeException extends ValidationException {

    public UnsupportedCloneTypeException(String message) {
        super(message);
    }
}
