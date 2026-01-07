/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.runtime.api.github;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;

import org.eclipse.microprofile.rest.client.ext.ClientHeadersFactory;
import org.jboss.pnc.reqour.config.utils.ConfigUtils;

@ApplicationScoped
public class GitHubRestClientHeadersFactory implements ClientHeadersFactory {

    @Inject
    ConfigUtils configUtils;

    @Override
    public MultivaluedMap<String, String> update(
            MultivaluedMap<String, String> multivaluedMap,
            MultivaluedMap<String, String> multivaluedMap1) {
        MultivaluedMap<String, String> result = new MultivaluedHashMap<>();
        result.add(
                HttpHeaders.AUTHORIZATION,
                String.format("Bearer %s", configUtils.getActiveGitProviderConfig().token()));
        return result;
    }
}
