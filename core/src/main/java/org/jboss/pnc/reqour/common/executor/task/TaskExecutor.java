/**
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.common.executor.task;

import org.jboss.pnc.api.dto.Request;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Executor used for starting long-running asynchronous tasks (which are, however, written in a synchronous manner).
 */
public interface TaskExecutor {

    /**
     * Execute the task asynchronously.
     *
     * @param callbackRequest request identifying where to send the callback
     * @param request input request of the synchronous executor
     * @param syncExecutor executor, which runs the operation synchronously
     * @param errorHandler handle the error, and create the default result
     * @param callbackSender callback sender
     * @param <T> request type
     * @param <R> result type
     */
    <T, R> void executeAsync(
            Request callbackRequest,
            T request,
            Function<T, R> syncExecutor,
            BiFunction<T, Throwable, R> errorHandler,
            BiConsumer<Request, R> callbackSender);
}
