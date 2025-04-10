/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.jboss.pnc.common.concurrent.HeartbeatScheduler;
import org.jboss.pnc.common.concurrent.mdc.MDCScheduledThreadPoolExecutor;
import org.jboss.pnc.common.http.PNCHttpClient;
import org.jboss.pnc.reqour.config.ConfigUtils;

import java.util.concurrent.ScheduledExecutorService;

@ApplicationScoped
public class BeanCreator {

    @Produces
    @ApplicationScoped
    public PNCHttpClient pncHttpClient(ObjectMapper objectMapper, ConfigUtils configUtils) {
        return new PNCHttpClient(objectMapper, configUtils.getPncHttpClientConfig());
    }

    @Produces
    @ApplicationScoped
    public HeartbeatScheduler heartbeatScheduler(
            ScheduledExecutorService scheduledExecutor,
            PNCHttpClient pncHttpClient) {
        MDCScheduledThreadPoolExecutor mdcExecutor = new MDCScheduledThreadPoolExecutor(scheduledExecutor);
        return new HeartbeatScheduler(mdcExecutor, pncHttpClient);
    }
}
