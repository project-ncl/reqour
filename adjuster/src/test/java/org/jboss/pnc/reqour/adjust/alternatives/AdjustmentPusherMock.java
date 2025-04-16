/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.alternatives;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;

import org.jboss.pnc.api.reqour.dto.AdjustRequest;
import org.jboss.pnc.api.reqour.dto.ManipulatorResult;
import org.jboss.pnc.reqour.adjust.model.AdjustmentPushResult;
import org.jboss.pnc.reqour.adjust.service.AdjustmentPusher;
import org.jboss.pnc.reqour.runtime.UserLogger;
import org.slf4j.Logger;

@Alternative
@ApplicationScoped
public class AdjustmentPusherMock implements AdjustmentPusher {

    @Inject
    @UserLogger
    Logger userLogger;

    @Override
    public AdjustmentPushResult pushAlignedChanges(AdjustRequest adjustRequest, ManipulatorResult manipulatorResult) {
        userLogger.info("Pushing aligned changes");
        return new AdjustmentPushResult("123", "reqour-eee");
    }
}
