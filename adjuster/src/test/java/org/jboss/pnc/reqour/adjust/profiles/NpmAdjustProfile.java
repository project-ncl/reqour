/**
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.profiles;

import org.jboss.pnc.reqour.common.profile.CommonTestProfile;

import java.util.Set;

public class NpmAdjustProfile extends CommonTestProfile {

    @Override
    public Set<String> tags() {
        return Set.of(TestTag.NPM.name(), TestTag.ADJUST.name());
    }
}
