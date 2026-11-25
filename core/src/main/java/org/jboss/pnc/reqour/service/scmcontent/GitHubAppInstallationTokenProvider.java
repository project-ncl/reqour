/*
 * SPDX-FileCopyrightText: Copyright © 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.service.scmcontent;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.StringReader;
import java.security.PrivateKey;
import java.security.interfaces.RSAPrivateKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import jakarta.enterprise.context.ApplicationScoped;

import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.jboss.pnc.reqour.common.exceptions.GitHubAppAuthenticationException;
import org.jboss.pnc.reqour.common.exceptions.GitHubAppException;
import org.jboss.pnc.reqour.common.exceptions.GitHubAppInstallationNotFoundException;
import org.jboss.pnc.reqour.common.exceptions.GitHubAppInstallationTokenException;
import org.jboss.pnc.reqour.common.exceptions.GitHubAppTransientException;
import org.jboss.pnc.reqour.common.exceptions.InvalidPrivateKeyException;
import org.jboss.pnc.reqour.config.GitHubAppInstallationTokenFaultTolerancePolicy;
import org.kohsuke.github.GHApp;
import org.kohsuke.github.GHAppInstallation;
import org.kohsuke.github.GHAppInstallationToken;
import org.kohsuke.github.GHFileNotFoundException;
import org.kohsuke.github.GHPermissionType;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.kohsuke.github.HttpException;
import org.kohsuke.github.extras.authorization.JWTTokenProvider;

import io.smallrye.faulttolerance.api.ApplyGuard;

/**
 * Obtains a GitHub App installation access token using the GitHub App id and its
 * PKCS#1 ({@code -----BEGIN RSA PRIVATE KEY-----}) or PKCS#8 ({@code -----BEGIN PRIVATE KEY-----}) private key.
 *
 * <p>
 * The public {@code getInstallationToken} methods are guarded by the
 * {@value GitHubAppInstallationTokenFaultTolerancePolicy#GITHUB_APP_INSTALLATION_TOKEN_FAULT_TOLERANCE_GUARD}
 * fault-tolerance policy, which applies retry (with exponential backoff) and a timeout. Any failure that is not
 * retried, or that remains after the retries are exhausted, is surfaced as a {@link GitHubAppException}.
 */
@ApplicationScoped
public class GitHubAppInstallationTokenProvider {

    public static final String DEFAULT_API_URL = "https://api.github.com";

    /** Installation token and its expiry. {@link #toString()} never reveals the token. */
    public record InstallationToken(String token, Instant expiresAt) {
        @Override
        public String toString() {
            return "InstallationToken[token=***, expiresAt=" + expiresAt + "]";
        }
    }

    /**
     * Obtains an installation access token using the default GitHub API URL ({@value #DEFAULT_API_URL}).
     *
     * @param appId GitHub App id; must not be {@code null} or blank
     * @param privateKeyPem PKCS#1 or PKCS#8 PEM content of the App's private key; must not be {@code null}
     * @param organization organization where the App is installed; must not be {@code null} or blank
     * @param contentsPermission access level for the repository {@code contents} permission (e.g. {@code READ});
     *        required to clone over HTTPS
     * @param metadataPermission access level for the {@code metadata} permission (e.g. {@code READ}); required for
     *        repository and installation metadata lookups (it does not grant access to the whole GitHub API)
     * @param repositories repositories the token may access, given as short names without the organization prefix
     *        (e.g. {@code reqour}, not {@code project-ncl/reqour}); must not be {@code null} or empty
     * @return the installation token and its expiry
     * @throws NullPointerException if {@code appId}, {@code privateKeyPem}, {@code organization},
     *         {@code contentsPermission}, {@code metadataPermission}, or {@code repositories} is {@code null}
     * @throws IllegalArgumentException if {@code appId} or {@code organization} is blank, or {@code repositories}
     *         is empty
     * @throws GitHubAppException if the token cannot be obtained; see
     *         {@link #getInstallationToken(String, String, String, String, GHPermissionType, GHPermissionType, List)}
     *         for the specific subtypes
     */
    @ApplyGuard(GitHubAppInstallationTokenFaultTolerancePolicy.GITHUB_APP_INSTALLATION_TOKEN_FAULT_TOLERANCE_GUARD)
    public InstallationToken getInstallationToken(
            String appId,
            String privateKeyPem,
            String organization,
            GHPermissionType contentsPermission,
            GHPermissionType metadataPermission,
            List<String> repositories) {
        return getInstallationToken(
                appId,
                privateKeyPem,
                organization,
                DEFAULT_API_URL,
                contentsPermission,
                metadataPermission,
                repositories);
    }

