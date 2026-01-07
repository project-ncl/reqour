/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.config.adjuster;

import java.nio.file.Path;
import java.util.List;

/**
 * Configuration for NPM Provider.
 */
public interface NpmProviderConfig {

    Path cliJarPath();

    List<String> alignmentParameters();
}
