/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.common.profile;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

public class WithStaticBifrostUrl implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("reqour.log.final-log.bifrost-uploader.base-url", "https://test.bifrost.com");
    }
}
