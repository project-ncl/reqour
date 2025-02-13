/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.provider;

import org.jboss.pnc.api.reqour.dto.AdjustRequest;
import org.jboss.pnc.api.reqour.dto.ManipulatorResult;

/**
 * Interface for providing alignment for any {@link org.jboss.pnc.api.enums.BuildType}.
 */
public interface AdjustProvider {

    /**
     * Make the alignment.
     *
     * @return result of alignment operation
     */
    ManipulatorResult adjust(AdjustRequest adjustRequest);
}
