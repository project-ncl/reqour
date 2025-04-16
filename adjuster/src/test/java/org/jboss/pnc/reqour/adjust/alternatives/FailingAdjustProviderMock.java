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
import org.jboss.pnc.reqour.adjust.exception.AdjusterException;
import org.jboss.pnc.reqour.adjust.provider.AdjustProvider;
import org.jboss.pnc.reqour.runtime.UserLogger;
import org.slf4j.Logger;

@Alternative
@ApplicationScoped
public class FailingAdjustProviderMock implements AdjustProvider {

    @Inject
    @UserLogger
    Logger userLogger;

    @Override
    public ManipulatorResult adjust(AdjustRequest adjustRequest) {
        userLogger.info("Starting an alignment process using the corresponding manipulator");
        throw new AdjusterException("Oops, alignment exception");
    }
}
