/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.config.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.jboss.pnc.reqour.config.GitConfig;

public class ActiveGitBackendExistsValidator
        implements ConstraintValidator<WithExistingActive, GitConfig.GitBackendsConfig> {

    private String message;

    @Override
    public void initialize(WithExistingActive constraintAnnotation) {
        message = constraintAnnotation.message();
    }

    @Override
    public boolean isValid(GitConfig.GitBackendsConfig value, ConstraintValidatorContext context) {
        context.buildConstraintViolationWithTemplate(message);
        return value.availableGitBackends().containsKey(value.activeGitBackend());
    }
}
