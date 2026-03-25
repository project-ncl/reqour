/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.rest.endpoints;

import java.util.Objects;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import org.jboss.pnc.api.dto.ExceptionResolution;
import org.jboss.pnc.api.enums.ResultStatus;
import org.jboss.pnc.api.reqour.dto.RepositoryCloneRequest;
import org.jboss.pnc.api.reqour.dto.RepositoryCloneResponse;
import org.jboss.pnc.api.reqour.dto.ReqourCallback;
import org.jboss.pnc.api.reqour.rest.CloneEndpoint;
import org.jboss.pnc.reqour.common.callbacksender.CallbackSender;
import org.jboss.pnc.reqour.common.exceptions.GitException;
import org.jboss.pnc.reqour.common.executor.task.TaskExecutor;
import org.jboss.pnc.reqour.common.utils.ValidationUtils;
import org.jboss.pnc.reqour.runtime.UserLogger;
import org.jboss.pnc.reqour.service.api.CloneService;
import org.slf4j.Logger;

import lombok.extern.slf4j.Slf4j;

@ApplicationScoped
@Slf4j
public class CloneEndpointImpl implements CloneEndpoint {

    private final CloneService service;
    private final TaskExecutor taskExecutor;
    private final CallbackSender callbackSender;
    private final ValidationUtils validationUtils;
    private final Logger userLogger;

    @Inject
    public CloneEndpointImpl(
            CloneService service,
            TaskExecutor taskExecutor,
            CallbackSender callbackSender,
            ValidationUtils validationUtils,
            @UserLogger Logger logger) {
        this.service = service;
        this.taskExecutor = taskExecutor;
        this.callbackSender = callbackSender;
        this.validationUtils = validationUtils;
        this.userLogger = logger;
    }

    @Override
    @RolesAllowed({ OidcRoleConstants.PNC_APP_REPOUR_USER, OidcRoleConstants.PNC_USERS_ADMIN })
    public void clone(RepositoryCloneRequest cloneRequest) {
        userLogger.info("Clone request: {}", cloneRequest);

        validationUtils.validateInternalUrlMatchesActiveGitProvider(cloneRequest.getTargetRepoUrl());

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

        final ResultStatus status;
        final String errorProposal;
        if (t instanceof GitException) {
            status = ResultStatus.FAILED;
            log.warn("Async cloning task ended with git-related exception, probably conflicting with repo state", t);
            errorProposal = "There is a git error, please check repository configuration, or contact PNC team at #forum-pnc-users";
        } else {
            status = ResultStatus.SYSTEM_ERROR;
            errorProposal = "There is an internal system error, please contact PNC team at #forum-pnc-users";
            log.error("Async cloning task ended with unexpected exception", t);
        }

        final String errorReason = String.format(
                "Repository clone failed: %s",
                Objects.requireNonNullElseGet(t.getMessage(), t::toString));
        final ExceptionResolution exceptionResolution = ExceptionResolution.builder()
                .reason(errorReason)
                .proposal(errorProposal)
                .build();

        return RepositoryCloneResponse.builder()
                .originRepoUrl(request.getOriginRepoUrl())
                .targetRepoUrl(request.getTargetRepoUrl())
                .ref(request.getRef())
                .callback(
                        ReqourCallback.builder()
                                .status(status)
                                .id(request.getTaskId())
                                .exceptionResolution(exceptionResolution)
                                .build())
                .build();
    }
}
