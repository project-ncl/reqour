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

import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * This executor is used for starting long-running tasks. It assigns every task to the provided task ID.
 */
public interface TaskExecutor {

    /**
     * Execute the task asynchronously.
     *
     * @param taskID ID of the task
     * @param request request
     * @param syncExecutor executor, which runs the operation synchronously
     * @param callbackSender callback sender
     * @param <T> request type
     * @param <R> result type
     */
    <T, R> void executeAsync(
            String taskID,
            T request,
            Function<T, R> syncExecutor,
            BiConsumer<R, Throwable> callbackSender);
}
