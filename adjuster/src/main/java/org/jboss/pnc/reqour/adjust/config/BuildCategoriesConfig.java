/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.config;

import java.util.Map;

import io.smallrye.config.WithParentName;

public interface BuildCategoriesConfig {

    @WithParentName
    Map<String, BuildCategoryConfig> buildCategories();
}
