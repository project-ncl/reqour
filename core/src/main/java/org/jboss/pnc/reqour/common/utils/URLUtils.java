/*
 * Copyright 2024 Red Hat, Inc.
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
