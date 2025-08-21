/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.rest.endpoints;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.context.ManagedExecutor;
import org.jboss.pnc.api.enums.ResultStatus;
import org.jboss.pnc.api.reqour.dto.CancelRequest;
import org.jboss.pnc.api.reqour.dto.CancelResponse;
import org.jboss.pnc.api.reqour.dto.ReqourCallback;
import org.jboss.pnc.api.reqour.rest.CancelEndpoint;
import org.jboss.pnc.common.http.PNCHttpClient;
import org.jboss.pnc.reqour.rest.openshift.OpenShiftAdjusterJobController;
import org.jboss.pnc.reqour.runtime.UserLogger;
import org.slf4j.Logger;

import lombok.extern.slf4j.Slf4j;

@ApplicationScoped
@Slf4j
public class CancelEndpointImpl implements CancelEndpoint {

    private final ManagedExecutor managedExecutor;
    private final OpenShiftAdjusterJobController openShiftAdjusterJobController;
    private final PNCHttpClient pncHttpClient;
    private final Logger userLogger;

    public CancelEndpointImpl(
            ManagedExecutor managedExecutor,
            OpenShiftAdjusterJobController openShiftAdjusterJobController,
            PNCHttpClient pncHttpClient,
            @UserLogger Logger userLogger) {
        this.managedExecutor = managedExecutor;
        this.openShiftAdjusterJobController = openShiftAdjusterJobController;
        this.pncHttpClient = pncHttpClient;
        this.userLogger = userLogger;
    }

    @Override
    @RolesAllowed({ OidcRoleConstants.PNC_APP_REPOUR_USER, OidcRoleConstants.PNC_USERS_ADMIN })
    public void cancelTask(CancelRequest cancelRequest) {
        userLogger.info("Cancel request: {}", cancelRequest);

        managedExecutor.supplyAsync(() -> openShiftAdjusterJobController.destroyAdjusterJob(cancelRequest.getTaskId()))
                .exceptionally(t -> {
                    log.error("Cancellation of task with ID '{}' ended unexpectedly", cancelRequest.getTaskId(), t);
                    return ResultStatus.SYSTEM_ERROR;
                })
                .thenAccept(
                        status -> pncHttpClient.sendRequest(
                                cancelRequest.getCallback(),
                                CancelResponse.builder()
                                        .callback(
                                                ReqourCallback.builder()
                                                        .id(cancelRequest.getTaskId())
                                                        .status(status)
                                                        .build())
                                        .build()));

        throw new WebApplicationException(Response.Status.ACCEPTED);
    }
}
