/*
 * SPDX-FileCopyrightText: Copyright © 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.service.scmcontent;

import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jboss.pnc.reqour.common.exceptions.ScmContentException;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.extern.slf4j.Slf4j;

/**
 * GET requests to a git provider's REST API with reqour's token.
 * <ul>
 * <li>The token is only sent to the origin (scheme, host and port) of the first request; redirects from https to
 * http are refused.</li>
 * <li>Provider errors become {@link ScmContentException}s with a generic message (details are logged): 404 stays 404
 * (callers try the next candidate), timeouts are 504 and anything else is 502.</li>
 * <li>Bodies read into memory have a deadline and a size limit; streamed bodies are aborted when idle.</li>
 * <li>I/O errors, 5xx and 429 are retried with a backoff, honouring {@code Retry-After}; timeouts are not.</li>
 * </ul>
 */
@Slf4j
class ProviderHttp {

    /** Timeouts, limits and retry delays. */
    record Settings(
            Duration connectTimeout,
            Duration jsonTimeout,
            Duration contentTimeout,
            Duration streamIdleTimeout,
            Duration requestBudget,
            long maxBodyBytes,
            long maxFileBytes,
            long maxFetchedBytes,
            List<Duration> retryDelays,
            Duration maxRetryAfter) {

        /**
         * The whole request must end before orch and the routes give up (10 minutes); bodies are bounded for a pod
         * that also runs alignments: a JSON answer or raw diff is at most 20 MiB, a file read to fill a diff at most
         * 5 MiB, and all the files of one diff at most 20 MiB (others are left out and reported incomplete).
         */
        static final Settings DEFAULT = new Settings(
                Duration.ofSeconds(30),
                Duration.ofSeconds(30),
                Duration.ofMinutes(5),
                Duration.ofMinutes(2),
                Duration.ofMinutes(9),
                20L * 1024 * 1024,
                5L * 1024 * 1024,
                20L * 1024 * 1024,
                List.of(Duration.ofSeconds(1), Duration.ofSeconds(2)),
                Duration.ofSeconds(10));
    }

