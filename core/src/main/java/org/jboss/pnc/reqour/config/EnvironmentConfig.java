/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.config;

import java.util.List;

import io.smallrye.config.WithDefault;
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

    /**
     * List of environment variable name prefixes whose matching entries from the process environment will be forwarded
     * into every manipulator subprocess. For example, {@code ["ARTIFACTORY"]} causes all env vars whose names begin
     * with {@code ARTIFACTORY} to be propagated.
     */
    @WithDefault("")
    List<String> propagatedEnvPrefixes();
}
