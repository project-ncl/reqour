/**
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.rest.endpoints;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.jboss.pnc.api.reqour.rest.CancelEndpoint;
import org.jboss.pnc.reqour.rest.openshift.OpenShiftAdjusterPodController;

@ApplicationScoped
public class CancelEndpointImpl implements CancelEndpoint {

    private final ManagedExecutor managedExecutor;
    private final OpenShiftAdjusterPodController openShiftAdjusterPodController;

    public CancelEndpointImpl(
            ManagedExecutor managedExecutor,
            OpenShiftAdjusterPodController openShiftAdjusterPodController) {
        this.managedExecutor = managedExecutor;
        this.openShiftAdjusterPodController = openShiftAdjusterPodController;
    }

    @Override
    public void cancelTask(String taskId) {
        managedExecutor.runAsync(() -> openShiftAdjusterPodController.destroyAdjusterPod(taskId));
    }
}
