/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.common.executor.process;

import org.jboss.pnc.reqour.model.ProcessContext;

/**
 * This executor is used for starting of the {@link Process}es.
 */
public interface ProcessExecutor {

    /**
     * Execute the process as defined by {@link ProcessContext}. Also, do the following additional things: <br/>
     * - stream the STDOUT into {@link ProcessContext#getStdoutConsumer()} <br/>
     * - stream the STDERR into {@link ProcessContext#getStderrConsumer()} <br/>
     * - wait for completion of both consumers
     *
     * @param processContext context of the process to be run
     * @return exit code of the process
     */
    int execute(ProcessContext processContext);

    /**
     * Analogical to {@link ProcessExecutor#execute(ProcessContext)}. However, ignores the exit code and returns STDOUT.
     *
     * @param processContextBuilder builder of the process context. Uses this builder, but overrides the
     *        {@link ProcessContext.Builder#getStdoutConsumer()} in order to return the whole STDOUT as the result.
     *        Hence, the STDOUT consumer provided by the client is ignored.
     * @return STDOUT of the process
     */
    String stdout(ProcessContext.Builder processContextBuilder);
}
