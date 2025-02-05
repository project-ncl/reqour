/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.rest.endpoints;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.RedirectionException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.jboss.pnc.api.dto.ComponentVersion;

import java.net.URI;

/**
 * Endpoint which redirects GET requests from base URL to '/version'.
 */
@ApplicationScoped
@Path("/")
@Slf4j
public class BaseUrlEndpoint {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public ComponentVersion redirectToVersion() {
        log.debug("Redirecting request to base URL at version endpoint handler");
        throw new RedirectionException(Response.Status.SEE_OTHER, URI.create("/version"));
    }
}
