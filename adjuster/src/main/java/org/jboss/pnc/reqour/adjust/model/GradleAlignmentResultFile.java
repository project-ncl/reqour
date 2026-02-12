/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.model;

import java.nio.file.Path;

import lombok.Getter;

@Getter
public enum GradleAlignmentResultFile {

    GME_ENABLED(Path.of(Constants.STANDARD_BUILD_DIRECTORY, Constants.ALIGNMENT_REPORT_JSON)),
    GME_DISABLED(Path.of(Constants.MANIPULATION_JSON)),
    ;

    private final Path gmeAlignmentResultFile;

    private GradleAlignmentResultFile(Path gmeAlignmentResultFile) {
        this.gmeAlignmentResultFile = gmeAlignmentResultFile;
    }

    public static class Constants {
        private static final String STANDARD_BUILD_DIRECTORY = "build";
        public static final String ALIGNMENT_REPORT_JSON = "alignmentReport.json";
        private static final String MANIPULATION_JSON = "manipulation.json";
    }
}
