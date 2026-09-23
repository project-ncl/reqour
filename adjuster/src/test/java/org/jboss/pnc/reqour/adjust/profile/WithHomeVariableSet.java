/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.profile;

import java.util.Map;

import org.jboss.pnc.reqour.config.ConfigConstants;
import org.jboss.pnc.reqour.config.EnvironmentConfig;

import io.quarkus.test.junit.QuarkusTestProfile;

public class WithHomeVariableSet implements QuarkusTestProfile {

    public static final String HOME_VALUE = "/foo/bar/baz";
    public static final String LANG_VALUE = "en_US.UTF-8";

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.ofEntries(
                Map.entry(ConfigConstants.ENVS_CONFIG + "." + EnvironmentConfig.HOME_ENV_VARIABLE, HOME_VALUE),
                Map.entry(ConfigConstants.ENVS_CONFIG + "." + EnvironmentConfig.LANG_ENV_VARIABLE, LANG_VALUE));
    }
}