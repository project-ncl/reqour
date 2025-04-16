/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.runtime;

import java.util.concurrent.ScheduledExecutorService;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import org.jboss.pnc.common.concurrent.HeartbeatScheduler;
import org.jboss.pnc.common.concurrent.mdc.MDCScheduledThreadPoolExecutor;
import org.jboss.pnc.common.http.PNCHttpClient;

@ApplicationScoped
public class BeanFactory {

    @Produces
    @ApplicationScoped
    public HeartbeatScheduler heartbeatScheduler(
            ScheduledExecutorService scheduledExecutor,
            PNCHttpClient pncHttpClient) {
        MDCScheduledThreadPoolExecutor mdcExecutor = new MDCScheduledThreadPoolExecutor(scheduledExecutor);
        return new HeartbeatScheduler(mdcExecutor, pncHttpClient);
    }
}
