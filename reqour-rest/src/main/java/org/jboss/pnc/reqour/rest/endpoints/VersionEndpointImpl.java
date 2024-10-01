/**
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.rest.endpoints;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.pnc.api.dto.ComponentVersion;
import org.jboss.pnc.api.endpoints.VersionEndpoint;
import org.jboss.pnc.reqour.BuildInformationConstants;

import java.time.ZonedDateTime;

@ApplicationScoped
public class VersionEndpointImpl implements VersionEndpoint {

    @ConfigProperty(name = "quarkus.application.name")
    String name;

    @Override
    public ComponentVersion getVersion() {
        return ComponentVersion.builder()
                .name(name)
                .version(BuildInformationConstants.VERSION)
                .commit(BuildInformationConstants.COMMIT_HASH)
                .builtOn(ZonedDateTime.parse(BuildInformationConstants.BUILD_TIME))
                .build();
    }
}
