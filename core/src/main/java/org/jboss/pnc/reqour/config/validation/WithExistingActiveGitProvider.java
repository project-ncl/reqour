/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.config.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Validation annotation denoting that active git provider should exist (i.e., be configured as one of the git
 * providers).
 */
@Constraint(validatedBy = ExistingActiveGitProviderValidator.class)
@Target({ ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface WithExistingActiveGitProvider {

    String message() default "Invalid config file: active git provider is not configured";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
