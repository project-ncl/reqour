/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.config.core;

public class ConfigConstants {

    public static final String GITLAB = "gitlab";
    public static final String GITHUB = "github";

    public static final String INTERNAL_URLS = "reqour.git.internal-urls";
    public static final String GITLAB_PROVIDER_ENABLED = "reqour.git.git-providers.gitlab.enabled";
    public static final String GITHUB_PROVIDER_ENABLED = "reqour.git.git-providers.github.enabled";
    public static final String OIDC_CLIENT_ENABLED = "quarkus.oidc-client.enabled";
    public static final String OIDC_CLIENT_SECRET = "quarkus.oidc-client.credentials.secret";
    public static final String APP_NAME = "quarkus.application.name";
    public static final String USER_LOGGER_NAME = "reqour.log.user-log.user-logger-name";
    public static final String GIT_PROVIDERS = "reqour.git.git-providers";
    public static final String GIT_PROVIDERS_FAULT_TOLERANCE = "reqour.git.git-providers.fault-tolerance";
    public static final String BIFROST_UPLOADER_URL = "reqour.log.final-log.bifrost-uploader.base-url";
    public static final String ADJUSTER_CONFIG = "reqour.adjuster";
    public static final String REST_CONFIG = "reqour.rest";
    public static final String PERMANENT_SUFFIX = "reqour.adjuster.alignment.suffix.permanent";
    public static final String TEMPORARY_PREFIX_OF_VERSION_SUFFIX = "reqour.adjuster.alignment.suffix.temporary-prefix";
    public static final String VALIDATE_ALIGNMENT_CONFIG = "reqour.adjuster.alignment.validate";
    public static final String ADJUSTER_MDC = "reqour.adjuster.mdc";
    public static final String PRIVATE_GITHUB_USER = "reqour.git.private-github-user";

    public static final String TRUE = "true";
    public static final String FALSE = "false";
}
