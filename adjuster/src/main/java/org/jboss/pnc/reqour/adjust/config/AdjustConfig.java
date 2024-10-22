/**
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.config;

import io.smallrye.config.WithName;

import java.util.Map;

/**
 * Alignment configuration.
 */
public interface AdjustConfig {

    boolean validate();

    @WithName("mvn")
    MvnProviderConfig mvnProviderConfig();

    @WithName("gradle")
    GradleProviderConfig gradleProviderConfig();

    @WithName("scala")
    SbtProviderConfig scalaProviderConfig();

    @WithName("npm")
    NpmProviderConfig npmProviderConfig();

    Map<String, BuildCategoryConfig> buildCategories();
}
