/*
 * SPDX-FileCopyrightText: Copyright © 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.common.utils;

import org.jboss.pnc.api.reqour.dto.validation.GitRepositoryURLValidator;
import org.jboss.pnc.api.reqour.dto.validation.Patterns;

public class URLUtils {

    public static GitRepositoryURLValidator.ParsedURL parseURL(String url) {
        return GitRepositoryURLValidator.parseURL(url);
    }

    public static String addUsernameToUrl(String url, String username) {
        if (username == null || !isNonScpLike(url)) {
            return url;
        }

        GitRepositoryURLValidator.ParsedURL parsedURL = parseURL(url);

        if (parsedURL == null || parsedURL.getUser() != null) {
            return url;
        }

        return composeURL(
                GitRepositoryURLValidator.ParsedURL.builder()
                        .protocol(parsedURL.getProtocol())
                        .user(username)
                        .host(parsedURL.getHost())
                        .port(parsedURL.getPort())
                        .organization(parsedURL.getOrganization())
                        .repository(parsedURL.getRepository())
                        .build());
    }

    /**
     * Inject {@code username:token@} userinfo into an http(s) URL so git clones with those credentials
     * ({@code https://username:token@host/path}). Returns the URL unchanged for non-http(s) URLs (e.g.
     * scp-like or git+ssh, where token-in-URL does not apply) or when the token is missing. Any existing
     * userinfo is replaced. {@code username} defaults to {@code oauth2} when blank.
     */
    public static String addCredentialsToUrl(String url, String username, String token) {
        if (url == null || token == null || token.isBlank()) {
            return url;
        }
        if (!(url.startsWith("https://") || url.startsWith("http://"))) {
            return url;
        }
        int schemeEnd = url.indexOf("://") + 3;
        String protocolPart = url.substring(0, schemeEnd);
        String rest = url.substring(schemeEnd);
        // drop any existing "userinfo@" to avoid duplication
        int at = rest.indexOf('@');
        int firstSlash = rest.indexOf('/');
        if (at >= 0 && (firstSlash < 0 || at < firstSlash)) {
            rest = rest.substring(at + 1);
        }
        String user = (username == null || username.isBlank()) ? "oauth2" : username;
        return protocolPart + user + ":" + token + "@" + rest;
    }

    /**
     * Convert an SSH/scp-style git URL to its HTTPS equivalent, so it can be cloned over HTTPS (with a
     * token and/or through an HTTP proxy). Handles scp-like {@code git@host:group/repo.git},
     * {@code git+ssh://git@host/group/repo.git} and {@code ssh://git@host/...}. {@code http(s)://} URLs
     * (and anything not recognized as ssh/scp) are returned unchanged.
     * <p>
     * Example: {@code git@gitlab.com:lw/lw-builds/repo.git} →
     * {@code https://gitlab.com/lw/lw-builds/repo.git}.
     */
    public static String convertToHttps(String url) {
        if (url == null || url.isBlank()) {
            return url;
        }
        String s = url.trim();
        if (s.startsWith("https://") || s.startsWith("http://")) {
            return s;
        }
        // strip ssh schemes if present
        if (s.startsWith("git+ssh://")) {
            s = s.substring("git+ssh://".length());
        } else if (s.startsWith("ssh://")) {
            s = s.substring("ssh://".length());
        } else if (!s.startsWith("git@") && !s.contains("@") && !s.contains(":")) {
            // not recognizably an ssh/scp URL -> leave as is
            return url;
        }
        // strip userinfo "user@"
        int at = s.indexOf('@');
        int firstSlash = s.indexOf('/');
        if (at >= 0 && (firstSlash < 0 || at < firstSlash)) {
            s = s.substring(at + 1);
        }
        // scp-like "host:group/..." -> "host/group/..."; leave a numeric "host:port" as is
        int colon = s.indexOf(':');
        int slash = s.indexOf('/');
        if (colon >= 0 && (slash < 0 || colon < slash)) {
            String afterColon = (slash < 0) ? s.substring(colon + 1) : s.substring(colon + 1, slash);
            if (afterColon.isEmpty() || !afterColon.chars().allMatch(Character::isDigit)) {
                s = s.substring(0, colon) + "/" + s.substring(colon + 1);
            }
        }
        return "https://" + s;
    }

    private static boolean isNonScpLike(String url) {
        return Patterns.NonScpLike.PATTERN.matcher(url).matches();
    }

    private static String composeURL(GitRepositoryURLValidator.ParsedURL parsedURL) {
        StringBuilder sb = new StringBuilder();

        if (parsedURL.getProtocol() != null) {
            sb.append(parsedURL.getProtocol()).append("://");
        }

        if (parsedURL.getUser() != null) {
            sb.append(parsedURL.getUser()).append("@");
        }

        if (parsedURL.getHost() != null) {
            sb.append(parsedURL.getHost());
        }

        if (parsedURL.getPort() != -1) {
            sb.append(":").append(parsedURL.getPort());
        }

        if (parsedURL.getOrganization() != null) {
            sb.append("/").append(parsedURL.getOrganization());
        }

        sb.append("/").append(parsedURL.getRepository());

        return sb.toString();
    }
}
