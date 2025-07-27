/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.model;

import java.util.Optional;

import lombok.Builder;
import lombok.Value;

/**
 * Location option together with all the remaining alignment parameters.<br/>
 */
@Builder
@Value
public class LocationAndRemainingAlignmentParameters {

    /**
     * Parsed location option, e.g. specified by "--file=h2/pom.xml" for {@link org.jboss.pnc.api.enums.BuildType#MVN}
     * builds.
     */
    Optional<String> locationOption;

    /**
     * All the remaining parameters from the array we have been parsing.
     */
    String remainingAlignmentParameters;
}
