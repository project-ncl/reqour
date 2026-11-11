/*
 * SPDX-FileCopyrightText: Copyright © 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.service.scmcontent;

import java.io.InputStream;
import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.Semaphore;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.pnc.api.reqour.rest.ScmContentEndpoint;
import org.jboss.pnc.common.scm.ScmUrlNormalizer;
import org.jboss.pnc.common.scm.diff.UnifiedDiff;
import org.jboss.pnc.reqour.common.exceptions.ScmContentException;
import org.jboss.pnc.reqour.config.GitHubProviderConfig;
import org.jboss.pnc.reqour.config.GitLabProviderConfig;
import org.jboss.pnc.reqour.config.GitProviderConfig;
import org.jboss.pnc.reqour.config.ReqourCoreConfig;

import lombok.extern.slf4j.Slf4j;

/**
 * Serves source archives and complete alignment diffs of internal repositories through the git providers' REST APIs,
 * without cloning. Every configured provider is served whether or not it is the enabled one (older internal
 * repositories stay on the previous provider), but only repositories under its workspace or internal organization.
 */
@ApplicationScoped
@Slf4j
public class ScmContentService {

    /** An archive stream and the revision it was made from. */
    public record Archive(String revision, InputStream content) {
    }

    /** A complete unified diff and how it was obtained. */
    public record Diff(String base, String head, String mode, UnifiedDiff diff) {
    }

    /** Candidates per list; orch sends at most two. */
    static final int MAX_CANDIDATES = 10;

    /**
     * Diffs built at once: each one holds its files and the diff in memory (up to the fetch budgets of
     * {@link ProviderHttp}), in a pod that also runs alignments.
     */
    public static final int MAX_CONCURRENT_DIFFS = 4;
    public static final int BUSY_RETRY_AFTER_SECONDS = 30;

    private final HttpClient httpClient = ProviderHttp.newClient();
    private final Semaphore diffs = new Semaphore(MAX_CONCURRENT_DIFFS);

    @Inject
    ReqourCoreConfig config;

    public Archive archive(String url, List<String> revisions) {
        List<String> candidates = candidates(revisions, "revision");
        ProviderContentClient client = client(url);
        for (String revision : candidates) {
            Optional<InputStream> archive = client.archive(revision);
            if (archive.isPresent()) {
                log.info("Serving the archive of {} at {}", url, revision);
                return new Archive(revision, archive.get());
            }
        }
        throw new ScmContentException(
                ScmContentException.NOT_FOUND,
                "None of the revisions " + candidates + " exists in " + url);
    }

    /**
     * The diff from the first base that is an ancestor of a head to that head; when there is none, the diff of the
     * first existing head commit against its parent (alignment commits on top of an import with unrelated history).
     */
    public Diff diff(String url, List<String> bases, List<String> heads) {
        List<String> baseCandidates = candidates(bases, "base");
        List<String> headCandidates = candidates(heads, "head");
        ProviderContentClient client = client(url);
        if (!diffs.tryAcquire()) {
            throw ScmContentException.unavailable(
                    "Already building " + MAX_CONCURRENT_DIFFS + " diffs, try again later",
                    BUSY_RETRY_AFTER_SECONDS);
        }
        try {
            return diff(url, client, baseCandidates, headCandidates);
        } finally {
            diffs.release();
        }
    }

    private Diff diff(
            String url,
            ProviderContentClient client,
            List<String> baseCandidates,
            List<String> headCandidates) {
        List<String> tried = new ArrayList<>();
        for (String base : baseCandidates) {
            for (String head : headCandidates) {
                Optional<Boolean> ancestor;
                try {
                    ancestor = client.isAncestor(base, head);
                } catch (ScmContentException e) {
                    if (!refNotFound(e)) {
                        throw e;
                    }
                    tried.add(base + ".." + head + " (ref not found)");
                    continue;
                }
                if (ancestor.orElse(false)) {
                    log.info("Serving the diff {}..{} of {}", base, head, url);
                    return new Diff(base, head, ScmContentEndpoint.DIFF_MODE_COMPARE, client.compare(base, head));
                }
                tried.add(base + ".." + head + (ancestor.isEmpty() ? " (not found)" : " (not an ancestor)"));
            }
        }
        for (String head : headCandidates) {
            Optional<UnifiedDiff> commit = client.commitDiff(head);
            if (commit.isPresent()) {
                log.info("No base is an ancestor of a head in {}; serving the diff of commit {}", url, head);
                return new Diff(null, head, ScmContentEndpoint.DIFF_MODE_COMMIT, commit.get());
            }
            tried.add(head + " (commit not found)");
        }
        throw new ScmContentException(
                ScmContentException.NOT_FOUND,
                "No alignment diff in " + url + ". Tried: " + String.join(", ", tried));
    }

