/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.profile;

import java.util.Set;

import org.jboss.pnc.reqour.adjust.alternatives.AdjustProviderPickerMock;
import org.jboss.pnc.reqour.adjust.alternatives.AdjustmentPusherMock;
import org.jboss.pnc.reqour.adjust.alternatives.FailingAdjustProviderMock;
import org.jboss.pnc.reqour.adjust.alternatives.RepositoryFetcherMock;

import io.quarkus.test.junit.QuarkusTestProfile;

public class WithFailingAdjustProviderAlternative implements QuarkusTestProfile {

    @Override
    public Set<Class<?>> getEnabledAlternatives() {
        return Set.of(
                RepositoryFetcherMock.class,
                AdjustProviderPickerMock.class,
                FailingAdjustProviderMock.class,
                AdjustmentPusherMock.class);
    }
}
