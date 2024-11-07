/**
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.rest.endpoints;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.jboss.pnc.api.enums.ResultStatus;
import org.jboss.pnc.api.reqour.dto.RepositoryCloneRequest;
import org.jboss.pnc.api.reqour.dto.RepositoryCloneResponse;
import org.jboss.pnc.api.reqour.dto.ReqourCallback;
import org.jboss.pnc.api.reqour.rest.CloneEndpoint;
import org.jboss.pnc.reqour.common.callbacksender.CallbackSender;
import org.jboss.pnc.reqour.common.exceptions.GitException;
import org.jboss.pnc.reqour.common.executor.task.TaskExecutor;
import org.jboss.pnc.reqour.service.api.CloneService;

@ApplicationScoped
@Slf4j
public class CloneEndpointImpl implements CloneEndpoint {

    private final CloneService service;
    private final TaskExecutor taskExecutor;
    private final CallbackSender callbackSender;

    @Inject
    public CloneEndpointImpl(CloneService service, TaskExecutor taskExecutor, CallbackSender callbackSender) {
        this.service = service;
        this.taskExecutor = taskExecutor;
        this.callbackSender = callbackSender;
    }

    @Override
    public void clone(RepositoryCloneRequest cloneRequest) {
        taskExecutor.executeAsync(
                cloneRequest.getCallback(),
                cloneRequest,
                service::clone,
                this::handleError,
                callbackSender::sendRepositoryCloneCallback);

        throw new WebApplicationException(Response.Status.ACCEPTED);
    }

    private RepositoryCloneResponse handleError(RepositoryCloneRequest request, Throwable t) {
        t = t.getCause();

        ResultStatus status;
        if (t instanceof GitException) {
            status = ResultStatus.FAILED;
            log.warn("Async cloning task ended with git-related exception, probably conflicting with repo state", t);
        } else {
            status = ResultStatus.SYSTEM_ERROR;
            log.error("Async cloning task ended with unexpected exception", t);
        }

        return RepositoryCloneResponse.builder()
                .originRepoUrl(request.getOriginRepoUrl())
                .targetRepoUrl(request.getTargetRepoUrl())
                .ref(request.getRef())
                .callback(ReqourCallback.builder().status(status).id(request.getTaskId()).build())
                .build();
    }
}
