/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.runtime;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import io.quarkus.logging.Log;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import lombok.extern.slf4j.Slf4j;

@ApplicationScoped
@Slf4j
public class AppLifecycleBean {

    public void startup(@Observes StartupEvent event) {
        Log.info("Application is starting");
    }

    public void shutdown(@Observes ShutdownEvent event) {
        Log.info("Application is shutting down");
    }
}
