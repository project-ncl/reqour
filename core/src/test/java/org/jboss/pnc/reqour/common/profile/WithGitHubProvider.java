/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.common.profile;

import java.util.Map;

import org.jboss.pnc.reqour.config.core.ConfigConstants;

public class WithGitHubProvider extends CommonTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.ofEntries(
                Map.entry(ConfigConstants.GITLAB_PROVIDER_ENABLED, ConfigConstants.FALSE),
                Map.entry(ConfigConstants.GITHUB_PROVIDER_ENABLED, ConfigConstants.TRUE));
    }
}
