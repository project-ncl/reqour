/**
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.config;

import java.nio.file.Path;
import java.util.List;

/**
 * Configuration for {@link org.jboss.pnc.reqour.adjust.provider.MvnProvider}.
 */
public interface MvnProviderConfig {

    Path cliJarPath();

    Path defaultSettingsFilePath();

    Path temporarySettingsFilePath();

    List<String> alignmentParameters();
}
