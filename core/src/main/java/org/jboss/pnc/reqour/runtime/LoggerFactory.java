/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.runtime;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;

@ApplicationScoped
public class LoggerFactory {

    private final Logger userLogger;

    public LoggerFactory(@ConfigProperty(name = "reqour.log.user-log.user-logger-name") String userLoggerName) {
        userLogger = org.slf4j.LoggerFactory.getLogger(userLoggerName);
    }

    @Produces
    @UserLogger
    @ApplicationScoped
    Logger userLogger() {
        return userLogger;
    }
}
