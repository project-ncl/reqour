/**
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.rest.config;

import io.smallrye.config.ConfigMapping;

import java.nio.file.Path;

@ConfigMapping(prefix = "reqour-rest")
public interface ReqourRestConfig {

    Path podDefinitionFile();
}
