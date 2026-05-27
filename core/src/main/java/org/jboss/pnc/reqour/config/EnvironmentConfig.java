/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.config;

import io.smallrye.config.WithName;

public interface EnvironmentConfig {

    String HOME_ENV_VARIABLE = "HOME";

    @WithName(HOME_ENV_VARIABLE)
    String home();
}
