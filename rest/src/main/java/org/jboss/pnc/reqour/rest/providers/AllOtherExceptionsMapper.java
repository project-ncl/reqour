/*
 * SPDX-FileCopyrightText: Copyright © 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.rest.providers;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import org.jboss.pnc.api.dto.ErrorResponse;

import lombok.extern.slf4j.Slf4j;

/**
 * Unexpected errors: the details stay in the log. The message of an unexpected exception can contain anything (a JDK
 * exception about an invalid header value contains the value, e.g. a token), so callers only get its type.
 */
@Provider
@Slf4j
public class AllOtherExceptionsMapper implements ExceptionMapper<Throwable> {

    static final String MESSAGE = "Unexpected error in reqour, see its log for details";

    @Override
    public Response toResponse(Throwable exception) {
        log.error("Unexpected error occurred: ", exception);
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ErrorResponse(exception.getClass().getSimpleName(), MESSAGE))
                .type(MediaType.APPLICATION_JSON_TYPE)
                .build();
    }
}
