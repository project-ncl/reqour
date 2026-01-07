/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.runtime.api.github.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum GHRulesetTarget {

    @JsonProperty("branch") BRANCH,

    @JsonProperty("tag") TAG,

    @JsonProperty("push") PUSH,

    @JsonProperty("repository") REPOSITORY,
    ;
}
