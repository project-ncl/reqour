/*
 * SPDX-FileCopyrightText: Copyright © 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.service.scmcontent;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.StringWriter;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.interfaces.RSAPrivateKey;
import java.time.Instant;
import java.util.List;

import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.openssl.jcajce.JcaPKCS8Generator;
import org.jboss.pnc.reqour.common.exceptions.GitHubAppAuthenticationException;
import org.jboss.pnc.reqour.common.exceptions.GitHubAppException;
import org.jboss.pnc.reqour.common.exceptions.GitHubAppInstallationNotFoundException;
import org.jboss.pnc.reqour.common.exceptions.GitHubAppInstallationTokenException;
import org.jboss.pnc.reqour.common.exceptions.GitHubAppTransientException;
import org.jboss.pnc.reqour.common.exceptions.InvalidPrivateKeyException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.kohsuke.github.GHPermissionType;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

class GitHubAppInstallationTokenProviderTest {

    private final GitHubAppInstallationTokenProvider provider = new GitHubAppInstallationTokenProvider();

    /** Non-empty repository scope reused across the input-validation and integration tests. */
    private static final List<String> REPOS = List.of("my-repo");

    @Test
    @DisplayName("default API URL is https://api.github.com")
    void defaultApiUrlConstant() {
        assertThat(GitHubAppInstallationTokenProvider.DEFAULT_API_URL)
                .isEqualTo("https://api.github.com");
    }

    private static KeyPair testKeyPair;
    private static String pkcs1Pem;
    private static String pkcs8Pem;

    @BeforeAll
    static void setUpKeys() throws NoSuchAlgorithmException, IOException {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        testKeyPair = kpg.generateKeyPair();

        // Generate PKCS#1 PEM ("BEGIN RSA PRIVATE KEY")
        StringWriter pkcs1Writer = new StringWriter();
        try (JcaPEMWriter writer = new JcaPEMWriter(pkcs1Writer)) {
            writer.writeObject(testKeyPair.getPrivate());
        }
        pkcs1Pem = pkcs1Writer.toString();

        // Generate PKCS#8 PEM ("BEGIN PRIVATE KEY")
        StringWriter pkcs8Writer = new StringWriter();
        try (JcaPEMWriter writer = new JcaPEMWriter(pkcs8Writer)) {
            writer.writeObject(new JcaPKCS8Generator(testKeyPair.getPrivate(), null));
        }
        pkcs8Pem = pkcs8Writer.toString();
    }

    @Nested
    @DisplayName("InstallationToken Record Tests")
    class InstallationTokenTests {

        @Test
        @DisplayName("accessors return expected values")
        void accessorsWorkCorrectly() {
            Instant expiry = Instant.parse("2026-11-21T14:30:00Z");
            GitHubAppInstallationTokenProvider.InstallationToken token = new GitHubAppInstallationTokenProvider.InstallationToken(
                    "ghs_secret123",
                    expiry);

            assertThat(token.token()).isEqualTo("ghs_secret123");
            assertThat(token.expiresAt()).isEqualTo(expiry);
        }

        @Test
        @DisplayName("toString masks the secret token")
        void toStringMasksToken() {
            Instant expiry = Instant.parse("2026-11-21T14:30:00Z");
            GitHubAppInstallationTokenProvider.InstallationToken token = new GitHubAppInstallationTokenProvider.InstallationToken(
                    "ghs_secret123",
                    expiry);

            assertThat(token.toString())
                    .doesNotContain("ghs_secret123")
                    .isEqualTo("InstallationToken[token=***, expiresAt=2026-11-21T14:30:00Z]");
        }

        @Test
        @DisplayName("equals and hashCode contract")
        void equalsAndHashCode() {
            Instant expiry = Instant.parse("2026-11-21T14:30:00Z");
            GitHubAppInstallationTokenProvider.InstallationToken token1 = new GitHubAppInstallationTokenProvider.InstallationToken(
                    "ghs_secret123",
                    expiry);
            GitHubAppInstallationTokenProvider.InstallationToken token2 = new GitHubAppInstallationTokenProvider.InstallationToken(
                    "ghs_secret123",
                    expiry);
            GitHubAppInstallationTokenProvider.InstallationToken token3 = new GitHubAppInstallationTokenProvider.InstallationToken(
                    "ghs_different",
                    expiry);

            assertThat(token1).isEqualTo(token2).hasSameHashCodeAs(token2);
            assertThat(token1).isNotEqualTo(token3);
        }
    }

    @Nested
    @DisplayName("Private Key Parsing Tests")
    class ParsePrivateKeyTests {

        @Test
        @DisplayName("parses PKCS#1 RSA private key successfully")
        void parsesPkcs1PrivateKey() throws Exception {
            assertThat(pkcs1Pem).contains("BEGIN RSA PRIVATE KEY");
            PrivateKey privateKey = provider.parsePrivateKeyPem(pkcs1Pem);

            assertThat(privateKey).isNotNull();
            assertThat(privateKey.getAlgorithm()).isEqualTo("RSA");
            assertThat(privateKey).isInstanceOf(RSAPrivateKey.class);
        }

        @Test
        @DisplayName("parses a PKCS#1 PEM with extra leading and trailing newlines")
        void parsesPkcs1PrivateKeyWithExtraNewlines() throws Exception {
            // A key loaded from a file, env var, or secret store often arrives with extra surrounding newlines.
            String paddedPem = "\n\n" + pkcs1Pem + "\n\n";

            PrivateKey privateKey = provider.parsePrivateKeyPem(paddedPem);

            assertThat(privateKey).isNotNull();
            assertThat(privateKey.getAlgorithm()).isEqualTo("RSA");
            assertThat(privateKey).isInstanceOf(RSAPrivateKey.class);
        }

        @Test
        @DisplayName("parses a PKCS#1 PEM whose line breaks are literal \\n escape sequences")
        void parsesPkcs1PrivateKeyWithEscapedNewlines() throws Exception {
            // Secrets/env vars often store the PEM with the two characters "\n" instead of real line breaks.
            String escapedPem = pkcs1Pem.replace("\n", "\\n");
            assertThat(escapedPem).doesNotContain("\n").contains("\\n");

            PrivateKey privateKey = provider.parsePrivateKeyPem(escapedPem);

            assertThat(privateKey).isNotNull();
            assertThat(privateKey.getAlgorithm()).isEqualTo("RSA");
            assertThat(privateKey).isInstanceOf(RSAPrivateKey.class);
        }

        @Test
        @DisplayName("parses a PKCS#1 PEM whose line breaks are literal \\r\\n escape sequences")
        void parsesPkcs1PrivateKeyWithEscapedCrlf() throws Exception {
            String escapedPem = pkcs1Pem.replace("\n", "\\r\\n");
            assertThat(escapedPem).doesNotContain("\n").contains("\\r\\n");

            PrivateKey privateKey = provider.parsePrivateKeyPem(escapedPem);

            assertThat(privateKey).isNotNull();
            assertThat(privateKey.getAlgorithm()).isEqualTo("RSA");
            assertThat(privateKey).isInstanceOf(RSAPrivateKey.class);
        }

        @Test
        @DisplayName("parses a PKCS#1 PEM containing double literal \\n escape sequences")
        void parsesPkcs1PrivateKeyWithDoubleEscapedNewlines() throws Exception {
            // Blank lines show up as consecutive "\n\n"; every occurrence must be normalized.
            String escapedPem = "\\n\\n" + pkcs1Pem.replace("\n", "\\n") + "\\n\\n";

            PrivateKey privateKey = provider.parsePrivateKeyPem(escapedPem);

            assertThat(privateKey).isNotNull();
            assertThat(privateKey.getAlgorithm()).isEqualTo("RSA");
            assertThat(privateKey).isInstanceOf(RSAPrivateKey.class);
        }

        @Test
        @DisplayName("parses PKCS#8 private key successfully")
        void parsesPkcs8PrivateKey() throws Exception {
            assertThat(pkcs8Pem).contains("BEGIN PRIVATE KEY");
            PrivateKey privateKey = provider.parsePrivateKeyPem(pkcs8Pem);

            assertThat(privateKey).isNotNull();
            assertThat(privateKey.getAlgorithm()).isEqualTo("RSA");
        }

        @Test
        @DisplayName("throws InvalidPrivateKeyException for null PEM")
        void throwsForNullPem() {
            assertThatThrownBy(() -> provider.parsePrivateKeyPem(null))
                    .isInstanceOf(InvalidPrivateKeyException.class)
                    .isInstanceOf(GitHubAppException.class)
                    .hasMessage("Private key PEM must not be null");
        }

        @Test
        @DisplayName("throws InvalidPrivateKeyException for non-private-key PEM")
        void throwsForPublicKeyPem() throws IOException {
            StringWriter sw = new StringWriter();
            try (JcaPEMWriter writer = new JcaPEMWriter(sw)) {
                writer.writeObject(testKeyPair.getPublic());
            }
            String publicKeyPem = sw.toString();

            assertThatThrownBy(() -> provider.parsePrivateKeyPem(publicKeyPem))
                    .isInstanceOf(InvalidPrivateKeyException.class)
                    .isInstanceOf(GitHubAppException.class)
                    .hasMessageContaining("Unsupported or invalid private key PEM");
        }

        @Test
        @DisplayName("throws InvalidPrivateKeyException for empty or invalid string")
        void throwsForInvalidString() {
            assertThatThrownBy(() -> provider.parsePrivateKeyPem(""))
                    .isInstanceOf(InvalidPrivateKeyException.class)
                    .isInstanceOf(GitHubAppException.class)
                    .hasMessageContaining("Unsupported or invalid private key PEM");

            assertThatThrownBy(() -> provider.parsePrivateKeyPem("not-a-pem"))
                    .isInstanceOf(InvalidPrivateKeyException.class)
                    .isInstanceOf(GitHubAppException.class)
                    .hasMessageContaining("Unsupported or invalid private key PEM");
        }

        @Test
        @DisplayName("throws InvalidPrivateKeyException for non-RSA private key")
        void throwsForNonRsaKey() throws Exception {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
            kpg.initialize(256);
            StringWriter sw = new StringWriter();
            try (JcaPEMWriter writer = new JcaPEMWriter(sw)) {
                writer.writeObject(new JcaPKCS8Generator(kpg.generateKeyPair().getPrivate(), null));
            }
            String ecPem = sw.toString();

            assertThatThrownBy(() -> provider.parsePrivateKeyPem(ecPem))
                    .isInstanceOf(InvalidPrivateKeyException.class)
                    .hasMessageContaining("Unsupported private key algorithm: EC");
        }
    }

    @Nested
    @DisplayName("getInstallationToken Input Validation")
    class ValidationTests {

        @Test
        @DisplayName("null appId throws NullPointerException")
        void nullAppIdThrows() {
            assertThatThrownBy(
                    () -> provider.getInstallationToken(
                            null,
                            pkcs1Pem,
                            "my-org",
                            "http://127.0.0.1:8080",
                            GHPermissionType.READ,
                            GHPermissionType.READ,
                            REPOS))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("appId");

            assertThatThrownBy(
                    () -> provider.getInstallationToken(
                            null,
                            pkcs1Pem,
                            "my-org",
                            GHPermissionType.READ,
                            GHPermissionType.READ,
                            REPOS))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("appId");
        }

        @Test
        @DisplayName("blank appId throws IllegalArgumentException")
        void blankAppIdThrows() {
            assertThatThrownBy(
                    () -> provider.getInstallationToken(
                            "   ",
                            pkcs1Pem,
                            "my-org",
                            "http://127.0.0.1:8080",
                            GHPermissionType.READ,
                            GHPermissionType.READ,
                            REPOS))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("appId must not be blank");

            assertThatThrownBy(
                    () -> provider.getInstallationToken(
                            "",
                            pkcs1Pem,
                            "my-org",
                            GHPermissionType.READ,
                            GHPermissionType.READ,
                            REPOS))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("appId must not be blank");
        }

        @Test
        @DisplayName("null privateKeyPem throws NullPointerException")
        void nullPrivateKeyPemThrows() {
            assertThatThrownBy(
                    () -> provider.getInstallationToken(
                            "123456",
                            null,
                            "my-org",
                            "http://127.0.0.1:8080",
                            GHPermissionType.READ,
                            GHPermissionType.READ,
                            REPOS))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("privateKeyPem");
        }

        @Test
        @DisplayName("null organization throws NullPointerException")
        void nullOrganizationThrows() {
            assertThatThrownBy(
                    () -> provider.getInstallationToken(
                            "123456",
                            pkcs1Pem,
                            null,
                            "http://127.0.0.1:8080",
                            GHPermissionType.READ,
                            GHPermissionType.READ,
                            REPOS))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("organization");
        }

        @Test
        @DisplayName("null contentsPermission throws NullPointerException")
        void nullContentsPermissionThrows() {
            assertThatThrownBy(
                    () -> provider.getInstallationToken(
                            "123456",
                            pkcs1Pem,
                            "my-org",
                            "http://127.0.0.1:8080",
                            null,
                            GHPermissionType.READ,
                            REPOS))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("contentsPermission");
        }

        @Test
        @DisplayName("null metadataPermission throws NullPointerException")
        void nullMetadataPermissionThrows() {
            assertThatThrownBy(
                    () -> provider.getInstallationToken(
                            "123456",
                            pkcs1Pem,
                            "my-org",
                            "http://127.0.0.1:8080",
                            GHPermissionType.READ,
                            null,
                            REPOS))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("metadataPermission");
        }

        @Test
        @DisplayName("null repositories throws NullPointerException")
        void nullRepositoriesThrows() {
            assertThatThrownBy(
                    () -> provider.getInstallationToken(
                            "123456",
                            pkcs1Pem,
                            "my-org",
                            "http://127.0.0.1:8080",
                            GHPermissionType.READ,
                            GHPermissionType.READ,
                            null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("repositories");
        }

        @Test
        @DisplayName("blank organization throws IllegalArgumentException")
        void blankOrganizationThrows() {
            assertThatThrownBy(
                    () -> provider.getInstallationToken(
                            "123456",
                            pkcs1Pem,
                            "   ",
                            "http://127.0.0.1:8080",
                            GHPermissionType.READ,
                            GHPermissionType.READ,
                            REPOS))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("organization must not be blank");
        }

        @Test
        @DisplayName("empty repositories throws IllegalArgumentException")
        void emptyRepositoriesThrows() {
            assertThatThrownBy(
                    () -> provider.getInstallationToken(
                            "123456",
                            pkcs1Pem,
                            "my-org",
                            "http://127.0.0.1:8080",
                            GHPermissionType.READ,
                            GHPermissionType.READ,
                            List.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("repositories must not be empty");
        }

        @Test
        @DisplayName("invalid private key throws InvalidPrivateKeyException")
        void invalidPrivateKeyThrows() {
            assertThatThrownBy(
                    () -> provider.getInstallationToken(
                            "123456",
                            "invalid-key-data",
                            "my-org",
                            "http://127.0.0.1:8080",
                            GHPermissionType.READ,
                            GHPermissionType.READ,
                            REPOS))
                    .isInstanceOf(InvalidPrivateKeyException.class)
                    .isInstanceOf(GitHubAppException.class);
        }
    }

    @Nested
    @DisplayName("Endpoint Resolution")
    class EndpointResolutionTests {

        @Test
        @DisplayName("null apiUrl resolves to the default API URL")
        void nullResolvesToDefault() {
            assertThat(provider.resolveEndpoint(null))
                    .isEqualTo(GitHubAppInstallationTokenProvider.DEFAULT_API_URL);
        }

        @Test
        @DisplayName("blank apiUrl resolves to the default API URL")
        void blankResolvesToDefault() {
            assertThat(provider.resolveEndpoint("   "))
                    .isEqualTo(GitHubAppInstallationTokenProvider.DEFAULT_API_URL);
        }

        @Test
        @DisplayName("non-blank apiUrl is used verbatim")
        void nonBlankIsUsedVerbatim() {
            assertThat(provider.resolveEndpoint("https://ghe.example.com/api/v3"))
                    .isEqualTo("https://ghe.example.com/api/v3");
        }
    }

    @Nested
    @DisplayName("getInstallationToken Integration Tests with WireMock")
    class IntegrationTests {

        private static WireMockServer wireMockServer;

        @BeforeAll
        static void startWireMock() {
            wireMockServer = new WireMockServer(
                    WireMockConfiguration.wireMockConfig()
                            .bindAddress("127.0.0.1")
                            .dynamicPort());
            wireMockServer.start();
        }

        @AfterAll
        static void stopWireMock() {
            if (wireMockServer != null) {
                wireMockServer.stop();
            }
        }

        @BeforeEach
        void resetStubs() {
            wireMockServer.resetAll();

            // Default GET /app mock
            wireMockServer.stubFor(
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
                                                      "owner": {
                                                        "login": "test-owner",
                                                        "id": 1
                                                      }
                                                    }
                                                    """)));
        }

        private String getBaseUrl() {
            return "http://127.0.0.1:" + wireMockServer.port();
        }

        /** Calls the provider against the WireMock endpoint with the default read permissions and repository scope. */
        private GitHubAppInstallationTokenProvider.InstallationToken obtainToken(String pem, String org) {
            return provider.getInstallationToken(
                    "123456",
                    pem,
                    org,
                    getBaseUrl(),
                    GHPermissionType.READ,
                    GHPermissionType.READ,
                    REPOS);
        }

        private void stubSuccessfulInstallation(String orgName, long installationId, String tokenValue, String expiry) {
            String baseUrl = getBaseUrl();
            wireMockServer.stubFor(
                    get(urlEqualTo("/orgs/" + orgName + "/installation"))
                            .willReturn(
                                    aResponse()
                                            .withStatus(200)
                                            .withHeader("Content-Type", "application/json")
                                            .withBody("""
                                                    {
                                                      "id": %d,
                                                      "account": {
                                                        "login": "%s",
                                                        "id": 2
                                                      },
                                                      "app_id": 123456,
                                                      "target_id": 2,
                                                      "target_type": "Organization",
                                                      "permissions": {
                                                        "contents": "read",
                                                        "metadata": "read"
                                                      },
                                                      "events": [],
                                                      "single_file_name": null,
                                                      "has_multiple_single_files": false,
                                                      "single_file_paths": [],
                                                      "repository_selection": "selected",
                                                      "access_tokens_url": "%s/app/installations/%d/access_tokens"
                                                    }
                                                    """.formatted(installationId, orgName, baseUrl, installationId))));

            wireMockServer.stubFor(
                    post(urlEqualTo("/app/installations/" + installationId + "/access_tokens"))
                            .willReturn(
                                    aResponse()
                                            .withStatus(201)
                                            .withHeader("Content-Type", "application/json")
                                            .withBody("""
                                                    {
                                                      "token": "%s",
                                                      "expires_at": "%s",
                                                      "permissions": {
                                                        "contents": "read",
                                                        "metadata": "read"
                                                      },
                                                      "repository_selection": "selected"
                                                    }
                                                    """.formatted(tokenValue, expiry))));
        }

        @Test
        @DisplayName("successfully retrieves installation token for organization")
        void successfullyObtainsInstallationToken() {
            stubSuccessfulInstallation(
                    "my-org",
                    78910L,
                    "ghs_16C7e42F292c6912E7710c838347Ae178B4a",
                    "2026-11-21T14:12:31Z");

            GitHubAppInstallationTokenProvider.InstallationToken token = obtainToken(pkcs1Pem, "my-org");

            assertThat(token).isNotNull();
            assertThat(token.token()).isEqualTo("ghs_16C7e42F292c6912E7710c838347Ae178B4a");
            assertThat(token.expiresAt()).isEqualTo(Instant.parse("2026-11-21T14:12:31Z"));

            // Verify that requests included Bearer Authorization header
            wireMockServer.verify(
                    getRequestedFor(urlEqualTo("/app"))
                            .withHeader("Authorization", matching("Bearer .+")));
            wireMockServer.verify(
                    getRequestedFor(urlEqualTo("/orgs/my-org/installation"))
                            .withHeader("Authorization", matching("Bearer .+")));
            // Verify the token request scopes the requested permissions and repositories
            wireMockServer.verify(
                    postRequestedFor(urlEqualTo("/app/installations/78910/access_tokens"))
                            .withHeader("Authorization", matching("Bearer .+"))
                            .withRequestBody(matchingJsonPath("$.permissions.contents", equalTo("read")))
                            .withRequestBody(matchingJsonPath("$.permissions.metadata", equalTo("read")))
                            .withRequestBody(matchingJsonPath("$.repositories[0]", equalTo("my-repo"))));
        }

        @Test
        @DisplayName("works when using PKCS#8 private key PEM")
        void successfullyObtainsTokenWithPkcs8Pem() {
            stubSuccessfulInstallation(
                    "my-org",
                    78910L,
                    "ghs_16C7e42F292c6912E7710c838347Ae178B4a",
                    "2026-11-21T14:12:31Z");

            GitHubAppInstallationTokenProvider.InstallationToken token = obtainToken(pkcs8Pem, "my-org");

            assertThat(token).isNotNull();
            assertThat(token.token()).isEqualTo("ghs_16C7e42F292c6912E7710c838347Ae178B4a");
            assertThat(token.expiresAt()).isEqualTo(Instant.parse("2026-11-21T14:12:31Z"));
        }

        @Test
        @DisplayName("throws InstallationNotFoundException when organization installation not found (404)")
        void throwsWhenInstallationNotFound() {
            wireMockServer.stubFor(
                    get(urlEqualTo("/orgs/unknown-org/installation"))
                            .willReturn(
                                    aResponse()
                                            .withStatus(404)
                                            .withHeader("Content-Type", "application/json")
                                            .withBody("""
                                                    {
                                                      "message": "Not Found",
                                                      "documentation_url": "https://docs.github.com/rest"
                                                    }
                                                    """)));

            assertThatThrownBy(() -> obtainToken(pkcs1Pem, "unknown-org"))
                    .isInstanceOf(GitHubAppInstallationNotFoundException.class)
                    .isInstanceOf(GitHubAppException.class)
                    .hasMessageContaining(
                            "GitHub App (id=123456) installation not found for organization: unknown-org");
        }

        @Test
        @DisplayName("throws GitHubAppAuthenticationException, not installation-not-found, when the App itself is unknown (404 on /app)")
        void throwsAuthenticationExceptionWhenAppNotFound() {
            wireMockServer.stubFor(
                    get(urlEqualTo("/app"))
                            .willReturn(
                                    aResponse().withStatus(404)
                                            .withHeader("Content-Type", "application/json")
                                            .withBody("{\"message\": \"Not Found\"}")));

            assertThatThrownBy(() -> obtainToken(pkcs1Pem, "my-org"))
                    .isInstanceOf(GitHubAppAuthenticationException.class);
        }

        @Test
        @DisplayName("a 5xx while authenticating as the App is a transient error, not an authentication failure")
        void throwsTransientWhenAppAuthReturns500() {
            wireMockServer.stubFor(
                    get(urlEqualTo("/app"))
                            .willReturn(
                                    aResponse().withStatus(500)
                                            .withHeader("Content-Type", "application/json")
                                            .withBody("{\"message\": \"Internal Server Error\"}")));

            assertThatThrownBy(() -> obtainToken(pkcs1Pem, "my-org"))
                    .isInstanceOf(GitHubAppTransientException.class)
                    // A 5xx must not be reported as a permanent authentication failure.
                    .isNotInstanceOf(GitHubAppAuthenticationException.class)
                    .hasMessageContaining("failed due to a transient error");
        }

        @Test
        @DisplayName("a 5xx during token creation is a transient error, not a token-minting failure")
        void throwsTransientWhenTokenCreationReturns500() {
            String baseUrl = getBaseUrl();
            wireMockServer.stubFor(
                    get(urlEqualTo("/orgs/transient-token-org/installation"))
                            .willReturn(
                                    aResponse()
                                            .withStatus(200)
                                            .withHeader("Content-Type", "application/json")
                                            .withBody("""
                                                    {
                                                      "id": 44444,
                                                      "account": { "login": "transient-token-org", "id": 5 },
                                                      "app_id": 123456,
                                                      "access_tokens_url": "%s/app/installations/44444/access_tokens"
                                                    }
                                                    """.formatted(baseUrl))));
            wireMockServer.stubFor(
                    post(urlEqualTo("/app/installations/44444/access_tokens"))
                            .willReturn(
                                    aResponse()
                                            .withStatus(500)
                                            .withHeader("Content-Type", "application/json")
                                            .withBody("{\"message\": \"Internal Server Error\"}")));

            assertThatThrownBy(() -> obtainToken(pkcs1Pem, "transient-token-org"))
                    .isInstanceOf(GitHubAppTransientException.class)
                    .isNotInstanceOf(GitHubAppInstallationTokenException.class)
                    .hasMessageContaining("due to a transient error");
        }

        @Test
        @DisplayName("throws GitHubAppInstallationTokenException when token creation fails with 401")
        void throwsWhenTokenCreationFails() {
            String baseUrl = getBaseUrl();
            wireMockServer.stubFor(
                    get(urlEqualTo("/orgs/auth-fail-org/installation"))
                            .willReturn(
                                    aResponse()
                                            .withStatus(200)
                                            .withHeader("Content-Type", "application/json")
                                            .withBody("""
                                                    {
                                                      "id": 99999,
                                                      "account": {
                                                        "login": "auth-fail-org",
                                                        "id": 3
                                                      },
                                                      "app_id": 123456,
                                                      "access_tokens_url": "%s/app/installations/99999/access_tokens"
                                                    }
                                                    """.formatted(baseUrl))));

            wireMockServer.stubFor(
                    post(urlEqualTo("/app/installations/99999/access_tokens"))
                            .willReturn(
                                    aResponse()
                                            .withStatus(401)
                                            .withHeader("Content-Type", "application/json")
                                            .withBody("""
                                                    {
                                                      "message": "Bad credentials"
                                                    }
                                                    """)));

            assertThatThrownBy(() -> obtainToken(pkcs1Pem, "auth-fail-org"))
                    .isInstanceOf(GitHubAppInstallationTokenException.class)
                    .isInstanceOf(GitHubAppException.class)
                    .hasMessageContaining("Failed to obtain an installation token for organization: auth-fail-org")
                    .hasCauseInstanceOf(IOException.class);
        }

        @Test
        @DisplayName("a 500 during installation lookup is a transient error, not installation-not-found")
        void throwsWhenGitHubReturns500() {
            wireMockServer.stubFor(
                    get(urlEqualTo("/orgs/server-err-org/installation"))
                            .willReturn(
                                    aResponse()
                                            .withStatus(500)
                                            .withHeader("Content-Type", "application/json")
                                            .withBody("""
                                                    {
                                                      "message": "Internal Server Error"
                                                    }
                                                    """)));

            assertThatThrownBy(() -> obtainToken(pkcs1Pem, "server-err-org"))
                    .isInstanceOf(GitHubAppTransientException.class)
                    // A 5xx must not be misreported as a missing installation (that is reserved for a genuine 404).
                    .isNotInstanceOf(GitHubAppInstallationNotFoundException.class)
                    .hasMessageContaining(
                            "Failed to look up the GitHub App (id=123456) installation for organization: server-err-org")
                    .hasCauseInstanceOf(IOException.class);

            wireMockServer.verify(1, getRequestedFor(urlEqualTo("/orgs/server-err-org/installation")));
        }

        @Test
        @DisplayName("a 403 during installation lookup is a permanent error, not transient and not installation-not-found")
        void throwsPermanentWhenInstallationLookupReturns403() {
            wireMockServer.stubFor(
                    get(urlEqualTo("/orgs/forbidden-org/installation"))
                            .willReturn(
                                    aResponse()
                                            .withStatus(403)
                                            .withHeader("Content-Type", "application/json")
                                            .withBody("{\"message\":\"Forbidden\"}")));

            assertThatThrownBy(() -> obtainToken(pkcs1Pem, "forbidden-org"))
                    .isInstanceOf(GitHubAppException.class)
                    // A 4xx other than 404 is permanent: it must not be retried (transient) nor treated as missing.
                    .isNotInstanceOf(GitHubAppTransientException.class)
                    .isNotInstanceOf(GitHubAppInstallationNotFoundException.class)
                    .hasMessageContaining(
                            "Failed to look up the GitHub App (id=123456) installation for organization: forbidden-org");
        }

        @Test
        @DisplayName("throws GitHubAppInstallationTokenException when token response has no expiry")
        void throwsWhenTokenHasNoExpiry() {
            String baseUrl = getBaseUrl();
            wireMockServer.stubFor(
                    get(urlEqualTo("/orgs/no-expiry-org/installation"))
                            .willReturn(
                                    aResponse()
                                            .withStatus(200)
                                            .withHeader("Content-Type", "application/json")
                                            .withBody("""
                                                    {
                                                      "id": 55555,
                                                      "account": {
                                                        "login": "no-expiry-org",
                                                        "id": 4
                                                      },
                                                      "app_id": 123456,
                                                      "access_tokens_url": "%s/app/installations/55555/access_tokens"
                                                    }
                                                    """.formatted(baseUrl))));

            // 201 response intentionally omits "expires_at"
            wireMockServer.stubFor(
                    post(urlEqualTo("/app/installations/55555/access_tokens"))
                            .willReturn(
                                    aResponse()
                                            .withStatus(201)
                                            .withHeader("Content-Type", "application/json")
                                            .withBody("""
                                                    {
                                                      "token": "ghs_noexpiry",
                                                      "permissions": {
                                                        "contents": "read",
                                                        "metadata": "read"
                                                      },
                                                      "repository_selection": "selected"
                                                    }
                                                    """)));

            assertThatThrownBy(() -> obtainToken(pkcs1Pem, "no-expiry-org"))
                    .isInstanceOf(GitHubAppInstallationTokenException.class)
                    .isInstanceOf(GitHubAppException.class)
                    .hasMessageContaining("without an expiry for organization: no-expiry-org");
        }
    }
}
