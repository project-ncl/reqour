/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.model.smeg;

import lombok.Value;

@Value
public class ExecutionRoot {

    String groupId;
    String artifactId;
    String version;
}
