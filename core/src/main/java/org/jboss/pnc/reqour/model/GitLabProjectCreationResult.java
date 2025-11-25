/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.model;

import org.gitlab4j.api.models.Project;
import org.jboss.pnc.api.enums.InternalSCMCreationStatus;

/**
 * Aggregated model for storing the {@link Project} together with info whether this project is newly created or already
 * existed.
 */
public record GitLabProjectCreationResult(Project project, InternalSCMCreationStatus status) {
}
