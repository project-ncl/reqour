/**
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.model;

import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Optional;

@Builder
@Value
public class LocationAndRemainingArgsOptions {

    Optional<String> locationOption;
    List<String> remainingArgs;
}
