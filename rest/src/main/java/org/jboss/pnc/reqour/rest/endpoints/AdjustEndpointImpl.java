/**
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.rest.endpoints;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.jboss.pnc.api.reqour.dto.AdjustRequest;
import org.jboss.pnc.api.reqour.rest.AdjustEndpoint;
import org.jboss.pnc.reqour.rest.openshift.OpenShiftAdjusterPodController;

@ApplicationScoped
@Slf4j
public class AdjustEndpointImpl implements AdjustEndpoint {

    private final ManagedExecutor managedExecutor;

    private final OpenShiftAdjusterPodController openShiftAdjusterPodController;

    @Inject
    public AdjustEndpointImpl(
            OpenShiftAdjusterPodController openShiftAdjusterPodController,
            ManagedExecutor managedExecutor) {
        this.managedExecutor = managedExecutor;
        this.openShiftAdjusterPodController = openShiftAdjusterPodController;
    }

    @Override
    public void adjust(AdjustRequest adjustRequest) {
        managedExecutor.runAsync(() -> openShiftAdjusterPodController.createAdjusterPod(adjustRequest))
                .exceptionally(throwable -> {
                    log.warn("Endpoint ended with the exception", throwable);
                    return null;
                });
    }
}
