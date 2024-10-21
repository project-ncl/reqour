/**
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.rest.endpoints;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.pnc.api.reqour.dto.AdjustRequest;
import org.jboss.pnc.api.reqour.rest.AdjustEndpoint;
import org.jboss.pnc.reqour.rest.openshift.OpenShiftResourceController;

@ApplicationScoped
public class AdjustEndpointImpl implements AdjustEndpoint {

    private final OpenShiftResourceController openShiftResourceController;

    @Inject
    public AdjustEndpointImpl(OpenShiftResourceController openShiftResourceController) {
        this.openShiftResourceController = openShiftResourceController;
    }

    @Override
    public void adjust(AdjustRequest adjustRequest) {
        openShiftResourceController.createAdjusterPod();
    }
}
