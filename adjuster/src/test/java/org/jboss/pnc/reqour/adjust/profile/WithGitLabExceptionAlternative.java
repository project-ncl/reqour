/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.profile;

import java.util.Set;

import org.jboss.pnc.reqour.adjust.alternatives.AdjustProviderMock;
import org.jboss.pnc.reqour.adjust.alternatives.AdjustProviderPickerMock;
import org.jboss.pnc.reqour.adjust.alternatives.AdjustmentPusherMock;
import org.jboss.pnc.reqour.adjust.alternatives.FailingRepositoryFetcherMock2;

import io.quarkus.test.junit.QuarkusTestProfile;

public class WithGitLabExceptionAlternative implements QuarkusTestProfile {

    @Override
    public Set<Class<?>> getEnabledAlternatives() {
        return Set.of(
                AdjustProviderPickerMock.class,
                AdjustProviderMock.class,
                FailingRepositoryFetcherMock2.class,
                AdjustmentPusherMock.class);
    }
}
