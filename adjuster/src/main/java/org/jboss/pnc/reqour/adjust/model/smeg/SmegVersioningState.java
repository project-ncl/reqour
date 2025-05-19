/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.model.smeg;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Value;

@Value
public class SmegVersioningState {

    @JsonProperty("executionRootModified")
    ExecutionRoot executionRoot;
}
