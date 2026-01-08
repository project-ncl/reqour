/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.config.adjuster;

import java.util.Map;

import org.jboss.pnc.api.reqour.dto.AdjustRequest;

import io.smallrye.config.WithName;

/**
 * Alignment configuration.
 */
public interface AlignmentConfig {

    SuffixConfiguration suffix();

    AdjustRequest request();

    @WithName("mvn")
    MvnProviderConfig mvnProviderConfig();

    @WithName("gradle")
    GradleProviderConfig gradleProviderConfig();

    @WithName("scala")
    SbtProviderConfig scalaProviderConfig();

    @WithName("npm")
    NpmProviderConfig npmProviderConfig();

    Map<String, BuildCategoryConfig> buildCategories();

    boolean validate();
}
