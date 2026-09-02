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
import java.util.Arrays;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.pnc.reqour.adjust.exception.AdjusterException;
import org.jboss.pnc.reqour.config.ConfigUtils;
import org.jboss.pnc.reqour.runtime.UserLogger;
import org.jboss.pnc.reqour.service.translation.GitProvider;
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
    private final HttpClient httpClient;

    @Inject
    public ScriptPrefetcher(ConfigUtils configUtils, @UserLogger Logger userLogger) {
        this.configUtils = configUtils;
        this.userLogger = userLogger;
        this.httpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
    }

    public List<String> prefetchRemoteScripts(List<String> params, Path workdir) {
        if (configUtils.getActiveGitProvider() != GitProvider.GITHUB) {
            return params;
        }

        String gitProviderHostname = configUtils.getActiveGitProviderConfig().hostname();
        String gitProviderToken = configUtils.getActiveGitProviderConfig().token();

        List<String> result = new ArrayList<>(params.size());
        int scriptCounter = 0;
        for (String param : params) {
            String prefix = getGroovyScriptPrefix(param);
            if (prefix == null) {
                result.add(param);
                continue;
            }

            String[] urls = param.substring(prefix.length()).split(",");
            List<String> processedUrls = new ArrayList<>(urls.length);
            for (String url : urls) {
                String prefetched = tryPrefetchSingleUrl(
                        url,
                        gitProviderHostname,
                        gitProviderToken,
                        workdir,
                        scriptCounter);
                if (prefetched != null) {
                    processedUrls.add(prefetched);
                    scriptCounter++;
                } else {
                    processedUrls.add(url);
                }
            }
            result.add(prefix + String.join(",", processedUrls));
        }
        return result;
    }

    private static String getGroovyScriptPrefix(String param) {
        // Check the longer prefix first — "-DgroovyScripts=" starts with "-DgroovyScript="
        if (param.startsWith(GROOVY_SCRIPTS_PREFIX)) {
            return GROOVY_SCRIPTS_PREFIX;
        }
        if (param.startsWith(GROOVY_SCRIPT_PREFIX)) {
            return GROOVY_SCRIPT_PREFIX;
        }
        return null;
    }

    private String tryPrefetchSingleUrl(String url, String hostname, String token, Path workdir, int counter) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return null;
        }

        try {
            URI uri = new URI(url);
            if (uri.getHost() == null || !uri.getHost().equals(hostname)) {
                return null;
            }
            Path localFile = downloadScript(httpClient, uri, token, workdir, counter);
            userLogger.info("Pre-fetched groovy script from {} to {}", url, localFile);
            return localFile.toUri().toString();
        } catch (URISyntaxException e) {
            throw new AdjusterException("Invalid URL in groovy script parameter: " + url, e);
        }
    }

    private static Path downloadScript(HttpClient client, URI uri, String token, Path workdir, int counter) {
        String fileName = extractFileName(uri, counter);
        Path targetFile = workdir.resolve(fileName);

        URI apiUri = convertToApiUri(uri);

        try {
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
     * Examples:
     * {@code /org/repo/raw/main/file.groovy} → ref={@code main}, path={@code file.groovy}
     * {@code /org/repo/raw/v1.0/path/file.groovy} → ref={@code v1.0}, path={@code path/file.groovy}
     * {@code /org/repo/raw/refs/heads/main/file.groovy} → ref={@code refs/heads/main}, path={@code file.groovy}
     * {@code /org/repo/raw/refs/tags/v1.0/file.groovy} → ref={@code refs/tags/v1.0}, path={@code file.groovy}
     */
    static URI convertToApiUri(URI uri) {
        String path = uri.getPath();
        if (path == null || path.isBlank()) {
            throw new AdjusterException("Cannot convert URL with empty path: " + uri);
        }

        String[] segments = path.split("/");
        // Expected: ["", org, repo, "raw", ref-segments..., path-segments...]
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

        int refSegments = computeRefSegmentCount(segments, rawIndex);
        int filePathStart = rawIndex + 1 + refSegments;

        if (filePathStart >= segments.length) {
            throw new AdjusterException(
                    "URL does not contain a file path after the ref: " + uri);
        }

        String ref = String.join("/", Arrays.copyOfRange(segments, rawIndex + 1, filePathStart));
        String filePath = String.join("/", Arrays.copyOfRange(segments, filePathStart, segments.length));

        try {
            return new URI(
                    uri.getScheme(),
                    null,
                    uri.getHost(),
                    uri.getPort(),
                    "/api/v3/repos/" + org + "/" + repo + "/contents/" + filePath,
                    "ref=" + ref,
                    null);
        } catch (URISyntaxException e) {
            throw new AdjusterException("Failed to construct API URL from " + uri, e);
        }
    }

    /**
     * Determines how many path segments after "raw" belong to the ref.
     *
     * {@code refs/heads/<branch>} and {@code refs/tags/<tag>} consume 3 segments; everything else is a single-segment
     * ref (plain branch name or tag).
     */
    private static int computeRefSegmentCount(String[] segments, int rawIndex) {
        int afterRaw = rawIndex + 1;
        if (afterRaw < segments.length && "refs".equals(segments[afterRaw])) {
            int nextIndex = afterRaw + 1;
            if (nextIndex < segments.length
                    && ("heads".equals(segments[nextIndex]) || "tags".equals(segments[nextIndex]))) {
                return 3;
            }
        }
        return 1;
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