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
 * Annotation used for validating whether the active git backend (key 'reqour.git.git-backends.active') is being present
 * in the available git backends, i.e., is one of the keys in the 'reqour.git.git-backends.available' map.
 */
@Documented
@Constraint(validatedBy = ActiveGitBackendExistsValidator.class)
@Target({ ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface WithExistingActive {

    String message() default "Invalid config file: active git backend is not available";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
