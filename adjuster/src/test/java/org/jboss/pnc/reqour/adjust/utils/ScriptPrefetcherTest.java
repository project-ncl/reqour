/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.adjust.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.jboss.pnc.reqour.adjust.exception.AdjusterException;
import org.jboss.pnc.reqour.config.ConfigUtils;
import org.jboss.pnc.reqour.config.GitProviderConfig;
import org.jboss.pnc.reqour.service.translation.GitProvider;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;

import com.sun.net.httpserver.HttpServer;

class ScriptPrefetcherTest {

    @Test
    void convertToApiUri_standardRawUrl_convertsCorrectly() throws Exception {
        URI input = new URI(
                "https://github.ibm.com/pnc-prod/micrometer-metrics-micrometer/raw/1.15.12.redhat-1/groovy/productize.groovy");

        URI result = ScriptPrefetcher.convertToApiUri(input);

        assertThat(result.getScheme()).isEqualTo("https");
        assertThat(result.getHost()).isEqualTo("github.ibm.com");
        assertThat(result.getPort()).isEqualTo(-1);
        assertThat(result.getPath())
                .isEqualTo("/api/v3/repos/pnc-prod/micrometer-metrics-micrometer/contents/groovy/productize.groovy");
        assertThat(result.getQuery()).isEqualTo("ref=1.15.12.redhat-1");
    }

    @Test
    void convertToApiUri_nestedFilePath_convertsCorrectly() throws Exception {
        URI input = new URI("https://github.ibm.com/org/repo/raw/main/src/scripts/align.groovy");

        URI result = ScriptPrefetcher.convertToApiUri(input);

        assertThat(result.getPath()).isEqualTo("/api/v3/repos/org/repo/contents/src/scripts/align.groovy");
        assertThat(result.getQuery()).isEqualTo("ref=main");
    }

    @Test
    void convertToApiUri_urlWithPort_portIsPreserved() throws Exception {
        URI input = new URI("http://localhost:8080/org/repo/raw/main/file.groovy");

        URI result = ScriptPrefetcher.convertToApiUri(input);

        assertThat(result.getHost()).isEqualTo("localhost");
        assertThat(result.getPort()).isEqualTo(8080);
        assertThat(result.getPath()).isEqualTo("/api/v3/repos/org/repo/contents/file.groovy");
        assertThat(result.getQuery()).isEqualTo("ref=main");
    }

    @Test
    void convertToApiUri_refsHeadsRef_fullRefPreserved() throws Exception {
        URI input = new URI(
                "https://github.ibm.com/pnc-prod/tpolacek-empty/raw/refs/heads/main/scripts/info.groovy");

        URI result = ScriptPrefetcher.convertToApiUri(input);

        assertThat(result.getPath())
                .isEqualTo("/api/v3/repos/pnc-prod/tpolacek-empty/contents/scripts/info.groovy");
        assertThat(result.getQuery()).isEqualTo("ref=refs/heads/main");
    }

    @Test
    void convertToApiUri_refsTagsRef_fullRefPreserved() throws Exception {
        URI input = new URI("https://github.ibm.com/org/repo/raw/refs/tags/v1.0/path/file.groovy");

        URI result = ScriptPrefetcher.convertToApiUri(input);

        assertThat(result.getPath()).isEqualTo("/api/v3/repos/org/repo/contents/path/file.groovy");
        assertThat(result.getQuery()).isEqualTo("ref=refs/tags/v1.0");
    }

    @Test
    void convertToApiUri_noRawSegment_throwsException() throws Exception {
        URI input = new URI("https://github.ibm.com/org/repo/blob/main/file.groovy");

        assertThatThrownBy(() -> ScriptPrefetcher.convertToApiUri(input)).isInstanceOf(AdjusterException.class)
                .hasMessageContaining("does not match expected format");
    }

    @Test
    void convertToApiUri_rawButTooFewSegments_throwsException() throws Exception {
        URI input = new URI("https://github.ibm.com/raw/ref");

        assertThatThrownBy(() -> ScriptPrefetcher.convertToApiUri(input)).isInstanceOf(AdjusterException.class);
    }

    @Test
    void prefetchRemoteScripts_multipleCommaSeparatedUrls_allPrefetched() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        int port = server.getAddress().getPort();
        String hostname = "localhost";

