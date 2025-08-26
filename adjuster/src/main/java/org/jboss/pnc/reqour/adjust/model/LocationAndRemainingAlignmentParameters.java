/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.model;

import java.nio.file.Path;
import java.util.Optional;

import lombok.Builder;
import lombok.Value;

/**
 * Location together with all the remaining alignment parameters.<br/>
 */
@Builder
@Value
public class LocationAndRemainingAlignmentParameters {

    /**
     * Parsed location (from e.g. "--file=h2/pom.xml" for {@link org.jboss.pnc.api.enums.BuildType#MVN}) builds.
     */
    Optional<Path> location;

    /**
     * All the remaining parameters from the array we have been parsing.
     */
    String remainingAlignmentParameters;
}
