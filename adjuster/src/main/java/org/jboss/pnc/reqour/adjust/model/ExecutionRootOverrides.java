/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.model;

import org.jboss.pnc.api.reqour.dto.VersioningState;

/**
 * Possible overrides for {@link VersioningState#getExecutionRootModified()} specified by a user
 *
 * @param groupId root's group id
 * @param artifactId root's artifact id
 */
public record ExecutionRootOverrides(String groupId, String artifactId) {

    public static ExecutionRootOverrides noOverrides() {
        return new ExecutionRootOverrides(null, null);
    }

    public boolean hasNoOverrides() {
        return groupId == null && artifactId == null;
    }
}
