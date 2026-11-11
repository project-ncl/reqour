/*
 * SPDX-FileCopyrightText: Copyright © 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.rest.providers;

import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import org.jboss.pnc.api.dto.ErrorResponse;
import org.jboss.pnc.reqour.common.exceptions.ScmContentException;

import lombok.extern.slf4j.Slf4j;

@Provider
@Slf4j
public class ScmContentExceptionMapper implements ExceptionMapper<ScmContentException> {

    /** BAD_REQUEST, NOT_FOUND, BAD_GATEWAY, SERVICE_UNAVAILABLE or GATEWAY_TIMEOUT. */
    static String errorType(int status) {
        Response.Status known = Response.Status.fromStatusCode(status);
        return known == null ? "HTTP_" + status : known.name();
    }

    @Override
    public Response toResponse(ScmContentException exception) {
        if (exception.getStatus() == ScmContentException.SERVICE_UNAVAILABLE) {
            log.warn("Cannot serve SCM content: {}", exception.getMessage());
        } else if (exception.getStatus() / 100 == 5) {
            log.error("Cannot serve SCM content: {}", exception.getMessage(), exception);
        } else {
            log.info("Cannot serve SCM content: {}", exception.getMessage());
        }
        Response.ResponseBuilder response = Response.status(exception.getStatus())
                .entity(new ErrorResponse(errorType(exception.getStatus()), exception.getMessage()))
                .type(MediaType.APPLICATION_JSON_TYPE);
        if (exception.getRetryAfterSeconds() > 0) {
            response.header(HttpHeaders.RETRY_AFTER, exception.getRetryAfterSeconds());
        }
        return response.build();
    }
}
