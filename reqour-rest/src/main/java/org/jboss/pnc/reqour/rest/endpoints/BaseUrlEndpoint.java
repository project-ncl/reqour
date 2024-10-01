/**
 * JBoss, Home of Professional Open Source.
 * Copyright 2024-2024 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
