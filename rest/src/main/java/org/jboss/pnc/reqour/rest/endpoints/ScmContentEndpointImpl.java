/*
 * SPDX-FileCopyrightText: Copyright © 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.rest.endpoints;

import java.io.InputStream;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;

import org.jboss.pnc.api.reqour.rest.ScmContentEndpoint;
import org.jboss.pnc.reqour.service.scmcontent.ScmContentService;

import io.quarkus.security.Authenticated;
import lombok.extern.slf4j.Slf4j;

/**
 * Read-only: any authenticated caller, like the PNC endpoints that expose this content. The repository is limited to
 * the internal locations by the service.
 */
@ApplicationScoped
@Slf4j
public class ScmContentEndpointImpl implements ScmContentEndpoint {

    @Inject
    ScmContentService service;

    @Override
    @Authenticated
    public Response archive(String url, List<String> revisions) {
        ScmContentService.Archive archive = service.archive(url, revisions);
        StreamingOutput body = out -> {
            try (InputStream in = archive.content()) {
                in.transferTo(out);
            }
        };
        return Response.ok(body, GZIP).header(REVISION_HEADER, archive.revision()).build();
    }

    @Override
    @Authenticated
    public Response diff(String url, List<String> bases, List<String> heads) {
        ScmContentService.Diff diff = service.diff(url, bases, heads);
        if (!diff.diff().isComplete()) {
            log.warn("Diff of {} without the content of {}", url, diff.diff().getIncompleteFiles());
        }
        // the diff bytes as they are in the repository: files are not necessarily UTF-8
        StreamingOutput body = out -> diff.diff().writeTo(out);
        Response.ResponseBuilder response = Response.ok(body, DIFF)
                .header(DIFF_HEAD_HEADER, diff.head())
                .header(DIFF_MODE_HEADER, diff.mode());
        if (diff.base() != null) {
            response.header(DIFF_BASE_HEADER, diff.base());
        }
        return response.build();
    }
}
