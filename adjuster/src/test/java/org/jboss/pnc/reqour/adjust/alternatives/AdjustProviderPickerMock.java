/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.alternatives;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;
import org.jboss.pnc.api.reqour.dto.AdjustRequest;
import org.jboss.pnc.reqour.adjust.provider.AdjustProvider;
import org.jboss.pnc.reqour.adjust.provider.AdjustProviderPicker;

@Alternative
@ApplicationScoped
public class AdjustProviderPickerMock implements AdjustProviderPicker {

    @Inject
    AdjustProvider adjustProvider;

    @Override
    public AdjustProvider pickAdjustProvider(AdjustRequest adjustRequest) {
        return adjustProvider;
    }
}
