/**
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
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

    @Inject
    public TaskExecutorImpl(ManagedExecutor executor) {
        this.executor = executor;
    }

    @Override
    public <T, R> void executeAsync(
            Request callbackRequest,
            T request,
            Function<T, R> syncExecutor,
            BiFunction<T, Throwable, R> errorHandler,
            BiConsumer<Request, R> callbackSender) {
        executor.supplyAsync(() -> syncExecutor.apply(request))
                .exceptionally(t -> errorHandler.apply(request, t))
                .thenAccept(res -> callbackSender.accept(callbackRequest, res));
    }
}
