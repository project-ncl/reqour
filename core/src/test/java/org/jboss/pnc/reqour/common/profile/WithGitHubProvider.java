/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.common.profile;

import java.util.Map;

import org.jboss.pnc.reqour.config.ConfigConstants;

public class WithGitHubProvider extends CommonTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(ConfigConstants.ACTIVE_GIT_PROVIDER, ConfigConstants.GITHUB);
    }
}
