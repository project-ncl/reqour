/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.config;

import java.util.Optional;

/**
 * Configuration for {@link org.jboss.pnc.api.constants.BuildConfigurationParameterKeys#BUILD_CATEGORY}.
 */
public interface BuildCategoryConfig {

    String persistentMode();

    String temporaryMode();

    String temporaryPreferPersistentMode();

    Optional<String> prefixOfSuffixVersion();
}
