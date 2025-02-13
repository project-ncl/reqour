/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.profile;

import io.quarkus.test.junit.QuarkusTestProfile;
import org.jboss.pnc.reqour.adjust.alternatives.AdjustProviderMock;
import org.jboss.pnc.reqour.adjust.alternatives.AdjustProviderPickerMock;
import org.jboss.pnc.reqour.adjust.alternatives.AdjustmentPusherMock;
import org.jboss.pnc.reqour.adjust.alternatives.RepositoryFetcherMock;

import java.util.Set;

public class WithSuccessfulAlternatives implements QuarkusTestProfile {

    @Override
    public Set<Class<?>> getEnabledAlternatives() {
        return Set.of(
                RepositoryFetcherMock.class,
                AdjustProviderPickerMock.class,
                AdjustProviderMock.class,
                AdjustmentPusherMock.class);
    }
}
