/**
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.service.api;

import org.jboss.pnc.api.reqour.dto.InternalSCMCreationRequest;
import org.jboss.pnc.api.reqour.dto.InternalSCMCreationResponse;

/**
 * Service used for creating new internal SCM repository.
 */
public interface InternalSCMRepositoryCreationService {

    /**
     * Create new internal SCM repository based on the given request.
     *
     * @param creationRequest internal SCM repository creation request
     */
    InternalSCMCreationResponse createInternalSCMRepository(InternalSCMCreationRequest creationRequest);
}
