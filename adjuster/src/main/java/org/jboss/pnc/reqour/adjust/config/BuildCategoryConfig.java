/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.config;

import java.util.List;
import java.util.Optional;

/**
 * Configuration for {@link org.jboss.pnc.api.constants.BuildConfigurationParameterKeys#BUILD_CATEGORY}.
 */
public interface BuildCategoryConfig {

    String persistentMode();

    String temporaryMode();

    String temporaryPreferPersistentMode();

    VersionIncrementalSuffixConfig versionIncrementalSuffix();

    Optional<List<String>> versionSuffixAlternatives();

    /**
     * List of additional parameters which are sent to a manipulator and are possible to be overridden by a user.
     */
    Optional<List<String>> additionalOverridableAlignmentParameters();

    /**
     * List of additional parameters which are sent to a manipulator and are **NOT** possible to be overridden by a
     * user.
     */
    Optional<List<String>> additionalNonOverridableAlignmentParameters();
}
