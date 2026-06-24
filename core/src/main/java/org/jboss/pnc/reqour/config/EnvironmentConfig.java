/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.config;

import io.smallrye.config.WithName;

public interface EnvironmentConfig {

    String HOME_ENV_VARIABLE = "HOME";
    String PATH_ENV_VARIABLE = "PATH";
    String JAVA_HOME_ENV_VARIABLE = "JAVA_HOME";

    @WithName(HOME_ENV_VARIABLE)
    String home();

    @WithName(PATH_ENV_VARIABLE)
    String path();

    @WithName(JAVA_HOME_ENV_VARIABLE)
    String javaHome();
}
