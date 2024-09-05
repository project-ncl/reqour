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
package org.jboss.pnc.reqour.common.executor.task;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.jboss.pnc.api.dto.Request;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Implementation of {@link TaskExecutor} using container's {@link ManagedExecutor}, which acts as a delegate, where the
 * task executor actually delegates the computation.
 */
@ApplicationScoped
@Slf4j
public class TaskExecutorImpl implements TaskExecutor {

    private final ManagedExecutor executor;
    private final Map<String, Future<?>> runningTasks = new ConcurrentHashMap<>();

    @Inject
    public TaskExecutorImpl(ManagedExecutor executor) {
        this.executor = executor;
    }

    @Override
    public <T, R> void executeAsync(
            String taskID,
            Request callbackRequest,
            T request,
            Function<T, R> syncExecutor,
            BiFunction<T, Throwable, R> errorHandler,
            BiConsumer<Request, R> callbackSender) {
        log.debug("Starting the task with ID={}", taskID);
        runningTasks.put(
                taskID,
                executor.supplyAsync(() -> syncExecutor.apply(request))
                        .exceptionally(t -> errorHandler.apply(request, t))
                        .thenAccept(res -> callbackSender.accept(callbackRequest, res)));
    }
}
