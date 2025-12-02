/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.common.profile;

import io.quarkus.test.junit.QuarkusTestProfile;

/**
 * This test profile extracts common test-profile-related overrides, which would be normally replicated among several
 * test profiles. Hence, test profiles which do not have any unusual requirements should just extend this test profile.
 */
public abstract class CommonTestProfile implements QuarkusTestProfile {

    @Override
    public String getConfigProfile() {
        return "test";
    }
}
