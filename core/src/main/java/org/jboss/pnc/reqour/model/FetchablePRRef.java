package org.jboss.pnc.reqour.model;

/**
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
public record FetchablePRRef(String refToFetchableBranch, String branch) {

    public static FetchablePRRef getDefault() {
        return new FetchablePRRef(null, null);
    }
}
