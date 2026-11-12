/*
 * SPDX-FileCopyrightText: Copyright © 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.service.scmcontent;

import static org.jboss.pnc.reqour.service.scmcontent.ProviderSupport.encode;
import static org.jboss.pnc.reqour.service.scmcontent.ProviderSupport.encodePath;
import static org.jboss.pnc.reqour.service.scmcontent.ProviderSupport.fromProviderJson;
import static org.jboss.pnc.reqour.service.scmcontent.ProviderSupport.notFoundAsEmpty;
import static org.jboss.pnc.reqour.service.scmcontent.ProviderSupport.paginate;
import static org.jboss.pnc.reqour.service.scmcontent.ProviderSupport.requireArray;
import static org.jboss.pnc.reqour.service.scmcontent.ProviderSupport.stripTrailingSlash;

import java.io.InputStream;
import java.net.URI;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.jboss.pnc.common.scm.diff.ContentFetcher;
import org.jboss.pnc.common.scm.diff.ModeResolver;
import org.jboss.pnc.common.scm.diff.UnifiedDiff;
import org.jboss.pnc.common.scm.diff.UnifiedDiffBuilder;
import org.jboss.pnc.reqour.common.exceptions.ScmContentException;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.extern.slf4j.Slf4j;

/**
 * GitHub (Enterprise) REST API. Diffs come from the diff media type, with the binary files GitHub leaves out read from
 * the contents API. When GitHub refuses the diff as too large, it is rebuilt from the JSON file list instead (modes
 * from the trees API).
 */
@Slf4j
class GitHubContentClient implements ProviderContentClient {

    static final String JSON_MEDIA_TYPE = "application/vnd.github+json";
    static final String DIFF_MEDIA_TYPE = "application/vnd.github.diff";
    private static final String RAW_MEDIA_TYPE = "application/vnd.github.raw";
    /**
     * GitHub lists at most this many files of a comparison, on its first page only (compare pages are pages of
     * commits), so a larger comparison cannot be rebuilt from the file list.
     */
    private static final int COMPARE_FILES_LIMIT = 300;
    private static final int PER_PAGE = 100;

    private final ProviderHttp http;
    private final String repoUrl;

    /**
     * @param apiUrl the API base URL, as configured for the provider: {@code https://<host>/api/v3} for GitHub
     *        Enterprise, {@code https://api.github.com} for github.com
     */
    GitHubContentClient(ProviderHttp http, String apiUrl, String owner, String repository) {
        this.http = http;
        this.repoUrl = stripTrailingSlash(apiUrl) + "/repos/" + encode(owner) + "/" + encode(repository);
    }

    @Override
    public Optional<InputStream> archive(String revision) {
        return notFoundAsEmpty(() -> http.stream(URI.create(repoUrl + "/tarball/" + encode(revision)), "*/*"));
    }

    /** GitHub answers 404 for unrelated histories and for refs that do not exist. */
    @Override
    public Optional<Boolean> isAncestor(String base, String head) {
        return notFoundAsEmpty(() -> {
            String status = json(compareUri(base, head, "?per_page=1")).path("status").asText("");
            return status.equals("ahead") || status.equals("identical");
        });
    }

    @Override
    public UnifiedDiff compare(String base, String head) {
        String what = "GitHub compare " + base + "..." + head;
        try {
            byte[] raw = http.bytes(compareUri(base, head, ""), DIFF_MEDIA_TYPE);
            return UnifiedDiffBuilder.fillBinaryFiles(raw, fetcher(), base, head);
        } catch (ScmContentException e) {
            if (!tooLarge(e)) {
                throw e;
            }
            log.info("GitHub refused the diff {}...{} as too large, rebuilding it from the file list", base, head);
        }
        JsonNode files = requireArray(json(compareUri(base, head, "")).path("files"), what);
        if (files.size() >= COMPARE_FILES_LIMIT) {
            throw new ScmContentException(
                    ScmContentException.BAD_GATEWAY,
                    "The comparison has too many files for the git provider API (" + files.size() + " or more)");
        }
        return fromProviderJson(what, () -> UnifiedDiffBuilder.fromGithub(files, fetcher(), modes(), base, head));
    }

    @Override
    public Optional<UnifiedDiff> commitDiff(String head) {
        return notFoundAsEmpty(() -> {
            String what = "GitHub commit diff " + head;
            // per_page=1 keeps the file list out of this answer: only the parent is needed here
            JsonNode commit = json(URI.create(repoUrl + "/commits/" + encode(head) + "?per_page=1"));
            String parent = commit.path("parents").path(0).path("sha").asText(null);
            try {
                byte[] raw = http.bytes(URI.create(repoUrl + "/commits/" + encode(head)), DIFF_MEDIA_TYPE);
                return UnifiedDiffBuilder.fillBinaryFiles(raw, fetcher(), parent, head);
            } catch (ScmContentException e) {
                if (!tooLarge(e)) {
                    throw e;
                }
            }
            JsonNode files = paginate(
                    page -> json(
                            URI.create(
                                    repoUrl + "/commits/" + encode(head) + "?per_page=" + PER_PAGE + "&page=" + page))
                            .path("files"),
                    PER_PAGE,
                    what);
            return fromProviderJson(
                    what,
                    () -> UnifiedDiffBuilder.fromGithub(files, fetcher(), modes(), parent, head));
        });
    }

    private JsonNode json(URI uri) {
        return http.json(uri, JSON_MEDIA_TYPE);
    }

    private URI compareUri(String base, String head, String query) {
        return URI.create(repoUrl + "/compare/" + encode(base) + "..." + encode(head) + query);
    }

    private ContentFetcher fetcher() {
        return (path, ref) -> http.fileOrNull(
                URI.create(repoUrl + "/contents/" + encodePath(path) + "?ref=" + encode(ref)),
                RAW_MEDIA_TYPE);
    }

    /** File modes from the trees API, one recursive tree per revision. */
    private ModeResolver modes() {
        Map<String, Map<String, String>> trees = new HashMap<>();
        return (path, ref) -> trees.computeIfAbsent(ref, this::tree).get(path);
    }

    private Map<String, String> tree(String ref) {
        Map<String, String> modes = new HashMap<>();
        try {
            JsonNode tree = json(URI.create(repoUrl + "/git/trees/" + encode(ref) + "?recursive=1"));
            if (tree.path("truncated").asBoolean(false)) {
                log.warn("The tree of {} is truncated; missing file modes default to 100644", ref);
            }
            tree.path("tree").forEach(entry -> modes.put(entry.path("path").asText(), entry.path("mode").asText()));
        } catch (ScmContentException e) {
            log.warn("Cannot read the tree of {}; file modes default to 100644: {}", ref, e.getMessage());
        }
        return modes;
    }

    /** GitHub answers 406 (or 422 on some versions) when a diff is over its limits. */
    private static boolean tooLarge(ScmContentException e) {
        int status = ProviderHttp.providerStatus(e);
        return status == 406
                || (status == 422 && ProviderHttp.providerMessage(e).toLowerCase(Locale.ROOT).contains("too large"));
    }
}
