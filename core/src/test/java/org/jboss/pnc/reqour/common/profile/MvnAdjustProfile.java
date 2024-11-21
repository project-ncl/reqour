/**
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.common.profile;

import java.util.Set;

public class MvnAdjustProfile extends CommonTestProfile {

    @Override
    public Set<String> tags() {
        return Set.of(TestTag.MVN.name(), TestTag.ADJUST.name());
    }
}
