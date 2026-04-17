/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.config.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import org.jboss.pnc.reqour.adjust.config.BuildCategoriesConfig;

public class ExactlyOneDefaultBuildCategoryValidator
        implements ConstraintValidator<WithExactlyOneDefaultBuildCategory, BuildCategoriesConfig> {

    @Override
    public boolean isValid(BuildCategoriesConfig value, ConstraintValidatorContext context) {
        int useByDefaultCount = 0;

        for (var buildCategory : value.buildCategories().values()) {
            if (buildCategory.useByDefault()) {
                useByDefaultCount += 1;
            }
        }
        return useByDefaultCount == 1;
    }
}
