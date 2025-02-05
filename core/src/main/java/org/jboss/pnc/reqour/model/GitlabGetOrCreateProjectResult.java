/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.model;

import org.gitlab4j.api.models.Project;
import org.jboss.pnc.api.reqour.dto.InternalSCMCreationResponse;

/**
 * Aggregated model for storing the project together with another metadata, e.g. whether the project has already been
 * existing in the internal SCM repository before.
 */
public record GitlabGetOrCreateProjectResult(Project project, InternalSCMCreationResponse result) {
}
