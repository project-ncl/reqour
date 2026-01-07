/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.runtime.api.bifrost;

import lombok.Getter;

@Getter
public enum FinalLogUploader {

    ADJUSTER("reqour-adjuster"), REST("reqour-rest");

    private final String name;

    FinalLogUploader(String name) {
        this.name = name;
    }
}
