/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.model;

import java.util.Optional;

import lombok.Builder;
import lombok.Value;

/**
 * Location option together with all remaining arguments.<br/>
 */
@Builder
@Value
public class LocationAndRemainingArgsOptions {

    /**
     * Parsed location option, e.g. "-Dfile=h2/pom.xml"
     */
    Optional<String> locationOption;

    /**
     * All the remaining arguments from the array we have been parsing.
     */
    String remainingArgs;
}
