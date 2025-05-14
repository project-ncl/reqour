/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.runtime;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import org.jboss.pnc.common.http.PNCHttpClient;
import org.jboss.pnc.reqour.config.ConfigUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.oidc.client.OidcClient;

@ApplicationScoped
public class BeanFactory {

    @Inject
    OidcClient oidcClient;

    @Produces
    @ApplicationScoped
    public PNCHttpClient pncHttpClient(ObjectMapper objectMapper, ConfigUtils configUtils) {
        PNCHttpClient client = new PNCHttpClient(objectMapper, configUtils.getPncHttpClientConfig());
        client.setTokenSupplier(() -> oidcClient.getTokens().await().indefinitely().getAccessToken());
        return client;
    }
}
