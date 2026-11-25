/*
 * SPDX-FileCopyrightText: Copyright © 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.service.scmcontent;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.StringWriter;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.List;

import jakarta.inject.Inject;

import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.pnc.reqour.common.exceptions.GitHubAppException;
import org.jboss.pnc.reqour.common.exceptions.GitHubAppInstallationNotFoundException;
import org.jboss.pnc.reqour.common.exceptions.GitHubAppTransientException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.kohsuke.github.GHPermissionType;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.http.Fault;

import io.quarkiverse.wiremock.devservice.ConnectWireMock;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Verifies that the fault-tolerance {@code Guard} applied to {@link GitHubAppInstallationTokenProvider} actually takes
 * effect. These tests inject the provider as a CDI bean (so the {@code @ApplyGuard} interceptor fires) and drive a real
 * HTTP exchange through WireMock, which the plain unit tests in {@code GitHubAppInstallationTokenProviderTest} cannot
 * do
 * (they instantiate the provider directly, bypassing the interceptor).
 */
@QuarkusTest
@ConnectWireMock
class GitHubAppInstallationTokenProviderGuardTest {

    @Inject
    GitHubAppInstallationTokenProvider provider;

    // Injected by the quarkus-wiremock dev service.
    WireMock wiremock;

    // Resolved lazily at runtime from the port the quarkus-wiremock dev service binds to.
    private String wireMockBaseUrl() {
        int port = ConfigProvider.getConfig().getValue("quarkus.wiremock.devservices.port", Integer.class);
        return "http://localhost:" + port;
    }

    private static final String APP_ID = "123456";
    private static final List<String> REPOS = List.of("my-repo");

    private static String pkcs1Pem;

