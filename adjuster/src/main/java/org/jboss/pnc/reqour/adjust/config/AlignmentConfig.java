/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.config;

import org.jboss.pnc.api.reqour.dto.AdjustRequest;
import org.jboss.pnc.reqour.adjust.config.validation.WithExactlyOneDefaultBuildCategory;

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

    @WithExactlyOneDefaultBuildCategory
    BuildCategoriesConfig buildCategoriesConfig();

    boolean validate();
}
