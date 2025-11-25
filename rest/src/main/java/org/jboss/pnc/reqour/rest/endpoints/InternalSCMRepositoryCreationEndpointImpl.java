/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.rest.endpoints;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import org.jboss.pnc.api.enums.InternalSCMCreationStatus;
import org.jboss.pnc.api.enums.ResultStatus;
import org.jboss.pnc.api.reqour.dto.InternalSCMCreationRequest;
import org.jboss.pnc.api.reqour.dto.InternalSCMCreationResponse;
import org.jboss.pnc.api.reqour.dto.ReqourCallback;
import org.jboss.pnc.api.reqour.rest.InternalSCMRepositoryCreationEndpoint;
import org.jboss.pnc.reqour.common.callbacksender.CallbackSender;
import org.jboss.pnc.reqour.common.exceptions.GitHubApiException;
import org.jboss.pnc.reqour.common.exceptions.GitLabApiRuntimeException;
import org.jboss.pnc.reqour.common.exceptions.InvalidProjectPathException;
import org.jboss.pnc.reqour.common.executor.task.TaskExecutor;
import org.jboss.pnc.reqour.common.executor.task.TaskExecutorImpl;
import org.jboss.pnc.reqour.config.ConfigUtils;
import org.jboss.pnc.reqour.config.GitProviderConfig;
import org.jboss.pnc.reqour.runtime.UserLogger;
import org.jboss.pnc.reqour.service.api.InternalSCMRepositoryCreationService;
import org.slf4j.Logger;

import lombok.extern.slf4j.Slf4j;

@ApplicationScoped
@Slf4j
public class InternalSCMRepositoryCreationEndpointImpl implements InternalSCMRepositoryCreationEndpoint {

    private final InternalSCMRepositoryCreationService service;
    private final GitProviderConfig gitProviderConfig;
    private final TaskExecutor taskExecutor;
    private final CallbackSender callbackSender;
    private final Logger userLogger;

    @Inject
    public InternalSCMRepositoryCreationEndpointImpl(
            Instance<InternalSCMRepositoryCreationService> service,
            ConfigUtils configUtils,
            TaskExecutorImpl taskExecutor,
            CallbackSender callbackSender,
            @UserLogger Logger userLogger) {
        this.service = service.get();
        gitProviderConfig = configUtils.getActiveGitProviderConfig();
        this.taskExecutor = taskExecutor;
        this.callbackSender = callbackSender;
        this.userLogger = userLogger;
    }

    @Override
    @RolesAllowed({ OidcRoleConstants.PNC_APP_REPOUR_USER, OidcRoleConstants.PNC_USERS_ADMIN })
    public void createInternalSCMRepository(InternalSCMCreationRequest creationRequest) {
        userLogger.info("Internal SCM repository creation request: {}", creationRequest);

        taskExecutor.executeAsync(
                creationRequest.getCallback(),
                creationRequest,
                service::createInternalSCMRepository,
                this::handleError,
                callbackSender::sendInternalSCMRepositoryCreationCallback);

        throw new WebApplicationException(Response.Status.ACCEPTED);
    }

    private InternalSCMCreationResponse handleError(InternalSCMCreationRequest creationRequest, Throwable t) {
        t = t.getCause();

        ResultStatus status;
        if (t instanceof GitLabApiRuntimeException || t instanceof GitHubApiException) {
            status = ResultStatus.FAILED;
            log.warn("Async SCM repository creation task ended with git provider exception", t);
        } else if (t instanceof InvalidProjectPathException) {
            status = ResultStatus.FAILED;
            log.warn("SCM repository creation request has invalid project path", t);
        } else {
            status = ResultStatus.SYSTEM_ERROR;
            log.error("Async SCM repository creation task ended with unexpected exception", t);
        }

        String projectPath;
        try {
            projectPath = service.computeProjectPath(creationRequest);
        } catch (InvalidProjectPathException e) {
            // In case the provided project path in the request is invalid, leave there user's
            projectPath = creationRequest.getProject();
        }
        return InternalSCMCreationResponse.builder()
                .readonlyUrl(
                        InternalSCMRepositoryCreationService.completeTemplateWithProjectPath(
                                gitProviderConfig.readOnlyTemplate(),
                                projectPath))
                .readwriteUrl(
                        InternalSCMRepositoryCreationService.completeTemplateWithProjectPath(
                                gitProviderConfig.readWriteTemplate(),
                                projectPath))
                .status(InternalSCMCreationStatus.FAILED)
                .callback(ReqourCallback.builder().status(status).id(creationRequest.getTaskId()).build())
                .build();
    }
}