    @BeforeAll
    static void setUpKey() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        StringWriter writer = new StringWriter();
        try (JcaPEMWriter pemWriter = new JcaPEMWriter(writer)) {
            pemWriter.writeObject(kpg.generateKeyPair().getPrivate());
        }
        pkcs1Pem = writer.toString();
    }

    @BeforeEach
    void resetWireMock() {
        wiremock.resetMappings();
        wiremock.resetScenarios();
        wiremock.resetRequests();

        // Authenticating as the App (GET /app) always succeeds; the tests vary only the installation lookup.
        wiremock.register(
                get(urlEqualTo("/app"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json")
                                        .withBody("""
                                                {
                                                  "id": 123456,
                                                  "slug": "test-app",
                                                  "name": "Test App",
                                                  "owner": { "login": "test-owner", "id": 1 }
                                                }
                                                """)));
    }

    private GitHubAppInstallationTokenProvider.InstallationToken obtainToken(String org) {
        return provider.getInstallationToken(
                APP_ID,
                pkcs1Pem,
                org,
                wireMockBaseUrl(),
                GHPermissionType.READ,
                GHPermissionType.READ,
                REPOS);
    }

    private String installationBody(String org, long installationId) {
        return """
                {
                  "id": %d,
                  "account": { "login": "%s", "id": 2 },
                  "app_id": 123456,
                  "target_id": 2,
                  "target_type": "Organization",
                  "repository_selection": "selected",
                  "access_tokens_url": "%s/app/installations/%d/access_tokens"
                }
                """.formatted(installationId, org, wireMockBaseUrl(), installationId);
    }

    @Test
    @DisplayName("retries after a transient 5xx and succeeds once the installation lookup returns 200")
    void retriesOnTransientServerErrorThenSucceeds() {
        String org = "flaky-org";
        long installationId = 78910L;

        // First installation lookup fails with 500 (transient), the retry gets a 200.
        wiremock.register(
                get(urlEqualTo("/orgs/" + org + "/installation"))
                        .inScenario("transient-5xx")
                        .whenScenarioStateIs(STARTED)
                        .willReturn(
                                aResponse()
                                        .withStatus(500)
                                        .withHeader("Content-Type", "application/json")
                                        .withBody("{\"message\":\"Internal Server Error\"}"))
                        .willSetStateTo("recovered"));
        wiremock.register(
                get(urlEqualTo("/orgs/" + org + "/installation"))
                        .inScenario("transient-5xx")
                        .whenScenarioStateIs("recovered")
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(installationBody(org, installationId))));
        wiremock.register(
                post(urlEqualTo("/app/installations/" + installationId + "/access_tokens"))
                        .willReturn(
                                aResponse()
                                        .withStatus(201)
                                        .withHeader("Content-Type", "application/json")
                                        .withBody("""
                                                {
                                                  "token": "ghs_retrytoken",
                                                  "expires_at": "2026-11-21T14:12:31Z"
                                                }
                                                """)));

        GitHubAppInstallationTokenProvider.InstallationToken token = obtainToken(org);

        assertThat(token).isNotNull();
        assertThat(token.token()).isEqualTo("ghs_retrytoken");
        assertThat(token.expiresAt()).isEqualTo(Instant.parse("2026-11-21T14:12:31Z"));

        // The guard retried exactly once: two installation lookups (500 then 200).
        wiremock.verifyThat(2, getRequestedFor(urlEqualTo("/orgs/" + org + "/installation")));
    }

    @Test
    @DisplayName("does not retry a permanent 404 installation-not-found")
    void doesNotRetryPermanentNotFound() {
        String org = "unknown-org";

        wiremock.register(
                get(urlEqualTo("/orgs/" + org + "/installation"))
                        .willReturn(
                                aResponse()
                                        .withStatus(404)
                                        .withHeader("Content-Type", "application/json")
                                        .withBody("{\"message\":\"Not Found\"}")));

        assertThatThrownBy(() -> obtainToken(org))
                .isInstanceOf(GitHubAppInstallationNotFoundException.class);

        // A missing installation is permanent, so the guard must abort instead of retrying: exactly one lookup.
        wiremock.verifyThat(1, getRequestedFor(urlEqualTo("/orgs/" + org + "/installation")));
    }

    @Test
    @DisplayName("exhausts the retries on a persistent 5xx and then fails with a transient exception")
    void exhaustsRetriesOnPersistentServerError() {
        String org = "down-org";

        wiremock.register(
                get(urlEqualTo("/orgs/" + org + "/installation"))
                        .willReturn(
                                aResponse()
                                        .withStatus(500)
                                        .withHeader("Content-Type", "application/json")
                                        .withBody("{\"message\":\"Internal Server Error\"}")));

        assertThatThrownBy(() -> obtainToken(org))
                .isInstanceOf(GitHubAppTransientException.class);

        // Every attempt is transient, so the guard retries until exhausted: maxRetries + 1 lookups.
        int maxRetries = ConfigProvider.getConfig()
                .getValue(
                        "reqour.core.git.github-app-installation-token.fault-tolerance.retry.max-retries",
                        Integer.class);
        wiremock.verifyThat(maxRetries + 1, getRequestedFor(urlEqualTo("/orgs/" + org + "/installation")));
    }

    @Test
    @DisplayName("does not retry a permanent 401 unauthorized installation lookup")
    void doesNotRetryUnauthorized() {
        String org = "unauthorized-org";

        wiremock.register(
                get(urlEqualTo("/orgs/" + org + "/installation"))
                        .willReturn(
                                aResponse()
                                        .withStatus(401)
                                        .withHeader("Content-Type", "application/json")
                                        .withBody("{\"message\":\"Bad credentials\"}")));

        assertThatThrownBy(() -> obtainToken(org))
                .isInstanceOf(GitHubAppException.class);

        // A 401 is permanent, so the guard must abort instead of retrying: exactly one lookup.
        wiremock.verifyThat(1, getRequestedFor(urlEqualTo("/orgs/" + org + "/installation")));
    }

    @Test
    @DisplayName("does not retry a permanent 403 forbidden installation lookup")
    void doesNotRetryForbidden() {
        String org = "forbidden-org";

        wiremock.register(
                get(urlEqualTo("/orgs/" + org + "/installation"))
                        .willReturn(
                                aResponse()
                                        .withStatus(403)
                                        .withHeader("Content-Type", "application/json")
                                        .withBody("{\"message\":\"Forbidden\"}")));

        assertThatThrownBy(() -> obtainToken(org))
                .isInstanceOf(GitHubAppException.class);

        // A 403 is permanent, so the guard must abort instead of retrying: exactly one lookup.
        wiremock.verifyThat(1, getRequestedFor(urlEqualTo("/orgs/" + org + "/installation")));
    }

    @Test
    @DisplayName("retries a transport-level error and then fails with a transient exception")
    void retriesTransportErrorThenFails() {
        String org = "reset-org";

        wiremock.register(
                get(urlEqualTo("/orgs/" + org + "/installation"))
                        .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        assertThatThrownBy(() -> obtainToken(org))
                .isInstanceOf(GitHubAppTransientException.class);

        // A transport error is transient, so the guard retries until exhausted: maxRetries + 1 lookups.
        int maxRetries = ConfigProvider.getConfig()
                .getValue(
                        "reqour.core.git.github-app-installation-token.fault-tolerance.retry.max-retries",
                        Integer.class);
        wiremock.verifyThat(maxRetries + 1, getRequestedFor(urlEqualTo("/orgs/" + org + "/installation")));
    }
}
