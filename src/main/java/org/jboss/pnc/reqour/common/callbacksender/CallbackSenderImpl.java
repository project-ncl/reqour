/**
 * JBoss, Home of Professional Open Source.
 * Copyright 2024-2024 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.pnc.reqour.common.callbacksender;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.failsafe.Failsafe;
import dev.failsafe.RetryPolicy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.MediaType;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.jboss.pnc.api.reqour.dto.RepositoryCloneResponseCallback;
import org.jboss.pnc.reqour.config.CallbackSenderConfig;
import org.jboss.pnc.reqour.config.ConfigUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.temporal.ChronoUnit;

/**
 * Sender using {@link java.net.http.HttpClient}, together with {@link dev.failsafe.Failsafe} for increased resiliency.
 */
@ApplicationScoped
@Slf4j
public class CallbackSenderImpl implements CallbackSender {

    private final ManagedExecutor executor;
    private final ConfigUtils configUtils;
    private final ObjectMapper objectMapper;

    @Inject
    public CallbackSenderImpl(ManagedExecutor executor, ConfigUtils configUtils, ObjectMapper objectMapper) {
        this.executor = executor;
        this.configUtils = configUtils;
        this.objectMapper = objectMapper;
    }

    @Override
    public void sendRepositoryCloneCallback(String method, String url, RepositoryCloneResponseCallback callback) {
        sendCallback(method, url, callback);
    }

    private void sendCallback(String method, String url, Object callback) {
        CallbackSenderConfig.RetryConfig retryConfig = configUtils.getCallbackSenderConfig().retryConfig();

        RetryPolicy<HttpResponse<String>> retryPolicy = RetryPolicy.<HttpResponse<String>> builder()
                .withBackoff(retryConfig.backoffInitialDelay(), retryConfig.backoffMaxDelay(), ChronoUnit.SECONDS)
                .withMaxRetries(retryConfig.maxRetries())
                .withMaxDuration(Duration.of(retryConfig.maxDuration(), ChronoUnit.SECONDS))
                .onSuccess(e -> log.info("Callback successfully sent, response: {}", e.getResult().body()))
                .onRetry(
                        e -> log.warn(
                                "Retrying (attempt #{}), last response was: {}",
                                e.getAttemptCount(),
                                e.getLastException().toString()))
                .onFailure(e -> log.warn("Callback couldn't be sent: {}", e.getException().toString()))
                .build();

        Failsafe.with(retryPolicy).with(executor).getAsync(() -> {
            log.info("Sending the callback {}", callback);
            try (HttpClient httpClient = HttpClient.newHttpClient()) {
                String callbackJson = objectMapper.writeValueAsString(callback);
                HttpResponse<String> response = httpClient
                        .send(getCallbackHttpRequest(method, url, callbackJson), HttpResponse.BodyHandlers.ofString());
                log.info("Got response: statusCode={}, body={}", response.statusCode(), response.body());
                return response;
            } catch (JsonProcessingException ex) {
                throw new RuntimeException("Cannot serialize into JSON the following: " + callback, ex);
            }
        });
    }

    private static HttpRequest getCallbackHttpRequest(String method, String url, String body) {
        return HttpRequest.newBuilder()
                .header("Content-Type", MediaType.APPLICATION_JSON)
                .method(method, HttpRequest.BodyPublishers.ofString(body))
                .uri(URI.create(url))
                .build();
    }
}
