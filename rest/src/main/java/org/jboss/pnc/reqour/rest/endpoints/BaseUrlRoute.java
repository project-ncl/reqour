/*
 * SPDX-FileCopyrightText: Copyright © 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.rest.endpoints;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.ws.rs.core.Response;

import org.jboss.pnc.api.constants.HttpHeaders;

import io.quarkus.vertx.http.runtime.RouteConstants;
import io.vertx.ext.web.Router;
import lombok.extern.slf4j.Slf4j;

/**
 * Redirects GET requests from the base URL to '/version'.
 * <p>
 * This is registered as a programmatic Vert.x route (rather than a JAX-RS endpoint on {@code @Path("/")}) with an order
 * ahead of the default route. The {@code quarkus-github-app} extension ships a static
 * {@code META-INF/resources/index.html}
 * landing page which Quarkus serves at '/' via the default route; a JAX-RS resource mapped to '/' would be shadowed by
 * it.
 * Registering this route with {@link RouteConstants#ROUTE_ORDER_BEFORE_DEFAULT} ensures the base URL redirect wins.
 * <p>
 * The route is restricted to GET so the github-app webhook handler (POST '/') keeps working.
 */
@ApplicationScoped
@Slf4j
public class BaseUrlRoute {

    void registerBaseUrlRedirect(@Observes Router router) {
        router.get("/")
                .order(RouteConstants.ROUTE_ORDER_BEFORE_DEFAULT)
                .handler(routingContext -> {
                    log.debug("Redirecting request to base URL at version endpoint handler");
                    routingContext.response()
                            .setStatusCode(Response.Status.SEE_OTHER.getStatusCode())
                            .putHeader(HttpHeaders.LOCATION_STRING, "/version")
                            .end();
                });
    }
}
