/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.config;

import java.nio.file.Path;
import java.util.List;

/**
 * Configuration for {@link org.jboss.pnc.reqour.adjust.provider.SbtProvider}.
 */
public interface SbtProviderConfig {

    Path sbtPath();

    List<String> alignmentParameters();
}
