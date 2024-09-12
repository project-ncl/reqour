/**
 * JBoss, Home of Professional Open Source.
 * Copyright 2024-2024 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
import org.jboss.pnc.reqour.common.exceptions.InvalidProjectPathException;
import org.jboss.pnc.reqour.common.executor.task.TaskExecutor;
import org.jboss.pnc.reqour.common.executor.task.TaskExecutorImpl;
import org.jboss.pnc.reqour.config.ConfigUtils;
import org.jboss.pnc.reqour.config.GitBackendConfig;
import org.jboss.pnc.reqour.rest.providers.GitlabApiRuntimeException;
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
                creationRequest.getTaskId(),
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
