/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.rest.endpoints;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.context.ManagedExecutor;
import org.jboss.pnc.api.enums.ResultStatus;
import org.jboss.pnc.api.reqour.dto.AdjustRequest;
import org.jboss.pnc.api.reqour.dto.AdjustResponse;
import org.jboss.pnc.api.reqour.dto.ReqourCallback;
import org.jboss.pnc.api.reqour.rest.AdjustEndpoint;
import org.jboss.pnc.common.http.PNCHttpClient;
import org.jboss.pnc.common.log.ProcessStageUtils;
import org.jboss.pnc.reqour.enums.AdjustProcessStage;
import org.jboss.pnc.reqour.rest.openshift.OpenShiftAdjusterJobController;
import org.jboss.pnc.reqour.rest.service.FinalLogManager;
import org.jboss.pnc.reqour.runtime.UserLogger;
import org.slf4j.Logger;

import lombok.extern.slf4j.Slf4j;

@ApplicationScoped
@Slf4j
public class AdjustEndpointImpl implements AdjustEndpoint {

    private final ManagedExecutor managedExecutor;
    private final OpenShiftAdjusterJobController openShiftAdjusterJobController;
    private final PNCHttpClient pncHttpClient;
    private final Logger userLogger;
    private final FinalLogManager finalLogManager;

    @Inject
    public AdjustEndpointImpl(
            OpenShiftAdjusterJobController openShiftAdjusterJobController,
            ManagedExecutor managedExecutor,
            PNCHttpClient pncHttpClient,
            @UserLogger Logger userLogger,
            FinalLogManager finalLogManager) {
        this.managedExecutor = managedExecutor;
        this.pncHttpClient = pncHttpClient;
        this.openShiftAdjusterJobController = openShiftAdjusterJobController;
        this.userLogger = userLogger;
        this.finalLogManager = finalLogManager;
    }

    @Override
    @RolesAllowed({ OidcRoleConstants.PNC_APP_REPOUR_USER, OidcRoleConstants.PNC_USERS_ADMIN })
    public void adjust(AdjustRequest adjustRequest) {
        managedExecutor.runAsync(() -> {
            finalLogManager.addMessage(getMessageStepStartingAlignmentPod(ProcessStageUtils.Step.BEGIN));
            openShiftAdjusterJobController.createAdjusterJob(adjustRequest);
        })
                .thenRun(() -> onSuccess(adjustRequest))
                .exceptionally(throwable -> onException(throwable, adjustRequest))
                .handle(this::handleFinally);

        throw new WebApplicationException(Response.Status.ACCEPTED);
    }

    private void onSuccess(AdjustRequest adjustRequest) {
        String message = String
                .format(
                        "Adjuster Job for taskID='%s' was successfully requested to be created",
                        adjustRequest.getTaskId());
        userLogger.info(message);
        finalLogManager.addMessage(message);
    }

    private Void onException(Throwable throwable, AdjustRequest adjustRequest) {
        userLogger.error("Adjuster Job for taskId={} cannot be created", adjustRequest.getTaskId());
        log.error("Endpoint ended with the exception, sending SYSTEM_ERROR as the callback", throwable);
        finalLogManager.addMessage(getMessageStepStartingAlignmentPod(ProcessStageUtils.Step.END));
        finalLogManager.addMessage("Alignment pod creation ended with the exception: " + throwable.getMessage());
        pncHttpClient.sendRequest(
                adjustRequest.getCallback(),
                AdjustResponse.builder()
                        .callback(
                                ReqourCallback.builder()
                                        .id(adjustRequest.getTaskId())
                                        .status(ResultStatus.SYSTEM_ERROR)
                                        .build())
                        .build());
        return null;
    }

    private Void handleFinally(Void _val, Throwable _t) {
        finalLogManager.sendMessage();
        return null;
    }

    static String getMessageStepStartingAlignmentPod(ProcessStageUtils.Step step) {
        return String.format("%s: %s", step, AdjustProcessStage.STARTING_ALIGNMENT_POD);
    }
}
