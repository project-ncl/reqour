/**
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.provider;

import org.jboss.pnc.api.reqour.dto.AdjustResponse;

/**
 * Interface for providing alignment for any {@link org.jboss.pnc.api.enums.BuildType}.
 */
public interface AdjustProvider {

    /**
     * Make the alignment.
     *
     * @return result of alignment operation
     */
    AdjustResponse adjust();
}