    /**
     * Obtains an installation access token.
     *
     * @param appId GitHub App id; must not be {@code null} or blank
     * @param privateKeyPem PKCS#1 or PKCS#8 PEM content of the App's private key; must not be {@code null}
     * @param organization organization where the App is installed; must not be {@code null} or blank
     * @param apiUrl GitHub API URL (e.g. {@code https://api.github.com} or
     *        {@code https://ghe.example.com/api/v3}); defaults to
     *        {@link #DEFAULT_API_URL} if {@code null} or blank
     * @param contentsPermission access level for the repository {@code contents} permission (e.g. {@code READ});
     *        required to clone over HTTPS
     * @param metadataPermission access level for the {@code metadata} permission (e.g. {@code READ}); required for
     *        repository and installation metadata lookups (it does not grant access to the whole GitHub API)
     * @param repositories repositories the token may access, given as short names without the organization prefix
     *        (e.g. {@code reqour}, not {@code project-ncl/reqour}); must not be {@code null} or empty
     * @return the installation token and its expiry
     * @throws NullPointerException if {@code appId}, {@code privateKeyPem}, {@code organization},
     *         {@code contentsPermission}, {@code metadataPermission}, or {@code repositories} is {@code null}
     * @throws IllegalArgumentException if {@code appId} or {@code organization} is blank, or {@code repositories}
     *         is empty
     * @throws InvalidPrivateKeyException if {@code privateKeyPem} is not a valid PKCS#1/PKCS#8 RSA private key
     * @throws GitHubAppAuthenticationException if authenticating as the App fails permanently (e.g. a 4xx such as bad
     *         credentials or an unknown App)
     * @throws GitHubAppInstallationNotFoundException if the App is not installed on {@code organization}
     * @throws GitHubAppInstallationTokenException if the token cannot be minted for a permanent reason (e.g. a 4xx) or
     *         is returned without an expiry
     * @throws GitHubAppTransientException if any step fails for a transient reason (a 5xx or transport error) worth
     *         retrying
     * @throws GitHubAppException if looking up the installation fails for a permanent reason other than a missing
     *         installation (e.g. a 403)
     */
    @ApplyGuard(GitHubAppInstallationTokenFaultTolerancePolicy.GITHUB_APP_INSTALLATION_TOKEN_FAULT_TOLERANCE_GUARD)
    public InstallationToken getInstallationToken(
            String appId,
            String privateKeyPem,
            String organization,
            String apiUrl,
            GHPermissionType contentsPermission,
            GHPermissionType metadataPermission,
            List<String> repositories) {

        Objects.requireNonNull(appId, "appId");

        if (appId.isBlank()) {
            throw new IllegalArgumentException("appId must not be blank");
        }

        Objects.requireNonNull(privateKeyPem, "privateKeyPem");
        Objects.requireNonNull(organization, "organization");
        Objects.requireNonNull(contentsPermission, "contentsPermission");
        Objects.requireNonNull(metadataPermission, "metadataPermission");
        Objects.requireNonNull(repositories, "repositories");

        if (organization.isBlank()) {
            throw new IllegalArgumentException("organization must not be blank");
        }

        if (repositories.isEmpty()) {
            throw new IllegalArgumentException("repositories must not be empty");
        }

        String endpoint = resolveEndpoint(apiUrl);
        PrivateKey privateKey = parsePrivateKeyPem(privateKeyPem);

        GitHub gitHubAsApp;
        GHApp app;
        try {
            // The app JWT (valid max 10 minutes) is used only to authenticate as the App.
            gitHubAsApp = new GitHubBuilder()
                    .withEndpoint(endpoint)
                    .withAuthorizationProvider(new JWTTokenProvider(appId, privateKey))
                    .build();

            app = gitHubAsApp.getApp();
        } catch (IOException | RuntimeException e) {
            if (isTransient(e)) {
                // A 5xx or a transport error: GitHub may be down, not necessarily bad credentials. Let the guard retry.
                throw new GitHubAppTransientException(
                        "Authenticating as the GitHub App (id=" + appId + ") failed due to a transient error",
                        e);
            }
            // A 4xx (e.g. 401/403): the credentials or App id are wrong; retrying will not help.
            throw new GitHubAppAuthenticationException("Authentication as the GitHub App (id=" + appId + ") failed", e);
        }

        GHAppInstallation installation;
        try {
            installation = app.getInstallationByOrganization(organization);
        } catch (GHFileNotFoundException e) {
            // 404: the App authenticated fine (getApp() succeeded above) but is not installed on this organization.
            throw new GitHubAppInstallationNotFoundException(
                    "GitHub App (id=" + appId + ") installation not found for organization: " + organization,
                    e);
        } catch (IOException e) {
            String message = "Failed to look up the GitHub App (id=" + appId + ") installation for organization: "
                    + organization;
            if (isTransient(e)) {
                // A 5xx or a transport error, not a missing installation: a transient failure worth retrying.
                throw new GitHubAppTransientException(message + " due to a transient error", e);
            }
            // A permanent non-404 failure (e.g. a 403): retrying will not help.
            throw new GitHubAppException(message, e);
        }

        Map<String, GHPermissionType> installationTokenPermissions = Map
                .of("contents", contentsPermission, "metadata", metadataPermission);
        try {
            GHAppInstallationToken installationToken = installation.createToken(installationTokenPermissions)
                    .repositories(repositories)
                    .create();
            Date expiresAt = installationToken.getExpiresAt();
            if (expiresAt == null) {
                throw new GitHubAppInstallationTokenException(
                        "GitHub returned an installation token without an expiry for organization: " + organization);
            }
            return new InstallationToken(installationToken.getToken(), expiresAt.toInstant());
        } catch (IOException e) {
            if (isTransient(e)) {
                // A 5xx or a transport error: a transient failure worth retrying, not a credentials/permission problem.
                throw new GitHubAppTransientException(
                        "Failed to obtain an installation token for organization: " + organization
                                + " due to a transient error",
                        e);
            }
            // A 4xx (e.g. 401/403): bad credentials or insufficient permissions; retrying will not help.
            throw new GitHubAppInstallationTokenException(
                    "Failed to obtain an installation token for organization: " + organization,
                    e);
        }
    }

