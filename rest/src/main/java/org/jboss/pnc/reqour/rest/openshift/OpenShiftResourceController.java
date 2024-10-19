/**
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.rest.openshift;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.openshift.client.OpenShiftClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * TODO
 */
@ApplicationScoped
public class OpenShiftResourceController {

    @Inject
    OpenShiftClient openShiftClient;

    @Inject
    PodDefinitionCreator podDefinitionCreator;

    public void createAdjusterPod() {
        Pod adjusterPod = podDefinitionCreator.getAdjusterPodDefinition();
        openShiftClient.resource(adjusterPod).create();
    }
}
