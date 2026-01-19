/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.rest.providers;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import org.jboss.pnc.api.dto.ErrorResponse;
import org.jboss.pnc.reqour.common.exceptions.ConflictingInternalUrlException;

import lombok.extern.slf4j.Slf4j;

@Provider
@Slf4j
public class ConflictingInternalUrlExceptionMapper implements ExceptionMapper<ConflictingInternalUrlException> {

    @Override
    public Response toResponse(ConflictingInternalUrlException exception) {
        log.warn("Invalid internal URL provided", exception);
        return Response.status(Response.Status.CONFLICT)
                .entity(new ErrorResponse(exception))
                .type(MediaType.APPLICATION_JSON_TYPE)
                .build();
    }
}
