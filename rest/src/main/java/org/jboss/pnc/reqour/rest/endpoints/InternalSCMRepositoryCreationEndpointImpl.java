/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.rest.endpoints;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.jboss.pnc.api.enums.InternalSCMCreationStatus;
import org.jboss.pnc.api.enums.ResultStatus;
import org.jboss.pnc.api.reqour.dto.InternalSCMCreationRequest;
import org.jboss.pnc.api.reqour.dto.InternalSCMCreationResponse;
import org.jboss.pnc.api.reqour.dto.ReqourCallback;
import org.jboss.pnc.api.reqour.rest.InternalSCMRepositoryCreationEndpoint;
import org.jboss.pnc.reqour.common.callbacksender.CallbackSender;
import org.jboss.pnc.reqour.common.exceptions.GitlabApiRuntimeException;
import org.jboss.pnc.reqour.common.exceptions.InvalidProjectPathException;
import org.jboss.pnc.reqour.common.executor.task.TaskExecutor;
import org.jboss.pnc.reqour.common.executor.task.TaskExecutorImpl;
import org.jboss.pnc.reqour.config.ConfigUtils;
import org.jboss.pnc.reqour.config.GitBackendConfig;
import org.jboss.pnc.reqour.service.GitlabRepositoryCreationService;
import org.jboss.pnc.reqour.service.api.InternalSCMRepositoryCreationService;

@ApplicationScoped
@Slf4j
public class InternalSCMRepositoryCreationEndpointImpl implements InternalSCMRepositoryCreationEndpoint {

    private final InternalSCMRepositoryCreationService service;
    private final TaskExecutor taskExecutor;
    private final CallbackSender callbackSender;
    private final ConfigUtils configUtils;

    @Inject
    public InternalSCMRepositoryCreationEndpointImpl(
            InternalSCMRepositoryCreationService service,
            TaskExecutorImpl taskExecutor,
            CallbackSender callbackSender,
            ConfigUtils configUtils) {
        this.service = service;
        this.taskExecutor = taskExecutor;
        this.callbackSender = callbackSender;
        this.configUtils = configUtils;
    }

    @Override
    public void createInternalSCMRepository(InternalSCMCreationRequest creationRequest) {
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
        if (t instanceof GitlabApiRuntimeException) {
            status = ResultStatus.FAILED;
            log.warn("Async SCM repository creation task ended with GitLab API-related exception", t);
        } else if (t instanceof InvalidProjectPathException) {
            status = ResultStatus.FAILED;
            log.warn("SCM repository creation request has invalid project path", t);
        } else {
            status = ResultStatus.SYSTEM_ERROR;
            log.error("Async SCM repository creation task ended with unexpected exception", t);
        }

        GitBackendConfig gitlabConfig = configUtils.getActiveGitBackend();
        return InternalSCMCreationResponse.builder()
                .readonlyUrl(
                        GitlabRepositoryCreationService.completeTemplateWithProjectPath(
                                gitlabConfig.readOnlyTemplate(),
                                creationRequest.getProject()))
                .readwriteUrl(
                        GitlabRepositoryCreationService.completeTemplateWithProjectPath(
                                gitlabConfig.readWriteTemplate(),
                                creationRequest.getProject()))
                .status(InternalSCMCreationStatus.FAILED)
                .callback(ReqourCallback.builder().status(status).id(creationRequest.getTaskId()).build())
                .build();
    }
}
