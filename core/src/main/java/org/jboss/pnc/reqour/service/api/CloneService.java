/**
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.service.api;

import org.jboss.pnc.api.reqour.dto.RepositoryCloneRequest;
import org.jboss.pnc.api.reqour.dto.RepositoryCloneResponse;

/**
 * Clone service used for cloning of the repository to the internal repository.
 */
public interface CloneService {

    /**
     * Clone the external repository (either completely or partially - depending on the
     * {@link RepositoryCloneRequest#getRef()}) to the internal repository.
     *
     * @param cloneRequest cloning request describing the way it should be cloned
     */
    RepositoryCloneResponse clone(RepositoryCloneRequest cloneRequest);
}
