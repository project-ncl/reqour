/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.runtime;

import java.io.IOException;
import java.net.URI;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import org.gitlab4j.api.GitLabApi;
import org.jboss.pnc.bifrost.upload.BifrostLogUploader;
import org.jboss.pnc.common.http.PNCHttpClient;
import org.jboss.pnc.reqour.config.BifrostUploaderConfig;
import org.jboss.pnc.reqour.config.ConfigUtils;
import org.jboss.pnc.reqour.config.GitProviderFaultTolerancePolicy;
import org.jboss.pnc.reqour.config.ReqourConfig;
import org.jboss.pnc.reqour.service.githubrestapi.GitHubRestClient;
import org.jboss.pnc.reqour.service.githubrestapi.GitHubRestClientHeadersFactory;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.slf4j.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.oidc.client.OidcClient;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import io.smallrye.common.annotation.Identifier;
import io.smallrye.faulttolerance.api.Guard;
import lombok.extern.slf4j.Slf4j;

@ApplicationScoped
@Slf4j
public class BeanFactory {

    @Produces
    @ApplicationScoped
    public GitLabApi gitLabApi(ConfigUtils configUtils) {
        return new GitLabApi(
                GitLabApi.ApiVersion.V4,
                configUtils.getActiveGitProviderConfig().url(),
                configUtils.getActiveGitProviderConfig().token());
    }

    @Produces
    @ApplicationScoped
    public GitHub gitHub(ConfigUtils configUtils) {
        try {
            return new GitHubBuilder()
                    .withEndpoint(configUtils.getActiveGitProviderConfig().url())
                    .withOAuthToken(configUtils.getActiveGitProviderConfig().token())
                    .build();
        } catch (IOException e) {
            log.error("Class for accessing GitHub API cannot be created", e);
            throw new RuntimeException(e);
        }
    }

    @Produces
    @ApplicationScoped
    public GitHubRestClient gitHubRestClient(ConfigUtils configUtils, GitHubRestClientHeadersFactory headersFactory) {
        return QuarkusRestClientBuilder.newBuilder()
                .baseUri(URI.create(configUtils.getActiveGitProviderConfig().url()))
                .clientHeadersFactory(headersFactory)
                .build(GitHubRestClient.class);
    }

    @Produces
    @ApplicationScoped
    public BifrostLogUploader bifrostLogUploader(ReqourConfig config, OidcClient oidcClient) {
        BifrostUploaderConfig bifrostUploaderConfig = config.log().finalLog().bifrostUploader();
        return new BifrostLogUploader(
                bifrostUploaderConfig.baseUrl(),
                bifrostUploaderConfig.maxRetries(),
                bifrostUploaderConfig.retryDelay(),
                () -> oidcClient.getTokens().await().indefinitely().getAccessToken());
    }

    @Produces
    @Identifier(GitProviderFaultTolerancePolicy.GIT_PROVIDERS_FAULT_TOLERANCE_GUARD)
    public Guard internalRetry(
            GitProviderFaultTolerancePolicy gitProviderFaultTolerancePolicy,
            @UserLogger Logger userLogger) {
        return Guard.create()
                .withDescription(gitProviderFaultTolerancePolicy.description())
                .withRetry()
                .maxRetries(gitProviderFaultTolerancePolicy.retry().maxRetries())
                .delay(
                        gitProviderFaultTolerancePolicy.retry().initialDelay(),
                        gitProviderFaultTolerancePolicy.retry().initialDelayUnit())
                .withExponentialBackoff()
                .factor(gitProviderFaultTolerancePolicy.retry().exponentialBackoff().factor())
                .maxDelay(
                        gitProviderFaultTolerancePolicy.retry().exponentialBackoff().maxDelay(),
                        gitProviderFaultTolerancePolicy.retry().exponentialBackoff().maxDelayUnit())
                .done()
                .beforeRetry(
                        t -> userLogger
                                .warn("Performing retry, last exception was: {}: {}", t.getMessage(), t.getCause(), t))
                .done()
                .withTimeout()
                .duration(
                        gitProviderFaultTolerancePolicy.timeout().duration(),
                        gitProviderFaultTolerancePolicy.timeout().durationUnit())
                .done()
                .build();
    }

    @Produces
    @ApplicationScoped
    public PNCHttpClient pncHttpClient(ObjectMapper objectMapper, ConfigUtils configUtils, OidcClient oidcClient) {
        PNCHttpClient client = new PNCHttpClient(objectMapper, configUtils.getPncHttpClientConfig());
        client.setTokenSupplier(() -> oidcClient.getTokens().await().indefinitely().getAccessToken());
        return client;
    }
}
