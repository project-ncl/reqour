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
