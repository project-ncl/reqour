/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.common.exceptions;

import org.gitlab4j.api.GitLabApiException;

/**
 * Own GitLab API exception, which is used to wrap {@link org.gitlab4j.api.GitLabApiException} in order to make it
 * unchecked exception.
 */
public class GitlabApiRuntimeException extends RuntimeException {

    public GitlabApiRuntimeException(GitLabApiException cause) {
        super(cause);
    }
}