    private static final int PREVIEW_BYTES = 300;
    private static final int MAX_REDIRECTS = 5;
    private static final ScheduledExecutorService WATCHDOG = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "scm-content-watchdog");
        thread.setDaemon(true);
        return thread;
    });

    private final HttpClient client;
    private final String authHeader;
    private final String authValue;
    private final Settings settings;
    /** One instance per request: its deadline and the bytes of files it may still read. */
    private final long deadline;
    private long fetchBudget;

    ProviderHttp(HttpClient client, String authHeader, String authValue) {
        this(client, authHeader, authValue, Settings.DEFAULT);
    }

    ProviderHttp(HttpClient client, String authHeader, String authValue, Settings settings) {
        this.client = client;
        this.authHeader = authHeader;
        this.authValue = authValue;
        this.settings = settings;
        this.deadline = System.nanoTime() + settings.requestBudget().toNanos();
        this.fetchBudget = settings.maxFetchedBytes();
    }

    static HttpClient newClient() {
        return newClient(Settings.DEFAULT);
    }

    static HttpClient newClient(Settings settings) {
        return HttpClient.newBuilder()
                .connectTimeout(settings.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    JsonNode json(URI uri) {
        return json(uri, "application/json");
    }

    JsonNode json(URI uri, String accept) {
        byte[] body = read(uri, accept, settings.jsonTimeout(), settings.maxBodyBytes());
        try {
            return ProviderSupport.JSON.readTree(body);
        } catch (IOException e) {
            log.warn("Invalid JSON from {}: {}", withoutQuery(uri), e.getMessage());
            throw new ScmContentException(ScmContentException.BAD_GATEWAY, ProviderSupport.UNEXPECTED_ANSWER, e);
        }
    }

    /** A raw diff. */
    byte[] bytes(URI uri, String accept) {
        return read(uri, accept, settings.contentTimeout(), settings.maxBodyBytes());
    }

    /**
     * A file to fill a diff with; null when the provider answers 404, or when the file is over the per-file limit or
     * the rest of the request's budget (the file is then reported incomplete).
     */
    synchronized byte[] fileOrNull(URI uri, String accept) {
        long limit = Math.min(settings.maxFileBytes(), fetchBudget);
        if (limit <= 0) {
            log.warn("Budget for file contents spent, leaving out {}", withoutQuery(uri));
            return null;
        }
        try {
            byte[] content = read(uri, accept, settings.contentTimeout(), limit);
            fetchBudget -= content.length;
            return content;
        } catch (ScmContentException e) {
            if (e.getStatus() == ScmContentException.NOT_FOUND) {
                return null;
            }
            if (e.getCause() instanceof TooLarge) {
                log.warn("Leaving out {}: over {} bytes", withoutQuery(uri), limit);
                return null;
            }
            throw e;
        }
    }

    /** The body of a 200 answer, to stream; aborted when no data arrives for the idle timeout. */
    InputStream stream(URI uri, String accept) {
        return withRetries(
                uri,
                () -> new IdleTimeoutInputStream(open(uri, accept, timeout(settings.contentTimeout())).body(), uri));
    }

    private byte[] read(URI uri, String accept, Duration callTimeout, long maxBytes) {
        return withRetries(uri, () -> {
            Duration timeout = timeout(callTimeout);
            long callDeadline = System.nanoTime() + timeout.toNanos();
            HttpResponse<InputStream> response = open(uri, accept, timeout);
            long length = response.headers().firstValueAsLong("Content-Length").orElse(-1);
            if (length > maxBytes) {
                closeQuietly(response.body());
                throw tooLarge(uri, maxBytes);
            }
            return readLimited(
                    response.body(),
                    uri,
                    Duration.ofNanos(Math.max(0, callDeadline - System.nanoTime())),
                    maxBytes,
                    length);
        });
    }

    /** The call timeout, shortened to what is left of the request's budget; 504 when nothing is left. */
    private Duration timeout(Duration callTimeout) {
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0) {
            log.warn("The request budget of {} is spent", settings.requestBudget());
            throw new ScmContentException(
                    ScmContentException.GATEWAY_TIMEOUT,
                    "The git provider calls took too long");
        }
        return remaining < callTimeout.toNanos() ? Duration.ofNanos(remaining) : callTimeout;
    }

    private interface Call<T> {
        T run();
    }

    private <T> T withRetries(URI uri, Call<T> call) {
        List<Duration> delays = settings.retryDelays();
        for (int attempt = 0;; attempt++) {
            try {
                return call.run();
            } catch (ScmContentException e) {
                if (attempt >= delays.size() || !retryable(e)) {
                    throw e;
                }
                Duration delay = retryDelay(e, delays.get(attempt));
                if (System.nanoTime() + delay.toNanos() >= deadline) {
                    throw e;
                }
                log.debug(
                        "Attempt {} for {} failed ({}), retrying in {}",
                        attempt + 1,
                        withoutQuery(uri),
                        e.getMessage(),
                        delay);
                sleep(delay);
            }
        }
    }

    /** The 200 answer, following redirects. */
    private HttpResponse<InputStream> open(URI uri, String accept, Duration timeout) {
        URI current = uri;
        for (int redirects = 0; redirects <= MAX_REDIRECTS; redirects++) {
            HttpRequest.Builder request = HttpRequest.newBuilder(current)
                    .timeout(timeout)
                    .header("Accept", accept)
                    .header("User-Agent", "reqour");
            if (sameOrigin(uri, current)) {
                request.header(authHeader, authValue);
            }
            HttpResponse<InputStream> response = send(request.GET().build());
            int status = response.statusCode();
            if (status == 200) {
                return response;
            }
            String location = response.headers().firstValue("Location").orElse(null);
            if (status / 100 == 3 && location != null) {
                closeQuietly(response.body());
                URI next = current.resolve(location);
                if (!redirectAllowed(current, next)) {
                    log.warn("Refusing the redirect from {} to {}", withoutQuery(current), withoutQuery(next));
                    throw new ScmContentException(
                            ScmContentException.BAD_GATEWAY,
                            "The git provider redirected to an insecure location");
                }
                current = next;
                continue;
            }
            throw error(current, status, response);
        }
        throw new ScmContentException(ScmContentException.BAD_GATEWAY, "Too many redirects from the git provider");
    }

    private HttpResponse<InputStream> send(HttpRequest request) {
        try {
            return client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (HttpTimeoutException e) {
            log.warn("Timeout requesting {}", withoutQuery(request.uri()));
            throw new ScmContentException(
                    ScmContentException.GATEWAY_TIMEOUT,
                    "The git provider did not answer in time",
                    e);
        } catch (IOException e) {
            log.warn("I/O error requesting {}: {}", withoutQuery(request.uri()), e.getMessage());
            throw new ScmContentException(
                    ScmContentException.BAD_GATEWAY,
                    "The git provider could not be reached",
                    new ProviderStatus(0, null, e));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ScmContentException(
                    ScmContentException.BAD_GATEWAY,
                    "Interrupted while calling the git provider");
        }
    }

    /** Reads the whole body, aborting it at the deadline or when it is over the size limit. */
    private byte[] readLimited(InputStream body, URI uri, Duration timeout, long maxBytes, long length) {
        AtomicBoolean expired = new AtomicBoolean();
        ScheduledFuture<?> deadline = WATCHDOG.schedule(() -> {
            expired.set(true);
            closeQuietly(body);
        }, timeout.toMillis(), TimeUnit.MILLISECONDS);
        try (body) {
            ByteArrayOutputStream out = new ByteArrayOutputStream(length > 0 ? (int) length : 8192);
            byte[] buffer = new byte[8192];
            long total = 0;
            int read;
            while ((read = body.read(buffer)) >= 0) {
                total += read;
                if (total > maxBytes) {
                    throw tooLarge(uri, maxBytes);
                }
                out.write(buffer, 0, read);
            }
            if (expired.get()) {
                throw timeout(uri);
            }
            return out.toByteArray();
        } catch (IOException e) {
            if (expired.get()) {
                throw timeout(uri);
            }
            log.warn("I/O error reading {}: {}", withoutQuery(uri), e.getMessage());
            throw new ScmContentException(
                    ScmContentException.BAD_GATEWAY,
                    "The git provider answer could not be read",
                    new ProviderStatus(0, null, e));
        } finally {
            deadline.cancel(false);
        }
    }

    private static ScmContentException tooLarge(URI uri, long maxBytes) {
        log.warn("Answer from {} is over {} bytes", withoutQuery(uri), maxBytes);
        return new ScmContentException(
                ScmContentException.BAD_GATEWAY,
                "The git provider answer is too large",
                new TooLarge());
    }

    private static ScmContentException timeout(URI uri) {
        log.warn("Timeout reading the answer from {}", withoutQuery(uri));
        return new ScmContentException(ScmContentException.GATEWAY_TIMEOUT, "The git provider did not answer in time");
    }

    private static ScmContentException error(URI uri, int status, HttpResponse<InputStream> response) {
        String preview = preview(response.body());
        log.warn("HTTP {} from {}{}", status, withoutQuery(uri), preview.isBlank() ? "" : ": " + preview);
        return new ScmContentException(
                status == 404 ? ScmContentException.NOT_FOUND : ScmContentException.BAD_GATEWAY,
                "The git provider returned HTTP " + status,
                new ProviderStatus(status, preview, retryAfter(response)));
    }

    private static Duration retryAfter(HttpResponse<?> response) {
        try {
            return response.headers()
                    .firstValue("Retry-After")
                    .map(String::trim)
                    .map(Long::parseLong)
                    .map(Duration::ofSeconds)
                    .orElse(null);
        } catch (NumberFormatException e) {
            return null; // HTTP-date form: use the regular delay
        }
    }

    private static boolean retryable(ScmContentException e) {
        if (e.getStatus() != ScmContentException.BAD_GATEWAY || !(e.getCause() instanceof ProviderStatus status)) {
            return false;
        }
        return status.status == 0 || status.status >= 500 || status.status == 429;
    }

    private Duration retryDelay(ScmContentException e, Duration regular) {
        if (e.getCause() instanceof ProviderStatus status && status.retryAfter != null) {
            return status.retryAfter.compareTo(settings.maxRetryAfter()) > 0 ? settings.maxRetryAfter()
                    : status.retryAfter;
        }
        return regular;
    }

    /** The provider's HTTP status of a {@link ScmContentException}, or 0. */
    static int providerStatus(ScmContentException e) {
        return e.getCause() instanceof ProviderStatus status ? status.status : 0;
    }

    /** The beginning of the provider's error answer, or an empty string. */
    static String providerMessage(ScmContentException e) {
        return e.getCause() instanceof ProviderStatus status && status.preview != null ? status.preview : "";
    }

    static boolean sameOrigin(URI a, URI b) {
        return a.getScheme() != null && a.getScheme().equalsIgnoreCase(b.getScheme()) && a.getHost() != null
                && a.getHost().equalsIgnoreCase(b.getHost()) && port(a) == port(b);
    }

    /** Redirects may leave the origin (credentials are not sent there) but never downgrade https to http. */
    static boolean redirectAllowed(URI from, URI to) {
        String toScheme = to.getScheme() == null ? "" : to.getScheme().toLowerCase(Locale.ROOT);
        return toScheme.equals("https") || (toScheme.equals("http") && !"https".equalsIgnoreCase(from.getScheme()));
    }

    private static int port(URI uri) {
        if (uri.getPort() != -1) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    static String withoutQuery(URI uri) {
        String text = uri.toString();
        int query = text.indexOf('?');
        // redirect targets may carry credentials in the query
        return query < 0 || !text.contains("token=") ? text : text.substring(0, query);
    }

    private static String preview(InputStream body) {
        try (body) {
            return new String(body.readNBytes(PREVIEW_BYTES), StandardCharsets.UTF_8).replaceAll("\\s+", " ").trim();
        } catch (IOException e) {
            return "";
        }
    }

    private static void closeQuietly(InputStream in) {
        try {
            in.close();
        } catch (IOException ignored) {
            // nothing to do
        }
    }

    private static void sleep(Duration delay) {
        try {
            Thread.sleep(delay.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ScmContentException(
                    ScmContentException.BAD_GATEWAY,
                    "Interrupted while calling the git provider");
        }
    }

    /** Marks an answer over its size limit. */
    private static final class TooLarge extends RuntimeException {
        TooLarge() {
            super("too large", null, false, false);
        }
    }

    /** Status, error preview and Retry-After of a provider answer, kept out of the client-facing message. */
    private static final class ProviderStatus extends RuntimeException {
        final int status;
        final String preview;
        final Duration retryAfter;

        ProviderStatus(int status, String preview, Duration retryAfter) {
            super("HTTP " + status, null, false, false);
            this.status = status;
            this.preview = preview;
            this.retryAfter = retryAfter;
        }

        ProviderStatus(int status, String preview, Throwable cause) {
            super("HTTP " + status, cause, false, false);
            this.status = status;
            this.preview = preview;
            this.retryAfter = null;
        }
    }

    /**
     * Closes the stream when a read waits for data longer than the idle timeout, so that a stalled provider cannot hang
     * it. Only time spent inside a read counts: a slow consumer does not abort the stream.
     */
    private final class IdleTimeoutInputStream extends FilterInputStream {
        private final URI uri;
        private final AtomicBoolean expired = new AtomicBoolean();

        IdleTimeoutInputStream(InputStream in, URI uri) {
            super(in);
            this.uri = uri;
        }

        private ScheduledFuture<?> arm() {
            return WATCHDOG.schedule(() -> {
                expired.set(true);
                log.warn("No data from {} for {}, aborting", withoutQuery(uri), settings.streamIdleTimeout());
                closeQuietly(in);
            }, settings.streamIdleTimeout().toMillis(), TimeUnit.MILLISECONDS);
        }

        @Override
        public int read() throws IOException {
            ScheduledFuture<?> watchdog = arm();
            try {
                return check(super.read());
            } catch (IOException e) {
                throw expired.get() ? idle() : e;
            } finally {
                watchdog.cancel(false);
            }
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            ScheduledFuture<?> watchdog = arm();
            try {
                return check(super.read(b, off, len));
            } catch (IOException e) {
                throw expired.get() ? idle() : e;
            } finally {
                watchdog.cancel(false);
            }
        }

        private int check(int result) throws IOException {
            if (expired.get()) {
                throw idle();
            }
            return result;
        }

        private IOException idle() {
            return new IOException("No data from the git provider for " + settings.streamIdleTimeout());
        }
    }
}