    /**
     * GitLab answers 400 "Could not find ref" when a revision does not exist (and 404 when there is no merge base,
     * which {@link ProviderContentClient#isAncestor} returns as empty).
     */
    private static boolean refNotFound(ScmContentException e) {
        return ProviderHttp.providerStatus(e) == 400
                && ProviderHttp.providerMessage(e).toLowerCase(Locale.ROOT).contains("could not find ref");
    }

    ProviderContentClient client(String url) {
        if (url == null || url.isBlank()) {
            throw new ScmContentException(ScmContentException.BAD_REQUEST, "The repository URL is missing");
        }
        ScmUrlNormalizer parsed;
        try {
            parsed = ScmUrlNormalizer.parse(url);
        } catch (IllegalArgumentException e) {
            throw new ScmContentException(ScmContentException.BAD_REQUEST, "Invalid repository URL: " + url, e);
        }
        String host = parsed.getHost().toLowerCase(Locale.ROOT);
        String path = stripSuffix(parsed.getPath(), ".git");
        if (path.contains("..") || path.contains("%")) {
            throw new ScmContentException(ScmContentException.BAD_REQUEST, "Invalid repository path: " + url);
        }

        GitLabProviderConfig gitlab = config.git().gitProviders().gitlab();
        if (served(gitlab, host, path, gitlab.workspaceName())) {
            ProviderHttp http = new ProviderHttp(httpClient, "PRIVATE-TOKEN", gitlab.token());
            // like gitlab4j, the GitLab url is the instance URL
            return new GitLabContentClient(http, ProviderSupport.stripTrailingSlash(gitlab.url()) + "/api/v4", path);
        }
        GitHubProviderConfig github = config.git().gitProviders().github();
        if (served(github, host, path, github.internalOrganizationName())) {
            String[] parts = path.split("/");
            if (parts.length != 2) {
                throw new ScmContentException(ScmContentException.BAD_REQUEST, "Invalid GitHub repository: " + url);
            }
            ProviderHttp http = new ProviderHttp(httpClient, "Authorization", "Bearer " + github.token());
            // like the GitHub clients of reqour, the GitHub url is the API base URL (https://<host>/api/v3 for GHE)
            return new GitHubContentClient(http, github.url(), parts[0], parts[1]);
        }
        log.info(
                "{} is not under {}/{} nor {}/{}",
                url,
                gitlab.hostname(),
                gitlab.workspaceName(),
                github.hostname(),
                github.internalOrganizationName());
        throw new ScmContentException(
                ScmContentException.BAD_REQUEST,
                url + " is not a repository of an internal git provider served by reqour");
    }

    private static boolean served(GitProviderConfig provider, String host, String path, String owner) {
        return provider.hostname() != null && provider.hostname().equalsIgnoreCase(host) && owner != null
                && !owner.isBlank() && path.toLowerCase(Locale.ROOT).startsWith(owner.toLowerCase(Locale.ROOT) + "/")
                && provider.token() != null && !provider.token().isBlank();
    }

    private static List<String> candidates(List<String> values, String name) {
        List<String> candidates = values == null ? List.of()
                : values.stream().filter(v -> v != null && !v.isBlank()).map(String::trim).distinct().toList();
        if (candidates.isEmpty()) {
            throw new ScmContentException(ScmContentException.BAD_REQUEST, "At least one " + name + " is required");
        }
        if (candidates.size() > MAX_CANDIDATES) {
            throw new ScmContentException(
                    ScmContentException.BAD_REQUEST,
                    "At most " + MAX_CANDIDATES + " " + name + " values are accepted");
        }
        return candidates;
    }

    private static String stripSuffix(String value, String suffix) {
        return value.endsWith(suffix) ? value.substring(0, value.length() - suffix.length()) : value;
    }
}
