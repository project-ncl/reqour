/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.common.utils;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.pnc.reqour.common.exceptions.ConflictingInternalUrlException;
import org.jboss.pnc.reqour.config.ConfigUtils;
import org.jboss.pnc.reqour.config.ReqourCoreConfig;

import lombok.NonNull;

@ApplicationScoped
public class ValidationUtils {

    @Inject
    ReqourCoreConfig reqourCoreConfig;

    @Inject
    ConfigUtils configUtils;

    public void validateInternalUrlMatchesActiveGitProvider(@NonNull String internalUrl) {
        if (!reqourCoreConfig.git().validateInternalUrl()) {
            return;
        }

        final String activeGitProvidersHostname = configUtils.getActiveGitProvidersHostname();
        if (!internalUrl.contains(activeGitProvidersHostname)) {
            throw new ConflictingInternalUrlException(
                    String.format(
                            "Provided internal URL (%s) is not from active git provider's hostname (%s)",
                            internalUrl,
                            activeGitProvidersHostname));
        }
    }
}
