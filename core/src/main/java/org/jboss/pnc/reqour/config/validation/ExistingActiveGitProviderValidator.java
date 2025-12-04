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
 * Validator which checks whether the git provider, which was marked as active really exists.
 */
@Slf4j
public class ExistingActiveGitProviderValidator
        implements ConstraintValidator<WithExistingActiveGitProvider, GitProvidersConfig> {

    @Override
    public boolean isValid(GitProvidersConfig value, ConstraintValidatorContext context) {
        log.info("Checking if active git provider is configured");
        value.gitProviders().keySet().forEach(log::info);
        return value.gitProviders().containsKey(value.activeGitProvider());
    }
}
