/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.runtime;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import org.jboss.pnc.bifrost.upload.BifrostLogUploader;
import org.jboss.pnc.reqour.config.BifrostUploaderConfig;
import org.jboss.pnc.reqour.config.ReqourConfig;

import io.quarkus.oidc.client.OidcClient;

@ApplicationScoped
public class BifrostLogUploaderProducer {

    @Inject
    OidcClient oidcClient;

    private final BifrostLogUploader logUploader;

    public BifrostLogUploaderProducer(ReqourConfig config) {
        BifrostUploaderConfig bifrostUploaderConfig = config.log().finalLog().bifrostUploader();
        logUploader = new BifrostLogUploader(
                bifrostUploaderConfig.baseUrl(),
                bifrostUploaderConfig.maxRetries(),
                bifrostUploaderConfig.retryDelay(),
                this::getFreshAccessToken);
    }

    private String getFreshAccessToken() {
        return oidcClient.getTokens().await().indefinitely().getAccessToken();
    }

    @Produces
    public BifrostLogUploader produce() {
        return logUploader;
    }
}
