/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.service;

import org.jboss.pnc.api.reqour.dto.AdjustRequest;
import org.jboss.pnc.reqour.adjust.model.CloningResult;

import java.nio.file.Path;

/**
 * Fetcher of the SCM repository, which takes place before the manipulation phase in order to prepare the environment
 * for the manipulator.
 */
public interface RepositoryFetcher {

    /**
     * Clone the repository based on the given adjust request into the given directory.
     *
     * @param adjustRequest adjust request specifying the details of the cloning
     * @param workdir directory where to clone
     */
    CloningResult cloneRepository(AdjustRequest adjustRequest, Path workdir);
}
