/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.config.adjuster;

import java.nio.file.Path;
import java.util.List;

/**
 * Configuration for Maven provider.
 */
public interface MvnProviderConfig {

    Path cliJarPath();

    Path defaultSettingsFilePath();

    Path temporarySettingsFilePath();

    List<String> alignmentParameters();
}
