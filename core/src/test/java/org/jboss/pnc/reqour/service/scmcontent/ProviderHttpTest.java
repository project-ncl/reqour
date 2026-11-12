/*
 * SPDX-FileCopyrightText: Copyright © 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.service.scmcontent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.pnc.reqour.common.exceptions.ScmContentException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

class ProviderHttpTest {

    private static final ProviderHttp.Settings FAST = new ProviderHttp.Settings(
            Duration.ofSeconds(5),
            Duration.ofSeconds(5),
            Duration.ofMillis(500),
            Duration.ofMillis(500),
            Duration.ofSeconds(30),
            1024,
            100,
            150,
            List.of(Duration.ZERO, Duration.ZERO),
            Duration.ofMillis(10));

    private final List<HttpServer> servers = new CopyOnWriteArrayList<>();
    private final Map<String, String> authorizations = new ConcurrentHashMap<>();

    @AfterEach
    void stop() {
        servers.forEach(server -> server.stop(0));
    }

    @Test
    void origins() {
        assertThat(ProviderHttp.sameOrigin(URI.create("https://h/a"), URI.create("https://H:443/b"))).isTrue();
        assertThat(ProviderHttp.sameOrigin(URI.create("https://h/a"), URI.create("http://h/b"))).isFalse();
        assertThat(ProviderHttp.sameOrigin(URI.create("https://h/a"), URI.create("https://h:8443/b"))).isFalse();
        assertThat(ProviderHttp.sameOrigin(URI.create("https://h/a"), URI.create("https://other/b"))).isFalse();

        assertThat(ProviderHttp.redirectAllowed(URI.create("https://h/a"), URI.create("http://h/b"))).isFalse();
        assertThat(ProviderHttp.redirectAllowed(URI.create("https://h/a"), URI.create("https://cdn/b"))).isTrue();
        assertThat(ProviderHttp.redirectAllowed(URI.create("http://h/a"), URI.create("http://h:81/b"))).isTrue();
        assertThat(ProviderHttp.redirectAllowed(URI.create("http://h/a"), URI.create("ftp://h/b"))).isFalse();
    }

    @Test
    void theTokenIsOnlySentToTheSameOrigin() {
        HttpServer other = server("other", exchange -> send(exchange, 200, "archive"));
        HttpServer provider = server("provider", exchange -> {
            if (exchange.getRequestURI().getPath().equals("/local")) {
                redirect(exchange, "/target");
            } else if (exchange.getRequestURI().getPath().equals("/away")) {
                redirect(exchange, "http://127.0.0.1:" + other.getAddress().getPort() + "/codeload");
            } else {
                send(exchange, 200, "ok");
            }
        });

        assertThat(read(http(), url(provider, "/local"))).isEqualTo("ok");
        assertThat(authorizations).containsEntry("provider /target", "token");

        assertThat(read(http(), url(provider, "/away"))).isEqualTo("archive");
        assertThat(authorizations).containsEntry("other /codeload", "none");
    }

    @Test
    void serverErrorsAndRateLimitsAreRetried() {
        AtomicInteger calls = new AtomicInteger();
        HttpServer provider = server("provider", exchange -> {
            int call = calls.incrementAndGet();
            if (call == 1) {
                send(exchange, 503, "busy");
            } else if (call == 2) {
                exchange.getResponseHeaders().add("Retry-After", "0");
                send(exchange, 429, "slow down");
            } else {
                send(exchange, 200, "ok");
            }
        });

        assertThat(read(http(), url(provider, "/x"))).isEqualTo("ok");
        assertThat(calls).hasValue(3);
    }

    @Test
    void clientErrorsAreNotRetriedAndKeepTheProviderAnswerOutOfTheMessage() {
        AtomicInteger calls = new AtomicInteger();
        HttpServer provider = server("provider", exchange -> {
            calls.incrementAndGet();
            send(exchange, exchange.getRequestURI().getPath().equals("/missing") ? 404 : 400, "secret detail");
        });

        assertThatThrownBy(() -> read(http(), url(provider, "/bad"))).isInstanceOfSatisfying(
                ScmContentException.class,
                e -> {
                    assertThat(e.getStatus()).isEqualTo(ScmContentException.BAD_GATEWAY);
                    assertThat(e.getMessage()).isEqualTo("The git provider returned HTTP 400")
                            .doesNotContain("secret");
                    assertThat(ProviderHttp.providerStatus(e)).isEqualTo(400);
                    assertThat(ProviderHttp.providerMessage(e)).isEqualTo("secret detail");
                });
        assertThat(calls).hasValue(1);

        assertThat(http().fileOrNull(url(provider, "/missing"), "*/*")).isNull();
        assertThat(calls).hasValue(2);
    }

    @Test
    void aStalledBodyTimesOut() {
        HttpServer provider = server("provider", exchange -> {
            exchange.sendResponseHeaders(200, 0);
            OutputStream out = exchange.getResponseBody();
            out.write("partial".getBytes(StandardCharsets.UTF_8));
            out.flush();
            sleep(3000);
        });

        long start = System.nanoTime();
        assertThatThrownBy(() -> read(http(), url(provider, "/x"))).isInstanceOfSatisfying(
                ScmContentException.class,
                e -> assertThat(e.getStatus()).isEqualTo(ScmContentException.GATEWAY_TIMEOUT));
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(2));
    }

    @Test
    void bodiesOverTheLimitAreRejected() {
        HttpServer provider = server("provider", exchange -> send(exchange, 200, "x".repeat(2000)));

        assertThatThrownBy(() -> read(http(), url(provider, "/x"))).isInstanceOfSatisfying(
                ScmContentException.class,
                e -> assertThat(e.getMessage()).isEqualTo("The git provider answer is too large"));
    }

    @Test
    void anAnswerAnnouncedOverTheLimitIsRejectedBeforeReadingIt() {
        HttpServer provider = server("provider", exchange -> {
            exchange.sendResponseHeaders(200, 5000);
            exchange.getResponseBody().write(new byte[5000]);
            exchange.close();
        });

        assertThatThrownBy(() -> read(http(), url(provider, "/x"))).isInstanceOfSatisfying(
                ScmContentException.class,
                e -> assertThat(e.getMessage()).isEqualTo("The git provider answer is too large"));
    }

    @Test
    void filesOverTheFileLimitOrTheRequestBudgetAreLeftOut() {
        HttpServer provider = server(
                "provider",
                exchange -> send(
                        exchange,
                        200,
                        "x".repeat(Integer.parseInt(exchange.getRequestURI().getPath().substring(1)))));
        ProviderHttp http = http();

        assertThat(http.fileOrNull(url(provider, "/101"), "*/*")).isNull();
        assertThat(http.fileOrNull(url(provider, "/100"), "*/*")).hasSize(100);
        // 50 bytes of the budget of 150 are left
        assertThat(http.fileOrNull(url(provider, "/60"), "*/*")).isNull();
        assertThat(http.fileOrNull(url(provider, "/50"), "*/*")).hasSize(50);
        assertThat(http.fileOrNull(url(provider, "/1"), "*/*")).isNull();
        // the budget is per request
        assertThat(http().fileOrNull(url(provider, "/1"), "*/*")).hasSize(1);
    }

    @Test
    void callsEndWithTheRequestBudget() {
        ProviderHttp.Settings shortBudget = new ProviderHttp.Settings(
                FAST.connectTimeout(),
                Duration.ofSeconds(5),
                Duration.ofSeconds(5),
                FAST.streamIdleTimeout(),
                Duration.ofMillis(700),
                FAST.maxBodyBytes(),
                FAST.maxFileBytes(),
                FAST.maxFetchedBytes(),
                FAST.retryDelays(),
                FAST.maxRetryAfter());
        HttpServer provider = server("provider", exchange -> {
            sleep(3000);
            send(exchange, 200, "late");
        });
        ProviderHttp http = new ProviderHttp(
                ProviderHttp.newClient(shortBudget),
                "Authorization",
                "token",
                shortBudget);

        long start = System.nanoTime();
        assertThatThrownBy(() -> read(http, url(provider, "/x"))).isInstanceOfSatisfying(
                ScmContentException.class,
                e -> assertThat(e.getStatus()).isEqualTo(ScmContentException.GATEWAY_TIMEOUT));
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(2));
        // nothing is left for further calls
        assertThatThrownBy(() -> read(http, url(provider, "/y"))).isInstanceOfSatisfying(
                ScmContentException.class,
                e -> assertThat(e.getStatus()).isEqualTo(ScmContentException.GATEWAY_TIMEOUT));
    }

    @Test
    void aSlowConsumerDoesNotAbortAStream() throws IOException {
        HttpServer provider = server("provider", exchange -> send(exchange, 200, "x".repeat(10)));

        try (InputStream in = http().stream(url(provider, "/archive"), "*/*")) {
            assertThat(in.read()).isEqualTo('x');
            // longer than the idle timeout, spent outside of reads
            sleep(1000);
            assertThat(in.readAllBytes()).hasSize(9);
        }
    }

    @Test
    void anIdleStreamIsAborted() throws IOException {
        HttpServer provider = server("provider", exchange -> {
            exchange.sendResponseHeaders(200, 0);
            OutputStream out = exchange.getResponseBody();
            out.write(new byte[100]);
            out.flush();
            sleep(3000);
        });

        long start = System.nanoTime();
        try (InputStream in = http().stream(url(provider, "/archive"), "*/*")) {
            assertThatThrownBy(in::readAllBytes).isInstanceOf(IOException.class);
        }
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(2));
    }

    private ProviderHttp http() {
        return new ProviderHttp(ProviderHttp.newClient(FAST), "Authorization", "token", FAST);
    }

    private static String read(ProviderHttp http, URI uri) {
        return new String(http.bytes(uri, "*/*"), StandardCharsets.UTF_8);
    }

    private interface Handler {
        void handle(HttpExchange exchange) throws IOException;
    }

    private HttpServer server(String name, Handler handler) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(Executors.newCachedThreadPool(r -> {
                Thread thread = new Thread(r);
                thread.setDaemon(true);
                return thread;
            }));
            server.createContext("/", exchange -> {
                String auth = exchange.getRequestHeaders().getFirst("Authorization");
                authorizations.put(name + " " + exchange.getRequestURI().getPath(), auth == null ? "none" : auth);
                handler.handle(exchange);
            });
            server.start();
            servers.add(server);
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static URI url(HttpServer server, String path) {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path);
    }

    private static void redirect(HttpExchange exchange, String location) throws IOException {
        exchange.getResponseHeaders().add("Location", location);
        exchange.sendResponseHeaders(302, -1);
        exchange.close();
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
