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
 * This executor is used for starting long-running tasks. It assigns every task to the provided task ID.
 */
public interface TaskExecutor {

    /**
     * Execute the task asynchronously.
     *
     * @param taskID ID of the task
     * @param callbackRequest request identifying where to send the callback
     * @param request input request of the synchronous executor
     * @param syncExecutor executor, which runs the operation synchronously
     * @param errorHandler handle the error, and create the default result
     * @param callbackSender callback sender
     * @param <T> request type
     * @param <R> result type
     */
    <T, R> void executeAsync(
            String taskID,
            Request callbackRequest,
            T request,
            Function<T, R> syncExecutor,
            BiFunction<T, Throwable, R> errorHandler,
            BiConsumer<Request, R> callbackSender);
}