        server.createContext("/api/v3/repos/org/repo/contents/script1.groovy", exchange -> {
            byte[] body = "// script1 content".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.createContext("/api/v3/repos/org/repo/contents/script2.groovy", exchange -> {
            byte[] body = "// script2 content".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();

        Path workdir = Files.createTempDirectory("prefetch-test");
        try {
            ScriptPrefetcher prefetcher = createPrefetcher(hostname, "test-token");

            String url1 = String.format("http://%s:%d/org/repo/raw/main/script1.groovy", hostname, port);
            String url2 = String.format("http://%s:%d/org/repo/raw/main/script2.groovy", hostname, port);
            List<String> params = List.of("-DgroovyScripts=" + url1 + "," + url2, "-DrestMode=PERSISTENT");

            List<String> result = prefetcher.prefetchRemoteScripts(params, workdir);

            assertThat(result).hasSize(2);
            assertThat(result.get(0)).startsWith("-DgroovyScripts=file:");
            assertThat(result.get(0)).contains(",");
            String[] rewrittenUrls = result.get(0).substring("-DgroovyScripts=".length()).split(",");
            assertThat(rewrittenUrls).hasSize(2);
            assertThat(rewrittenUrls[0]).startsWith("file:");
            assertThat(rewrittenUrls[1]).startsWith("file:");
            assertThat(Files.readString(Path.of(URI.create(rewrittenUrls[0])))).isEqualTo("// script1 content");
            assertThat(Files.readString(Path.of(URI.create(rewrittenUrls[1])))).isEqualTo("// script2 content");
            assertThat(result.get(1)).isEqualTo("-DrestMode=PERSISTENT");
        } finally {
            server.stop(0);
            IOUtils.deleteRecursively(workdir);
        }
    }

    @Test
    void prefetchRemoteScripts_mixedMatchingAndNonMatchingUrls_onlyMatchingPrefetched() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        int port = server.getAddress().getPort();
        String hostname = "localhost";

        server.createContext("/api/v3/repos/org/repo/contents/script1.groovy", exchange -> {
            byte[] body = "// script1".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();

        Path workdir = Files.createTempDirectory("prefetch-test");
        try {
            ScriptPrefetcher prefetcher = createPrefetcher(hostname, "test-token");

            String matchingUrl = String.format("http://%s:%d/org/repo/raw/main/script1.groovy", hostname, port);
            String nonMatchingUrl = "https://other-host.com/org/repo/raw/main/script2.groovy";
            List<String> params = List.of("-DgroovyScripts=" + matchingUrl + "," + nonMatchingUrl);

            List<String> result = prefetcher.prefetchRemoteScripts(params, workdir);

            assertThat(result).hasSize(1);
            String[] rewrittenUrls = result.get(0).substring("-DgroovyScripts=".length()).split(",");
            assertThat(rewrittenUrls).hasSize(2);
            assertThat(rewrittenUrls[0]).startsWith("file:");
            assertThat(rewrittenUrls[1]).isEqualTo(nonMatchingUrl);
        } finally {
            server.stop(0);
            IOUtils.deleteRecursively(workdir);
        }
    }

    @Test
    void prefetchRemoteScripts_singleGroovyScriptUrl_prefetched() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        int port = server.getAddress().getPort();
        String hostname = "localhost";

        server.createContext("/api/v3/repos/org/repo/contents/align.groovy", exchange -> {
            byte[] body = "// align".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();

        Path workdir = Files.createTempDirectory("prefetch-test");
        try {
            ScriptPrefetcher prefetcher = createPrefetcher(hostname, "test-token");

            String url = String.format("http://%s:%d/org/repo/raw/main/align.groovy", hostname, port);
            List<String> params = List.of("-DgroovyScript=" + url);

            List<String> result = prefetcher.prefetchRemoteScripts(params, workdir);

            assertThat(result).hasSize(1);
            assertThat(result.get(0)).startsWith("-DgroovyScript=file:");
            assertThat(result.get(0)).doesNotContain(",");
        } finally {
            server.stop(0);
            IOUtils.deleteRecursively(workdir);
        }
    }

    private static ScriptPrefetcher createPrefetcher(String hostname, String token) {
        GitProviderConfig gitProviderConfig = Mockito.mock(GitProviderConfig.class);
        Mockito.when(gitProviderConfig.hostname()).thenReturn(hostname);
        Mockito.when(gitProviderConfig.token()).thenReturn(token);

        ConfigUtils configUtils = Mockito.mock(ConfigUtils.class);
        Mockito.when(configUtils.getActiveGitProvider()).thenReturn(GitProvider.GITHUB);
        Mockito.when(configUtils.getActiveGitProviderConfig()).thenReturn(gitProviderConfig);

        return new ScriptPrefetcher(configUtils, LoggerFactory.getLogger("test-user-logger"));
    }

    private static final class IOUtils {
        static void deleteRecursively(Path path) throws IOException {
            if (Files.isDirectory(path)) {
                try (var entries = Files.list(path)) {
                    for (Path entry : entries.toList()) {
                        deleteRecursively(entry);
                    }
                }
            }
            Files.deleteIfExists(path);
        }
    }
}