/**
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.model;

import org.jboss.pnc.api.reqour.dto.ManipulatorResult;

/**
 * Result of the whole alignment operation, i.e. containing sub-results from both its phases:<br/>
 * - manipulation phase<br/>
 * - committing the adjustment changes
 *
 * @param manipulatorResult result of the manipulation phase
 * @param adjustmentPushResult result of the committing the adjustment changes
 */
public record AdjustmentResult(ManipulatorResult manipulatorResult, AdjustmentPushResult adjustmentPushResult) {
}
