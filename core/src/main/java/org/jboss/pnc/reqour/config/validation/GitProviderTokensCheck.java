/*
 * SPDX-FileCopyrightText: Copyright © 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.config.validation;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import org.jboss.pnc.reqour.common.utils.HttpHeaderValues;
import org.jboss.pnc.reqour.config.GitProviderConfig;
import org.jboss.pnc.reqour.config.GitProvidersConfig;

import io.quarkus.runtime.StartupEvent;
import lombok.extern.slf4j.Slf4j;

/**
 * Reports at startup a git provider token that cannot be sent as an HTTP header, instead of at its first use. Only
 * logged: the token is not always needed (e.g. by a provider kept for reading old repositories).
 */
@ApplicationScoped
@Slf4j
public class GitProviderTokensCheck {

    @Inject
    GitProvidersConfig config;

    void check(@Observes StartupEvent event) {
        check("gitlab", config.gitlab());
        check("github", config.github());
    }

    /** @return false when the token is set but is not a valid header value; the value is never logged */
    public static boolean check(String provider, GitProviderConfig config) {
        String token = config.token();
        if (token == null || token.isEmpty() || HttpHeaderValues.valid(token)) {
            return true;
        }
        log.error(
                "The token of the {} git provider is not a valid HTTP header value ({})",
                provider,
                HttpHeaderValues.INVALID_HINT);
        return false;
    }
}
