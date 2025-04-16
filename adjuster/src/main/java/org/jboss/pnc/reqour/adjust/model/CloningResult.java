/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.model;

/**
 * Result of the SCM cloning operation which takes place before the manipulation phase in order to prepare the working
 * directory for the manipulation.
 * 
 * @param upstreamCommit ID of the upstream commit over which we wish to do the alignment
 * @param isRefRevisionInternal boolean flag whether the {@link org.jboss.pnc.api.reqour.dto.AdjustRequest#ref} is
 *        present in the internal SCM
 */
public record CloningResult(String upstreamCommit, boolean isRefRevisionInternal) {
}