    /**
     * Classifies a GitHub call failure as transient (worth retrying) or permanent. A transport-level error or a 5xx
     * server response is transient; a 4xx response (e.g. a 401 for bad credentials or a 404 for a missing resource) is
     * permanent, since retrying it would only repeat the same failure.
     */
    private static boolean isTransient(Throwable e) {
        for (Throwable current = e; current != null; current = current.getCause()) {
            if (current instanceof HttpException httpException) {
                int responseCode = httpException.getResponseCode();
                // A non-positive code means the status line could not be read, i.e. a transport-level failure.
                return responseCode <= 0 || responseCode >= 500;
            }
            if (current instanceof FileNotFoundException) {
                // kohsuke surfaces a 404 as a FileNotFoundException rather than an HttpException.
                return false;
            }
        }
        // No HTTP status anywhere in the chain: a plain IOException is a transport error (timeout, connection
        // refused, ...) and is transient; anything else (e.g. a RuntimeException while signing the JWT) is permanent.
        return e instanceof IOException;
    }

    /** Resolves the API endpoint, falling back to {@link #DEFAULT_API_URL} when {@code apiUrl} is null or blank. */
    String resolveEndpoint(String apiUrl) {
        return (apiUrl == null || apiUrl.isBlank()) ? DEFAULT_API_URL : apiUrl;
    }

    /** Parses an RSA private key from a PKCS#1 or PKCS#8 PEM string. */
    PrivateKey parsePrivateKeyPem(String pem) {
        if (pem == null) {
            throw new InvalidPrivateKeyException("Private key PEM must not be null");
        }
        // Secrets/env vars often store the PEM with literal "\n" (and "\r\n") escape sequences rather than real line
        // breaks; PEMParser needs actual newlines, so normalize them back before parsing.
        String normalizedPem = pem.replace("\\r\\n", "\n").replace("\\n", "\n");
        try (PEMParser parser = new PEMParser(new StringReader(normalizedPem))) {
            Object parsed = parser.readObject();
            JcaPEMKeyConverter converter = new JcaPEMKeyConverter();
            PrivateKey key;
            if (parsed instanceof PEMKeyPair keyPair) { // "BEGIN RSA PRIVATE KEY" (PKCS#1)
                key = converter.getPrivateKey(keyPair.getPrivateKeyInfo());
            } else if (parsed instanceof PrivateKeyInfo info) { // "BEGIN PRIVATE KEY" (PKCS#8)
                key = converter.getPrivateKey(info);
            } else {
                throw new InvalidPrivateKeyException(
                        "Unsupported or invalid private key PEM: expected PKCS#1 or PKCS#8 format");
            }
            // GitHub signs App JWTs with RS256; a non-RSA PKCS#8 key would otherwise only fail later, with an
            // unchecked exception, when the JWT is signed.
            if (!(key instanceof RSAPrivateKey)) {
                throw new InvalidPrivateKeyException("Unsupported private key algorithm: " + key.getAlgorithm());
            }
            return key;
        } catch (InvalidPrivateKeyException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new InvalidPrivateKeyException("Failed to parse private key PEM: " + e.getMessage(), e);
        }
    }
}
