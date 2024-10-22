/**
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.model;

import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Optional;

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
    List<String> remainingArgs;
}
