/**
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.rest.endpoints;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.jboss.pnc.api.enums.ResultStatus;
import org.jboss.pnc.api.reqour.dto.AdjustRequest;
import org.jboss.pnc.api.reqour.dto.ReqourCallback;
import org.jboss.pnc.api.reqour.rest.AdjustEndpoint;
import org.jboss.pnc.common.http.PNCHttpClient;
import org.jboss.pnc.reqour.config.ConfigUtils;
import org.jboss.pnc.reqour.rest.openshift.OpenShiftAdjusterPodController;

@ApplicationScoped
@Slf4j
public class AdjustEndpointImpl implements AdjustEndpoint {

    private final ManagedExecutor managedExecutor;
    private final OpenShiftAdjusterPodController openShiftAdjusterPodController;
    private final PNCHttpClient pncHttpClient;

    @Inject
    public AdjustEndpointImpl(
            OpenShiftAdjusterPodController openShiftAdjusterPodController,
            ManagedExecutor managedExecutor,
            ObjectMapper objectMapper,
            ConfigUtils configUtils) {
        this.managedExecutor = managedExecutor;
        this.openShiftAdjusterPodController = openShiftAdjusterPodController;
        pncHttpClient = new PNCHttpClient(objectMapper, configUtils.getPncHttpClientConfig());
    }

    @Override
    public void adjust(AdjustRequest adjustRequest) {
        managedExecutor.runAsync(() -> openShiftAdjusterPodController.createAdjusterPod(adjustRequest))
                .exceptionally(throwable -> {
                    log.error("Endpoint ended with the exception, sending SYSTEM_ERROR as the callback", throwable);
                    pncHttpClient.sendRequest(
                            adjustRequest.getCallback(),
                            ReqourCallback.builder()
                                    .id(adjustRequest.getTaskId())
                                    .status(ResultStatus.SYSTEM_ERROR)
                                    .build());
                    return null;
                });

        throw new WebApplicationException(Response.Status.ACCEPTED);
    }
}
