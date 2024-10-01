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
                cloneRequest.getTaskId(),
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
