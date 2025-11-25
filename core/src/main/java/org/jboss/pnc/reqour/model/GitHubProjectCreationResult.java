/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.model;

import org.jboss.pnc.api.enums.InternalSCMCreationStatus;
import org.kohsuke.github.GHRepository;

/**
 * Aggregated model for storing the {@link GHRepository} together with info whether this project is newly created or
 * already existed.
 */
public record GitHubProjectCreationResult(GHRepository repository, InternalSCMCreationStatus status) {
}
