/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.runtime;

import java.time.Duration;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import org.jboss.pnc.reqour.config.core.ConfigConstants;

import io.quarkus.arc.lookup.LookupIfProperty;
import io.quarkus.arc.profile.IfBuildProfile;
import io.quarkus.oidc.client.OidcClient;
import io.quarkus.oidc.client.Tokens;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;

@ApplicationScoped
@LookupIfProperty(name = ConfigConstants.OIDC_CLIENT_ENABLED, stringValue = "false")
@IfBuildProfile(anyOf = { "dev", "test" })
/*
 * To be able to start in development/test mode without authorization
 */
public class OidcClientAlternative {

    @Produces
    public OidcClient produceToken() {
        return new OidcAlt();
    }

    private static class OidcAlt implements OidcClient {

        @Override
        public Uni<Tokens> getTokens(Map<String, String> additionalGrantParameters) {
            return Uni.createFrom()
                    .item(
                            new Tokens(
                                    "access-token",
                                    Long.MAX_VALUE,
                                    Duration.ofNanos(Long.MAX_VALUE),
                                    "refresh-token",
                                    Long.MAX_VALUE,
                                    JsonObject.of(),
                                    "*"));
        }

        @Override
        public Uni<Tokens> refreshTokens(String refreshToken, Map<String, String> additionalGrantParameters) {
            return getTokens();
        }

        @Override
        public Uni<Boolean> revokeAccessToken(String accessToken, Map<String, String> additionalParameters) {
            return Uni.createFrom().item(true);
        }

        @Override
        public void close() {
        }
    }
}
