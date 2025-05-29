/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.service;

import org.jboss.pnc.api.reqour.dto.AdjustRequest;
import org.jboss.pnc.api.reqour.dto.ManipulatorResult;
import org.jboss.pnc.reqour.adjust.model.AdjustmentPushResult;

/**
 * Pusher of changes made by an alignment into a downstream repository.
 */
public interface AdjustmentPusher {

    /**
     * Push aligned changes into the downstream repository.
     */
    AdjustmentPushResult pushAlignedChanges(
            AdjustRequest adjustRequest,
            ManipulatorResult manipulatorResult,
            boolean failOnNoAlignmentChanges);
}
