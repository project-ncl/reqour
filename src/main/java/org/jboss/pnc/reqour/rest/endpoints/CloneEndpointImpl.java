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
import org.jboss.pnc.api.reqour.dto.Callback;
import org.jboss.pnc.api.reqour.dto.RepositoryCloneRequest;
import org.jboss.pnc.api.reqour.dto.RepositoryCloneResponseCallback;
import org.jboss.pnc.api.reqour.dto.rest.CloneEndpoint;
import org.jboss.pnc.reqour.common.callbacksender.CallbackSenderImpl;
import org.jboss.pnc.reqour.common.exceptions.GitException;
import org.jboss.pnc.reqour.common.executor.task.TaskExecutor;
import org.jboss.pnc.reqour.facade.api.CloneProvider;
import org.jboss.pnc.reqour.facade.clone.CloneProviderPicker;

@ApplicationScoped
@Slf4j
public class CloneEndpointImpl implements CloneEndpoint {

    private final CloneProviderPicker delegatePicker;
    private final TaskExecutor taskExecutor;
    private final CallbackSenderImpl callbackSender;

    @Inject
    public CloneEndpointImpl(
            CloneProviderPicker delegatePicker,
            TaskExecutor taskExecutor,
            CallbackSenderImpl callbackSender) {
        this.delegatePicker = delegatePicker;
        this.taskExecutor = taskExecutor;
        this.callbackSender = callbackSender;
    }

    @Override
    public void clone(RepositoryCloneRequest cloneRequest) {

        // Picking of the correct delegate (based on request) is better to be done in the current thread
        // In order to fail in case the cloneRequest.getType() is invalid
        CloneProvider cloneProvider = delegatePicker.pickProvider(cloneRequest.getType());

        taskExecutor.executeAsync(
                cloneRequest.getTaskId(),
                cloneRequest,
                cloneProvider::clone,
                (_res, ex) -> fireCallback(cloneRequest, ex));

        throw new WebApplicationException(Response.Status.ACCEPTED);
    }

    private void fireCallback(RepositoryCloneRequest cloneRequest, Throwable t) {
        int status = Response.Status.OK.getStatusCode();

        if (t != null) {
            t = t.getCause();
            log.warn("Asynchronous cloning task ended with exception", t);
            if (t instanceof GitException) {
                status = Response.Status.CONFLICT.getStatusCode();
            } else {
                status = Response.Status.INTERNAL_SERVER_ERROR.getStatusCode();
            }
        }

        callbackSender.sendRepositoryCloneCallback(
                cloneRequest.getCallback().getMethod().toString(),
                cloneRequest.getCallback().getUri().toString(),
                createCallback(cloneRequest, status));
    }

    private RepositoryCloneResponseCallback createCallback(RepositoryCloneRequest cloneRequest, int status) {
        return RepositoryCloneResponseCallback.builder()
                .type(cloneRequest.getType())
                .originRepoUrl(cloneRequest.getOriginRepoUrl())
                .targetRepoUrl(cloneRequest.getTargetRepoUrl())
                .ref(cloneRequest.getRef())
                .callback(Callback.builder().status(status).id(cloneRequest.getTaskId()).build())
                .build();
    }
}
