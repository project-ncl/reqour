/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.model.smeg;

import java.util.List;

import org.jboss.pnc.api.reqour.dto.ManipulatorResult;
import org.jboss.pnc.api.reqour.dto.RemovedRepository;
import org.jboss.pnc.api.reqour.dto.VersioningState;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Value;

/**
 * Represents result of the <a href="https://github.com/project-ncl/smeg">SMEg</a>.
 */
@Value
public class SmegResult {

    @JsonProperty("VersioningState")
    SmegVersioningState versioningState;

    @JsonProperty("RemovedRepositories")
    List<RemovedRepository> removedRepositories;

    public ManipulatorResult toManipulatorResult() {
        return ManipulatorResult.builder()
                .versioningState(
                        VersioningState.builder()
                                .executionRootName(
                                        getExecutionRootName(
                                                versioningState.getExecutionRoot().getGroupId(),
                                                versioningState.getExecutionRoot().getArtifactId()))
                                .executionRootVersion(versioningState.getExecutionRoot().getVersion())
                                .build())
                .build();
    }

    private String getExecutionRootName(String groupId, String artifactId) {
        if (groupId != null && artifactId != null) {
            return groupId + ":" + artifactId;
        }
        if (groupId == null) {
            return artifactId;
        }
        return groupId;
    }
}
