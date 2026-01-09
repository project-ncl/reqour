/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.config.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import org.jboss.pnc.reqour.config.GitProvidersConfig;

import lombok.extern.slf4j.Slf4j;

/**
 * Validator which checks whether exactly one git provider is enabled.
 */
@Slf4j
public class ExactlyOneGitProviderEnabledValidator
        implements ConstraintValidator<WithExactlyOneProviderEnabled, GitProvidersConfig> {

    @Override
    public boolean isValid(GitProvidersConfig value, ConstraintValidatorContext context) {
        log.info("Checking if exactly one git provider is enabled");
        boolean gitLabEnabled = value.gitlab().enabled();
        boolean gitHubEnabled = value.github().enabled();
        return (gitLabEnabled && !gitHubEnabled) || (!gitLabEnabled && gitHubEnabled);
    }
}
