/**
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.model;

import lombok.Builder;
import lombok.Value;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * User specified alignment parameters, i.e., parameters provided in
 * {@link org.jboss.pnc.api.constants.BuildConfigurationParameterKeys#ALIGNMENT_PARAMETERS}.
 */
@Builder
@Value
public class UserSpecifiedAlignmentParameters {

    private static final Path DEFAULT_SUB_FOLDER = Path.of("");

    List<String> alignmentParameters;
    Path subFolder;

    public static UserSpecifiedAlignmentParameters defaultResult() {
        return UserSpecifiedAlignmentParameters.builder()
                .alignmentParameters(Collections.emptyList())
                .subFolder(DEFAULT_SUB_FOLDER)
                .build();
    }

    public static UserSpecifiedAlignmentParameters withoutSubFolder(String alignmentParameters) {
        return UserSpecifiedAlignmentParameters.builder()
                .alignmentParameters(Arrays.asList(alignmentParameters.split(" ")))
                .subFolder(DEFAULT_SUB_FOLDER)
                .build();
    }
}
