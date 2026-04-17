/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.config.validation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

@Constraint(validatedBy = ExactlyOneDefaultBuildCategoryValidator.class)
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.METHOD })
public @interface WithExactlyOneDefaultBuildCategory {

    String message() default "Invalid config file: exactly one default build category not present";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
