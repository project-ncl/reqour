/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.model;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import lombok.Builder;
import lombok.Value;

/**
 * User specified alignment parameters, i.e., parameters provided in
 * {@link org.jboss.pnc.api.constants.BuildConfigurationParameterKeys#ALIGNMENT_PARAMETERS}.
 */
@Builder
@Value
public class UserSpecifiedAlignmentParameters {

    private static final Path DEFAULT_SUB_FOLDER = Path.of("");

    /**
     * Sub-folder where is the manipulation results file located.
     */
    Path subFolderWithResults;

    /**
     * All the remaining user-specified alignment parameters.
     */
    List<String> alignmentParameters;

    public static UserSpecifiedAlignmentParameters defaultResult() {
        return UserSpecifiedAlignmentParameters.builder()
                .subFolderWithResults(DEFAULT_SUB_FOLDER)
                .alignmentParameters(Collections.emptyList())
                .build();
    }

    public static UserSpecifiedAlignmentParameters withoutSubFolder(String alignmentParameters) {
        return UserSpecifiedAlignmentParameters.builder()
                .subFolderWithResults(DEFAULT_SUB_FOLDER)
                .alignmentParameters(Arrays.asList(alignmentParameters.split(" ")))
                .build();
    }
}
