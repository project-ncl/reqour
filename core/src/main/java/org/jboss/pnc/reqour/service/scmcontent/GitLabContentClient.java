/*
 * SPDX-FileCopyrightText: Copyright © 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.service.scmcontent;

import static org.jboss.pnc.reqour.service.scmcontent.ProviderSupport.encode;
import static org.jboss.pnc.reqour.service.scmcontent.ProviderSupport.fromProviderJson;
import static org.jboss.pnc.reqour.service.scmcontent.ProviderSupport.notFoundAsEmpty;
import static org.jboss.pnc.reqour.service.scmcontent.ProviderSupport.paginate;
import static org.jboss.pnc.reqour.service.scmcontent.ProviderSupport.requireArray;
import static org.jboss.pnc.reqour.service.scmcontent.ProviderSupport.stripTrailingSlash;

import java.io.InputStream;
import java.net.URI;
import java.util.Optional;

import org.jboss.pnc.common.scm.diff.ContentFetcher;
import org.jboss.pnc.common.scm.diff.UnifiedDiff;
import org.jboss.pnc.common.scm.diff.UnifiedDiffBuilder;
import org.jboss.pnc.reqour.common.exceptions.ScmContentException;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.extern.slf4j.Slf4j;

/**
 * GitLab REST API v4. GitLab has no raw diff in its API: the per-file JSON diffs of the compare and commit APIs are
 * rebuilt into a unified diff, with the files GitLab leaves out (binary, over its size limit) read from the raw file
 * API.
 */
@Slf4j
class GitLabContentClient implements ProviderContentClient {

    private static final int PER_PAGE = 100;
    /**
     * GitLab's default diff_max_files: a comparison with this many entries may have been cut by the instance without
     * any flag in the answer.
     */
    static final int COMPARE_MAX_FILES = 1000;

    private final ProviderHttp http;
    private final String projectUrl;

    /**
     * @param apiUrl e.g. {@code https://gitlab.cee.redhat.com/api/v4}
     * @param projectPath e.g. {@code pnc-workspace/airlift/airbase}
     */
    GitLabContentClient(ProviderHttp http, String apiUrl, String projectPath) {
        this.http = http;
        this.projectUrl = stripTrailingSlash(apiUrl) + "/projects/" + encode(projectPath);
    }

    @Override
    public Optional<InputStream> archive(String revision) {
        return notFoundAsEmpty(
                () -> http.stream(
                        URI.create(projectUrl + "/repository/archive.tar.gz?sha=" + encode(revision)),
                        "application/gzip, */*"));
    }

    /** GitLab answers 404 when there is no merge base and 400 when a ref does not exist. */
    @Override
    public Optional<Boolean> isAncestor(String base, String head) {
        return notFoundAsEmpty(() -> {
            JsonNode mergeBase = http.json(
                    URI.create(
                            projectUrl + "/repository/merge_base?refs%5B%5D=" + encode(base) + "&refs%5B%5D="
                                    + encode(head)));
            String id = mergeBase.path("id").asText("");
            // base may be a tag or branch: compare with the commit it points to
            return !id.isEmpty() && id.equals(commitId(base));
        });
    }

    @Override
    public UnifiedDiff compare(String base, String head) {
        String what = "GitLab compare " + base + ".." + head;
        JsonNode compare = http.json(
                URI.create(
                        projectUrl + "/repository/compare?from=" + encode(base) + "&to=" + encode(head)
                                + "&straight=true"));
        if (compare.path("compare_timeout").asBoolean(false)) {
            log.warn("{} timed out on the GitLab side", what);
            throw new ScmContentException(
                    ScmContentException.BAD_GATEWAY,
                    "The git provider timed out computing the comparison");
        }
        JsonNode diffs = requireArray(compare.path("diffs"), what);
        if (diffs.size() >= COMPARE_MAX_FILES) {
            log.warn("{} has {} entries, GitLab may have left files out", what, diffs.size());
            throw new ScmContentException(
                    ScmContentException.BAD_GATEWAY,
                    "The comparison has too many files for the git provider API (" + diffs.size() + " or more)");
        }
        return fromProviderJson(what, () -> UnifiedDiffBuilder.fromGitlab(diffs, fetcher(), base, head));
    }

    @Override
    public Optional<UnifiedDiff> commitDiff(String head) {
        return notFoundAsEmpty(() -> {
            String what = "GitLab commit diff " + head;
            JsonNode commit = http.json(URI.create(projectUrl + "/repository/commits/" + encode(head)));
            String parent = commit.path("parent_ids").path(0).asText(null);
            JsonNode diffs = paginate(
                    page -> http.json(
                            URI.create(
                                    projectUrl + "/repository/commits/" + encode(head) + "/diff?per_page=" + PER_PAGE
                                            + "&page=" + page)),
                    PER_PAGE,
                    what);
            return fromProviderJson(what, () -> UnifiedDiffBuilder.fromGitlab(diffs, fetcher(), parent, head));
        });
    }

    private String commitId(String ref) {
        return http.json(URI.create(projectUrl + "/repository/commits/" + encode(ref))).path("id").asText("");
    }

    private ContentFetcher fetcher() {
        return (path, ref) -> http.fileOrNull(
                URI.create(projectUrl + "/repository/files/" + encode(path) + "/raw?ref=" + encode(ref)),
                "*/*");
    }
}
