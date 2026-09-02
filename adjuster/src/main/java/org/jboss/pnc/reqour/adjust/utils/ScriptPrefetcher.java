/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.utils;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.pnc.reqour.adjust.exception.AdjusterException;
import org.jboss.pnc.reqour.config.ConfigUtils;
import org.jboss.pnc.reqour.runtime.UserLogger;
import org.slf4j.Logger;

/**
 * Pre-fetches remote Groovy scripts referenced in alignment parameters. When a {@code -DgroovyScript} or
 * {@code -DgroovyScripts} parameter points to an HTTPS URL on the configured git provider, the script is downloaded
 * using the provider's token and the parameter is rewritten to a local {@code file://} path.
 */
@ApplicationScoped
public class ScriptPrefetcher {

    private static final String GROOVY_SCRIPT_PREFIX = "-DgroovyScript=";
    private static final String GROOVY_SCRIPTS_PREFIX = "-DgroovyScripts=";

    private final ConfigUtils configUtils;
    private final Logger userLogger;

    @Inject
    public ScriptPrefetcher(ConfigUtils configUtils, @UserLogger Logger userLogger) {
        this.configUtils = configUtils;
        this.userLogger = userLogger;
    }

    public List<String> prefetchRemoteScripts(List<String> params, Path workdir) {
        String gitProviderHostname = configUtils.getActiveGitProviderConfig().hostname();
        String gitProviderToken = configUtils.getActiveGitProviderConfig().token();

        List<String> result = new ArrayList<>(params.size());
        int scriptCounter = 0;
        for (String param : params) {
            String processed = tryPrefetchParam(
                    param,
                    GROOVY_SCRIPT_PREFIX,
                    gitProviderHostname,
                    gitProviderToken,
                    workdir,
                    scriptCounter);
            if (processed == null) {
                processed = tryPrefetchParam(
                        param,
                        GROOVY_SCRIPTS_PREFIX,
                        gitProviderHostname,
                        gitProviderToken,
                        workdir,
                        scriptCounter);
            }
            if (processed != null) {
                result.add(processed);
                scriptCounter++;
            } else {
                result.add(param);
            }
        }
        return result;
    }

    private String tryPrefetchParam(
            String param,
            String prefix,
            String hostname,
            String token,
            Path workdir,
            int counter) {
        if (!param.startsWith(prefix)) {
            return null;
        }

        String url = param.substring(prefix.length());
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return null;
        }

        try {
            URI uri = new URI(url);
            if (uri.getHost() == null || !uri.getHost().equals(hostname)) {
                return null;
            }
            Path localFile = downloadScript(uri, token, workdir, counter);
            userLogger.info("Pre-fetched groovy script from {} to {}", url, localFile);
            return prefix + localFile.toUri();
        } catch (URISyntaxException e) {
            throw new AdjusterException("Invalid URL in groovy script parameter: " + url, e);
        }
    }

    private static Path downloadScript(URI uri, String token, Path workdir, int counter) {
        String fileName = extractFileName(uri, counter);
        Path targetFile = workdir.resolve(fileName);

        URI apiUri = convertToApiUri(uri);

        try {
            HttpClient client = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(apiUri)
                    .header("Authorization", "token " + token)
                    .header("Accept", "application/vnd.github.raw")
                    .GET()
                    .build();
            HttpResponse<Path> response = client.send(request, HttpResponse.BodyHandlers.ofFile(targetFile));

            if (response.statusCode() != 200) {
                try {
                    Files.deleteIfExists(targetFile);
                } catch (IOException ignored) {
                }
                throw new AdjusterException(
                        String.format(
                                "Failed to download groovy script from %s: HTTP %d",
                                apiUri,
                                response.statusCode()));
            }

            return targetFile;
        } catch (IOException | InterruptedException e) {
            throw new AdjusterException("Failed to download groovy script from " + apiUri, e);
        }
    }

    /**
     * Converts a GitHub raw URL to a GitHub API contents URL.
     *
     * Input: https://github.ibm.com/org/repo/raw/ref/path/to/file.groovy
     * Output: https://github.ibm.com/api/v3/repos/org/repo/contents/path/to/file.groovy?ref=ref
     */
    static URI convertToApiUri(URI uri) {
        String path = uri.getPath();
        if (path == null || path.isBlank()) {
            throw new AdjusterException("Cannot convert URL with empty path: " + uri);
        }

        String[] segments = path.split("/");
        // Expected: ["", org, repo, "raw", ref, path...]
        int rawIndex = -1;
        for (int i = 0; i < segments.length; i++) {
            if ("raw".equals(segments[i])) {
                rawIndex = i;
                break;
            }
        }

        if (rawIndex < 2 || rawIndex + 2 >= segments.length) {
            throw new AdjusterException(
                    "URL does not match expected format /<org>/<repo>/raw/<ref>/<path>: " + uri);
        }

        String org = segments[rawIndex - 2];
        String repo = segments[rawIndex - 1];
        String ref = segments[rawIndex + 1];
        String filePath = String.join("/", java.util.Arrays.copyOfRange(segments, rawIndex + 2, segments.length));

        try {
            return new URI(
                    uri.getScheme(),
                    uri.getHost(),
                    "/api/v3/repos/" + org + "/" + repo + "/contents/" + filePath,
                    "ref=" + ref,
                    null);
        } catch (URISyntaxException e) {
            throw new AdjusterException("Failed to construct API URL from " + uri, e);
        }
    }

    private static String extractFileName(URI uri, int counter) {
        String path = uri.getPath();
        if (path != null && !path.isBlank()) {
            String name = Path.of(path).getFileName().toString();
            if (!name.isBlank()) {
                return (counter == 0) ? name : counter + "-" + name;
            }
        }
        return "groovy-script-" + counter + ".groovy";
    }
}