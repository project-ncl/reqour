/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.common.exceptions;

import jakarta.validation.ValidationException;

/**
 * Exception thrown in case invalid project path is given.<br/>
 * Project path is used for instance as {@link org.jboss.pnc.api.reqour.dto.InternalSCMCreationRequest#project}.<br/>
 * Valid project paths are:<br/>
 * 1) 'project'<br/>
 * 2) 'subgroup/project'
 */
public class InvalidProjectPathException extends ValidationException {

    public InvalidProjectPathException(String message) {
        super(message);
    }
}
