/*
 * SPDX-FileCopyrightText: Copyright © 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.service.scmcontent;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jboss.pnc.common.scm.diff.UnifiedDiff;
import org.jboss.pnc.reqour.common.exceptions.ScmContentException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import lombok.extern.slf4j.Slf4j;

/**
 * Helpers shared by the provider clients.
 */
@Slf4j
final class ProviderSupport {

    static final ObjectMapper JSON = new ObjectMapper();
    static final String UNEXPECTED_ANSWER = "The git provider returned an unexpected answer";

    private ProviderSupport() {
    }

    static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /** Encodes each segment of a path, keeping the slashes. */
    static String encodePath(String path) {
        return Stream.of(path.split("/")).map(ProviderSupport::encode).collect(Collectors.joining("/"));
    }

    static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /** The result of a call, or empty when the provider answers 404. */
    static <T> Optional<T> notFoundAsEmpty(Supplier<T> call) {
        try {
            return Optional.ofNullable(call.get());
        } catch (ScmContentException e) {
            if (e.getStatus() == ScmContentException.NOT_FOUND) {
                log.debug("Not found: {}", e.getMessage());
                return Optional.empty();
            }
            throw e;
        }
    }

    /** A provider answer without the expected diff entries is a provider error, not an empty diff. */
    static UnifiedDiff fromProviderJson(String what, Supplier<UnifiedDiff> build) {
        try {
            return build.get();
        } catch (IllegalArgumentException e) {
            log.warn("Unexpected answer to {}: {}", what, e.getMessage());
            throw new ScmContentException(ScmContentException.BAD_GATEWAY, UNEXPECTED_ANSWER, e);
        }
    }

    /** The node itself when it is a JSON array; otherwise the provider answered something else. */
    static JsonNode requireArray(JsonNode node, String what) {
        if (node == null || !node.isArray()) {
            log.warn("Expected a JSON array from {}, got {}", what, node == null ? "nothing" : node.getNodeType());
            throw new ScmContentException(ScmContentException.BAD_GATEWAY, UNEXPECTED_ANSWER);
        }
        return node;
    }

    /** All the entries of a paged list: pages are read until one has fewer than {@code perPage} entries. */
    static ArrayNode paginate(IntFunction<JsonNode> page, int perPage, String what) {
        ArrayNode all = JSON.createArrayNode();
        for (int number = 1;; number++) {
            JsonNode entries = requireArray(page.apply(number), what);
            entries.forEach(all::add);
            if (entries.size() < perPage) {
                return all;
            }
        }
    }
}
