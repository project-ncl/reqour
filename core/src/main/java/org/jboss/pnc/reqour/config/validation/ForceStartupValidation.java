/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.config.validation;

import io.quarkus.runtime.Startup;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jboss.pnc.reqour.config.ReqourConfig;

/**
 * In order to fail due to invalid config during startup, we force the validation using this bean.
 */
@Singleton
@Startup
public class ForceStartupValidation {

    @Inject
    ReqourConfig config;
}
