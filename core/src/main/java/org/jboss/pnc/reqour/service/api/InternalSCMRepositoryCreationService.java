/*
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

    /**
     * Compute project path (under the internal workspace/organization) from the request.
     *
     * @param creationRequest creation request
     * @return computed project path from the given request
     */
    String computeProjectPath(InternalSCMCreationRequest creationRequest);

    static String completeTemplateWithProjectPath(String template, String projectPath) {
        return String.format(template, projectPath);
    }
}
