/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.common.profile;

import java.util.Map;

import org.jboss.pnc.reqour.config.ConfigConstants;

public class WithInternalUrlValidation extends CommonTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.ofEntries(
                Map.entry(ConfigConstants.INTERNAL_URL_VALIDATION, ConfigConstants.TRUE));
    }
}
