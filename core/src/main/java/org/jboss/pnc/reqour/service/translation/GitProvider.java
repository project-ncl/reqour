/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.service.translation;

public enum GitProvider {

    GITLAB,
    GITHUB,
    ;

    public static GitProvider fromString(String configValue) {
        for (GitProvider value : GitProvider.values()) {
            if (value.name().equalsIgnoreCase(configValue)) {
                return value;
            }
        }
        throw new RuntimeException("No git provider for config value '" + configValue + "' was found.");
    }
}
