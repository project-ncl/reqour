/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.model;

import java.nio.file.Path;

import lombok.Getter;

@Getter
public enum GradleAlignmentResultFile {

    GME_ENABLED(Path.of("build", "alignmentReport.json")),
    GME_DISABLED(Path.of("manipulation.json")),
    ;

    private final Path gmeAlignmentResultFile;

    private GradleAlignmentResultFile(Path gmeAlignmentResultFile) {
        this.gmeAlignmentResultFile = gmeAlignmentResultFile;
    }
}
