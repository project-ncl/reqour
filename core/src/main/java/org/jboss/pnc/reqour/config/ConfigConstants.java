/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.config;

public class ConfigConstants {

    //region Quarkus config
    public static final String OIDC_CLIENT_ENABLED = "quarkus.oidc-client.enabled";
    public static final String OIDC_CLIENT_SECRET = "quarkus.oidc-client.credentials.secret";
    public static final String APP_NAME = "quarkus.application.name";
    //endregion

    //region Reqour Core config
    public static final String REQOUR_CORE_CONFIG = "reqour.core";
    public static final String INTERNAL_URLS = REQOUR_CORE_CONFIG + ".git.internal-urls";
    public static final String GITLAB_PROVIDER_ENABLED = REQOUR_CORE_CONFIG + ".git.git-providers.gitlab.enabled";
    public static final String GITHUB_PROVIDER_ENABLED = REQOUR_CORE_CONFIG + ".git.git-providers.github.enabled";
    public static final String USER_LOGGER_NAME = REQOUR_CORE_CONFIG + ".log.user-log.user-logger-name";
    public static final String GIT_PROVIDERS = REQOUR_CORE_CONFIG + ".git.git-providers";
    public static final String GIT_PROVIDERS_FAULT_TOLERANCE = REQOUR_CORE_CONFIG
            + ".git.git-providers.fault-tolerance";
    public static final String BIFROST_UPLOADER_URL = REQOUR_CORE_CONFIG + ".log.final-log.bifrost-uploader.base-url";
    public static final String GITLAB_INTERNAL_URL = REQOUR_CORE_CONFIG
            + ".git.git-providers.gitlab.git-url-internal-template";
    public static final String PRIVATE_GITHUB_USER = REQOUR_CORE_CONFIG + ".git.private-github-user";
    //endregion

    //region Reqour Adjuster config
    public static final String REQOUR_ADJUSTER_CONFIG = "reqour.adjuster";
    public static final String PERMANENT_SUFFIX = REQOUR_ADJUSTER_CONFIG + ".alignment.suffix.permanent";
    public static final String TEMPORARY_PREFIX_OF_VERSION_SUFFIX = REQOUR_ADJUSTER_CONFIG
            + ".alignment.suffix.temporary-prefix";
    public static final String VALIDATE_ALIGNMENT_CONFIG = REQOUR_ADJUSTER_CONFIG + ".alignment.validate";
    public static final String ADJUSTER_MDC = REQOUR_ADJUSTER_CONFIG + ".mdc";
    //endregion

    //region Reqour Rest config
    public static final String REQOUR_REST_CONFIG = "reqour.rest";
    //endregion

    //region miscs
    public static final String TRUE = "true";
    public static final String FALSE = "false";
    //endregion
}
