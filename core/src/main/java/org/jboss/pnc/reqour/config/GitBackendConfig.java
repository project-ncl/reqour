/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.config;

import io.smallrye.config.WithName;
import io.smallrye.config.WithParentName;

import java.util.List;
import java.util.Optional;

public interface GitBackendConfig {

    String url();

    String username();

    String hostname();

    /**
     * Do not end the workspace name with the trailing '/'.
     */
    @WithName("workspace")
    String workspaceName();

    long workspaceId();

    String readOnlyTemplate();

    String readWriteTemplate();

    String gitUrlInternalTemplate();

    String token();

    @WithParentName
    TagProtectionConfig tagProtection();

    interface TagProtectionConfig {

        Optional<String> protectedTagsPattern();

        Optional<List<String>> protectedTagsAcceptedPatterns();
    }
}
