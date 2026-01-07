/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.runtime.api.github;

import java.util.List;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;

import org.jboss.pnc.reqour.runtime.api.github.model.GHRuleset;

/**
 * Custom client for <a href="https://docs.github.com/en/rest?apiVersion=2022-11-28">GitHub's REST API</a>.<br/>
 * This is required because not everything is covered by <a href="https://hub4j.github.io/github-api">GitHub REST Java
 * client</a>, which is used from the codebase.
 */
public interface GitHubRestClient {

    @GET
    @Path("/orgs/{org}/rulesets")
    List<GHRuleset> getAllRulesets(@PathParam("org") String org);

    @GET
    @Path("/orgs/{org}/rulesets/{rulesetId}")
    GHRuleset getRuleset(@PathParam("org") String org, @PathParam("rulesetId") Integer ruleset);
}
