/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.config;

public class ConfigConstants {

    public static final String GITLAB = "gitlab";
    public static final String GITHUB = "github";

    public static final String INTERNAL_URLS = "reqour.git.internal-urls";
    public static final String ACTIVE_GIT_PROVIDER = "reqour.git.git-providers.active";
    public static final String OIDC_CLIENT_ENABLED = "quarkus.oidc-client.enabled";
    public static final String OIDC_CLIENT_SECRET = "quarkus.oidc-client.credentials.secret";
    public static final String APP_NAME = "quarkus.application.name";
    public static final String USER_LOGGER_NAME = "reqour.log.user-log.user-logger-name";
}
